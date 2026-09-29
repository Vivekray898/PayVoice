package com.vivekray898.payvoice.core.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Owner/employee registry state (spec §3, §15, §16) — Supabase REST+Realtime.
 *
 * Reads/writes ONLY rows RLS grants this authenticated identity: employees
 * whose `owner_uid` matches (owner side — canonical column name per
 * 0001_init.sql; the legacy `owner_id` caused PostgREST 42703), and the
 * employee's own row.
 *
 * State sync uses Supabase **Realtime** ([SupabaseRealtime], user JWT → RLS)
 * on top of a REST snapshot prime, with a bounded fallback poll that keeps
 * the UI fresh while the socket is not LIVE. FCM remains the payment-
 * delivery channel — Realtime never carries announcements.
 *
 * Concurrency note (the "Flow invariant is violated" crash): both observe
 * flows emit from TWO coroutines — the fallback-poll child and the Realtime
 * collector — inside one flow. A `flow { }` builder forbids emissions from
 * any coroutine other than the one running the builder, so the poll child
 * crashed the app with `IllegalStateException: Flow invariant is violated`.
 * `channelFlow { }` is the sanctioned multi-producer builder: its channel
 * serializes [send]s from any child, and a mutex keeps the change-detection
 * state (`latest`) check-then-send atomic.
 */
class EmployeeRepository(
    private val auth: PayVoiceAuth,
    private val client: SupabaseClient,
    private val realtime: SupabaseRealtime,
) {

    /** Live employee list for the Owner home card (spec §3). */
    fun observeEmployees(): Flow<List<EmployeeDevice>> = channelFlow {
        val uid = auth.ensureSignedIn()
        var latest = runCatching { fetchEmployees() }.getOrNull() ?: emptyList()
        send(latest)

        // One emission at a time: poll child + Realtime collector both fetch
        // fresh state and publish here. The mutex makes check-then-send
        // atomic; duplicate/equal values are filtered so the downstream
        // StateFlow only sees real changes.
        val emitMutex = Mutex()
        suspend fun publish(fresh: List<EmployeeDevice>) = emitMutex.withLock {
            if (fresh != latest) {
                latest = fresh
                send(fresh)
            }
        }

        coroutineScope {
            val poll = launch {
                // Lightweight fallback while Realtime is not LIVE. Once the
                // socket is live the poll idles; if the socket drops, it
                // resumes after the REALTIME_FALLBACK_MS grace (no tight
                // retry loop against deterministic failures).
                while (isActive) {
                    val live = realtime.status.value.state ==
                        SupabaseRealtime.Status.State.LIVE
                    delay(if (live) POLL_IDLE_WHEN_LIVE_MS else POLL_INTERVAL_MS)
                    if (live) continue
                    val fresh = runCatching { fetchEmployees() }.getOrNull() ?: continue
                    publish(fresh)
                }
            }
            if (uid != null) {
                realtime.postgresChanges(
                    filters = listOf(
                        SupabaseRealtime.RealtimeFilter(
                            event = "*", table = "employees",
                            filter = "owner_uid=eq.$uid",
                        ),
                    ),
                    accessTokenProvider = { client.currentSession()?.accessToken },
                ).collect { change ->
                    if (change.table != RemoteCollections.EMPLOYEES) return@collect
                    val fresh = runCatching { fetchEmployees() }.getOrNull()
                        ?: return@collect
                    publish(fresh)
                }
            }
            poll.join()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Employee side: observe THIS device's registry record (own row only —
     * RLS never allows reading other employees). Null = not paired.
     */
    fun observeOwnDevice(): Flow<EmployeeDevice?> = channelFlow {
        val uid = auth.ensureSignedIn()
        var latest = runCatching { fetchOwnDevice() }.getOrNull()
        send(latest)

        val emitMutex = Mutex()
        suspend fun publish(fresh: EmployeeDevice?) = emitMutex.withLock {
            if (fresh != latest) {
                latest = fresh
                send(fresh)
            }
        }

        coroutineScope {
            val poll = launch {
                // Same LIVE-gated fallback as the owner flow above.
                while (isActive) {
                    val live = realtime.status.value.state ==
                        SupabaseRealtime.Status.State.LIVE
                    delay(if (live) POLL_IDLE_WHEN_LIVE_MS else OWN_POLL_INTERVAL_MS)
                    if (live) continue
                    val fresh = runCatching { fetchOwnDevice() }.getOrNull() ?: continue
                    publish(fresh)
                }
            }
            if (uid != null) {
                realtime.postgresChanges(
                    filters = listOf(
                        SupabaseRealtime.RealtimeFilter(
                            event = "*", table = "employees",
                            filter = "id=eq.$uid",
                        ),
                    ),
                    accessTokenProvider = { client.currentSession()?.accessToken },
                ).collect { change ->
                    if (change.table != RemoteCollections.EMPLOYEES) return@collect
                    // Deliver only changes to THIS device's row (event carries
                    // the record; primary key column is `id`).
                    val rowId = change.new?.let { RestJson.str(it, "id") }
                        ?: change.old?.let { RestJson.str(it, "id") }
                    if (rowId != null && rowId != uid) return@collect
                    val fresh = runCatching { fetchOwnDevice() }.getOrNull() ?: return@collect
                    publish(fresh)
                }
            }
            poll.join()
        }
    }.flowOn(Dispatchers.IO)

    // ---- Employee-side remote-event authorization (spec §5) ----

    /**
     * Last known pairing state of THIS device, with the time it was read.
     * Kept fresh by every own-row fetch (observe flow AND the FCM delivery
     * path) so an announcement usually needs NO network call at all.
     */
    data class PairedSnapshot(
        val ownerUid: String?,
        val status: String?,
        val fetchedAtMs: Long,
    )

    @Volatile
    private var cachedPairing: PairedSnapshot? = null

    /**
     * Process-death fallback (reliability fix): the in-memory cache dies with
     * the process. When FCM spawns a COLD employee process while offline
     * (killed app, reboot), the fresh fetch fails and the in-memory cache is
     * empty — without a persisted snapshot every such event was denied as
     * `pairing-unknown` and the payment was never announced. The snapshot is
     * written on EVERY successful own-row read (EncryptedSharedPreferences).
     */
    private val persistedPairing by lazy { PersistedPairingStore(client.appContext) }

    /**
     * Authorize an incoming remote event BEFORE the announcement pipeline
     * (spec §5): own row must exist, be ACTIVE, and its `owner_uid` must
     * match the event's owner. Decision order: fresh cache (TTL) → bounded
     * network fetch → stale in-memory cache → persisted snapshot. Each
     * fallback is logged (debug) so the decision is visible. The backend's
     * ACTIVE-only fan-out stays the PRIMARY enforcement — this is the client
     * safety layer, and its offline behavior is announce-once-dedup-guarded.
     */
    suspend fun authorizeRemoteEvent(eventOwnerUid: String?): RemoteAuthorization.Verdict {
        val cached = cachedPairing
        if (cached != null &&
            System.currentTimeMillis() - cached.fetchedAtMs < AUTH_CACHE_TTL_MS
        ) {
            return RemoteAuthorization.decide(eventOwnerUid, cached.ownerUid, cached.status)
        }
        val fresh = withTimeoutOrNull(AUTH_FETCH_TIMEOUT_MS) {
            runCatching { fetchOwnPairing() }.getOrNull()
        }
        if (fresh != null) {
            return RemoteAuthorization.decide(eventOwnerUid, fresh.ownerUid, fresh.status)
        }
        // Network fetch failed/timed out. Prefer the stale in-memory cache,
        // then the persisted snapshot — announce on last-known pairing rather
        // than dropping the event (dedup keeps FCM retries honest).
        cached?.let {
            logFallback("stale in-memory pairing used (fetch failed)")
            return RemoteAuthorization.decide(eventOwnerUid, it.ownerUid, it.status)
        }
        val persisted = runCatching { persistedPairing.load() }.getOrNull()
        if (persisted != null) {
            logFallback(
                "persisted pairing used (fetch failed, age=" +
                    ((System.currentTimeMillis() - persisted.fetchedAtMs) / 3_600_000L) + "h)",
            )
            return RemoteAuthorization.decide(eventOwnerUid, persisted.ownerUid, persisted.status)
        }
        return RemoteAuthorization.Verdict.Deny("pairing-unknown")
    }

    private fun logFallback(message: String) {
        runCatching {
            android.util.Log.d("EmployeeRepo", "auth-fallback: $message")
        }
    }

    // ---- Supabase REST (PostgREST) queries ----

    private fun employeeFrom(row: JsonObject): EmployeeDevice? {
        val status = RestJson.str(row, "status") ?: return null
        // Keep the authorization cache fresh with every read (owner_uid is
        // selected alongside the display fields for exactly this purpose)
        // AND persist it for the offline cold-start fallback.
        PairedSnapshot(
            ownerUid = RestJson.str(row, "owner_uid"),
            status = status,
            fetchedAtMs = System.currentTimeMillis(),
        ).also {
            cachedPairing = it
            runCatching { persistedPairing.save(it) }
        }
        return EmployeeDevice(
            uid = RestJson.str(row, "id") ?: return null,
            name = RestJson.str(row, "name") ?: "Employee Device",
            status = status,
            pairedAtMs = RestJson.long(row, "paired_at") ?: 0L,
            lastSeenAtMs = RestJson.long(row, "last_seen_at") ?: 0L,
            lastEventDeliveredAtMs = RestJson.long(row, "last_event_delivered_at"),
        )
    }

    private suspend fun fetchEmployees(): List<EmployeeDevice>? {
        val uid = auth.ensureSignedIn() ?: return null
        val body = withContext(Dispatchers.IO) {
            client.selectRows(
                table = RemoteCollections.EMPLOYEES,
                query = "owner_uid=eq.$uid&select=id,name,status,paired_at,last_seen_at,last_event_delivered_at",
                bearer = null,
            )
        } ?: return null
        return RestJson.parseArray(body).mapNotNull { row ->
            val status = RestJson.str(row, "status") ?: return@mapNotNull null
            EmployeeDevice(
                uid = RestJson.str(row, "id") ?: return@mapNotNull null,
                name = RestJson.str(row, "name") ?: "Employee Device",
                status = status,
                pairedAtMs = RestJson.long(row, "paired_at") ?: 0L,
                lastSeenAtMs = RestJson.long(row, "last_seen_at") ?: 0L,
                lastEventDeliveredAtMs = RestJson.long(row, "last_event_delivered_at"),
            )
        }
    }

    private suspend fun fetchOwnDevice(): EmployeeDevice? {
        val uid = auth.ensureSignedIn() ?: return null
        val body = withContext(Dispatchers.IO) {
            client.selectRows(
                table = RemoteCollections.EMPLOYEES,
                query = "id=eq.$uid&select=id,owner_uid,name,status,paired_at,last_seen_at,last_event_delivered_at",
                bearer = null,
            )
        } ?: return null
        // An EMPTY array means "no row for this uid" = not paired (a real
        // state worth caching); a null body was a network failure.
        val row = RestJson.parseArray(body).firstOrNull() ?: run {
            cachedPairing = PairedSnapshot(null, null, System.currentTimeMillis())
            return null
        }
        return employeeFrom(row)
    }

    /** Lightweight authoritative read of THIS device's pairing state. */
    private suspend fun fetchOwnPairing(): PairedSnapshot? {
        val uid = auth.ensureSignedIn() ?: return null
        val body = withContext(Dispatchers.IO) {
            client.selectRows(
                table = RemoteCollections.EMPLOYEES,
                query = "id=eq.$uid&select=id,owner_uid,status",
                bearer = null,
            )
        } ?: return null
        val row = RestJson.parseArray(body).firstOrNull() ?: run {
            cachedPairing = PairedSnapshot(null, null, System.currentTimeMillis())
            return null
        }
        return PairedSnapshot(
            ownerUid = RestJson.str(row, "owner_uid"),
            status = RestJson.str(row, "status"),
            fetchedAtMs = System.currentTimeMillis(),
        ).also {
            cachedPairing = it
            runCatching { persistedPairing.save(it) }
        }
    }

    /**
     * Outcome of an owner-side revoke. RLS rejections surface as HTTP 404
     * with ZERO rows affected from PostgREST (never a 403) — the counted
     * result makes "nothing was updated" visible instead of silently
     * "succeeding".
     */
    sealed class RevokeResult {
        /** Row flipped to REVOKED; fan-out stops at the database. */
        data class Success(val rows: Int) : RevokeResult()

        /** Filter matched nothing: wrong uid, not the owner, or RLS denied. */
        object NotFound : RevokeResult()

        /** Transport/session failure — retry is meaningful. */
        data class Failed(val reason: String) : RevokeResult()
    }

    /**
     * Owner revokes an employee: backend status flips to REVOKED, the
     * fcm-gateway (ACTIVE-only query) stops delivering to this device, and
     * the employee's own-row read sees the status change. No row is deleted
     * (audit/history preserved; re-pairing flips it back to ACTIVE).
     *
     * Preferred path: the atomic security-definer `revoke_employee` RPC
     * (migration 0004) — ownership is verified server-side in ONE statement,
     * so no client-supplied owner id is ever trusted. Fallback: the counted
     * RLS-filtered UPDATE (works before migration 0004 is applied).
     */
    suspend fun revoke(employeeUid: String): RevokeResult {
        auth.ensureSignedIn() ?: return RevokeResult.Failed("no-auth")
        when (val viaRpc = revokeViaRpc(employeeUid)) {
            null -> Unit // RPC unavailable (not deployed yet) → counted-update fallback
            else -> return viaRpc
        }
        val uid = auth.currentUser() ?: return RevokeResult.Failed("no-auth")
        val result = withContext(Dispatchers.IO) {
            client.updateRow(
                table = RemoteCollections.EMPLOYEES,
                filter = "id=eq.$employeeUid&owner_uid=eq.$uid&status=neq.REVOKED",
                body = buildJsonObject {
                    put("status", "REVOKED")
                    put("revoked_at", System.currentTimeMillis())
                    put("revoked_by", uid)
                },
                bearer = null,
            )
        }
        return when {
            result.error != null -> RevokeResult.Failed(result.error)
            result.rows > 0 -> RevokeResult.Success(result.rows)
            else -> RevokeResult.NotFound
        }
    }

    /**
     * Atomic RPC attempt. Returns null ONLY when the RPC is unavailable or
     * its answer is inconclusive (so the caller can fall back); a real
     * verdict (success / not-found / rejection) is returned decisively.
     */
    private suspend fun revokeViaRpc(employeeUid: String): RevokeResult? {
        val body = withContext(Dispatchers.IO) {
            runCatching {
                client.rpc(
                    function = "revoke_employee",
                    args = buildJsonObject { put("p_employee_id", employeeUid) },
                )
            }.getOrNull()
        } ?: return null
        val obj = runCatching {
            RemoteConfig.json.parseToJsonElement(body) as? JsonObject
        }.getOrNull() ?: return null
        val ok = (obj["ok"] as? JsonPrimitive)?.content == "true"
        return if (ok) {
            RevokeResult.Success(
                (obj["rows"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1,
            )
        } else {
            when ((obj["error"] as? JsonPrimitive)?.content) {
                "not-found" -> RevokeResult.NotFound
                "unauthenticated" -> RevokeResult.Failed("no-auth")
                // Unknown rejection: inconclusive → let the fallback decide.
                else -> null
            }
        }
    }

    /**
     * Owner sends a test announcement (spec §22). Uses the same idempotent
     * event path with type TEST_ANNOUNCEMENT — never spoken as a payment.
     */
    suspend fun sendTestAnnouncement(): Boolean {
        val uid = auth.ensureSignedIn() ?: return false
        val event = RemotePaymentEvent(
            eventId = RemoteCollections.newEventId(),
            type = RemoteEventType.TEST_ANNOUNCEMENT,
            amountMinor = 0,
            currency = "INR",
            senderName = null,
            source = "TEST",
            timestampMs = System.currentTimeMillis(),
        )
        return withContext(Dispatchers.IO) {
            client.insertRow(
                table = RemoteCollections.PAYMENT_EVENTS,
                body = event.toPayload(uid, null),
                bearer = null,
                onConflictMerge = true,
            )
        }
    }

    private companion object {
        const val TAG = "EmployeeRepo"
        const val POLL_INTERVAL_MS = 15_000L
        const val OWN_POLL_INTERVAL_MS = 10_000L
        const val POLL_IDLE_WHEN_LIVE_MS = 60_000L
        const val AUTH_CACHE_TTL_MS = 30_000L
        const val AUTH_FETCH_TIMEOUT_MS = 5_000L
    }
}

/**
 * Encrypted on-disk copy of the last-known pairing snapshot. Keys/values are
 * opaque uid/status strings — no secrets, but encrypted anyway because the
 * device pairing state is identity material. All failures are soft: a broken
 * store degrades to the pre-fix behavior (deny when offline), never a crash.
 */
private class PersistedPairingStore(context: Context) {

    private val prefs by lazy {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "payvoice_pairing_snapshot",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences
                .PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences
                .PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(snapshot: EmployeeRepository.PairedSnapshot) {
        runCatching {
            prefs.edit()
                .putString(KEY_OWNER, snapshot.ownerUid)
                .putString(KEY_STATUS, snapshot.status)
                .putLong(KEY_FETCHED_AT, snapshot.fetchedAtMs)
                .apply()
        }
    }

    fun load(): EmployeeRepository.PairedSnapshot? = runCatching {
        val owner = prefs.getString(KEY_OWNER, null) ?: return null
        EmployeeRepository.PairedSnapshot(
            ownerUid = owner,
            status = prefs.getString(KEY_STATUS, null),
            fetchedAtMs = prefs.getLong(KEY_FETCHED_AT, 0L),
        )
    }.getOrNull()

    private companion object {
        const val KEY_OWNER = "owner_uid"
        const val KEY_STATUS = "status"
        const val KEY_FETCHED_AT = "fetched_at_ms"
    }
}
