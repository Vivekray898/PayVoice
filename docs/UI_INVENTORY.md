# PayVoice — UI Inventory (Step 0)

Audit of every screen, route, ViewModel and shared component, for both roles,
plus a file+line diagnosis of why `DESIGN.md` is not actually being followed.
Produced before any redesign work. **No code has been changed.**

Scope: `app/src/main/java/com/vivekray898/payvoice/` · 4,533 lines under `ui/`
· minSdk 26, targetSdk 37 (`app/build.gradle.kts:65-66`).

---

## 1. Navigation graph

Defined in one place: `MainActivity.kt:132-190` (`Routes` + `PayVoiceNavHost`).
**There is no bottom navigation anywhere** — `grep NavigationBar|BottomNav`
returns zero hits. Every destination is a push on a back stack, reached from
four "quick link" tiles on Home.

| Route | Composable | File | Role |
|---|---|---|---|
| `onboarding` | `OnboardingScreen` + 8 steps | `ui/onboarding/` | both |
| `home` | `ParentHomeScreen` | `ui/parenthome/ParentHomeScreen.kt` | both (branches on role) |
| `owner_remote` | `OwnerRemoteScreen` | `ui/owner/OwnerRemoteScreen.kt` | owner |
| `employee_remote` | `EmployeeScreen` | `ui/employee/EmployeeScreen.kt` | employee |
| `settings` | `SettingsScreen` | `ui/settings/SettingsScreen.kt` | both |
| `diagnostics` | `DiagnosticsScreen` | `ui/diagnostics/DiagnosticsScreen.kt` | both |
| `reliability` | `ReliabilityScreen` | `ui/reliability/ReliabilityScreen.kt` | both |

Start destination is decided at `MainActivity.kt:141` from
`settings.onboardingComplete`. `onResume` (`MainActivity.kt:106`) calls
`viewModel.refreshStatus()` — status does refresh when the user comes back
from system settings.

---

## 2. ViewModel & data sources

One `MainViewModel` (`ui/MainViewModel.kt`, 516 lines) serves **every** screen.
There is no per-screen ViewModel and no per-screen `UiState` class.

| Flow | Type | Backing source |
|---|---|---|
| `settings` | `StateFlow<ParentSettings>` | DataStore (`SettingsRepositoryImpl`) |
| `history` | `StateFlow<List<AnnouncementEntity>>` | Room, **unbounded in-memory list** |
| `status` | `StateFlow<DeviceStatusMonitor.Snapshot?>` | `service/status/DeviceStatusMonitor` |
| `captured`, `diagnostics` | `StateFlow<List<…Entity>>` | Room |
| `fcm` | `FcmStatus` | `MessagingRepository` |
| `listenerRuntime` | `ListenerRuntime` | `service/notification/ListenerRuntimeState` |
| `authState` | `PayVoiceAuth.State` | `core/remote/PayVoiceAuth` |
| `employees`, `ownDevice` | `List<EmployeeDevice>` / `EmployeeDevice?` | `EmployeeRepository` (Supabase) |
| `pairingCode` | `StateFlow<PairingCode?>` | `PairingRepository` |
| `joinState` / `revokeState` / `testSendState` | sealed classes | inline in VM |
| `ttsStatus`, `testSpeaking` | `AnnouncementSpeaker.Status` | `service/tts/AnnouncementSpeaker` |

Nested state machines: `JoinState` (`MainViewModel.kt:102-110`),
`RevokeState` (`:242`), `TestSendState` (`:304`).

Health-relevant VM functions: `refreshStatus` `:364`, `repairListener` `:376`,
`fixBattery` `:403`, `openListenerSettings` `:418`,
`openAppNotificationSettings` `:421`, `openAppDetailsSettings` `:424`,
`speakTest` `:482`.

`DeviceStatusMonitor.Snapshot` (`service/status/DeviceStatusMonitor.kt:31-44`)
carries only: `listenerEnabled`, `notificationsEnabled`, `batteryExempt`,
`gpay`, `manufacturer`, `model`, `androidVersion`, `romHint`,
`isXiaomiFamily`. **It does not check** DND, media volume, network, FCM token,
Play Services, TTS voice data, or channel importance — all of which Step 4
requires.

---

## 3. Shared component inventory (`ui/components/`)

| Component | File:line | Notes |
|---|---|---|
| `PvScaffold` | `PvScaffold.kt:20` | The **only** `Scaffold(` in the module — verified |
| `PvTopBar` | `PvTopBar.kt` | used by Diagnostics, Reliability only |
| `PvPrimaryButton` | `PvPrimaryButton.kt:12` | pill, `heightIn(min = 56dp)` |
| `PvSecondaryButton` | `PvSecondaryButton.kt` | outline pill, optional icon |
| `PvActionCard` | `PvActionCard.kt` | |
| `PvSecureWindow` | `PvSecureWindow.kt` | FLAG_SECURE; used on Owner + Employee only |
| `StatusPill` / `StatusTone` | `StatusPill.kt` | Success/Warning/Error/Neutral |
| `MoneyText` | `MoneyText.kt` | tabular figures |
| `SectionHeader` | `PvScreenBits.kt:19` | |
| `ScreenHeader`, `HeroBanner`, `IconTile` | `Illustrations.kt:133,173,198` | |
| `PvEmptyState`, `PvEmptyHint`, `PvLoadingRow`, `PvDivider`, `SwitchRow`, `StatusLine`, `StatusIcon`, `StatusDot`, `PvPaymentRow`, `timeAgo` | `Common.kt` | `PvEmptyState` itself uses a raw `Button` |
| `StorefrontHero`, `BellIllustration` | `Illustrations.kt` | Canvas art |

### Missing from the required component list

`PvTopBar` ✅ · `PvPrimaryButton` ✅ · `PvSecondaryButton` ✅ ·
`PvCard` ❌ · `PvListItem` ❌ · `PvTextField` ❌ · `PvSwitchRow` ❌
(there is an un-namespaced `SwitchRow`, `Common.kt:122`) · `StatusPill` ✅ ·
`MoneyText` ✅ · `PvEmptyState` ✅ · `PvErrorState` ❌ ·
`PvLoading` skeleton ❌ (only a spinner row, `Common.kt:233`) ·
`PvBottomSheet` ❌ · `PvDialog` ❌ · `PvSnackbar` ❌ ·
`PvPermissionCard` ❌ · `PvSectionHeader` ❌ (`SectionHeader`, un-namespaced) ·
`PvStatTile` ❌ · `PvTextButton` ❌ · `PvSwitchRow` ❌.

---

## 4. Screen-by-screen

### 4.1 Onboarding — `ui/onboarding/OnboardingScreen.kt` (both roles)
Steps: `WelcomeStep` → `RoleStep` → `PermissionsStep` →
`NotificationAccessStep` → `BatteryStep` → `ReadyStep`. Shell is `StepScaffold`
(`OnboardingScreen.kt:113`), which nests `PvScaffold`.

- **Good**: one primary action bottom-anchored per step; `PermissionStep`
  auto-advance; no dead ends.
- **Problems**: `PermissionStep.kt` auto-advances on false→true only, so a user
  who is already granted sees "Continue" — two different UIs for the same
  state. Wizard is a linear push with no way to revisit a step. Copy is
  hard-coded English.

### 4.2 Home — `ParentHomeScreen.kt` (**one screen for both roles**)
Structure: transparent app bar over a full-bleed `StorefrontHero` → greeting →
conditional `ActionCard` → today's total → 5 recent payments → quick links.

- **Data**: `history` filtered client-side with `Calendar.getInstance()`
  (`:88` called, `:356` defined — recomputed per composition) for today's
  total; `history.take(5)`.
- **P1 — dead end**: `text = "Show all payments", onClick = onOpenDiagnostics`
  (**`ParentHomeScreen.kt:242-243`**). The owner taps "Show all payments" and
  lands on the *technical diagnostics log*. There is **no payments history
  screen at all**.
- **P1 — role split is stringly-typed**: `when (role)` branches appear at
  `:80`, `:165-186` and the quick-link row at `:245`. Employee Home is not
  "Listening for payments"; it reuses the owner layout with different words.
- **P1 — only ONE health issue can ever be shown**: `needsAction` (`:84-87`)
  collapses everything to a boolean. For an owner it is only
  `listenerOk && notifOk` — battery, TTS, DND, volume, network, FCM and Play
  Services are never surfaced on Home, and multiple failures show as a single
  card. This is the "permissions shown as off with no way to fix" bug.
- **P2 — no device status chip** (Online/Offline + voice working). Not present
  anywhere in the app.
- **P2 — no loading/error state**: `history` empty renders "No payments yet"
  whether the query is still running or failed.
- **P3**: "Quick links" exposes four developer words — *Diagnostics*,
  *Employees*, *Settings*, *Reliability*.
- **P3**: greeting copy alternates "Hello"/"Welcome back" on `history.isEmpty()`
  (`:139`) — data-dependent prose.

### 4.3 Employees / Team — `OwnerRemoteScreen.kt` (owner)
List + `ExtendedFloatingActionButton` "Add employee" + two `ModalBottomSheet`s.

- **Good**: `PvSecureWindow()` on (`:73`) — the pairing code is a live
  credential; empty state has a real CTA; `items(employees.size, key = …uid)`.
- **P1 — pairing UX**: code sheet (`:198-241`) shows a *static* string
  `"Expires in 10 minutes · works once"` with **no countdown**, **no copy**,
  **no share**, and regenerate is a small pill buried next to "Done".
  Step 3 requires: large code, 10-min countdown, copy/share, regenerate.
- **P2 — no loading state**: `if (employees.isEmpty())` (`:100`) renders
  "No employees yet" during the first fetch — a false empty state.
- **P2 — no error-with-retry** on the employee list.
- **P3 — jargon**: `emp.status.lowercase().replaceFirstChar { … }` (`:255`,
  `:326`) surfaces raw enum values (`"revoked"`, `"inactive"`) to the user.
- **P3**: two raw `Button`s (`:153`, `:227`) instead of `PvPrimaryButton`.
- **P3**: last-active time only appears inside the detail sheet.

### 4.4 This device / Employee — `EmployeeScreen.kt` (employee)
Hero status card → Connection/Battery/Notifications status cards → test →
last payment → Leave. Join and Leave are `ModalBottomSheet`s.

- **Good**: `PvSecureWindow()` on (`:74`); granular `claim_pairing()` errors
  already mapped in `MainViewModel.kt:176-220` — `expired`, `already-used`,
  `invalid`, `unauthenticated`, `SessionNotReady` ("Still connecting
  securely…"), `NetworkError`. **Session-not-ready is already its own state,
  never "expired".** The remaining work is presentation, not logic.
- **P1 — no Home-equivalent**: the employee's primary screen is *not* Home.
  "Listening for payments" does not exist as a state; the big status is buried
  in a card on a screen called "This device".
- **P2 — pairing input is one plain field** (`:237`): no OTP-style
  auto-advance, no auto-uppercase display grouping, no paste helper, no
  format mask. `code = it.uppercase().take(10)` is the only formatting.
- **P2 — no "signing in…" separate state**: `Joining` renders one
  `PvLoadingRow("Connecting…")` (`:246`); Step 3 wants "Signing in…" as its
  own distinct step before the claim attempt.
- **P2 — no volume slider, no Test voice on Home** (they live behind a card
  further down, only when paired).
- **P2 — no recent payments list** — only `history.firstOrNull()` (`:181`).
- **P3**: `StatusCard` (`:301`) is a private duplicate of `PvCard`.
- **P3**: `OutlinedButton` + `ButtonDefaults.outlinedButtonColors` (`:201-205`)
  with `Text("Leave", color = …error)` (`:292`) — no `PvTextButton`, no
  `PvDialog`; destructive confirm is a bottom sheet.

### 4.5 Settings — `SettingsScreen.kt` (both)
Sections: Announcements (expandable voice block) · Payment detection ·
Your business · App settings · Support · About.

- **Good**: 72dp rows, inline controls, one primary `PvPrimaryButton` at the
  bottom for voice preview.
- **P1 — duplicated component**: `private fun SectionHeader` at
  **`SettingsScreen.kt:331`** shadows the shared one in `PvScreenBits.kt:19`
  and renders differently (uppercase muted vs. title).
- **P1 — permissions live here but with no health summary**: the Settings →
  Support entry is labelled *"Fix a problem" / "Permissions, battery and
  connection repair"* (`:297-299`) yet pushes `ReliabilityScreen`, a screen
  titled **"Reliability"**. Label ≠ destination ≠ title.
- **P2 — no account section**: no sign-out, no delete account (Step 3
  requires both). No `Auth` UI at all.
- **P2 — no permission/health checklist** as a first-class section; health is
  one row deep in Support.
- **P3 — raw components**: 12 raw Material instantiations, including a raw
  `Switch` at **`SettingsScreen.kt:427`** (duplicating `SwitchRow`), raw
  `Button` at `:167`, `FilterChip`, `Slider`, `SliderDefaults`.
- **P3**: `"%.1fx".format(...)`, `"%d%%".format(...)`, `"%d h".format(...)`
  (`:147,157,260`) — format-string copy not localizable.
- **P3**: header re-implements the back chevron + displaySmall pattern for the
  third time in the app (`:75-105`).

### 4.6 Diagnostics — `DiagnosticsScreen.kt` (developer screen)
System / Listener / FCM / Remote / Unknown-package capture.
**P1 jargon**: `"Push transport ready"`, `"Token: … registered (hidden)"`,
`"Listener not connected"`, `"dup-ignored: …"` (`:150`),
`"Unknown-package capture"`, `"Capture unknown packages (local only)"`.
Reached from Home quick links **and** from "Show all payments" — a
non-technical owner can land here from a payment-history button.

### 4.7 Reliability — `ReliabilityScreen.kt` (the health screen)
5 checks built ad-hoc inside the composable (`:49-96`): Notification access ·
Google Pay · App notifications · Battery optimization · Text-to-speech.
Summary pill "N of M checks passing"; each row has a "Fix" button.

- **Good**: every check has a fix action; `StatusPill` summary.
- **P1 — this is a local `data class Check` inside the composable**, not an
  injectable `AppHealthChecker`: untestable, not shared with Home, and it is
  the *only* place these five checks exist.
- **P1 — missing items** vs. Step 4: DND blocking, media/notification volume
  = 0, TTS *language voice data* (only engine presence), internet
  connectivity, FCM token registered, Supabase session valid, Play Services,
  channel importance lowered.
- **P1 — no severity ordering and no aggregate banner on Home.**
- **P2 — ad-hoc colour**: healthy/unhealthy is drawn with
  `primaryContainer.copy(alpha = 0.5f / 0.25f)` (`:126-130`) because
  `DESIGN.md` declares no semantic success/warning palette.
- **P2 — re-evaluation**: relies on `MainActivity.onResume → refreshStatus()`,
  which updates `status` but **not** `ttsStatus` or `listenerRuntime`. Coming
  back from Settings can leave two of the five checks stale.
- **P2 — string concat in copy**: `ReliabilityScreen.kt:171-172` joins two
  literals with `+` — the exact pattern `strings.xml` is meant to prevent.
- **P2 — fix intents are direct, not cascaded**: `viewModel.fixBattery`
  calls `monitor.launchBatteryFix()` with no `resolveActivity` guard chain.

---

## 5. Why `DESIGN.md` is not being followed

The naive gate **passes today** (it prints nothing):

```bash
grep -rnE "\.dp\b|Color\(0x|\.sp\b" ui/ --include="*.kt" \
  | grep -vE "ui/theme/(Color|Spacing|Shape|Type|Theme)\.kt" | …
```

So the problem is **not** raw literals. It is that the enforcement surface is
far narrower than the rules it claims to enforce. Seven root causes:

**R1 — The token scale does not contain the values screens actually need.**
`Spacing` is `2, 4, 8, 12, 16, 24, 32, 64` (`ui/theme/Spacing.kt:8-17`).
There is no 20, 28, 40, 48, 56 or 72 — yet those are exactly the structural
sizes the design calls for (20dp gutters, 40dp icon circles, 48dp bars, 56dp
touch rows, 72dp settings rows). So **50 arithmetic expressions** are written
instead of tokens, each with a hand-written `// … dp structural` comment:

- `SettingsScreen.kt:59` — `Spacing.xxl + Spacing.xxl + Spacing.sm // 72dp`
- `SettingsScreen.kt:84-85, 337-338, 360, 366` — `Spacing.xl - Spacing.xs // 20dp`
- `ParentHomeScreen.kt:99, 106-109, 133-134, 196-197, 217, 253-254, 308` —
  `Spacing.xl - Spacing.xs`, `Spacing.huge * 3 + Spacing.sm // ~200dp`,
  `Spacing.xxl + Spacing.lg // 48dp`
- `Common.kt:63, 76, 89, 132, 157, 187, 221, 241` — eight more
- `Illustrations.kt:203-204` — `Spacing.xxl + Spacing.lg + Spacing.sm // 56dp`
- `OwnerRemoteScreen.kt:93, 113, 310, 317` · `PvPrimaryButton.kt:33`
- `SettingsScreen.kt:59`, `PvPrimaryButton.kt:33`, `Common.kt:132` all
  re-derive **the same 56dp touch height** three different ways.

The gate's only escape hatch is the *exact* substring `// structural`, which
appears on just 3 lines — while 29 lines carry `// … dp` comments and 50 lines
carry arithmetic. The rule "no `.dp` outside `Spacing.kt`" is therefore
satisfied *by construction of the scale's gaps*, not by discipline. Any new
screen that needs 48dp must invent arithmetic.

**R2 — The component layer is missing the primitives screens reach for, so
screens fall back to raw Material.** 40 raw Material component instantiations
across screens:

| File | Raw `Button`/`OutlinedButton`/`Surface`/`TextField`/`Switch`/`Slider`/`ModalBottomSheet`/`FAB` |
|---|---|
| `ui/settings/SettingsScreen.kt` | **12** (incl. raw `Switch` `:427`) |
| `ui/employee/EmployeeScreen.kt` | **12** (incl. `OutlinedTextField` `:237`) |
| `ui/owner/OwnerRemoteScreen.kt` | **6** (incl. `ModalBottomSheet` `:198`, `:244`) |
| `ui/diagnostics/DiagnosticsScreen.kt` | 4 |
| `ui/onboarding/RoleStep.kt` | 2 |
| `ui/reliability/ReliabilityScreen.kt` · `ui/parenthome/ParentHomeScreen.kt` · `ui/onboarding/OnboardingScreen.kt` · `NotificationAccessStep.kt` | 1 each |

Consequences: default M3 button padding/press states leak through wherever a
raw `Button(` is used; `PvEmptyState` itself contains a raw `Button`
(`Common.kt:199`); `Surface(shape = …, tonalElevation = …)` is copy-pasted as
an ad-hoc `PvCard` in three places across two files
(`OwnerRemoteScreen.kt:292`, `EmployeeScreen.kt:93`, `EmployeeScreen.kt:307`).

**R3 — Nothing is checked in CI.** The grep gate exists **only as a bash block
at the bottom of `docs/DESIGN_SYSTEM.md`**. `.github/workflows/ci.yml` runs
`testDebugUnitTest`, `lintRelease`, `assembleRelease` and gitleaks — **no
design gate, no detekt**. `detekt`, `paparazzi` and `roborazzi` appear in
**zero** Gradle files. A future agent can delete `Spacing.kt` and CI stays
green.

**R4 — Zero previews.** `grep -rn "@Preview"` → **0 hits**. No light, no dark,
no fontScale 1.3, no 320dp width. There is no mechanism that would ever show
a designer a screen in dark mode before ship.

**R5 — Copy is not a token.** `stringResource(` → **0 hits** in `ui/`;`Text("` literals → **31**. Sentences are concatenated in code
(`ReliabilityScreen.kt:171-172`, `DiagnosticsScreen.kt:150`). No `values-hi`
directory exists. `DESIGN.md` documents no copy rules, so nothing covers it.

**R6 — Three competing screen-chrome patterns, no single primitive.**
`ScreenHeader` (`Illustrations.kt:133`, Owner + Employee) vs. a hand-rolled
"floating back chevron + `displaySmall`" header (`SettingsScreen.kt:74-105`,
`ParentHomeScreen.kt:74-99`) vs. `PvTopBar` (Diagnostics, Reliability). Plus
two different `SectionHeader` implementations (`PvScreenBits.kt:19` vs.
`SettingsScreen.kt:331`). Each screen re-implements its own chrome, which is
why spacing and rhythm drift between them.

**R7 — `DESIGN.md` and `AGENTS.md` disagree, and code followed neither.**
- `DESIGN.md` §Responsive: *"Pill buttons hit ≥ 40×40px… buttons size up to
  44×44px"*; *"Form fields stay at 40px minimum height."*
  `AGENTS.md` §Layout: *"Tap targets MUST be >= 48dp."* → The code chose 56dp
  (`PvPrimaryButton.kt:33`), which satisfies AGENTS.md but **contradicts
  DESIGN.md's own Do/Don't** ("Don't shrink button padding below 8px 16px" —
  the implementation uses 16dp/12dp content padding, not 8px/16px).
- `DESIGN.md` declares a **gradient mesh hero as non-negotiable** on heroes.
  The app uses a flat `StorefrontHero` Canvas instead. Nobody wrote that
  deviation down.
- `DESIGN.md` has no semantic success/warning palette ("the brand does not use
  a separate semantic color palette"), yet `StatusPill` needs four tones —
  so `colorScheme.error`/`primaryContainer` are pressed into service with
  ad-hoc `copy(alpha = …)` (`ReliabilityScreen.kt:126-129`).
- `DESIGN.md` has **no dark theme and no motion section**, while the code has
  a full dark scheme (`Theme.kt:41-80`) and AGENTS.md demands ≤200ms
  reduced-motion-safe animation. The doc is silent, so those decisions are
  unverifiable.

---

## 6. Proposed final screen map (for approval)

Max 3–4 bottom destinations per role. Shared chrome, one `PvBottomNav`.

| # | Owner | Employee |
|---|---|---|
| 1 | **Home** — today's total (big), count, last 5, device-status chip, health banner | **Home** — "Listening for payments", last announcement, volume slider, Test voice, health banner |
| 2 | **Payments** — searchable, today/week/month, paged | **Recent** — read-only, limited |
| 3 | **Team** — employees, add via pairing (countdown + copy/share), remove/pause, last active | **Pair** — OTP-style entry, or hidden once paired |
| 4 | **Settings** — voice + Test, announcement format, notifications, permissions & health checklist, account, sign out, delete account | **Settings** — voice + Test, permissions & health, sign out |

Routes become: `owner_home · owner_payments · owner_team · owner_settings` and
`employee_home · employee_recent · employee_pair · employee_settings`.
`Diagnostics` and `Reliability` **leave the bottom nav** and move under
Settings → Support (Reliability is renamed **"Health"** and becomes the
full-screen version of the Home banner). `Onboarding` stays as-is ahead of
the nav. Every dead end listed in §4 is resolved by this map.

---

## 7. Health/permission layer — current gap summary

| Required by Step 4 | Present today? |
|---|---|
| Injectable `AppHealthChecker` returning `HealthItem{id,title,why,status,fix}` | ❌ local `data class Check` in `ReliabilityScreen.kt:49` |
| Re-evaluate on every `ON_RESUME` | ⚠️ `status` only; `ttsStatus`/`listenerRuntime` stale |
| Notifications permission + channel importance | ✅ / ❌ importance |
| DND blocking | ❌ |
| Battery optimization / background restriction | ✅ (`batteryExempt`) |
| TTS engine + **language voice data** | engine only ❌ voice data |
| Volume ≠ 0 | ❌ |
| Internet, FCM token, Supabase session | ❌ |
| Play Services availability | ❌ |
| `SettingsLauncher` with cascading fallbacks + `resolveActivity` | ❌ direct intents (`MainViewModel.kt:403-425`) |
| OEM autostart intents in one file | ❌ (`romHint`/`isXiaomiFamily` computed but unused) |
| `PvPermissionCard` UI | ❌ |
| Aggregate banner on Home ("N things need fixing") | ❌ single boolean `needsAction` |
| Unit tests with fakes | ❌ |

---

*Nothing in this file changes behaviour. Awaiting approval before Step 2.*
