package com.vivekray898.payvoice.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Single owner of the app's Keystore-backed encryption key.
 *
 * Three call sites (session, FCM token, pairing snapshot) each built their own
 * `MasterKey`. Every one of those builds is a Keystore round trip on first use
 * and can fail independently, and nothing tied the three together — a key
 * invalidated or regenerated for one store left the others pointing at a key
 * nobody could decrypt with, so a partially-failed restore produced a session
 * that silently read as "not signed in" and an app that looked logged out with
 * no cause.
 *
 * The key alias is explicit and stable, so the same key is reused across
 * process restarts, and each named store is created at most once per process.
 *
 * Note: `EncryptedSharedPreferences` (androidx.security:security-crypto) is
 * deprecated upstream. Migrating these stores to DataStore plus a
 * Keystore-wrapped key is tracked as a follow-up; this change removes the
 * duplicated key handling without changing the on-disk format, so the two are
 * separable.
 */
object SecureStore {

    /**
     * Stable alias. `MasterKey.Builder` defaults to
     * `_androidx_security_master_key_`, which is shared with any other library
     * using the same default — naming it makes the key ours explicitly.
     */
    private const val KEY_ALIAS = "payvoice_master_key_v1"

    @Volatile
    private var masterKey: MasterKey? = null

    /** Cached per prefs file name; creating one is not free. */
    private val stores = HashMap<String, SharedPreferences>()

    /**
     * The process-wide [MasterKey], created on first use.
     *
     * @throws androidx.security.crypto.KeyStoreException when the Keystore is
     *   unavailable (no hardware, or a corrupted keyset) — callers decide
     *   whether that is fatal or a soft degradation.
     */
    @Synchronized
    fun masterKey(context: Context): MasterKey =
        masterKey ?: MasterKey.Builder(context.applicationContext, KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            .also { masterKey = it }

    /**
     * Returns the encrypted store named [fileName], creating it on first use.
     *
     * Keys are encrypted with AES-SIV (deterministic, so lookups by key name
     * work) and values with AES-GCM (authenticated), which is the scheme the
     * previous per-call-site code used — the on-disk format is unchanged.
     */
    @Synchronized
    fun prefs(context: Context, fileName: String): SharedPreferences =
        stores.getOrPut(fileName) {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                fileName,
                masterKey(context),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

    /** Test/diagnostic hook: forgets cached handles without clearing data. */
    @Synchronized
    internal fun resetCache() {
        masterKey = null
        stores.clear()
    }
}
