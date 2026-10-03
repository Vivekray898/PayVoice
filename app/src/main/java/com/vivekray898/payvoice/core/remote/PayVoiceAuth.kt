package com.vivekray898.payvoice.core.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * Single-flight in-flight initialization: every concurrent caller
     * (Activity recreation, VM init, warm-up, pairing) joins THE SAME
     * sign-in attempt instead of each firing its own anonymous signup —
     * that stampede is what created multiple anonymous users every few
     * seconds when the provider was disabled.
     */
    private val inFlight = kotlinx.coroutines.sync.Mutex()

    /** Current device uid if signed in (from the persisted session). */
    fun currentUser(): String? = client.currentUserId()

    /**
     * Ensures an anonymous identity exists. Returns the UID or null after
     * bounded attempts. Safe to call repeatedly: concurrent callers share
     * one in-flight sign-in, and a global cooldown after a full failure
     * cycle prevents every caller (Activity recreations, VM inits) from
     * re-triggering the retry ladder against a disabled backend.
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
        // Single-flight: while one sign-in ladder is running, everyone else
        // awaits the same mutex and then re-reads the (now existing) session.
        inFlight.lock()
        try {
            // Another caller may have completed the sign-in while we waited.
            client.currentUserId()?.let { uid ->
                _state.value = State.READY(uid)
                return uid
            }
            if (System.currentTimeMillis() - lastFullFailureAtMs < FAILURE_COOLDOWN_MS) {
                return null
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Cancellation is control flow, not a failure: swallowing it
                    // here makes a cancelled sign-in keep retrying and reporting
                    // FAILED after the caller's scope is already gone.
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    com.vivekray898.payvoice.core.util.DebugLog.w(
                        TAG,
                        "anon sign-in attempt ${attempt + 1} failed: ${e.javaClass.simpleName} " +
                            e.message?.take(80).orEmpty(),
                    )
                }
            }
            lastFullFailureAtMs = System.currentTimeMillis()
            _state.value = State.FAILED(lastError?.javaClass?.simpleName ?: "unknown")
            return null
        } finally {
            inFlight.unlock()
        }
    }

    /** Fire-and-forget sign-in at process start (never blocks startup). */
    fun warmUp() {
        scope.launch { runCatching { ensureSignedIn() } }
    }

    /**
     * Waits (bounded) for the single-flight sign-in to finish and returns the
     * uid, or null if identity is still not available. Callers that MUST NOT
     * act before identity exists (pairing RPC) await this instead of firing
     * prematurely — a fresh install may need a few seconds to establish the
     * anonymous session.
     */
    suspend fun awaitReady(timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            // ensureSignedIn is single-flight: concurrent callers join the
            // same attempt, so this either returns the existing/new uid or
            // null after the ladder — never a parallel sign-up.
            runCatching { ensureSignedIn() }.getOrNull()
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
