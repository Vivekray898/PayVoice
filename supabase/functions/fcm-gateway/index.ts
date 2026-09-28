/**
 * PayVoice fcm-gateway — Supabase Edge Function (Deno, Web APIs only).
 *
 * Auth model (no legacy keys, no legacy FCM):
 *  - Caller: the Supabase Database Webhook on payment_events INSERT, which
 *    authenticates with a SECRET KEY on the `apikey` header
 *    (`auth: 'secret'` below, `verify_jwt = false`). User JWTs and publishable
 *    keys are REJECTED. No `app.settings.service_jwt` exists anywhere.
 *  - FCM: HTTP v1 — POST https://fcm.googleapis.com/v1/projects/{PROJECT_ID}/messages:send
 *    with `Authorization: Bearer <short-lived OAuth 2.0 access token>` minted
 *    from the FCM_CLIENT_EMAIL / FCM_PRIVATE_KEY / FCM_PROJECT_ID function
 *    secrets. The legacy `Authorization: key=SERVER_KEY` flow is never used.
 *  - Device management: reads ACTIVE device tokens from `devices`; deactivates
 *    rows whose token FCM reports as UNREGISTERED (never retried again).
 *
 * Execution model (fixes the EarlyDrop / webhook-timeout kill):
 *  Database Webhooks time the function out (~6s) and DROP in-flight work —
 *  the OAuth exchange + FCM fan-out regularly exceeded that window. The
 *  handler therefore returns `202 Accepted` IMMEDIATELY and runs the whole
 *  fan-out via `EdgeRuntime.waitUntil()` in the background. Diagnostics land
 *  on the payment_events row when the background work finishes.
 *
 * Secrets (Supabase Dashboard → Edge Functions → Secrets):
 *   FCM_PROJECT_ID, FCM_CLIENT_EMAIL, FCM_PRIVATE_KEY
 */
import { withSupabase } from "npm:@supabase/server";

/// Supabase Edge Runtime global (present on hosted runtime; guarded below).
declare const EdgeRuntime: { waitUntil(p: Promise<unknown>): void };

/** Run work past the response edge; falls back to inline on other runtimes. */
function backgroundWork(p: Promise<void>): void {
  try {
    EdgeRuntime.waitUntil(p);
  } catch {
    // Not running on Supabase Edge Runtime (e.g. local `deno run`).
    // Fire-and-forget; errors already surface through logStage.
    p.catch(() => {});
  }
}

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";

interface GatewayEvent {
  eventId: string;
  ownerUid: string;
  type: "PAYMENT_RECEIVED" | "TEST_ANNOUNCEMENT";
  amountMinor?: number | string | null;
  currency?: string | null;
  senderName?: string | null;
  source?: string | null;
  timestampMs?: number | string | null;
}

/** Mirrors the Android RemoteEventValidator (spec §30). Returns null if invalid. */
function parseEvent(raw: unknown): GatewayEvent | null {
  if (!raw || typeof raw !== "object") return null;
  const body = raw as Record<string, unknown>;
  // Database Webhooks deliver the row under `record` (and `type: "INSERT"`).
  const row = (body.record ?? body) as Record<string, unknown>;
  const eventId = typeof row.id === "string" ? row.id : null;
  if (!eventId || !eventId.startsWith("evt_") || eventId.length > 64) return null;
  const type = row.type;
  if (type !== "PAYMENT_RECEIVED" && type !== "TEST_ANNOUNCEMENT") return null;
  const ownerUid = typeof row.owner_uid === "string" ? row.owner_uid : null;
  if (!ownerUid) return null;
  if (type === "TEST_ANNOUNCEMENT") return { eventId, type, ownerUid };
  const amountMinor = Number(row.amount_minor);
  if (!Number.isInteger(amountMinor) || amountMinor <= 0 || amountMinor > 1e11) return null;
  if (row.currency !== "INR") return null;
  const timestampMs = Number(row.timestamp_ms);
  if (!Number.isInteger(timestampMs)) return null;
  const senderName =
    typeof row.sender_name === "string" && row.sender_name.trim()
      ? row.sender_name.trim().slice(0, 40)
      : null;
  return {
    eventId,
    type,
    ownerUid,
    amountMinor,
    currency: "INR",
    senderName,
    source: typeof row.source === "string" ? row.source.slice(0, 24) : "GOOGLE_PAY",
    timestampMs,
  };
}

// ---- FCM HTTP v1 with a short-lived OAuth 2.0 access token -----------------

let cachedToken: { token: string; expiresAtMs: number } | null = null;

function base64UrlDecode(input: string): Uint8Array {
  const b64 = input.replace(/-/g, "+").replace(/_/g, "/").padEnd(
    Math.ceil(input.length / 4) * 4,
    "=",
  );
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function base64UrlEncode(input: string): string {
  return btoa(input).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * Mints (and caches until near-expiry) a Google OAuth 2.0 access token from
 * the service-account secrets, signing a JWT with RS256 via WebCrypto —
 * the documented non-SDK flow for the FCM HTTP v1 API.
 *
 * The JWT MUST include `iat` and `exp` (Google rejects a JWT without them:
 * `invalid_grant: Invalid JWT: iat (issued at) is not set.`). `iat` is
 * backdated 30s to tolerate clock skew between the edge runtime and Google.
 */
async function getAccessToken(): Promise<string> {
  const nowMs = Date.now();
  if (cachedToken && cachedToken.expiresAtMs - 60_000 > nowMs) {
    return cachedToken.token;
  }

  const clientEmail = Deno.env.get("FCM_CLIENT_EMAIL");
  const privateKey = Deno.env.get("FCM_PRIVATE_KEY");
  const projectId = Deno.env.get("FCM_PROJECT_ID");
  if (!clientEmail || !privateKey || !projectId) {
    throw new Error(
      "FCM service-account secrets not configured" +
        (!clientEmail ? " (missing FCM_CLIENT_EMAIL)" : "") +
        (!privateKey ? " (missing FCM_PRIVATE_KEY)" : "") +
        (!projectId ? " (missing FCM_PROJECT_ID)" : ""),
    );
  }

  // ---- JWT claim: iss, scope, aud, iat, exp (all five required) ----
  const nowSec = Math.floor(nowMs / 1000);
  const claim = {
    iss: clientEmail,
    scope: FCM_SCOPE,
    aud: OAUTH_TOKEN_URL,
    iat: nowSec - 30,        // backdate 30s to survive clock skew
    exp: nowSec + 3600,      // Google caps this flow at 1 hour
  };
  const header = { alg: "RS256", typ: "JWT" };

  const unsigned =
    base64UrlEncode(JSON.stringify(header)) +
    "." +
    base64UrlEncode(JSON.stringify(claim));

  // ---- Parse the private key defensively ----
  let keyData: string;
  try {
    // Accept both literal `\n` (from JSON/env-file) and real newlines.
    const pem = privateKey.includes("\\n")
      ? privateKey.replace(/\\n/g, "\n")
      : privateKey;
    keyData = pem
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replace(/\s+/g, "");
    if (keyData.length < 100) {
      throw new Error("private key body too short — check escaping");
    }
  } catch (e) {
    throw new Error(
      `FCM_PRIVATE_KEY parse failure: ${e instanceof Error ? e.message : String(e)}`,
    );
  }

  // ---- Sign with RS256 ----
  let assertion: string;
  try {
    const key = await crypto.subtle.importKey(
      "pkcs8",
      base64UrlDecode(keyData),
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const signature = await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      key,
      new TextEncoder().encode(unsigned),
    );
    let bin = "";
    new Uint8Array(signature).forEach((b) => (bin += String.fromCharCode(b)));
    const sig = btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    assertion = `${unsigned}.${sig}`;
  } catch (e) {
    throw new Error(
      `JWT signing failed: ${e instanceof Error ? e.message : String(e)}`,
    );
  }

  // ---- Exchange the assertion for an access token ----
  const res = await fetch(OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  if (!res.ok) {
    // Surface Google's verdict (invalid_grant / invalid_client / ...) — never
    // swallow it into a bare "oauth failure".
    const body = await res.text().catch(() => "");
    throw new Error(
      `oauth token exchange failed: ${res.status} ${body.slice(0, 300)}`,
    );
  }

  const json = await res.json();
  if (!json.access_token) {
    throw new Error("oauth token exchange returned no access_token");
  }
  cachedToken = {
    token: json.access_token,
    expiresAtMs: nowMs + Number(json.expires_in ?? 3600) * 1000,
  };
  return cachedToken.token;
}

// ---- Background fan-out -----------------------------------------------------

/**
 * The whole delivery pipeline, run in the background after the webhook has
 * already been answered. Everything here is best-effort: failures are logged
 * as RemoteDelivery stages and stamped on the event row for the owner.
 */
async function processFanout(
  ctx: { supabaseAdmin: any },
  event: GatewayEvent,
  logStage: (stage: string, extra?: Record<string, unknown>) => void,
): Promise<void> {
  // ---- 1. Find ACTIVE employees of THIS owner ----
  const { data: activeEmployees, error: employeesError } = await ctx.supabaseAdmin
    .from("employees")
    .select("id")
    .eq("owner_uid", event.ownerUid)
    .eq("status", "ACTIVE");

  if (employeesError) {
    logStage("failed", { reason: "employees-query-error", detail: String(employeesError.message ?? employeesError).slice(0, 160) });
    await stampEvent(ctx, event.eventId, { fanout_count: 0, delivered_to: {} });
    return;
  }

  const employeeIds = (activeEmployees ?? []).map((e: { id: string }) => e.id);
  if (employeeIds.length === 0) {
    await stampEvent(ctx, event.eventId, { fanout_count: 0, delivered_to: {} });
    logStage("fanout_finished", { employees: 0, delivered: 0 });
    return;
  }

  // ---- 2. Find active devices for those employees ----
  const { data: devices, error: devicesError } = await ctx.supabaseAdmin
    .from("devices")
    .select("id, user_id, fcm_token")
    .in("user_id", employeeIds)
    .eq("is_active", true)
    .neq("fcm_token", "");

  if (devicesError) {
    logStage("failed", { reason: "devices-query-error", detail: String(devicesError.message ?? devicesError).slice(0, 160) });
    await stampEvent(ctx, event.eventId, { fanout_count: 0, delivered_to: {} });
    return;
  }

  const deviceList = (devices ?? []) as Array<{
    id: number;
    user_id: string;
    fcm_token: string;
  }>;

  if (deviceList.length === 0) {
    await stampEvent(ctx, event.eventId, { fanout_count: 0, delivered_to: {} });
    logStage("fanout_finished", { employees: employeeIds.length, delivered: 0 });
    return;
  }

  // ---- 3. Build the FCM data payload ----
  // Minimal (spec §16): no credentials, no PII beyond the announcement-
  // required sender name. Keys MUST match the Android RemotePaymentEvent
  // canonical key set (RemoteEventValidator rejects e.g. `timestamp` vs
  // `timestampMs`). ownerUid rides along so the EMPLOYEE can authorize the
  // event against its own paired owner before announcing (spec §5).
  const data: Record<string, string> = {
    type: event.type,
    eventId: event.eventId,
    ownerUid: event.ownerUid,
  };
  if (event.type === "PAYMENT_RECEIVED") {
    data.amountMinor = String(event.amountMinor);
    data.currency = "INR";
    data.senderName = event.senderName ?? "";
    data.source = event.source ?? "GOOGLE_PAY";
    data.timestampMs = String(event.timestampMs);
  }

  // ---- 4. Mint an access token ----
  const projectId = Deno.env.get("FCM_PROJECT_ID")!;
  const endpoint = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  let accessToken: string;
  try {
    accessToken = await getAccessToken();
  } catch (e) {
    const reason = e instanceof Error ? e.message : String(e);
    logStage("failed", { reason: `fcm-auth: ${reason.slice(0, 200)}` });
    await stampEvent(ctx, event.eventId, {
      fanout_count: deviceList.length,
      delivered_to: {},
    });
    return;
  }

  logStage("fanout_started", {
    employees: employeeIds.length,
    tokens: deviceList.length,
  });

  // ---- 5. Fan out to each device ----
  const deliveredTo: Record<string, boolean> = {};
  let delivered = 0;

  await Promise.all(
    deviceList.map(async (device) => {
      deliveredTo[device.user_id] = false;
      try {
        const res = await fetch(endpoint, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json; UTF-8",
          },
          body: JSON.stringify({
            message: {
              token: device.fcm_token,
              android: { priority: "HIGH", ttl: "3600s" },
              data,
            },
          }),
        });

        if (res.ok) {
          const body = (await res.json().catch(() => null)) as { name?: string } | null;
          delivered += 1;
          deliveredTo[device.user_id] = true;
          logStage("fcm_accepted", {
            status: res.status,
            messageId: body?.name?.slice(0, 24),
          });
          return;
        }

        const errText = await res.text().catch(() => "");
        // Invalid/unregistered tokens are deactivated so they are never
        // retried (spec §15.6).
        if (
          res.status === 404 ||
          res.status === 410 ||
          errText.includes("UNREGISTERED")
        ) {
          await ctx.supabaseAdmin
            .from("devices")
            .update({ is_active: false })
            .eq("id", device.id);
          logStage("fcm_send_failed", {
            status: res.status,
            reason: "unregistered-token-deactivated",
          });
        } else {
          logStage("fcm_send_failed", {
            status: res.status,
            detail: errText.slice(0, 160),
          });
        }
      } catch (_e) {
        logStage("fcm_send_failed", { reason: "network-error" });
      }
    }),
  );

  // ---- 6. Record diagnostics on the event row ----
  await stampEvent(ctx, event.eventId, {
    fanout_count: deviceList.length,
    delivered_to: deliveredTo,
    remote_accepted_at_ms: Date.now(),
  });

  if (delivered > 0) {
    const nowMs = Date.now();
    await Promise.all(
      deviceList
        .filter((d) => deliveredTo[d.user_id])
        .map((d) =>
          ctx.supabaseAdmin
            .from("employees")
            .update({ last_event_delivered_at: nowMs })
            .eq("id", d.user_id),
        ),
    );
  }

  logStage("fanout_finished", { delivered });
}

/** Stamp delivery diagnostics on the payment_events row (best-effort). */
async function stampEvent(
  ctx: { supabaseAdmin: any },
  eventId: string,
  patch: Record<string, unknown>,
): Promise<void> {
  try {
    await ctx.supabaseAdmin
      .from("payment_events")
      .update({ ...patch, delivered_at: new Date().toISOString() })
      .eq("id", eventId);
  } catch (_e) {
    // Never let a diagnostics write take down the fan-out.
  }
}

// ---- Handler ----------------------------------------------------------------

export default {
  fetch: withSupabase({ auth: "secret" }, async (req: Request, ctx) => {
    // Only the Database Webhook (secret key) may reach this function.
    // publishable/user callers never get past the wrapper.

    let raw: unknown;
    try {
      raw = await req.json();
    } catch {
      return Response.json({ error: "bad json" }, { status: 400 });
    }

    const event = parseEvent(raw);
    if (!event) {
      return Response.json({ error: "invalid event" }, { status: 422 });
    }

    // Structured fan-out diagnostics (spec §8). Tokens are NEVER logged —
    // only counts and the (already non-secret) event id.
    const logStage = (stage: string, extra: Record<string, unknown> = {}) =>
      console.log(
        `RemoteDelivery event=${event.eventId.slice(0, 12)} stage=${stage}` +
          Object.entries(extra)
            .map(([k, v]) => ` ${k}=${v}`)
            .join(""),
      );

    // Respond IMMEDIATELY so the Database Webhook can never time the
    // execution out (the EarlyDrop signature). The DB queries, OAuth token
    // mint and FCM fan-out continue in the background.
    backgroundWork(
      processFanout(ctx, event, logStage).catch((e) => {
        const reason = e instanceof Error ? e.message : String(e);
        logStage("failed", { reason: reason.slice(0, 200) });
      }),
    );

    return Response.json(
      { accepted: true, eventId: event.eventId },
      { status: 202 },
    );
  }),
};