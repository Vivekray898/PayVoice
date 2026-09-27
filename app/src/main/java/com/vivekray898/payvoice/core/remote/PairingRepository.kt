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
     * Employee: atomically claim a code and register this device.
     * Returns the owner UID on success; null when the code is invalid,
     * expired, already used, or the call failed (offline etc.).
     */
    suspend fun acceptCode(code: String, deviceName: String, fcmToken: String?): String? {
        auth.ensureSignedIn() ?: return null
        val trimmed = code.trim().uppercase()
        if (!PairingCodeGenerator.CODE_REGEX.matches(trimmed)) return null
        val responseBody = withContext(Dispatchers.IO) {
            client.rpc(
                function = "claim_pairing",
                args = buildJsonObject {
                    put("p_code", trimmed)
                    put("p_device_name", deviceName.trim().take(40).ifBlank { "Employee Device" })
                },
            )
        } ?: return null
        val ownerUid = runCatching {
            val obj = RemoteConfig.json.parseToJsonElement(responseBody)
                as? kotlinx.serialization.json.JsonObject ?: return@runCatching null
            val okFlag = (obj["ok"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
            if (!okFlag) return@runCatching null
            RestJson.str(obj, "owner_uid")
        }.getOrNull() ?: return null

        // Token lives on the employee's own row (RLS: id = auth.uid()).
        if (fcmToken != null) touchDevice(fcmToken)
        return ownerUid
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
                        if (!fcmToken.isNullOrBlank()) put("fcm_token", fcmToken)
                    },
                    bearer = null,
                )
            }
        }
    }

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
            )
        }
    }
}
