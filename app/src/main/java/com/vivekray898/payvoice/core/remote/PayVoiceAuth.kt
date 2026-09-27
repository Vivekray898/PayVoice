package com.vivekray898.payvoice.core.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Device identity (spec §18): Supabase **anonymous sign-in** gives every
 * device a stable, backend-verifiable `auth.uid()` with zero login UI.
 * Ownership in the database is derived from this identity — `ownerUid` sent
 * by a client is never trusted on its own (RLS verifies `auth.uid()`).
 *
 * Sign-in is async and retried with bounded backoff; a network-less start
 * must never crash — pairing/sending simply waits until identity exists.
 * A signed-in session persists (encrypted) and is reused across process
 * starts; Clear Data wipes it, which is correct: the device then gets a
 * fresh identity, exactly as with Firebase anonymous auth before.
 */
class PayVoiceAuth(private val client: SupabaseClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<State>(initialState())
    val state: StateFlow<State> = _state

    private fun initialState(): State {
        val uid = client.currentUserId() ?: return State.SIGNING_IN
        return State.READY(uid)
    }

    sealed class State {
        object SIGNING_IN : State()
        data class READY(val uid: String) : State()
        data class FAILED(val reason: String) : State()
    }

    @Volatile
    private var lastFullFailureAtMs: Long = 0

    /** Current device uid if signed in (from the persisted session). */
    fun currentUser(): String? = client.currentUserId()

    /**
     * Ensures an anonymous identity exists. Returns the UID or null after
     * bounded attempts. Safe to call repeatedly: a global cooldown after a
     * full failure cycle prevents every caller (Activity recreations, VM
     * inits) from re-triggering the retry ladder against a disabled backend.
     */
    suspend fun ensureSignedIn(): String? {
        client.currentUserId()?.let { uid ->
            // Session exists; make sure its access token is fresh for callers
            // that need to make an authenticated call right away.
            withContext(Dispatchers.IO) { client.ensureSignedIn() }
            _state.value = State.READY(uid)
            return uid
        }
        if (System.currentTimeMillis() - lastFullFailureAtMs < FAILURE_COOLDOWN_MS) {
            return null // recent full failure: back off, don't hammer the API
        }
        var lastError: Exception? = null
        val delays = longArrayOf(0, 3_000, 10_000)
        delays.forEachIndexed { attempt, delayMs ->
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            try {
                val session = withContext(Dispatchers.IO) { client.signInAnonymously() }
                if (session != null) {
                    _state.value = State.READY(session.userId)
                    return session.userId
                }
                lastError = IllegalStateException("no-session")
            } catch (e: Exception) {
                lastError = e
                Log.w(
                    TAG,
                    "anon sign-in attempt ${attempt + 1} failed: ${e.javaClass.simpleName} " +
                        e.message?.take(80).orEmpty(),
                )
            }
        }
        lastFullFailureAtMs = System.currentTimeMillis()
        _state.value = State.FAILED(lastError?.javaClass?.simpleName ?: "unknown")
        return null
    }

    /** Fire-and-forget sign-in at process start (never blocks startup). */
    fun warmUp() {
        scope.launch { runCatching { ensureSignedIn() } }
    }

    /** Fires when identity becomes available (used to register device records). */
    fun onReady(action: (uid: String) -> Unit) {
        scope.launch {
            ensureSignedIn()?.let { action(it) }
        }
    }

    private companion object {
        const val TAG = "PayVoiceAuth"
        const val FAILURE_COOLDOWN_MS = 60_000L
    }
}
