package com.vivekray898.payvoice.service.messaging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume

/**
 * FCM registration (receive-only transport). Identity and data live in
 * Supabase — Firebase here is ONLY the push pipe the `fcm-gateway` edge
 * function sends through. The google-services Gradle plugin was removed, so
 * the app initializes FirebaseApp itself from the local (gitignored)
 * `assets/google-services.json`; without that file the remote layer degrades
 * gracefully: local announcements never depend on FCM.
 *
 * Logging rules: shortened token in debug builds ONLY; never the full token.
 */
class MessagingRepository(private val context: Context) {

    data class FcmStatus(
        val firebaseInitialized: Boolean = false,
        val tokenAvailable: Boolean = false,
        val tokenPreview: String? = null,
        val error: String? = null,
    )

    private val _status = MutableStateFlow(FcmStatus())
    val status: StateFlow<FcmStatus> = _status

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "payvoice_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val isDebug: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Ensures a FirebaseApp exists, initializing it from the local
     * google-services.json asset (which the removed google-services plugin
     * would otherwise inject at build time). Idempotent; returns false when
     * the asset is absent or malformed so callers can surface an honest
     * status instead of crashing.
     *
     * google-services.json is Firebase CLIENT configuration (public by
     * design) — never a service-account/Admin JSON, which must never be
     * packaged.
     */
    fun ensureFirebaseInitialized(): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            _status.value = _status.value.copy(firebaseInitialized = true)
            return true
        }
        return runCatching {
            val root = context.assets.open("google-services.json").use { stream ->
                Json.parseToJsonElement(stream.readBytes().toString(Charsets.UTF_8))
                    as? JsonObject ?: return false
            }
            val projectInfo = root["project_info"] as? JsonObject ?: return false
            // Real google-services.json shape: client[0].client_info carries
            // mobilesdk_app_id; api_key[] sits beside it on the client object.
            val client = (root["client"] as? JsonArray)
                ?.firstNotNullOfOrNull { it as? JsonObject } ?: return false
            val clientInfo = client["client_info"] as? JsonObject
            val mobileSdkAppId = clientInfo?.get("mobilesdk_app_id")
                ?.let { (it as? JsonPrimitive)?.content }
                ?: return false
            val apiKey = (client["api_key"] as? JsonArray)
                ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("current_key") }
                ?.let { (it as? JsonPrimitive)?.content } ?: return false
            val options = FirebaseOptions.Builder()
                .setApplicationId(mobileSdkAppId)
                .setApiKey(apiKey)
                .setProjectId((projectInfo["project_id"] as? JsonPrimitive)?.content ?: return false)
                .setGcmSenderId(
                    (projectInfo["project_number"] as? JsonPrimitive)?.content
                        ?: clientInfo["android_client_info"]?.let { (it as? JsonObject)?.get("package_name") }
                            ?.let { (it as? JsonPrimitive)?.content },
                )
                .setStorageBucket((projectInfo["storage_bucket"] as? JsonPrimitive)?.content)
                .build()
            FirebaseApp.initializeApp(context, options) != null
        }.onFailure {
            Log.w(TAG, "FirebaseApp init failed: ${it.javaClass.simpleName}")
        }.getOrDefault(false).also { ok ->
            if (ok) _status.value = _status.value.copy(firebaseInitialized = true)
        }
    }

    /**
     * Fetches a fresh FCM token, persists it encrypted, and updates status.
     * Retries with short backoff — "Firebase Installations Service is
     * unavailable" is explicitly transient and succeeds on retry.
     */
    suspend fun refreshToken(): Result<String> {
        if (!ensureFirebaseInitialized()) {
            val error = "google-services.json asset missing or invalid"
            _status.value = FcmStatus(firebaseInitialized = false, error = error)
            return Result.failure(IllegalStateException(error))
        }
        var lastError: Exception? = null
        val delays = longArrayOf(0, 5_000, 15_000)
        delays.forEachIndexed { attempt, delayMs ->
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            try {
                val token = withContext(Dispatchers.IO) {
                    FirebaseMessaging.getInstance().token.await()
                }
                securePrefs.edit().putString(KEY_TOKEN, token).apply()
                _status.value = FcmStatus(
                    firebaseInitialized = true,
                    tokenAvailable = true,
                    tokenPreview = if (isDebug) token.take(12) + "…" else null,
                )
                if (isDebug) {
                    Log.d(TAG, "FCM token refreshed (attempt ${attempt + 1}): ${token.take(12)}…")
                }
                return Result.success(token)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "FCM token fetch attempt ${attempt + 1} failed: ${e.message}")
            }
        }
        _status.value = FcmStatus(
            firebaseInitialized = true,
            tokenAvailable = false,
            error = lastError?.javaClass?.simpleName,
        )
        return Result.failure(lastError ?: IllegalStateException("FCM registration failed"))
    }

    /** Current cached token, if any (encrypted at rest). */
    fun cachedToken(): String? = runCatching { securePrefs.getString(KEY_TOKEN, null) }.getOrNull()

    /** Persists a token (used by onNewToken rotation, spec §14). */
    fun storeTokenSecurely(token: String) {
        runCatching {
            securePrefs.edit().putString(KEY_TOKEN, token).apply()
            _status.value = FcmStatus(
                firebaseInitialized = true,
                tokenAvailable = true,
                tokenPreview = if (isDebug) token.take(12) + "…" else null,
            )
        }
    }

    private companion object {
        const val TAG = "MessagingRepo"
        const val KEY_TOKEN = "fcm_token"
    }
}

/** Await a Google Task without adding the coroutines-play-services dependency. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.cancel(it) }
    }
