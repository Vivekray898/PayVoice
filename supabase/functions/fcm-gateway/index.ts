/**
 * PayVoice fcm-gateway — Supabase Edge Function (Deno, Web APIs only).
 *
 * CURRENT auth model (no legacy keys, no legacy FCM):
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
 * Secrets (Supabase Dashboard → Edge Functions → Secrets):
 *   FCM_PROJECT_ID, FCM_CLIENT_EMAIL, FCM_PRIVATE_KEY
 */
import { withSupabase } from "npm:@supabase/server";

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

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

/**
 * Mints (and caches until near-expiry) a Google OAuth 2.0 access token from
 * the service-account secrets, signing a JWT with RS256 via WebCrypto —
 * the documented non-SDK flow for the FCM HTTP v1 API.
 */
async function getAccessToken(): Promise<string> {
  const now = Date.now();
  if (cachedToken && cachedToken.expiresAtMs - 60_000 > now) {
    return cachedToken.token;
  }
  const clientEmail = Deno.env.get("FCM_CLIENT_EMAIL");
  const privateKey = Deno.env.get("FCM_PRIVATE_KEY");
  const projectId = Deno.env.get("FCM_PROJECT_ID");
  if (!clientEmail || !privateKey || !projectId) {
    throw new Error("FCM service-account secrets not configured");
  }
  const claim = { iss: clientEmail, scope: FCM_SCOPE, aud: "https://oauth2.googleapis.com/token" };
  const enc = new TextEncoder();
  const header = { alg: "RS256", typ: "JWT" };
  const unsigned = btoa(JSON.stringify(header)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "") +
    "." +
    btoa(JSON.stringify(claim)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const pem = privateKey.replace(/\\n/g, "\n");
  const keyData = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
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
    enc.encode(unsigned),
  );
  let bin = "";
  new Uint8Array(signature).forEach((b) => (bin += String.fromCharCode(b)));
  const sig = btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const assertion = `${unsigned}.${sig}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) {
    throw new Error(`oauth token exchange failed: ${res.status}`);
  }
  const json = await res.json();
  cachedToken = {
    token: json.access_token,
    expiresAtMs: now + Number(json.expires_in ?? 3600) * 1000,
  };
  return cachedToken.token;
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

    // ACTIVE device tokens of THIS owner's employees (secret key = BYPASSRLS).
    // Schema: employees.owner_uid (never owner_id — see 0001_init.sql).
    const { data: activeEmployees, error: employeesError } = await ctx.supabaseAdmin
      .from("employees")
      .select("id")
      .eq("owner_uid", event.ownerUid)
      .eq("status", "ACTIVE");
    if (employeesError) {
      console.error("employees query failed");
      return Response.json({ error: "db error" }, { status: 500 });
    }
    const employeeIds = (activeEmployees ?? []).map((e: { id: string }) => e.id);
    if (employeeIds.length === 0) {
      // Nothing to fan out to; still record diagnostics on the event row.
      await ctx.supabaseAdmin.from("payment_events")
        .update({ fanout_count: 0, delivered_to: {}, delivered_at: new Date().toISOString() })
        .eq("id", event.eventId);
      return Response.json({ fanout: 0, delivered: 0 });
    }

    const { data: devices, error: devicesError } = await ctx.supabaseAdmin
      .from("devices")
      .select("id, user_id, fcm_token")
      .in("user_id", employeeIds)
      .eq("is_active", true)
      .neq("fcm_token", "");

    if (devicesError) {
      console.error("devices query failed");
      return Response.json({ error: "db error" }, { status: 500 });
    }

    const deviceList = (devices ?? []) as Array<{ id: number; user_id: string; fcm_token: string }>;

    // Minimal data payload (spec §16): no credentials, no PII beyond the
    // announcement-required sender name, no raw SMS/notification content.
    const data: Record<string, string> = {
      type: event.type,
      eventId: event.eventId,
    };
    if (event.type === "PAYMENT_RECEIVED") {
      data.amountMinor = String(event.amountMinor);
      data.currency = "INR";
      data.senderName = event.senderName ?? "";
      data.source = event.source ?? "GOOGLE_PAY";
      data.timestampMs = String(event.timestampMs);
    }

    const projectId = Deno.env.get("FCM_PROJECT_ID")!;
    const endpoint = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
    let accessToken: string;
    try {
      accessToken = await getAccessToken();
    } catch (e) {
      console.error("fcm oauth failure");
      return Response.json({ error: "fcm auth failure" }, { status: 502 });
    }

    const deliveredTo: Record<string, boolean> = {};
    let delivered = 0;
    await Promise.all(deviceList.map(async (device) => {
      deliveredTo[device.user_id] = false;
      try {
        const res = await fetch(endpoint, {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${accessToken}`,
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
          delivered += 1;
          deliveredTo[device.user_id] = true;
          return;
        }
        const errText = await res.text();
        // Invalid/unregistered tokens are deactivated so they are never
        // retried (spec §15.6).
        if (res.status === 404 || res.status === 410 || errText.includes("UNREGISTERED")) {
          await ctx.supabaseAdmin.from("devices")
            .update({ is_active: false })
            .eq("id", device.id);
        } else {
          console.error(`fcm send failed status=${res.status}`);
        }
      } catch (_e) {
        console.error("fcm send error");
      }
    }));

    // Delivery diagnostics on the event row (visible to the owner via RLS)
    // and on each employee row (last_event_delivered_at feeds the owner card).
    await ctx.supabaseAdmin.from("payment_events")
      .update({
        fanout_count: deviceList.length,
        delivered_to: deliveredTo,
        delivered_at: new Date().toISOString(),
        remote_accepted_at_ms: Date.now(),
      })
      .eq("id", event.eventId);
    if (delivered > 0) {
      const nowMs = Date.now();
      await Promise.all(deviceList
        .filter((d) => deliveredTo[d.user_id])
        .map((d) => ctx.supabaseAdmin.from("employees")
          .update({ last_event_delivered_at: nowMs })
          .eq("id", d.user_id)));
    }

    console.log(`event ${event.eventId.slice(0, 12)} fanout=${deviceList.length} delivered=${delivered}`);
    return Response.json({ fanout: deviceList.length, delivered });
  }),
};
