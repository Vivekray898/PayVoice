/**
 * PayVoice device pairing — Supabase Edge Function (optional fast path).
 *
 * NOT required for pairing: the Android client normally calls the atomic
 * `claim_pairing` Postgres function directly (PostgREST /rpc/claim_pairing),
 * which enforces expiry + single-use server-side and returns the owner uid.
 *
 * This function exists for the same operation through the Functions API —
 * useful when a client cannot use PostgREST (or for server-side testing):
 *   POST {SUPABASE_URL}/functions/v1/claim-pairing
 *   Authorization: Bearer <user JWT>   (employee device identity)
 *   { "code": "PAY-XXXXXX", "deviceName": "..." }
 *
 * It authenticates the caller, validates the code, claims it atomically
 * (same invariants as the SQL function) and registers the device row.
 */
// @ts-nocheck — Deno edge function, no typechecked deps in this repo.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;

const CODE_REGEX = /^PAY-[2-9A-HJ-NP-Z]{6}$/;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "POST only" }), { status: 405 });
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const bearer = authHeader.replace(/^Bearer\s+/i, "");
  if (!bearer) {
    return new Response(JSON.stringify({ error: "unauthenticated" }), { status: 401 });
  }

  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return new Response(JSON.stringify({ error: "bad json" }), { status: 400 });
  }
  const body = raw as Record<string, unknown>;
  const code = typeof body.code === "string" ? body.code.trim().toUpperCase() : "";
  const deviceName =
    typeof body.deviceName === "string" && body.deviceName.trim()
      ? body.deviceName.trim().slice(0, 40)
      : "Employee Device";
  if (!CODE_REGEX.test(code)) {
    return new Response(JSON.stringify({ ok: false, error: "bad-format" }), { status: 422 });
  }

  // Resolve the caller: user JWTs resolve via /auth/v1/user; anything else
  // (service key, anon key, garbage) is rejected — only a device may claim.
  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: `Bearer ${bearer}` },
  }).catch(() => null);
  const user = userRes && userRes.ok ? ((await userRes.json()) as { id?: string }) : null;
  if (!user?.id) {
    return new Response(JSON.stringify({ ok: false, error: "unauthenticated" }), { status: 401 });
  }

  const admin = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  // Atomic claim: same invariants as claim_pairing() — single-use + expiry.
  const { data: codeRow, error } = await admin
    .from("pairing_codes")
    .select("owner_uid, used, expires_at")
    .eq("code", code)
    .single();
  if (error || !codeRow) {
    return new Response(JSON.stringify({ ok: false, error: "invalid-or-expired" }), { status: 200 });
  }
  const nowMs = Date.now();
  if (codeRow.used || Number(codeRow.expires_at) <= nowMs) {
    return new Response(JSON.stringify({ ok: false, error: "invalid-or-expired" }), { status: 200 });
  }
  const claimed = await admin
    .from("pairing_codes")
    .update({ used: true })
    .eq("code", code)
    .eq("used", false);
  if (claimed.error || (claimed.count ?? 0) === 0) {
    return new Response(JSON.stringify({ ok: false, error: "already-used" }), { status: 200 });
  }

  const upsert = await admin.from("employees").upsert(
    {
      id: user.id,
      owner_uid: codeRow.owner_uid,
      name: deviceName,
      status: "ACTIVE",
      paired_at: nowMs,
      last_seen_at: nowMs,
    },
    { onConflict: "id" },
  );
  if (upsert.error) {
    return new Response(JSON.stringify({ ok: false, error: "db-error" }), { status: 500 });
  }

  return new Response(JSON.stringify({ ok: true, owner_uid: codeRow.owner_uid }), { status: 200 });
});
