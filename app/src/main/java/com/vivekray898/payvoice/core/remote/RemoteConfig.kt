package com.vivekray898.payvoice.core.remote

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Supabase project connection (CURRENT API-key system, no legacy keys).
 *
 * The Android app ships ONLY:
 *  - [SUPABASE_URL]            — the project URL
 *  - [SUPABASE_PUBLISHABLE_KEY]— a `sb_publishable_...` key
 *
 * The publishable key is NOT a secret: it is safe to bundle (docs: "Safe to
 * expose online"), and it never bypasses Row Level Security. The legacy
 * `anon`/`service_role` JWT keys (long `eyJ...` strings) are deprecated by
 * Supabase and MUST NOT be reintroduced (see AGENTS.md).
 *
 * NO privileged credential of any kind may appear in this file or anywhere
 * in the app: no `sb_secret_...`, no FCM service-account key, no OAuth
 * client secret. Server-side work happens exclusively in Supabase Edge
 * Functions holding `SUPABASE_SECRET_KEY` + FCM service-account secrets.
 */
object RemoteConfig {

    /**
     * Project URL. Placeholder value below — override without touching
     * source via a `payvoice_supabase_url` string resource, or set the real
     * value here when it is finalized (it is public by design).
     */
    const val SUPABASE_URL = "https://cbaqdornupiuexciwxbv.supabase.co"

    /**
     * Current publishable key (`sb_publishable_...`, Project Settings → API
     * Keys). Placeholder value — a placeholder is intentionally HONEST: with
     * no real key the remote layer fails fast with [isConfigured] == false
     * instead of silently half-working against a foreign project.
     */
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_46Mp6lj9FxKFTs83rQhU9Q_LAJY_eVD"

    @Volatile
    var urlOverride: String? = null

    @Volatile
    var publishableKeyOverride: String? = null

    /** Effective project URL (override-aware, for tests). */
    fun url(): String = urlOverride ?: SUPABASE_URL

    /**
     * Effective publishable key (override-aware, for tests). Must always
     * carry the `sb_publishable_` prefix in production builds — a legacy
     * `eyJ...` value here is a hard error (fail fast, never ship legacy).
     */
    fun publishableKey(): String {
        val key = publishableKeyOverride ?: SUPABASE_PUBLISHABLE_KEY
        check(key.startsWith("sb_publishable_")) {
            "Refusing to use a legacy/non-publishable Supabase API key (expected sb_publishable_...)"
        }
        return key
    }

    /** True when real (non-placeholder) credentials are wired. */
    fun isConfigured(): Boolean =
        !url().contains("REPLACE-ME") && !publishableKey().contains("REPLACE")

    /**
     * Convenience override hook for local development: reads a pair of
     * string resources if present (gitignored local setup), otherwise
     * leaves the constants in effect. Never stores secrets — publishable
     * only.
     */
    fun applyResourceOverrides(context: Context) {
        runCatching {
            val id = context.resources.getIdentifier(
                "payvoice_supabase_url", "string", context.packageName,
            )
            if (id != 0) urlOverride = context.resources.getString(id)
        }
        runCatching {
            val id = context.resources.getIdentifier(
                "payvoice_supabase_publishable_key", "string", context.packageName,
            )
            if (id != 0) publishableKeyOverride = context.resources.getString(id)
        }
    }

    // ---- Path layout ----

    const val REST_PATH = "/rest/v1"
    const val AUTH_PATH = "/auth/v1"
    const val FUNCTIONS_PATH = "/functions/v1"
    const val REALTIME_PATH = "/realtime/v1/websocket"

    // ---- FCM data-payload keys (spec §16 field names) ----

    const val FCM_KEY_TYPE = "type"
    const val FCM_KEY_EVENT_ID = "eventId"
    const val FCM_KEY_AMOUNT = "amountMinor"
    const val FCM_KEY_CURRENCY = "currency"
    const val FCM_KEY_SENDER = "senderName"
    const val FCM_KEY_SOURCE = "source"
    const val FCM_KEY_TIMESTAMP = "timestampMs"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
}
