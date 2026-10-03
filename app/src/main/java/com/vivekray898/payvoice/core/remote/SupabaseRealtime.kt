package com.vivekray898.payvoice.core.remote

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal Supabase Realtime client (Postgres Changes) — CURRENT protocol.
 *
 * Implements the documented Phoenix-over-WebSocket flow against
 * `{url}/realtime/v1/websocket`:
 *  1. `phx_join` on topic `realtime:public` with `postgres_changes` configs
 *     (event/schema/table/filter) — server-side change streams;
 *  2. the signed-in user's JWT rides in the JOIN payload as `access_token`
 *     — Realtime authorizes rows with THE USER'S RLS policies (never a
 *     privileged key);
 *  3. the CLIENT sends a `heartbeat` frame every [HEARTBEAT_INTERVAL_MS] on
 *     topic `phoenix` — the Realtime protocol documents this as the client's
 *     job ("should be sent at least every 25 seconds to avoid a connection
 *     timeout"), not the server's. OkHttp-level pings additionally keep the
 *     TCP path alive through NATs.
 *
 * Scope note (architecture rule): Realtime carries STATE synchronization
 * (employee/device/pairing state) ONLY. Payment announcement delivery stays
 * on FCM push, because a backgrounded/swiped employee app cannot rely on a
 * websocket.
 */
class SupabaseRealtime {

    /** A single Postgres change delivered from the joined channel. */
    data class Change(
        val event: String, // INSERT | UPDATE | DELETE
        val schema: String,
        val table: String,
        val commitTimestampMs: Long?,
        val new: JsonObject?,   // null for DELETE
        val old: JsonObject?,   // populated for DELETE (and UPDATE when requested)
    )

    data class Status(val state: State, val detail: String? = null) {
        enum class State { IDLE, CONNECTING, LIVE, RECONNECTING, CLOSED }
    }

    /** A postgres_changes subscription spec (documented filter fields). */
    data class RealtimeFilter(
        val event: String,   // INSERT | UPDATE | DELETE | "*"
        val schema: String = "public",
        val table: String? = null,
        val filter: String? = null,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("event", event)
            put("schema", schema)
            table?.let { put("table", it) }
            filter?.let { put("filter", it) }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _status = MutableStateFlow(Status(Status.State.IDLE))
    val status: StateFlow<Status> = _status

    private val msgRef = AtomicLong(0)

    /**
     * Suspends while the socket is healthy and returns as soon as it is not —
     * i.e. the caller can wait for "time to fall back to polling" without a
     * timer. Callers that poll as a Realtime fallback use this instead of
     * waking on an interval just to re-read a boolean that has not changed.
     *
     * [maxWaitMs] bounds the wait so a status left stale at LIVE (server-side
     * half-open, collector cancelled without awaitClose running) still
     * produces an occasional refresh instead of wedging the fallback forever.
     */
    suspend fun awaitNotLive(maxWaitMs: Long = MAX_LIVE_WAIT_MS): Status =
        withTimeoutOrNull(maxWaitMs) {
            status.first { it.state != Status.State.LIVE }
        } ?: status.value

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // websocket: reads never time out
            .pingInterval(20, TimeUnit.SECONDS)
            // TLS 1.2 floor for the realtime socket. The platform still offers
            // TLS 1.0/1.1 on API 26-27, so without this a downgrade is possible
            // on the one channel that carries owner/employee state.
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .build()
    }

    /**
     * Subscribe to Postgres changes. Single live connection per collection:
     * collecting opens the socket, cancelling closes it and stops timers.
     * [accessTokenProvider] supplies the current user JWT (kept fresh by the
     * auth layer) for the join payload.
     */
    fun postgresChanges(
        filters: List<RealtimeFilter>,
        accessTokenProvider: () -> String?,
    ): Flow<Change> = callbackFlow {
        if (!RemoteConfig.isConfigured()) {
            com.vivekray898.payvoice.core.util.DebugLog.w(TAG, "Realtime unavailable: project not configured")
            _status.value = Status(Status.State.CLOSED, "not-configured")
            close()
            return@callbackFlow
        }
        _status.value = Status(Status.State.CONNECTING)

        fun send(ws: WebSocket, topic: String, event: String, payload: JsonObject) {
            val frame = buildJsonObject {
                put("topic", topic)
                put("event", event)
                put("ref", msgRef.incrementAndGet().toString())
                put("payload", payload)
            }
            ws.send(frame.toString())
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val joinPayload = buildJsonObject {
                    put("config", buildJsonObject {
                        put(
                            "postgres_changes",
                            JsonArray(filters.map { it.toJson() }),
                        )
                        // RLS authorization: the USER's token, never a secret.
                        put("access_token", accessTokenProvider() ?: "")
                    })
                }
                send(ws, "realtime:public", "phx_join", joinPayload)
                // Documented Realtime authorization flow: after the join, a
                // separate `access_token` event carries the CURRENT token. If
                // auth was still initializing at join time (fresh install),
                // the server re-authorizes the channel here — without this
                // the subscription sat connected but RLS-blind (no owner
                // updates ever arrived).
                accessTokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                    send(
                        ws,
                        "realtime:public",
                        "access_token",
                        buildJsonObject { put("access_token", token) },
                    )
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val frame = runCatching { json.parseToJsonElement(text).let { it as? JsonObject } }
                    .getOrNull() ?: return
                when (val event = (frame["event"] as? JsonPrimitive)?.content) {
                    "phx_reply" -> {
                        val payload = frame["payload"].let { it as? JsonObject } ?: return
                        val status =
                            (payload["status"] as? JsonPrimitive)?.content ?: return
                        when (status) {
                            "ok" -> {
                                _status.value = Status(Status.State.LIVE)
                            }
                            "error" -> {
                                com.vivekray898.payvoice.core.util.DebugLog.w(TAG, "realtime join rejected")
                                _status.value = Status(Status.State.RECONNECTING, "join-rejected")
                            }
                        }
                    }
                    // Server-side heartbeat acknowledgement; the client is the
                    // one that must send them (see the ticker below).
                    "heartbeat" -> Unit
                    "postgres_changes" -> {
                        val payload = frame["payload"].let { it as? JsonObject } ?: return
                        // ids lists which of OUR subscription configs matched;
                        // empty/missing ids = the change is not for this client.
                        val ids = payload["ids"] as? JsonArray
                        if (ids == null || ids.isEmpty()) return
                        val data = payload["data"].let { it as? JsonObject } ?: return
                        val change = parseChange(data) ?: return
                        runCatching { trySend(change) }
                        Unit
                    }
                    else -> {
                        com.vivekray898.payvoice.core.util.DebugLog.d(TAG, "realtime frame event=$event (ignored)")
                    }
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _status.value = Status(Status.State.CLOSED, "server-closed:$code")
                close()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _status.value = Status(Status.State.RECONNECTING, t.javaClass.simpleName)
                close()
            }
        }

        val ws = client.newWebSocket(
            Request.Builder().url(webSocketUrl()).build(),
            listener,
        )

        // The documented protocol puts the heartbeat on the CLIENT: the server
        // closes a connection that has not seen one within its own window, and
        // a phone that sits on a locked screen for a few minutes is exactly
        // that case. Without this the socket dies silently every time the app
        // is backgrounded and the state-sync fallback has to carry the load
        // until the user reopens the app.
        val heartbeat = launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                // Topic `phoenix`, empty payload — not a channel message.
                runCatching { send(ws, PHOENIX_TOPIC, "heartbeat", JsonObject(emptyMap())) }
            }
        }

        awaitClose {
            heartbeat.cancel()
            runCatching { ws.close(1000, "client-shutdown") }
            _status.value = Status(Status.State.CLOSED)
        }
    }

    private fun parseChange(data: JsonObject): Change? {
        val type = (data["type"] as? JsonPrimitive)?.content ?: return null
        val schema = (data["schema"] as? JsonPrimitive)?.content ?: return null
        val table = (data["table"] as? JsonPrimitive)?.content ?: return null
        val commitTs = (data["commit_timestamp"] as? JsonPrimitive)?.content
            ?.let { parseIsoMillis(it) }
        val record = data["record"] as? JsonObject
        val oldRecord = data["old_record"] as? JsonObject
        return Change(
            event = type,
            schema = schema,
            table = table,
            commitTimestampMs = commitTs,
            new = record,
            old = oldRecord,
        )
    }

    /** ISO-8601 → epoch millis (Realtime commit_timestamp). */
    private fun parseIsoMillis(iso: String): Long? = runCatching {
        java.time.Instant.parse(iso).toEpochMilli()
    }.getOrNull()

    private fun webSocketUrl(): String {
        val base = RemoteConfig.url()
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        return base + RemoteConfig.REALTIME_PATH +
            "?apikey=" + RemoteConfig.publishableKey() + "&vsn=1.0.0"
    }

    private companion object {
        const val TAG = "SupabaseRealtime"

        /** Upper bound on a single LIVE wait; see [awaitNotLive]. */
        const val MAX_LIVE_WAIT_MS = 15 * 60_000L

        /** Realtime documents "at least every 25 seconds"; stay under it. */
        const val HEARTBEAT_INTERVAL_MS = 20_000L

        /** Heartbeats are not channel-scoped; the protocol fixes this topic. */
        const val PHOENIX_TOPIC = "phoenix"
    }
}
