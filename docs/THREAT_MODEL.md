# PayVoice — Threat Model

**Date:** 2026-10-03 · **Status:** draft for review · **Companion:** [`AUDIT_REPORT.md`](AUDIT_REPORT.md)

Scope: the Android client (`app/`), the Supabase project (`supabase/`), and the FCM gateway
Edge Function. Firebase is in scope **only** as FCM transport plus structural Analytics, per
`AGENTS.md`.

---

## 1. System in one paragraph

A shop owner installs PayVoice on their own phone and generates a short pairing code. An
employee installs the app, enters that code, and their phone is claimed by the owner's
Supabase account. The app reads Google Pay payment notifications locally via the Notification
Listener service, extracts the amount, and announces it aloud with TTS. A Supabase Edge
Function fans payment events out to paired devices over **FCM data messages**. Employees
receive events they are authorized for and announce them; they cannot see each other.

## 2. Assets

Ranked by what hurts the business if it leaks.

| # | Asset | Where it lives | Sensitivity |
|---|---|---|---|
| A1 | **Payment event content** — payer name, amount, timestamp | Phone notification listener → in-memory → Room `captured_notifications`; mirrored in Supabase `payment_events` | High — someone's actual income, on a phone that is often unattended on a counter |
| A2 | **Owner identity and team membership** | Supabase `employees` rows (`owner_uid`, names, pairing state) | Medium–high — reveals who works where and who controls the account |
| A3 | **Supabase auth session** (access + refresh tokens) | `EncryptedSharedPreferences` under a Keystore-backed AES-GCM key, on each paired phone | High — a stolen token is full account impersonation for its lifetime |
| A4 | **Pairing codes** | Generated on the owner's phone, 10-minute TTL, single-use | High **only during the TTL** — a valid unused code lets anyone claim a device as an employee |
| A5 | **FCM registration token** for a device | `devices.fcm_token` in Supabase, mirrored in prefs | Medium — lets an attacker push payment notifications to that device |
| A6 | **Release signing key** | Local `keystore.properties` + `.jks`, gitignored | Critical — its loss or exposure breaks every future update |
| A7 | **Service-account credentials / Supabase secret key** | Edge Function secrets only; **never** in the client | Critical — would grant full database admin |
| A8 | **Aggregate business metrics** | Firebase Analytics, structural-only, debug builds disabled | Low — deliberately content-free per `docs/ANALYTICS.md` |

**Explicitly not assets:** the `sb_publishable_` key and `SUPABASE_URL`. Both are designed to
ship in a client; RLS is the data guard, not the key. Treating them as secrets is a common
and harmful misconception.

## 3. Trust boundaries

```
┌─ OWNER PHONE ────────────┐   ┌─ EMPLOYEE PHONE ───────────┐
│ owner session token [A3] │   │ employee session token [A3]│
│ generates pairing code A4 │   │ claims code → employees row│
└───────────┬───────────────┘   └───────────┬───────────────┘
            │ pairing code (10 min, 1 use)   │
            ▼                               ▼
┌──────────────── SUPABASE (auth.uid() = the ONLY identity) ─────────────┐
│  employees · devices · payment_events · pairing_codes                  │
│  RLS on every table (0001:76-78, 0002:32)                              │
│  anon grants revoked on tables/functions/sequences (0006-0008)          │
└───────────┬──────────────────────────────────────────┬───────────────┘
            │ service-role via auth:'secret'            │  publishable key + JWT
            ▼                                          ▼
┌── EDGE FUNCTION (fcm-gateway) ──┐        ┌──── ANDROID CLIENT ────┐
│ mints OAuth → FCM HTTP v1       │───────▶│ validate → dedupe →    │
│ [A7] never leaves this boundary  │        │ TTS announce           │
└──────────────────────────────────┘        └────────────────────────┘

Local-only path (no network, per AGENTS.md):
GPay notification → NotificationListener → RemoteEventValidator → Room dedupe → TTS
```

**Key property:** the announcement path never *depends* on the network. Compromise of the
backend degrades cross-device delivery but not the core feature.

## 4. Attackers

| ID | Attacker | Capability | Motivation |
|---|---|---|---|
| **T1** | Malicious app on the same device | Runs as another UID; reads shared storage only within its own sandbox | Steal tokens, read payment notifications, spam announcements |
| **T2** | Rooted / custom-ROM device | Full filesystem, kernel-level observation, can hook the app process | Extract tokens from memory, read Room DB, bypass TTS |
| **T3** | Network MITM on public Wi-Fi | Sees and modifies unencrypted HTTP; can serve a hostile cert if the device trusts user CAs | Steal bearer tokens, inject fake payment events |
| **T4** | Stolen / sold phone, unlocked | Physical possession of a running session | Read payment history, act as the employee |
| **T5** | Curious or malicious employee | A legitimate paired device with a valid session | Enumerate the team, escalate to owner, read others' payment data |
| **T6** | Reverse engineer | Has the APK; static + dynamic analysis | Understand pairing logic, find bypasses, harden-target the app |
| **T7** | Supply-chain attacker | Compromises a dependency or the build | Code execution in every install |
| **T8** | Shoulder surfer / bystander | Sees an unlocked or locked screen | Reads payer name + amount off the notification |

---

## 5. Threats, mitigations, and residual risk

### T1 — Malicious app on the same device

| Threat | Mitigation | Status | Residual |
|---|---|---|---|
| Read session tokens from prefs | `EncryptedSharedPreferences`, AES256-GCM values, AES256-SIV keys, Keystore-backed non-exportable master key | **In place** | MasterKey rebuilt per access — perf issue, not security (H6) |
| Read Room DB directly | `allowBackup="false"` + explicit `dataExtractionRules` blocks backup/restore exfiltration | **Partial** | **DB file is plaintext.** See §6 |
| Inject a fake payment notification | App is the only NotificationListener consumer; `RemoteEventValidator` enforces `evt_` prefix, length, and field shape before anything is announced | **In place** | Validator strength is load-bearing — needs its own tests |
| Abuse an exported component | Both services `exported="false"`; listener carries `BIND_NOTIFICATION_LISTENER_SERVICE`; launcher activity is the only exported surface and has no sensitive intent extras | **In place** | None known |
| `PendingIntent` hijack | — | **Needs verification** | Confirm `FLAG_IMMUTABLE` on every `PendingIntent` (MASVS MSTG-0033) |

### T2 — Rooted device

A determined attacker on a rooted device can defeat any client-side control. The design
response is **server-side authorization**, which this project has:

| Threat | Mitigation | Status |
|---|---|---|
| Replay a `payment_events` row | **Every FCM event is authorized before any announcement work** — device must be paired, `ACTIVE`, and the event must belong to the paired owner. Fail-closed. | **In place** (`PayVoiceMessagingService.kt`) |
| Escalate employee → owner | RLS: employees `SELECT` only `owner_uid = auth.uid()`; an employee updates only their own row and **can never change `owner_uid`** | **In place** |
| Read another employee's rows | RLS denies at the database, not the client | **In place** |
| Fabricate a payment | Server-side: amounts, ids, ownership and roles are never taken from the client; only the Edge Function with the service role writes `payment_events` | **In place** |

**Residual risk accepted:** on a rooted device the announcement content and history are
readable. Root detection is deliberately **not** proposed — per the brief it is a risk
signal, not proof, and hard-crashing would punish legitimate custom-ROM users in the target
market. Server-side risk decisions are the correct layer.

### T3 — Network MITM

| Threat | Mitigation | Status | Residual |
|---|---|---|---|
| Read bearer tokens in transit | HTTPS; **but `usesCleartextTraffic` defaults false only at API 28+** and `minSdk = 26` | **GAP — C4** | **Android 8.0/8.1 can send HTTP.** Fix = network security config |
| Hostile CA | Release must not trust user-added CAs; debug-only `debug-overrides` | **GAP — C4** | Fix in the same config |
| Impersonate Supabase | — | **Not implemented — deliberate** | See §6 / Open Question 2: pinning without a backup pin can brick the app on cert rotation |
| Replay a payment event | `processed_events` dedupe on `evt_…` id | **In place**, but durability depends on process survival — **H3** | `goAsync()` |

### T4 — Stolen / unlocked phone

| Threat | Mitigation | Status |
|---|---|---|
| Read payment history | Lock screen, plus app lock is **not** implemented | **Gap** — `BiometricPrompt` (`BIOMETRIC_STRONG`, device-credential fallback) on owner screens is proposed, not built |
| Lock-screen leak of payer + amount | `setVisibility(VISIBILITY_PUBLIC)` with full text | **GAP — M1** |
| Recents-thumbnail leak | No `FLAG_SECURE` | **GAP — M2** |
| Continue acting as the employee | — | **Gap** — sign-out should wipe tokens, Room cache, and the device's FCM registration |

### T5 — Curious / malicious employee

The most important boundary in this app, and the one the database enforces.

| Threat | Mitigation | Status |
|---|---|---|
| Read the owner row or another employee | RLS `owner_uid = auth.uid()` | **In place** |
| Edit their own row to become owner | RLS restricts the updated columns; `owner_uid` is not updatable by an employee | **In place** |
| Enumerate the team | `observeEmployees()` is an **owner-only** code path; an employee reads only their own row (`id=eq.$uid`) | **In place** |
| Claim a second device | `claim_pairing()` is `SECURITY DEFINER`, atomic, single-use, 10-minute TTL enforced by **Postgres server time only** | **In place** — and granular errors (`invalid`, `already-used`, `expired`, `unauthenticated`) let the client avoid leaking which one occurred |
| Brute-force a pairing code | Random code, single-use, TTL. **Server-side rate limiting not implemented** | **Partial** — M4 |
| Reclaim a used/expired code | `SELECT … FOR UPDATE` then `UPDATE … SET used = true` in one transaction | **In place** (`0003:73-88`) |
| Read pairing codes | RLS on `pairing_codes`; the code is never logged and never written to `diagnostic_log` | **In place** |

### T6 — Reverse engineer

| Threat | Mitigation | Status | Residual |
|---|---|---|---|
| Read app logic | R8 obfuscation + resource shrinking | **GAP — C1.** The release APK is **fully deobfuscated today** | This is the cheapest large win available |
| Find a pairing bypass | All pairing decisions are server-side in Postgres; the client only renders outcomes | **In place (architecturally sound)** | Client compromise should never grant pairing — verify this stays true |
| Extract the publishable key | Not a secret — see §2 | **By design** | None |
| Extract a *secret* key | Full-history scan found none; client holds only publishable key + URL | **In place** | Maintain via CI secret scan (M9) |

### T7 — Supply chain

| Threat | Mitigation | Status |
|---|---|---|
| Compromised dependency resolves silently | No `gradle/verification-metadata.xml` | **GAP — H4** |
| Version drift | `gradle/libs.versions.toml` version catalog pins everything; FCM is `strictly` pinned so the BOM cannot move it | **In place** |
| Typosquat | Version catalog uses full coordinates with explicit versions | **In place** |
| Known CVE ships | No `osv-scanner` / `dependency-check` in CI | **GAP — M9** |

### T8 — Shoulder surfer / bystander

| Threat | Mitigation | Status |
|---|---|---|
| Payer name + amount visible on a locked screen | `VISIBILITY_PUBLIC` hard-coded | **GAP — M1** |
| Screenshot / screen-record | No `FLAG_SECURE` | **GAP — M2** |
| Notification in the shade when the phone is handed over | Silent high-priority receipt notification is required for Doze delivery | Accepted, documented tradeoff |

---

## 6. Accepted risks and deliberate omissions

These are conscious decisions, not oversights. Recording them so a future contributor does
not "fix" them by accident.

1. **`sb_publishable_` key ships in the client.** Correct. It is designed to be public; RLS is
   the guard. Rotating it is not a security response to a leak.
2. **No certificate pinning yet.** Pinning without a backup pin and a written rotation
   runbook risks bricking every install on cert rotation. Planned behind the network security
   config (C4), pending your answer to Open Question 2.
3. **No root/emulator detection.** Risk signal, not proof; would punish legitimate low-end and
   custom-ROM users. Risk decisions belong server-side.
4. **`EncryptedSharedPreferences` retained for now.** The crypto is correct (AES-GCM under a
   non-exportable Keystore master key). The deprecated *API* is a maintenance concern;
   the immediate defect is the per-access MasterKey rebuild (H6), which is a performance bug.
5. **Room DB is not encrypted with SQLCipher.** See the explicit cost question below.
6. **No app lock / BiometricPrompt yet.** Proposed, not built. Depends on your call on
   whether payment screens warrant it.
7. **Local payment content is not treated as high-sensitivity by the OS.** PayVoice is not a
   wallet; it observes notifications. The threat is a shoulder-surfer, not a targeted device
   compromise — which is why M1/M2 are Medium rather than High.

---

## 7. Open questions that change the model

**SQLCipher vs. performance (constraint 3 requires measurement, not assumption).** A Room DB
holding payer names and amounts is plaintext on a 1 GB device. SQLCipher would close that, but
it costs CPU on every query — and the query path is exactly the announcement path.

My plan is to **measure, report numbers, and ask** rather than choose unilaterally:

- Benchmark with and without SQLCipher: DB-open time, history query, retention sweep, and
  cold-start impact. The AVD available here (Pixel 9 Pro XL, API 36, 4 cores / 4 GB, host
  speed) is far too fast to answer the CPU question — this needs a throttled run or real
  hardware, and the SQLite numbers from an emulator should not be quoted as if they were.
- Present you the numbers plus the added APK size.
- **Then decide together.** If encryption costs more than the threat justifies for a device
  that is usually locked and PIN-protected, I will recommend *not* shipping it and document
  why. I will not quietly ship plaintext and I will not quietly ship a 30 % CPU tax.

**`Request_Ignore_Battery_Optimizations`** is a Play Console policy question, not a security
one — see AUDIT_REPORT Open Question 5.

---

## 8. Post-fix verification

No control in this model is considered done until it is checked, not assumed:

| Control | Verification |
|---|---|
| RLS owner isolation | Postgres test: owner A cannot read owner B's rows |
| RLS employee isolation | Postgres test: employee cannot read a peer, and cannot change `owner_uid` |
| Pairing single-use / TTL | Postgres test: second claim returns `already-used`; post-TTL returns `expired` |
| Cleartext disabled | `adb shell` on API 26/27: an HTTP request to Supabase must fail |
| No secrets in artifact | Unzip the release APK; scan for `sb_secret_`, `eyJ`, `AIza`, PEM headers |
| No logs in release | Run the app; `logcat` must be silent for `DebugLog` tags |
| Dependency verification | Tamper with a cached artifact; the build must fail |
| Token storage | `adb shell run-as` is unavailable in release; confirm the prefs file is ciphertext |
| FCM idempotency | Deliver the same `evt_…` twice; exactly one announcement |
