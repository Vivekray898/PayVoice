package com.vivekray898.payvoice.core.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pairing (spec §4, §16, §17) — Supabase.
 *
 * Owner side: writes a short-lived, single-use code row. The code string is
 * only a lookup key — no owner id is embedded in it and no secret material
 * is exposed; RLS verifies the writer's auth.uid().
 *
 * Employee side: [acceptCode] calls the atomic `claim_pairing` Postgres
 * function (security definer): it marks the code used AND upserts this
 * device's `employees` row in ONE statement — expiry/single-use are enforced
 * server-side, never by client state. The employee never reads the
 * `pairing_codes` table directly (RLS denies SELECT).
 *
 * The Android app never holds any privileged credentials (spec §5): all
 * privileged fan-out happens in the `fcm-gateway` edge function.
 */
class PairingRepository(
    private val auth: PayVoiceAuth,
    private val client: SupabaseClient,
    private val devices: DeviceRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Owner: create a fresh pairing invitation. Returns the code or null. */
    suspend fun createPairingCode(): PairingCode? {
        val uid = auth.ensureSignedIn() ?: return null
        val pairing = PairingCodeGenerator.generate()
        val ok = withContext(Dispatchers.IO) {
            client.insertRow(
                table = RemoteCollections.PAIRING_CODES,
                body = buildJsonObject {
                    put("code", pairing.code)
                    put("owner_uid", uid)
                    put("expires_at", pairing.expiresAtMs)
                    put("used", false)
                },
                bearer = null,
            )
        }
        return if (ok) pairing else null
    }

    /**
     * Outcome of a pairing attempt. The UI MUST distinguish these: showing
     * "Pair code expired" for an auth/network failure is the exact bug this
     * replaces (a direct-APK install with a still-initializing session was
     * rendered as an expired code).
     */
    sealed class ClaimResult {
        /** Paired; [ownerUid] is the business to announce for. */
        data class Success(val ownerUid: String) : ClaimResult()

        /** Secure session not ready yet (offline start / sign-in ladder in flight). */
        object SessionNotReady : ClaimResult()

        /** Structurally wrong code (never reached the server). */
        object InvalidCode : ClaimResult()

        /** Server-side, authoritative verdicts (server clock, never client). */
        data class Rejected(val reason: String) : ClaimResult() {
            companion object {
                const val INVALID = "invalid"
                const val EXPIRED = "expired"
                const val ALREADY_USED = "already-used"
                const val UNAUTHENTICATED = "unauthenticated"
            }
        }

        /** Network/transport failure — retry is meaningful. */
        object NetworkError : ClaimResult()
    }

    /**
     * Employee: atomically claim a code and register this device.
     * Waits for the single-flight anonymous sign-in BEFORE the RPC — a not-
     * yet-initialized session must surface as [ClaimResult.SessionNotReady],
     * never as an expired code. Expiry itself is enforced ONLY by the
     * server (claim_pairing uses now()); no client timestamp is consulted.
     */
    suspend fun acceptCode(code: String, deviceName: String, fcmToken: String?): ClaimResult {
        val trimmed = code.trim().uppercase()
        if (!PairingCodeGenerator.CODE_REGEX.matches(trimmed)) return ClaimResult.InvalidCode

        // Identity first: this can take a few seconds on a fresh install
        // (single-flight sign-in ladder). awaitReady() joins that in-flight
        // attempt; if auth still cannot establish, report it honestly.
        val uid = auth.awaitReady(timeoutMs = 20_000L) ?: return ClaimResult.SessionNotReady

        val responseBody = withContext(Dispatchers.IO) {
            client.rpc(
                function = "claim_pairing",
                args = buildJsonObject {
                    put("p_code", trimmed)
                    put("p_device_name", deviceName.trim().take(40).ifBlank { "Employee Device" })
                },
            )
        } ?: return ClaimResult.NetworkError

        val claim = runCatching {
            val obj = RemoteConfig.json.parseToJsonElement(responseBody)
                as? kotlinx.serialization.json.JsonObject
            when {
                obj == null -> null
                (obj["ok"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" ->
                    RestJson.str(obj, "owner_uid")?.let { ClaimResult.Success(it) }
                else -> ClaimResult.Rejected(
                    (obj["error"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: "unknown-rejection",
                )
            }
        }.getOrNull() ?: return ClaimResult.NetworkError

        val ownerUid = (claim as? ClaimResult.Success)?.ownerUid
            ?: return claim

        // Token lives on the employee's own row (RLS: id = auth.uid()).
        if (fcmToken != null) touchDevice(fcmToken)
        return ClaimResult.Success(ownerUid)
    }

    /** Employee: heartbeat so the Owner sees a real last-seen time (spec §13). */
    fun touchDevice(fcmToken: String?) {
        scope.launch {
            val uid = auth.ensureSignedIn() ?: return@launch
            withContext(Dispatchers.IO) {
                client.updateRow(
                    table = RemoteCollections.EMPLOYEES,
                    filter = "id=eq.$uid",
                    body = buildJsonObject {
                        put("last_seen_at", System.currentTimeMillis())
                    },
                    bearer = null,
                )
            }
            // FCM token lives in `devices` (spec §12/§15), not on employees.
            fcmToken?.let { devices?.upsertDevice(it, deviceName = deviceName()) }
        }
    }

    private fun deviceName(): String =
        android.os.Build.MODEL?.trim()?.take(60).orEmpty().ifBlank { "Employee Device" }

    /** Employee: leave the business (spec §21). Owner-side revoke is separate. */
    suspend fun leaveOwner(): Boolean {
        val uid = auth.ensureSignedIn() ?: return false
        return withContext(Dispatchers.IO) {
            client.updateRow(
                table = RemoteCollections.EMPLOYEES,
                filter = "id=eq.$uid",
                body = buildJsonObject {
                    put("status", "LEFT")
                    put("left_at", System.currentTimeMillis())
                },
                bearer = null,
            ).ok
        }
    }
}
