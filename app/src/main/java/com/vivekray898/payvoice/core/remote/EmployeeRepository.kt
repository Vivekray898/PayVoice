package com.vivekray898.payvoice.core.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Owner/employee registry state (spec §3, §15, §16) — Supabase REST+Realtime.
 *
 * Reads/writes ONLY rows RLS grants this authenticated identity: employees
 * whose `owner_id` matches (owner side), and the employee's own row.
 *
 * State sync uses Supabase **Realtime** ([SupabaseRealtime], user JWT → RLS)
 * on top of a REST snapshot prime, with a bounded fallback poll that keeps
 * the UI fresh while the socket is not LIVE. FCM remains the payment-
 * delivery channel — Realtime never carries announcements.
 */
class EmployeeRepository(
    private val auth: PayVoiceAuth,
    private val client: SupabaseClient,
    private val realtime: SupabaseRealtime,
) {

    /** Live employee list for the Owner home card (spec §3). */
    fun observeEmployees(): Flow<List<EmployeeDevice>> = flow {
        val uid = auth.ensureSignedIn()
        var latest = runCatching { fetchEmployees() }.getOrNull() ?: emptyList()
        emit(latest)

        coroutineScope {
            val poll = launch {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    val fresh = runCatching { fetchEmployees() }.getOrNull() ?: continue
                    if (fresh != latest) {
                        latest = fresh
                        emit(fresh)
                    }
                }
            }
            if (uid != null) {
                realtime.postgresChanges(
                    filters = listOf(
                        SupabaseRealtime.RealtimeFilter(event = "*", table = "employees"),
                    ),
                    accessTokenProvider = { client.currentSession()?.accessToken },
                ).collect { change ->
                    if (change.table != RemoteCollections.EMPLOYEES) return@collect
                    val fresh = runCatching { fetchEmployees() }.getOrNull()
                        ?: return@collect
                    if (fresh != latest) {
                        latest = fresh
                        emit(fresh)
                    }
                }
            }
            poll.join()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Employee side: observe THIS device's registry record (own row only —
     * RLS never allows reading other employees). Null = not paired.
     */
    fun observeOwnDevice(): Flow<EmployeeDevice?> = flow {
        val uid = auth.ensureSignedIn()
        var latest = runCatching { fetchOwnDevice() }.getOrNull()
        emit(latest)

        coroutineScope {
            val poll = launch {
                while (isActive) {
                    delay(OWN_POLL_INTERVAL_MS)
                    val fresh = runCatching { fetchOwnDevice() }.getOrNull() ?: continue
                    if (fresh != latest) {
                        latest = fresh
                        emit(fresh)
                    }
                }
            }
            if (uid != null) {
                realtime.postgresChanges(
                    filters = listOf(
                        SupabaseRealtime.RealtimeFilter(event = "*", table = "employees"),
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
                    if (fresh != latest) {
                        latest = fresh
                        emit(fresh)
                    }
                }
            }
            poll.join()
        }
    }.flowOn(Dispatchers.IO)

    // ---- Supabase REST (PostgREST) queries ----

    private suspend fun fetchEmployees(): List<EmployeeDevice>? {
        val uid = auth.ensureSignedIn() ?: return null
        val body = withContext(Dispatchers.IO) {
            client.selectRows(
                table = RemoteCollections.EMPLOYEES,
                query = "owner_id=eq.$uid&select=id,name,status,paired_at,last_seen_at,last_event_delivered_at",
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
                query = "id=eq.$uid&select=id,name,status,paired_at,last_seen_at,last_event_delivered_at",
                bearer = null,
            )
        } ?: return null
        return RestJson.parseArray(body).firstOrNull()?.let { row ->
            val status = RestJson.str(row, "status") ?: return null
            EmployeeDevice(
                uid = RestJson.str(row, "id") ?: uid,
                name = RestJson.str(row, "name") ?: "Employee Device",
                status = status,
                pairedAtMs = RestJson.long(row, "paired_at") ?: 0L,
                lastSeenAtMs = RestJson.long(row, "last_seen_at") ?: 0L,
                lastEventDeliveredAtMs = RestJson.long(row, "last_event_delivered_at"),
            )
        }
    }

    /** Owner revokes an employee: backend status flips, FCM fan-out stops. */
    suspend fun revoke(employeeUid: String): Boolean {
        val uid = auth.ensureSignedIn() ?: return false
        return withContext(Dispatchers.IO) {
            client.updateRow(
                table = RemoteCollections.EMPLOYEES,
                filter = "id=eq.$employeeUid&owner_id=eq.$uid",
                body = buildJsonObject {
                    put("status", "REVOKED")
                    put("revoked_at", System.currentTimeMillis())
                    put("revoked_by", uid)
                },
                bearer = null,
            )
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
    }
}
