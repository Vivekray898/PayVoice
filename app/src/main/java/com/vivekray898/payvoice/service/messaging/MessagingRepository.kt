package com.vivekray898.payvoice.service.messaging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * FCM registration (spec: FCM REGISTRATION). Fetches and securely stores the
 * device token. The token leaves the device only when Phase 2+ backend
 * pairing lands — until then it is registered and cached locally.
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

    /** Current cached token, if any (encrypted at rest). */
    fun cachedToken(): String? = securePrefs.getString(KEY_TOKEN, null)

    /**
     * Fetches a fresh FCM token, persists it encrypted, and updates status.
     * Retries with short backoff — "Firebase Installations Service is
     * unavailable" is explicitly transient and succeeds on retry.
     */
    suspend fun refreshToken(): Result<String> {
        var lastError: Exception? = null
        val delays = longArrayOf(0, 5_000, 15_000)
        delays.forEachIndexed { attempt, delayMs ->
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            try {
                val token = FirebaseMessaging.getInstance().token.await()
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
