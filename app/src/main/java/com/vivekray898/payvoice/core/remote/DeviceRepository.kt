package com.vivekray898.payvoice.core.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Device registry (spec §12 "devices", §15 token management).
 *
 * Every device (owner AND employee) registers the FCM token that belongs to
 * its authenticated anonymous user:
 *   obtain token → associate with auth.uid() → upsert `devices` row →
 *   update last_seen → refresh on rotation → deactivate on unregistered.
 *
 * RLS: a row is writable ONLY by its own user (`user_id = auth.uid()`).
 * The fcm-gateway marks rows inactive when FCM reports the token invalid —
 * permanently-invalid tokens are never retried.
 */
class DeviceRepository(
    private val auth: PayVoiceAuth,
    private val client: SupabaseClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** One device row as returned by PostgREST (diagnostics only). */
    data class DeviceRecord(
        val id: Long,
        val userId: String,
        val fcmToken: String,
        val deviceName: String,
        val platform: String,
        val isActive: Boolean,
        val lastSeenAtMs: Long,
        val tokenRefreshedAtMs: Long?,
    )

    private fun parse(row: kotlinx.serialization.json.JsonObject): DeviceRecord? {
        val id = RestJson.long(row, "id") ?: return null
        return DeviceRecord(
            id = id,
            userId = RestJson.str(row, "user_id") ?: return null,
            fcmToken = RestJson.str(row, "fcm_token") ?: "",
            deviceName = RestJson.str(row, "device_name") ?: "Device",
            platform = RestJson.str(row, "platform") ?: "android",
            isActive = RestJson.bool(row, "is_active") ?: true,
            lastSeenAtMs = RestJson.long(row, "last_seen_at") ?: 0L,
            tokenRefreshedAtMs = RestJson.long(row, "token_refreshed_at"),
        )
    }

    /**
     * Step 3+4 of spec §15: upsert this user's device with the FCM token and
     * stamp last-seen. Fire-and-forget safe to call from app start, pairing,
     * heartbeat and token-rotation paths.
     */
    fun registerDevice(fcmToken: String?, deviceName: String, platform: String = "android") {
        if (fcmToken.isNullOrBlank()) return
        scope.launch {
            runCatching { upsertDevice(fcmToken, deviceName, platform) }
                .onFailure { Log.w(TAG, "registerDevice failed: ${it.javaClass.simpleName}") }
        }
    }

    /** Blocking variant for paths that need the outcome (pairing flow). */
    suspend fun upsertDevice(fcmToken: String, deviceName: String, platform: String = "android"): Boolean {
        auth.ensureSignedIn() ?: return false
        val now = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            client.insertRow(
                table = RemoteCollections.DEVICES,
                body = buildJsonObject {
                    put("user_id", auth.currentUser() ?: "")
                    put("fcm_token", fcmToken)
                    put("device_name", deviceName.trim().take(60).ifBlank { "Device" })
                    put("platform", platform)
                    put("is_active", true)
                    put("last_seen_at", now)
                    put("token_refreshed_at", now)
                },
                bearer = null,
                onConflictMerge = true,
            )
        }
    }

    /** Heartbeat only (no token change): refresh last_seen_at. */
    fun touch() {
        scope.launch {
            val uid = auth.ensureSignedIn() ?: return@launch
            withContext(Dispatchers.IO) {
                client.updateRow(
                    table = RemoteCollections.DEVICES,
                    filter = "user_id=eq.$uid",
                    body = buildJsonObject { put("last_seen_at", System.currentTimeMillis()) },
                    bearer = null,
                )
            }
        }
    }

    /** Step 5 of spec §15: token rotation — persist the fresh token. */
    suspend fun onTokenRefreshed(newToken: String): Boolean =
        upsertDevice(newToken, deviceName = currentDeviceName())

    /** Step 6 of spec §15: this device is going away / token revoked. */
    suspend fun deactivate(): Boolean {
        val uid = auth.ensureSignedIn() ?: return false
        return withContext(Dispatchers.IO) {
            client.updateRow(
                table = RemoteCollections.DEVICES,
                filter = "user_id=eq.$uid",
                body = buildJsonObject { put("is_active", false) },
                bearer = null,
            )
        }
    }

    /** This device's own row (diagnostics; own-row RLS). */
    suspend fun ownDevice(): DeviceRecord? {
        val uid = auth.ensureSignedIn() ?: return null
        val body = withContext(Dispatchers.IO) {
            client.selectRows(
                table = RemoteCollections.DEVICES,
                query = "user_id=eq.$uid&select=id,user_id,fcm_token,device_name,platform,is_active,last_seen_at,token_refreshed_at",
                bearer = null,
            )
        } ?: return null
        return RestJson.parseArray(body).firstOrNull()?.let { parse(it) }
    }

    private fun currentDeviceName(): String =
        android.os.Build.MODEL?.trim()?.take(60).orEmpty().ifBlank { "Device" }

    private companion object {
        const val TAG = "DeviceRepo"
    }
}
