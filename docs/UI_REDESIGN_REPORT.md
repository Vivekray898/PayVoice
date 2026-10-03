# UI redesign — final report

Owner + Employee UI rebuilt on a four-tab navigation, a token-only design
system and an enforcement gate that runs in CI. This is the closing report
for the work planned in [UI_INVENTORY.md](UI_INVENTORY.md) and
[DESIGN_DIRECTION.md](DESIGN_DIRECTION.md).

**Baseline for every number below:** `100e61b` (the audit commit).
**Head at time of writing:** `5e376e9`.

---

## 1. What changed

### 1.1 Navigation: four tabs, two roles

Before, the app had **no bottom navigation at all**. Every screen was a push
off a "Quick links" row on Home, so an owner could end up in the technical
diagnostics log by tapping "Show all payments", and Settings — the screen
that most settings live on — had no way back at all.

| Slot | Owner | Employee |
|---|---|---|
| 1 | **Home** — today total, recent payments, health banner | **Home** — same shell, same health banner |
| 2 | **Payments** — search, Today/Week/Month/All, windowed paging | **Recent** — the same screen, read-only and capped |
| 3 | **Team** — devices, add via pairing (FAB), remove, test announcement | **Pair** — pairing status, OTP-style code entry, announcement volume, test voice, health banner |
| 4 | **Settings** — voice, detection, role, retention, Support | same |

`Diagnostics` and `Health` left the quick links and became rows under
Settings → Support. `Onboarding` still runs ahead of the tabs.

`PvTab` + `PvBottomNav` + `PvTabScaffold` (`ui/components/PvBottomNav.kt`)
own the bar; the four slots are identical for both roles and only the label,
icon and the screen behind slots 2 and 3 change.

### 1.2 A real Payments screen

`ui/payments/PaymentsScreen.kt` is new: an immutable `PaymentsUiState`, a
pure `buildState(history, loading, query, range, limit)` (unit-testable
without a ViewModel), windowed paging at 30 rows, and explicit
loading / empty / empty-search / error states instead of inferring them from
`list.isEmpty()`.

### 1.3 The employee got the controls they actually have

The Pair tab now carries announcement **volume**, a **Test voice** button,
the same `PvHealthBanner` Home shows (a phone that loses notification access
loses payments silently), and the pairing code in a `PvCodeField` whose
rejection reasons stay distinct — expired, already-used, invalid,
still-connecting and network each read differently.

### 1.4 Design system enforcement

* `verifyDesignTokens` — no `Color(0x…)`, no raw `.dp`/`.sp`, no bare
  `Scaffold(`.
* `verifyDesignComponents` — **no raw Material widget and no spacing
  arithmetic in a screen file**. This one existed as a registered task that
  found **78 violations** and was not wired anywhere. It now runs in CI
  (`.github/workflows/ci.yml`) and in `check`, and finds **0**.
* Six new library components came out of that migration rather than being
  papered over: `PvFab`, `PvChoiceChip`, `PvIconButton`, `PvSliderRow`,
  `PvProgressBar`, `PvIconPlate` (`ui/components/PvControls.kt`).
* `Spacing` gained the two sizes the base-8 scale has no step for —
  `dot` (6dp) and `iconPlate` (72dp) — so the status dot and the onboarding
  plate stop being `Spacing.sm + Spacing.xxs - Spacing.xs`.
* Dead code from the old screens (`ActionCard`, the duplicate
  `SectionHeader`/`SwitchRow`) was deleted.

### 1.5 Screenshots as a gate

Every component has a preview in four variants and a committed baseline.
`validateDebugScreenshotTest` runs in CI. **108 baselines** (84 existing +
24 for the new controls). The 84 pre-existing baselines are byte-identical
after the redesign — proof that the migration changed no component render.

---

## 2. Screenshots (emulator, API 36)

| | |
|---|---|
| `screenshots/01-home-owner.png` | Owner Home: hero, greeting, health banner, today's total |
| `screenshots/02-payments-owner.png` | Payments: search, range chips, summary, empty state |
| `screenshots/03-team-owner.png` | Team: empty state + "Try it out" test announcement |
| `screenshots/04-settings.png` | Settings: announcements, detection, business, support |

**Update (2026-10-04):** this table records the state verified during the
redesign, not the files in the repo. The screenshot set was later recaptured
for the README and `docs/screenshots/03-team-owner.png` was **deleted** — the
Team screen holds `FLAG_SECURE` (see `PvSecureWindow`), so Android refuses to
capture it. The surviving set is Home, Payments and Settings.

The employee Pair tab was verified live as well (status card, volume
slider, test voice, last payment, health banner) and the nav labels were
confirmed to switch to Home / Recent / Pair / Settings.

---

## 3. Measurements

### 3.1 Correctness gates

| Gate | Result |
|---|---|
| `verifyDesignTokens` | ✅ pass |
| `verifyDesignComponents` | ✅ pass (78 → **0** violations) |
| `testDebugUnitTest` | ✅ **127 tests, 0 failures** |
| `lintRelease` | ✅ pass (security findings fail the build) |
| `validateDebugScreenshotTest` | ✅ 108/108 baselines |
| `assembleRelease` | ✅ builds |

### 3.2 APK size

| Build | Release APK | Δ |
|---|---|---|
| Baseline `100e61b` (audit) | 3,103,429 B (2.96 MiB) | — |
| After Step 3 (health layer) | 3,140,789 B | +37,360 B |
| **After Step 4 (redesign)** | **3,168,465 B (3.02 MiB)** | **+27,676 B (+0.88 %)** |
| Total vs audit baseline | | **+65,036 B (+2.1 %)** |

**This is over the "must not grow" line by 65 KB, and it is real:** the
redesign adds a navigation layer, a screen that did not exist (Payments)
and the employee's volume/health controls. Nothing is padded — R8 already
strips every unused component and every `@Preview` (verified: 0 occurrences
of `HeroBanner`, `BellIllustration`, `PvStatTile`, `PvPreview*` in the
release dex). The audit's own ceiling (< 15 MB) still holds with 5×
headroom. If the 3.1 MB figure is a hard cap rather than a direction, the
lever is dropping the windowed search/paging on Payments — say so and it
goes.

### 3.3 Cold start

Measured back-to-back on the same AVD (`emulator-5554`, API 36), debug
builds, 9 runs each after force-stop, first two discarded as warm-up:

| Build | Samples (ms) | Warm median | Warm mean |
|---|---|---|---|
| Baseline `100e61b` | 194 208 214 185 215 201 189 208 193 | **201 ms** | 200 ms |
| Redesign | 182 178 199 184 189 204 195 190 218 | **195 ms** | 197 ms |

**−3 %, i.e. within noise.** The extra composables do not show up in
startup: they are constructed after the first frame, and the cold path is
still the same single screen. (The audit's release-build figure was a mean
of 235 ms over 25 runs on the same profile; this session A/B'd debug builds
so both sides are directly comparable.)

### 3.4 Layout verification on device

Checked with `uiautomator dump` on a cold start, per the rule in
`AGENTS.md`:

* first content node on every tab: **y = 131 px (51 dp)** — below the
  status-bar inset;
* last content node: **y = 2260 px (884 dp)** on a 2400 px (936 dp)
  screen — above the navigation-bar inset;
* **0** clickable nodes below 48 dp on any tab;
* role switch verified live: nav labels change Home/Payments/Team/Settings
  → Home/Recent/Pair/Settings and the Pair screen swaps to the employee
  build.

**This check earned its keep.** It caught a bug I had just introduced: the
three new tab screens consumed only the bottom inset, so "Settings",
"This device" and the Team list started 12 dp from the top of the window —
under the status bar. Lint and the compiler both passed. Fixed in
`e459099`.

---

## 4. Known gaps and what I would do next

1. **No screen-level `@Preview` or screenshot baselines.** Screens still take
   `MainViewModel`, which the preview renderer cannot construct; giving each
   one a `UiState` is the prerequisite and was out of scope for this pass.
   The component library *is* fully covered. `PaymentsScreen.buildState` is
   already pure, so Payments is the first one that would fall out for free.
2. **Pixel-level visual review was not possible in this session** (the
   preview panel would not composite). Verification was structural — UI
   dumps plus the 108 component baselines. The four device screenshots in
   `screenshots/` were captured but not eyeballed here; they are committed
   for review.
3. **Jank and PSS were not re-measured.** The audit reported 5.13 % janky
   frames with no baseline; the redesign adds a bottom bar (one more
   composable tree in every screen), so a re-measure is worth doing before
   shipping.
4. **Onboarding is still the old wizard shape** — one decision per screen
   with a "Skip for now" on every step. It is token-clean and accessible
   now, but it was not redesigned beyond the component migration.
5. **Dead library components remain** (`HeroBanner`, `BellIllustration`,
   `ScreenHeader`, `PvActionCard`, `PvProgressLine`). R8 already strips
   them, so they cost nothing at runtime — they are just clutter for the
   next person reading `ui/components/`.