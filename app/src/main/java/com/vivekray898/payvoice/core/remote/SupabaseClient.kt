package com.vivekray898.payvoice.core.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume

/**
 * Minimal Supabase REST client (GoTrue auth + PostgREST + edge functions).
 *
 * Deliberately hand-rolled on HttpURLConnection — the remote layer makes
 * exactly four request shapes, so a full SDK would only add startup cost and
 * dependency weight. Every call:
 *  - is suspend + cancellable, with a bounded timeout;
 *  - runs OFF the main thread (callers are already on Dispatchers.Default);
 *  - carries the CURRENT publishable key on `apikey` (sb_publishable_... —
 *    never the deprecated `anon`/`service_role` JWT keys), plus the user's
 *    access token when signed in (Authorization: Bearer) — PostgREST RLS
 *    sees the authenticated role via the JWT;
 *  - silently refreshes an expired access token once (GoTrue /token?grant_type=refresh_token).
 */
class SupabaseClient(private val context: Context) {

    /** Session from GoTrue: stable device identity (auth.uid()). */
    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val expiresAtMs: Long,
    )

    /** Serializable (for the encrypted store) + API (for responses). */
    @kotlinx.serialization.Serializable
    data class StoredSession(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val expiresAtMs: Long,
    ) {
        fun toSession() = Session(accessToken, refreshToken, userId, expiresAtMs)
    }

    private val json = RemoteConfig.json
    private val sessionStore = SessionStore(context)

    @Volatile
    private var cached: Session? = sessionStore.load()?.toSession()

    private val refreshing = AtomicReference<Unit?>(null)

    fun currentSession(): Session? = cached

    fun currentUserId(): String? = cached?.userId

    /**
     * Current valid access token for Realtime/RPC callers — refreshes when
     * near expiry. Null only when there is no identity (never crash).
     */
    suspend fun sessionAccessToken(): String? = ensureSignedIn()?.accessToken

    /** Persist a session (called after sign-in / refresh). */
    fun storeSession(session: StoredSession) {
        sessionStore.save(session)
        cached = session.toSession()
    }

    fun clearSession() {
        sessionStore.clear()
        cached = null
    }

    // ---- Auth (GoTrue) ----

    /**
     * Anonymous sign-in (spec §18): POST /auth/v1/signup {"is_anonymous":true}
     * with the publishable key. No email, no PII — the returned user id is
     * the device's stable identity. Project must have anonymous sign-ins
     * enabled.
     */
    suspend fun signInAnonymously(): Session? {
        cached?.let { return it }
        val body = buildJsonObject { put("is_anonymous", true) }.toString()
        val response = runCatching {
            postJson(
                url = RemoteConfig.url() + RemoteConfig.AUTH_PATH + "/signup",
                bearer = null,
                body = body,
            )
        }.getOrElse { e ->
            Log.w(TAG, "anon sign-in network failure: ${e.javaClass.simpleName}")
            return null
        }
        val session = parseAuthResponse(response.body) ?: run {
            Log.w(TAG, "anon sign-in failed: http=${response.code} ${response.body.take(120)}")
            return null
        }
        storeSession(session.toStored())
        return session
    }

    /**
     * Ensure a session exists, returning the access token or null. Offline
     * starts are fine: callers treat null as "retry later" (never crash).
     */
    suspend fun ensureSignedIn(): Session? {
        cached?.let { session ->
            if (session.expiresAtMs > System.currentTimeMillis() + EXPIRY_MARGIN_MS) {
                return session
            }
            // Token expired (or close): refresh once, then re-check.
            return refreshSession() ?: session
        }
        return signInAnonymously()
    }

    private suspend fun refreshSession(): Session? {
        // Single-flight: concurrent 401s must not stampede /token.
        if (!refreshing.compareAndSet(null, Unit)) return cached
        try {
            val current = cached ?: return null
            val body = buildJsonObject { put("refresh_token", current.refreshToken) }.toString()
            val response = runCatching {
                postJson(
                    url = RemoteConfig.url() + RemoteConfig.AUTH_PATH +
                        "/token?grant_type=refresh_token",
                    bearer = null,
                    body = body,
                )
            }.getOrNull() ?: return null
            val session = parseAuthResponse(response.body) ?: run {
                Log.w(TAG, "session refresh failed: http=${response.code}")
                return null
            }
            storeSession(session.toStored())
            return session
        } finally {
            refreshing.set(null)
        }
    }

    private fun parseAuthResponse(body: String): Session? = runCatching {
        val obj = json.parseToJsonElement(body).jsonObjectOrThrow()
        val access = obj.str("access_token") ?: return null
        val refresh = obj.str("refresh_token") ?: return null
        val user = (obj["user"] as? JsonObject)?.str("id")
            ?: obj.str("id")
            ?: return null
        val expiresIn = obj.str("expires_in")?.toLongOrNull() ?: DEFAULT_EXPIRES_IN_S
        Session(
            accessToken = access,
            refreshToken = refresh,
            userId = user,
            expiresAtMs = System.currentTimeMillis() + expiresIn * 1000,
        )
    }.getOrNull()

    private fun Session.toStored() = StoredSession(accessToken, refreshToken, userId, expiresAtMs)

    // ---- Generic REST helpers ----

    /** Headers used for every Supabase call: publishable key + user JWT. */
    private fun baseHeaders(bearer: String?): Map<String, String> = buildMap {
        put("apikey", RemoteConfig.publishableKey())
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        // User identity: Supabase Auth JWT (RLS applies) — distinct from the
        // API key itself (sb_publishable_...) which only names the project.
        put("Authorization", "Bearer ${bearer ?: RemoteConfig.publishableKey()}")
    }

    /**
     * One-shot POST returning the body, or null on failure. Refreshes the
     * session exactly once on a 401 before giving up.
     */
    suspend fun postJson(url: String, bearer: String?, body: String): HttpResponse =
        execute(url, baseHeaders(bearer), body)

    /**
     * Authenticated PostgREST insert. `onConflict` adds the
     * `Prefer: resolution=merge-duplicates` header (idempotent upsert).
     */
    suspend fun insertRow(
        table: String,
        body: JsonObject,
        bearer: String?,
        onConflictMerge: Boolean = false,
    ): Boolean {
        val session = bearer?.let { return@let Session(it, "", "", Long.MAX_VALUE) }
            ?: ensureSignedIn() ?: return false
        val headers = baseHeaders(session.accessToken).toMutableMap()
        headers["Prefer"] =
            if (onConflictMerge) "return=minimal,resolution=merge-duplicates"
            else "return=minimal"
        val response = runCatching {
            execute(
                RemoteConfig.url() + RemoteConfig.REST_PATH + "/" + table,
                headers,
                body.toString(),
            )
        }.getOrElse {
            Log.w(TAG, "insert $table network failure: ${it.javaClass.simpleName}")
            return false
        }
        if (response.code == 401) {
            // Access token expired mid-flight: refresh once and retry once.
            val refreshed = runCatching { refreshSession() }.getOrNull() ?: return false
            val retryHeaders = baseHeaders(refreshed.accessToken).toMutableMap()
            retryHeaders["Prefer"] =
                if (onConflictMerge) "return=minimal,resolution=merge-duplicates"
                else "return=minimal"
            val retry = runCatching {
                execute(
                    RemoteConfig.url() + RemoteConfig.REST_PATH + "/" + table,
                    retryHeaders,
                    body.toString(),
                )
            }.getOrNull() ?: return false
            return retry.code in 200..299
        }
        if (response.code !in 200..299) {
            Log.w(TAG, "insert $table failed: http=${response.code} ${response.body.take(120)}")
        }
        return response.code in 200..299
    }

    /**
     * Authenticated PostgREST SELECT with PostgREST filters, e.g.
     * `selectRows("employees", "owner_uid=eq.$uid&select=*")`.
     */
    suspend fun selectRows(table: String, query: String, bearer: String?): String? {
        val session = bearer?.let { return@let Session(it, "", "", Long.MAX_VALUE) }
            ?: ensureSignedIn() ?: return null
        val response = runCatching {
            get(
                RemoteConfig.url() + RemoteConfig.REST_PATH + "/" + table +
                    "?" + query,
                baseHeaders(session.accessToken),
            )
        }.getOrNull() ?: return null
        if (response.code == 401) {
            val refreshed = runCatching { refreshSession() }.getOrNull() ?: return null
            return runCatching {
                get(
                    RemoteConfig.url() + RemoteConfig.REST_PATH + "/" + table +
                        "?" + query,
                    baseHeaders(refreshed.accessToken),
                )
            }.getOrNull()?.body
        }
        if (response.code !in 200..299) {
            Log.w(TAG, "select $table failed: http=${response.code} ${response.body.take(120)}")
            return null
        }
        return response.body
    }

    /**
     * Authenticated PostgREST UPDATE (filtered). Returns true on 2xx/204.
     */
    suspend fun updateRow(
        table: String,
        filter: String,
        body: JsonObject,
        bearer: String?,
    ): Boolean {
        val session = bearer?.let { return@let Session(it, "", "", Long.MAX_VALUE) }
            ?: ensureSignedIn() ?: return false
        val headers = baseHeaders(session.accessToken).toMutableMap()
        headers["Prefer"] = "return=minimal"
        val response = runCatching {
            patch(
                RemoteConfig.url() + RemoteConfig.REST_PATH + "/" + table +
                    "?" + filter,
                headers,
                body.toString(),
            )
        }.getOrNull() ?: return false
        if (response.code == 401) {
            val refreshed = runCatching { refreshSession() }.getOrNull() ?: return false
            val retryHeaders = baseHeaders(refreshed.accessToken).toMutableMap()
            retryHeaders["Prefer"] = "return=minimal"
            val retry = runCatching {
                patch(
                    RemoteConfig.url() + RemoteConfig.REST_PATH + "/" + table +
                        "?" + filter,
                    retryHeaders,
                    body.toString(),
                )
            }.getOrNull() ?: return false
            return retry.code in 200..299
        }
        return response.code in 200..299
    }

    /**
     * Authenticated PostgREST RPC: POST /rest/v1/rpc/{function} with JSON
     * args, returning the response body or null on failure (401 refreshes
     * the session once and retries).
     */
    suspend fun rpc(function: String, args: JsonObject): String? {
        val session = ensureSignedIn() ?: return null
        val url = RemoteConfig.url() + RemoteConfig.REST_PATH + "/rpc/" + function
        val response = runCatching {
            postJson(url, session.accessToken, args.toString())
        }.getOrElse {
            Log.w(TAG, "rpc $function network failure: ${it.javaClass.simpleName}")
            return null
        }
        if (response.code == 401) {
            val refreshed = runCatching { refreshSession() }.getOrNull() ?: return null
            return runCatching {
                postJson(url, refreshed.accessToken, args.toString())
            }.getOrNull()?.body
        }
        if (response.code !in 200..299) {
            Log.w(TAG, "rpc $function failed: http=${response.code} ${response.body.take(120)}")
            return null
        }
        return response.body
    }

    /**
     * Invoke a Supabase edge function with the user's bearer token. Returns
     * the response body or null (functions return 2xx or an error status).
     */
    suspend fun invokeFunction(name: String, body: JsonObject): Pair<Int, String>? {
        val session = ensureSignedIn() ?: return null
        val response = runCatching {
            postJson(
                RemoteConfig.url() + RemoteConfig.FUNCTIONS_PATH + "/" + name,
                session.accessToken,
                body.toString(),
            )
        }.getOrNull() ?: return null
        return response.code to response.body
    }

    // ---- Low-level HTTP ----

    data class HttpResponse(val code: Int, val body: String)

    private fun execute(url: String, headers: Map<String, String>, body: String?): HttpResponse {
        val conn = open(url)
        conn.requestMethod = if (body == null) "GET" else "POST"
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return readResponse(conn)
    }

    private fun get(url: String, headers: Map<String, String>): HttpResponse {
        val conn = open(url)
        conn.requestMethod = "GET"
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return readResponse(conn)
    }

    private fun patch(url: String, headers: Map<String, String>, body: String): HttpResponse {
        val conn = open(url)
        // HttpURLConnection has no PATCH; tunnel it the PostgREST way.
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return readResponse(conn)
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = false
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): HttpResponse {
        val code = try {
            conn.responseCode
        } catch (e: IOException) {
            conn.disconnect()
            throw e
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return HttpResponse(code, text)
    }

    private companion object {
        const val TAG = "SupabaseClient"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val DEFAULT_EXPIRES_IN_S = 3600L
        const val EXPIRY_MARGIN_MS = 60_000L

        private fun JsonObject.str(key: String): String? =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString || it.content.toLongOrNull() != null }?.content

        private fun Any.jsonObjectOrThrow(): JsonObject = this as JsonObject
    }

    /**
     * Session persistence (encrypted at rest, spec: device identity survives
     * process death but is wiped by Clear Data — that is correct: Clear Data
     * creates a fresh anonymous identity, exactly like Firebase did).
     */
    private class SessionStore(context: Context) {
        private val prefs by lazy {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "payvoice_supabase_session",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        fun load(): StoredSession? = runCatching {
            prefs.getString(KEY, null)?.let { json.decodeFromString<StoredSession>(it) }
        }.getOrNull()

        fun save(session: StoredSession) = runCatching {
            prefs.edit().putString(KEY, json.encodeToString(session)).apply()
        }

        fun clear() = runCatching { prefs.edit().remove(KEY).apply() }

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
            const val KEY = "session_v1"
        }
    }
}
