package com.vivekray898.payvoice.core.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Owner-side employee registry (spec §3, §15, §16) — Supabase REST.
 *
 * Reads/writes ONLY rows RLS grants this authenticated owner: employees whose
 * `owner_uid` matches, and payment events created by this owner.
 *
 * REST has no server push, so the live lists are **polls**: the Owner list
 * refreshes every [POLL_INTERVAL_MS] while subscribed (UI-visible only), and
 * the employee's own-device row refreshes more often so pairing/revocation
 * shows up quickly. Polls stop immediately when the collector goes away.
 */
class EmployeeRepository(
    private val auth: PayVoiceAuth,
    private val client: SupabaseClient,
) {

    /** Live employee list for the Owner home card (spec §3). */
    fun observeEmployees(): Flow<List<EmployeeDevice>> = pollFlow(POLL_INTERVAL_MS) {
        val uid = auth.ensureSignedIn() ?: return@pollFlow emptyList()
        val body = withContext(Dispatchers.IO) {
            client.selectRows(
                table = RemoteCollections.EMPLOYEES,
                query = "owner_uid=eq.$uid&select=id,name,status,paired_at,last_seen_at,last_event_delivered_at",
                bearer = null,
            )
        } ?: return@pollFlow emptyList()
        RestJson.parseArray(body).mapNotNull { row ->
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

    /**
     * Employee side: observe THIS device's registry record (own doc only —
     * RLS never allows reading other employees). Null = not paired.
     */
    fun observeOwnDevice(): Flow<EmployeeDevice?> = pollFlow(OWN_POLL_INTERVAL_MS) {
        val uid = auth.ensureSignedIn() ?: return@pollFlow null
        val body = withContext(Dispatchers.IO) {
            client.selectRows(
                table = RemoteCollections.EMPLOYEES,
                query = "id=eq.$uid&select=id,name,status,paired_at,last_seen_at,last_event_delivered_at",
                bearer = null,
            )
        } ?: return@pollFlow null
        RestJson.parseArray(body).firstOrNull()?.let { row ->
            val status = RestJson.str(row, "status") ?: return@pollFlow null
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

    /** Generic bounded poll that re-runs [fetch] while collected. */
    private fun <T> pollFlow(intervalMs: Long, fetch: suspend () -> T): Flow<T> = flow {
        while (true) {
            val value = runCatching { fetch() }.getOrElse { e ->
                Log.w(TAG, "poll failed: ${e.javaClass.simpleName}")
                null
            }
            if (value != null) emit(value)
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    /** Owner revokes an employee: backend status flips, FCM fan-out stops. */
    suspend fun revoke(employeeUid: String): Boolean {
        val uid = auth.ensureSignedIn() ?: return false
        return withContext(Dispatchers.IO) {
            client.updateRow(
                table = RemoteCollections.EMPLOYEES,
                filter = "id=eq.$employeeUid&owner_uid=eq.$uid",
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
