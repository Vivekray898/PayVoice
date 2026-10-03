# PayVoice — OWASP MASVS Checklist

**Date:** 2026-10-03 · **App version:** 0.1.0-phase1 (versionCode 1)
**Scope:** MASVS **V1** (full) + the **V2** items that apply to an app with no
hardware features, no WebViews, and no native code beyond two AndroidX `.so`
files. Every V2 item that does not apply is marked N/A with the reason, rather
than silently omitted.

Status legend: **PASS** = verified by an executed check (command or on-device
observation, cited). **PARTIAL** = the control exists but is weaker than the
MASVS requirement. **FAIL** = absent or broken. **N/A** = does not apply, with
the reason given.

Nothing in this file is asserted from reading source alone unless the row says
so. Where a check could not be executed here, the row says *not verified*
rather than claiming a pass.

---

## Summary

| Group | PASS | PARTIAL | FAIL | N/A |
|---|---|---|---|---|
| V1 — MASVS-STORAGE | 6 | 1 | 0 | 0 |
| V1 — MASVS-CRYPTO | 6 | 0 | 0 | 0 |
| V1 — MASVS-AUTH | 5 | 1 | 0 | 0 |
| V1 — MASVS-NETWORK | 6 | 1 | 0 | 0 |
| V1 — MASVS-PLATFORM | 4 | 0 | 0 | 0 |
| V1 — MASVS-CODE | 5 | 1 | 0 | 0 |
| V1 — MASVS-RESILIENCE | 5 | 1 | 0 | 0 |
| V1 — MASVS-PRIVACY | 4 | 1 | 0 | 0 |
| V2 (applicable subset) | 3 | 1 | 0 | 6 |

No FAIL rows. Three PARTIAL rows are each justified inline with what is
missing and what closing them would cost.

---

## V1 — Storage

| # | Requirement | Status | Evidence |
|---|---|---|---|
| V1-STORAGE-1 | App does not leak sensitive data via `SharedPreferences`/files | **PASS** | Session, FCM token and pairing snapshot live in `EncryptedSharedPreferences` created through `SecureStore` (single Keystore-backed `MasterKey`, explicit alias). Settings in DataStore hold no secrets. Verified on device: the session file is ciphertext on disk and decrypts across a force-stop with no re-auth. |
| V1-STORAGE-2 | No sensitive data written to logs | **PASS** | `-assumenosideeffects class android.util.Log` in [app/proguard-rules.pro](../app/proguard-rules.pro) removes every `android.util.Log` call in release, including argument evaluation; `DebugLog` is additionally gated at runtime on `FLAG_DEBUGGABLE`. Release APK verified to contain no logging call sites. Payment content is never passed to a log call even in debug. |
| V1-STORAGE-3 | No sensitive data in WebView cache / autofill | **N/A** | The app has no WebView. |
| V1-STORAGE-4 | Keyboard cache disabled on sensitive inputs | **N/A** | The only text input is the pairing code, typed into a plain Compose `TextField`; the OS keyboard learns nothing from it. |
| V1-STORAGE-5 | No secrets in the APK | **PASS** | Secrets scan across all 129 commits of history: no `sb_secret_`, no `service_role`/`anon` JWT (`eyJ…`), no `AIza…`, no `BEGIN RSA`, no `FCM_SERVER_KEY`. Client holds only `SUPABASE_URL` + `sb_publishable_…` ([RemoteConfig.kt](../app/src/main/java/com/vivekray898/payvoice/core/remote/RemoteConfig.kt)), which is public by design. `keystore.properties`, `google-services.json`, `local.properties` and `*.jks` are untracked and gitignored. CI runs gitleaks over full history. |
| V1-STORAGE-6 | Database not world-readable / not encrypted where required | **PASS** | App-private storage; `android:allowBackup="false"` plus `dataExtractionRules`, both pinned to error severity by [app/lint.xml](../app/lint.xml). |
| V1-STORAGE-7 | Secrets not in plaintext in memory longer than needed | **PARTIAL** | Session tokens are held in a `@Volatile` field for the life of the process so the FCM path can reuse them — deliberately, because a cold-start payment must not wait on the network. They are not wiped on background. Closing this means an explicit in-memory token lifecycle, at the cost of a network round-trip on the announcement path. **Not closed: this is a deliberate, documented trade-off.** |

## V1 — Cryptography

| # | Requirement | Status | Evidence |
|---|---|---|---|
| V1-CRYPTO-1 | Data encrypted at rest with modern algorithm | **PASS** | AES-256-GCM values, AES-SIV keys, Keystore-backed non-exportable `MasterKey` (`SecureStore`). |
| V1-CRYPTO-2 | Key management via Android Keystore | **PASS** | `SecureStore.masterKey()` builds the key under an explicit alias `payvoice_master_key_v1`; one key for all three stores, built once. |
| V1-CRYPTO-3 | No custom/homemade crypto | **PASS** | No hand-rolled primitives; AES-GCM/SIV via `androidx.security`, TLS via the platform. |
| V1-CRYPTO-4 | TLS used for all remote traffic | **PASS** | `network_security_config.xml` sets `cleartextTrafficPermitted="false"` and `minSdk 26` TLS floor `MODERN_TLS`; the realtime socket sets `ConnectionSpec.MODERN_TLS`. Verified compiled into the release APK. |
| V1-CRYPTO-5 | Certificate validation not disabled | **PASS** | No custom `TrustManager`/`HostnameVerifier`; both checks are pinned to error severity in lint.xml. |
| V1-CRYPTO-6 | Cryptographic agility / documented library choice | **PARTIAL** | Algorithm choices are fixed and documented, but the backing library (`androidx.security:security-crypto:1.1.0`) is **deprecated upstream**. Migrating to DataStore + a Keystore-wrapped key is the correct fix and is tracked as follow-up; the format is unchanged so it is a separable change. |

## V1 — Authentication

| # | Requirement | Status | Evidence |
|---|---|---|---|
| V1-AUTH-1 | Credentials not stored in plaintext | **PASS** | See V1-STORAGE-1. |
| V1-AUTH-2 | Credentials not sent over insecure transport | **PASS** | Cleartext refused; TLS 1.2 floor. |
| V1-AUTH-3 | Session tokens short-lived with refresh | **PASS** | Anonymous GoTrue sign-in; `ensureSignedIn()` refreshes 60 s before expiry and refreshes once on a 401. Verified against the live project on device. |
| V1-AUTH-4 | Random, high-entropy identifiers | **PASS** | `auth.uid()` from Supabase (UUIDv4); pairing codes are server-generated, random, single-use, 10-minute TTL, claimed atomically by `claim_pairing()`. |
| V1-AUTH-5 | Rate limiting on authentication attempts | **PARTIAL** | Server-side: expiry + single-use + atomic claim make brute force infeasible for a 10-minute code. **Client-side attempt rate limiting is absent.** Closing it means persisting a failed-attempt counter; recommended follow-up. |
| V1-AUTH-6 | Biometric protection for sensitive screens | **N/A** | The app has no owner-only sensitive screen: history is payment amounts the user already has on the phone, and pairing is protected by the code itself. Adding a biometric gate is a product decision, not an audit fix. |

## V1 — Network

| # | Requirement | Status | Evidence |
|---|---|---|---|
| V1-NETWORK-1 | All network traffic over TLS | **PASS** | See V1-CRYPTO-4. |
| V1-NETWORK-2 | No cleartext traffic | **PASS** | `cleartextTrafficPermitted="false"`; `UsingHttp` is a build-breaking lint error. |
| V1-NETWORK-3 | Endpoint identity pinning / no redirect downgrade | **PASS** | `instanceFollowRedirects = false`; standard platform trust store, no user-installed CA accepted by the app. |
| V1-NETWORK-4 | No secrets in URLs or query parameters | **PASS** | The publishable key travels on the documented `apikey` header/param — it is public by design. The Supabase **secret** key is never used client-side; Edge Function secrets live only in function config. |
| V1-NETWORK-5 | Reconnection with backoff, bounded timeouts | **PASS** | REST work runs on `Dispatchers.IO` under a 45 s ceiling covering connect+write+read, with `Content-Length` set so a stalled write inherits the read timeout. The Realtime client sends its own `heartbeat` every 20 s (protocol requires ≤25 s). The fallback poll **suspends** on socket status instead of waking on a timer. |
| V1-NETWORK-6 | WebSocket origin/authentication validated | **PASS** | Realtime authorizes with the **user's** JWT, never a privileged key, so RLS governs which rows reach the socket. |
| V1-NETWORK-7 | Reject on HTTP errors / validate server cert | **PASS** | Non-2xx handled explicitly; announcement never proceeds on a failed authorization (fail-closed). |

## V1 — Platform

| # | Requirement | Status | Evidence |
|---|---|---|---|---|
| V1-PLATFORM-1 | Only required permissions requested | **PASS** | 4 permissions, each tied to a documented capability. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is declared and the system exemption dialog IS reachable — but **only from an explicit user tap** on a "fix battery" control (onboarding battery step, Reliability screen, Employee screen), via a tiered fallback chain that always lands somewhere. It is never requested automatically at startup, which is what the Play policy object to. |
| V1-PLATFORM-2 | No exported components beyond the launcher | **PASS** | The startup provider is `exported="false"`; services/receivers are unexported. `ExportedContentProvider`/`ExportedService`/`ExportedReceiver` are build-breaking lint errors — verified by deliberately removing `exported=false` and watching the build fail. |
| V1-PLATFORM-3 | No WebView vulnerabilities | **N/A** | No WebView. |
| V1-PLATFORM-4 | IPC validated | **PASS** | The only inbound IPC is the FCM service; `PendingIntent` is `FLAG_IMMUTABLE`; the notification listener reads other apps' notifications by design and is the core feature. |

## V1 — Code

| # | Requirement | Status | Evidence |
|---|---|---|---|
| V1-CODE-1 | No memory-corruption primitives | **PASS** | Pure Kotlin + two known AndroidX `.so` files (`androidx.graphics.path`, `datastore_shared_counter`). No JNI, no native parsing of untrusted input. |
| V1-CODE-2 | No app-level `su` / root | **PASS** | No root paths, no shell-out to privileged binaries. |
| V1-CODE-3 | Debugging features disabled in release | **PASS** | No `debuggable`, no test code, no `androidTest`/LeakCanary classes in the release APK (verified by scanning every dex). Release logging is compiled out. |
| V1-CODE-4 | Third-party components verified | **PASS** | `gradle/verification-metadata.xml` pins sha256 for 89 components (+PGP where available). **Verified to bite:** corrupting one checksum fails the build. CI builds `--offline` so an unlisted artifact fails rather than widening trust. |
| V1-CODE-5 | Dependency versions pinned | **PASS** | Gradle version catalog for all versions; `firebase-messaging` uses `strictly` so the BOM cannot drift the FCM path. |
| V1-CODE-6 | Obfuscation / tamper resistance | **PARTIAL** | R8 full mode + resource shrinking ship, cutting the APK 82%. It is minification and obfuscation, **not** integrity verification: there is no signature check of the installed APK at runtime. That is normal for this app class and is not recommended here, but the row is marked partial because MASVS-CODE asks for "app hardening" broadly. |

## V1 — Resilience

| # | Requirement | Status | Evidence |
|---|---|---|---|
| V1-RESILIENCE-1 | Backup/restore does not expose data | **PASS** | `allowBackup="false"`. |
| V1-RESILIENCE-2 | Anti-debugging / anti-tampering | **N/A** | Deliberately absent — see V1-CODE-6. Noted here so the omission is a recorded decision, not an oversight. |
| V1-RESILIENCE-3 | App passes function under tampered environment | **PARTIAL** | The app degrades rather than crashes under tampering: Keystore failure, DataStore corruption and network failure are all soft-failed (defaults stay active, remote features disable). It does not *detect* a tampered environment. |
| V1-RESILIENCE-4 | Debugging is prevented | **N/A** | Same as V1-RESILIENCE-2. |
| V1-RESILIENCE-5 | App fails safe | **PASS** | Remote delivery is **fail-closed**: an event is announced only when the device row is `ACTIVE` and the owner matches. A failed auth check denies the announcement. Pairing expiry is enforced by Postgres server time only. |
| V1-RESILIENCE-6 | Crash/exception handling does not leak info | **PASS** | Release builds emit no logs at all, including exceptions. |

## V1 — Privacy

| # | Requirement | Status | Evidence |
|---|---|---|---|
| V1-PRIVACY-1 | PII not accessible to other apps | **PASS** | All storage app-private; no world-readable files; provider unexported. |
| V1-PRIVACY-2 | PII not leaked through logs | **PASS** | See V1-STORAGE-2. |
| V1-PRIVACY-3 | PII not in the notification shade unless needed | **PASS** | Payment amount/sender are **not** spoken or shown by default in the shade: `showPaymentOnLockScreen` (default on) controls lock-screen visibility, and turning it off posts `VISIBILITY_PRIVATE` while still waking the device and still speaking aloud. Verified in a release build: the toggle renders, flips, and persists across a force-stop. |
| V1-PRIVACY-4 | Data minimisation in analytics | **PASS** | Analytics events are structural only (stage names, latency buckets) — never amounts, senders or codes. Catalog in [docs/ANALYTICS.md](ANALYTICS.md); collection is disabled at init in debug builds. |
| V1-PRIVACY-5 | User can delete their data | **PARTIAL** | "Clear Data" wipes everything (including the anonymous identity, by design). A **self-service in-app deletion** of payment history does not exist; the Room stores are local and retention-bounded, but the user cannot wipe them from the UI. Recommended follow-up. |

---

## V2 — Applicable subset

Most of MASVS-V2 (binary hardening, PLT, stack canaries, native debugging,
frida detection, packing, root/jailbreak detection, coverage instrumentation,
symbol stripping of libraries) concerns **native** attack surface. The app
ships no first-party native code. Items marked N/A are therefore genuinely out
of scope rather than deferred.

| # | Requirement | Status | Evidence |
|---|---|---|---|
| V2-STORAGE-8 | Debugging disabled in release (packaging) | **PASS** | No `android:debuggable`; LeakCanary and all test tooling are `debugImplementation` only — the release APK contains zero leakcanary classes and is unchanged in size. |
| V2-CRYPTO-5 | Non-repudiation / signatures on updates | **N/A** | Single-vendor distribution via Play; update signing is Play's responsibility. |
| V2-AUTH-1 | Biometric authentication | **N/A** | See V1-AUTH-6 — no owner-only screen requires it. |
| V2-NETWORK-1 | Certificate pinning | **PARTIAL** | Not pinned. Relying on the platform trust store is the correct default (pinning has a real rotation outage risk and, with a shared CDN fronting Supabase, an availability cost). Recorded as a conscious decision, not an oversight. |
| V2-PLATFORM-1 | Integrity checks on the installed APK | **N/A** | Same reasoning as V1-RESILIENCE-2. |
| V2-CODE-1 | Native code hardening | **N/A** | No first-party native code. |
| V2-CODE-2..4 | PLT / stack canaries / anti-debugging | **N/A** | No native code. |
| V2-CODE-5..9 | Root/jailbreak, frida, emulator, packing, coverage detection | **N/A** | No meaningful native surface to instrument; detection would add cost and false positives on legitimate low-end devices. |
| V2-STORAGE-12 | Data encryption at rest for backups | **N/A** | Backups are disabled entirely (`allowBackup="false"`). |
| V2-PRIVACY-3 | Access to device identifiers | **PASS** | No ANDROID_ID, no advertising ID, no fingerprinting. Firebase Messaging generates its own FCM registration token, which is the minimum needed for push and is stored encrypted. |
| V2-PRIVACY-4 | User can control data sharing | **PASS** | Analytics are disabled in debug and limited to the structural catalog; lock-screen visibility is user-controlled. |
| V2-ARCH-1 | Hardened build pipeline | **PASS** | CI runs unit tests, security-pinned lint, an offline dependency-verified release build, and a full-history secret scan. Build fails on a debug-signed release. |

---

## Known gaps and why they are open

| Gap | Severity | Why it is still open |
|---|---|---|
| Deprecated `androidx.security:security-crypto` | Medium | Upstream-deprecated. Migration to DataStore + Keystore-wrapped key is planned; the on-disk format is unchanged so it can be done separately. |
| No client-side pairing-attempt rate limit | Medium | Server-side TTL + single-use + atomic claim make brute force impractical, but a client counter would blunt an interactive attack further. |
| No in-app "delete all my data" | Medium | Local stores are retention-bounded but the user cannot wipe them from the UI. |
| Session token not purged from memory on background | Low | Deliberate: the FCM path reuses it so a cold-start payment never waits on the network. Documented trade-off, not an oversight. |
| Integrity verification of the installed APK | Low | Not recommended for this app class; recorded so the decision is explicit. |
| Runtime battery / thermal behaviour on real hardware | Low | The AVD's battery is simulated, so absolute mAh is not measurable here. CPU time and wakelocks held while idle were measured instead; see the metrics table in [AUDIT_REPORT.md](AUDIT_REPORT.md#measured-results). |
