/**
 * PayVoice Owner→Employee fan-out — Supabase Edge Function (Deno).
 *
 * The Android client NEVER holds privileged credentials (spec §5). This
 * function is the ONLY component that sends FCM; it runs server-side and
 * reads the FCM server key from Supabase secrets (`FCM_SERVER_KEY`).
 *
 * Invocation:
 *   payment_events AFTER INSERT trigger (pg_net) → POST here with the event
 *   payload and `Authorization: Bearer <service JWT>` (see migration §5).
 *
 * Flow:
 *   trigger → this function → validate payload → ACTIVE employees of
 *   ownerUid → FCM data messages (high priority) → deliveredTo recorded.
 *
 * Defense in depth: the Android validator already gates the employee side;
 * this function re-validates everything before spending an FCM send.
 */
// @ts-nocheck — Deno edge function, no typechecked deps in this repo.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const FCM_ENDPOINT = "https://fcm.googleapis.com/fcm/send";

/** Mirrors the Android RemoteEventValidator (spec §30). Returns null if invalid. */
function parseEvent(body: Record<string, unknown>) {
  if (!body || typeof body !== "object") return null;
  const eventId = typeof body.eventId === "string" ? body.eventId : null;
  if (!eventId || !eventId.startsWith("evt_") || eventId.length > 64) return null;
  const type = body.type;
  if (type !== "PAYMENT_RECEIVED" && type !== "TEST_ANNOUNCEMENT") return null;
  const ownerUid = typeof body.ownerUid === "string" ? body.ownerUid : null;
  if (!ownerUid) return null;
  if (type === "TEST_ANNOUNCEMENT") {
    return { eventId, type, ownerUid };
  }
  const amountMinor = Number(body.amountMinor);
  if (!Number.isInteger(amountMinor) || amountMinor <= 0 || amountMinor > 1e11) return null;
  if (body.currency !== "INR") return null;
  const timestampMs = Number(body.timestampMs);
  if (!Number.isInteger(timestampMs)) return null;
  const senderName =
    typeof body.senderName === "string" && body.senderName.trim()
      ? body.senderName.trim().slice(0, 40)
      : null;
  return {
    eventId,
    type,
    ownerUid,
    amountMinor,
    senderName,
    source: typeof body.source === "string" ? body.source.slice(0, 24) : "GOOGLE_PAY",
    timestampMs,
  };
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "POST only" }), { status: 405 });
  }

  // Only the database trigger may call this function: it authenticates with
  // the service role key. User JWTs (employees/owners) are rejected — an
  // authenticated client must never be able to mint FCM sends directly.
  const authHeader = req.headers.get("Authorization") ?? "";
  const bearer = authHeader.replace(/^Bearer\s+/i, "");
  if (!bearer || bearer === Deno.env.get("SUPABASE_ANON_KEY")) {
    return new Response(JSON.stringify({ error: "forbidden" }), { status: 403 });
  }
  const serviceCheck = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${bearer}`,
    },
  }).catch(() => null);
  if (!serviceCheck || !serviceCheck.ok) {
    // Not a valid service JWT (user tokens resolve to an auth user; the
    // service role key resolves without one).
    return new Response(JSON.stringify({ error: "forbidden" }), { status: 403 });
  }

  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return new Response(JSON.stringify({ error: "bad json" }), { status: 400 });
  }
  const event = parseEvent(raw as Record<string, unknown>);
  if (!event) {
    return new Response(JSON.stringify({ error: "invalid event" }), { status: 422 });
  }

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

  // ACTIVE employees of THIS owner only (spec §28: no cross-owner access).
  const { data: employees, error: employeesError } = await admin
    .from("employees")
    .select("id, fcm_token")
    .eq("owner_uid", event.ownerUid)
    .eq("status", "ACTIVE");
  if (employeesError) {
    return new Response(JSON.stringify({ error: "db error" }), { status: 500 });
  }

  const deliveredTo: Record<string, boolean> = {};
  const messages = (employees ?? [])
    .map((row: { id: string; fcm_token: string | null }) => ({
      row,
      token: row.fcm_token,
    }))
    .filter((m: { token: string | null }) => !!m.token);

  let deliveredCount = 0;
  const serverKey = Deno.env.get("FCM_SERVER_KEY");
  if (!serverKey) {
    // Honest failure: without the secret nothing can be delivered.
    return new Response(JSON.stringify({ error: "FCM_SERVER_KEY not configured" }), {
      status: 500,
    });
  }

  await Promise.all(
    messages.map(async ({ row, token }: { row: { id: string }; token: string }) => {
      deliveredTo[row.id] = true;
      // DATA message (spec §10): employee processes without any tap. High
      // priority for time-sensitive delivery through Doze.
      const payload: Record<string, unknown> = {
        to: token,
        priority: "high",
        ttl: 3600,
        data: {
          type: event.type,
          eventId: event.eventId,
          ...(event.type === "PAYMENT_RECEIVED"
            ? {
                amount: String(event.amountMinor),
                currency: "INR",
                senderName: event.senderName ?? "",
                source: event.source,
                timestamp: String(event.timestampMs),
              }
            : {}),
        },
      };
      try {
        const res = await fetch(FCM_ENDPOINT, {
          method: "POST",
          headers: {
            Authorization: `key=${serverKey}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(payload),
        });
        if (res.ok) deliveredCount += 1;
        else deliveredTo[row.id] = false;
      } catch {
        deliveredTo[row.id] = false;
      }
    }),
  );

  // Delivery diagnostics written back for the Owner (RLS: own rows only).
  await admin
    .from("payment_events")
    .update({
      fanout_count: employees?.length ?? 0,
      delivered_to: deliveredTo,
      delivered_at: new Date().toISOString(),
      remote_accepted_at_ms: Date.now(),
    })
    .eq("id", event.eventId);

  console.log(
    `event ${event.eventId.slice(0, 12)} type=${event.type} ` +
      `fanout=${employees?.length ?? 0} delivered=${deliveredCount}`,
  );
  return new Response(
    JSON.stringify({ fanout: employees?.length ?? 0, delivered: deliveredCount }),
    { status: 200 },
  );
});
