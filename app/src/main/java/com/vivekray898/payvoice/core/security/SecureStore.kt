package com.vivekray898.payvoice.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vivekray898.payvoice.core.util.DebugLog

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
 * ## Recovery
 *
 * A stable alias is only stable while the Keystore entry survives. It does not:
 * a device lock change, a factory reset, an emulator snapshot restore, or a
 * backup restore onto different hardware can all drop the key while leaving the
 * `SharedPreferences` files on disk. Every read of that file then fails with
 * `AEADBadTagException`, and because the failure is indistinguishable from
 * "no value stored", the app degrades into a state it can never leave — the
 * FCM token read fails, the token is never re-registered, and Health reports
 * "this device isn't registered for alerts" forever.
 *
 * So [prefs] treats an undecryptable store as recoverable: it drops the
 * ciphertext and rebuilds it. The stored data is genuinely unrecoverable — the
 * key that encrypted it is gone — but an empty store is a *known* state the app
 * already handles (signed out, no token, re-pair). All three files are reset
 * together, because they share one key and a partial reset is exactly the
 * half-signed-in state this class exists to prevent.
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

    private const val TAG = "SecureStore"

    /**
     * Every encrypted file this app owns. They share [KEY_ALIAS], so they
     * fail and recover together; a reset that missed one would leave a store
     * writing under a new key while another reads under the old one.
     */
    private val STORE_FILES = listOf(
        "payvoice_secure_prefs",
        "payvoice_supabase_session",
        "payvoice_pairing_snapshot",
    )

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
     *
     * If the file exists but cannot be decrypted, the store is rebuilt empty
     * rather than throwing. See the class docs for why.
     */
    @Synchronized
    fun prefs(context: Context, fileName: String): SharedPreferences {
        stores[fileName]?.let { return it }
        val app = context.applicationContext
        val direct = runCatching { create(app, fileName) }
        if (direct.isSuccess) {
            return direct.getOrThrow().also { stores[fileName] = it }
        }
        val cause = direct.exceptionOrNull()
        DebugLog.w(
            TAG,
            "store '$fileName' unreadable (${cause?.javaClass?.simpleName}); resetting secure stores",
        )
        // TEMPORARY: release-visible DIAG. DebugLog is stripped/disabled in
        // release, and a secure-store reset signs the user out — an
        // unexplained logout is exactly what we would want to see reported.
        Log.w(TAG, "DIAG: secure store reset, $fileName unreadable: ${cause?.javaClass?.simpleName}")
        resetEncryptedData(app)
        return create(app, fileName).also { stores[fileName] = it }
    }

    private fun create(context: Context, fileName: String): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    /**
     * Drops every encrypted file and the cached handles, so the next access
     * rebuilds both the key and the stores from nothing.
     *
     * Only the app's own three files are touched. Deleting the master key
     * entry first is what makes this a real recovery: if the alias still
     * resolves to a keyset that cannot read the existing ciphertext, keeping
     * it would just reproduce the same `AEADBadTagException`.
     */
    private fun resetEncryptedData(context: Context) {
        stores.clear()
        masterKey = null
        runCatching {
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        }.onFailure { DebugLog.w(TAG, "master key delete failed: ${it.javaClass.simpleName}") }
        STORE_FILES.forEach { name ->
            runCatching { context.deleteSharedPreferences(name) }
        }
    }

    /** Test/diagnostic hook: forgets cached handles without clearing data. */
    @Synchronized
    internal fun resetCache() {
        masterKey = null
        stores.clear()
    }
}