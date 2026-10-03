# PayVoice — UI Review (DESIGN.md rebuild v3 + GPay-Business Home, 2026-10-02)

Scope: full UI rebuild from `DESIGN.md` — not a re-style. Theme tokens,
primitives, and every screen are direct translations of the reference
system; the v2-era component layer was replaced. One commit per phase:
`ccef6a9` (theme) → `6e5d55b` (primitives) → `efeea2b`/`c358132`/
`328295d`/`cd51696`/`29a6dfe`/`d4918a4`/`c25a153` (screens) →
`4505923` (compat removal) → docs + screenshots (`e019bc2`).

## GPay-Business pass (2026-10-02, commits `311d3d6` → `4584f9e`)

Home was rebuilt from scratch to the reference structure (no admin-panel
action rows):

1. Custom inline app bar — business/device name + chevron affordance +
   avatar initial, over an EMPTY scaffold topBar so the hero bleeds
   under the status bar.
2. Full-bleed ~200dp Canvas storefront illustration (flat vector shapes:
   sky wash, clouds, sun, distant blocks, shop with striped awning,
   window/door, counter figure — all scheme-role colors).
3. "Hello, {name}" at displaySmall + pending/all-set subline.
4. Action card (only when needed): rounded.xl surfaceVariant card,
   bell illustration with lemon badge, inline TextButton CTA — verified
   in both states (granted → no card; revoked → "Turn on now").
5. Numeric block: today's total via MoneyText at displaySmall (tabular)
   + "Received today" + payments empty-state illustration or recent list
   + pill "Show all payments" (disabled when empty).
6. Quick links: four 56dp rounded-square IconTiles (QR / Employees /
   Settings / Support) with ≥72dp tap height.
7. Footer = nav-bar inset + 24dp.

Settings adopts the reference treatment: displaySmall headline with a
floating back chevron (no TopAppBar, no 3-dot menu), uppercase muted
section headers, 72dp rows with 40dp icon tiles. Owner/Employee gain
ScreenHeader + a clipped HeroBanner. Diagnostics/Reliability get the
light treatment (20dp sides, uppercase headers, icon tiles) and stay
dense. Diagnostics/Reliability moved under Settings → Support (Home's
quick links replace the Manage stack, matching the reference).

## Design language

- Colors: `colors.primary` indigo on canvas white (light track), navy
  dashboard shell (dark track), hairline borders, ruby errors, cream
  tertiary track, lemon warning foreground. Zero non-token colors — the
  legacy teal/sand/Google palettes were deleted with the theme rewrite.
- Type: the Sohne-shaped scale (Light display tiers with negative
  tracking, Medium labels) on a SansSerif fallback; money renders through
  `MoneyText` with tabular figures.
- Components: pill button pair, `PvActionCard` icon-circle rows,
  `StatusPill` four-tone chips, `PvTopBar`/`LargeTopAppBar` chrome, and
  `PvScaffold` as the only `Scaffold` in the module.
- Wizard: `StepScaffold` pages (4dp progress bar, icon circle,
  bottom-anchored pill actions) with `PermissionStep` auto-advance.

## Screenshots

`docs/screens/v3/` — captured on emulator-5554 by real UI navigation
(uiautomator-verified taps), light and dark:

| Screen | Light | Dark |
|---|---|---|
| Wizard 1 — Welcome | wizard-1-welcome.png | wizard-1-welcome-dark.png |
| Wizard 2 — Role | wizard-2-role.png | wizard-2-role-dark.png |
| Wizard 3 — Access (pending) | wizard-3-access-pending.png | wizard-3-access-dark.png |
| Wizard 4 — Notifications | wizard-4-permissions.png | wizard-4-permissions-dark.png |
| Wizard 5 — Battery | wizard-5-battery.png | wizard-5-battery-dark.png |
| Wizard 6 — Ready | wizard-6-ready.png | wizard-6-ready-dark.png |
| Home (owner) | home-owner.png | home-owner-dark.png · wizard-7-home-dark.png |
| Home (employee) | home-employee.png | — (same layout, role pill differs) |
| Owner (Employees) | owner-empty.png | owner-dark.png |
| Employee (This device) | employee.png | employee-dark.png |
| Settings | settings.png · settings-bottom.png | settings-dark.png |
| Diagnostics | diagnostics.png | diagnostics-dark.png |
| Reliability | reliability.png | reliability-dark.png |
| Pipeline regression | pipeline-simulate.png | — |

## Verification gates

| Gate | Result |
|---|---|
| Token grep (`\.dp\b\|Color\(0x\|\.sp\b`, excluding the five theme files, `unit.dp` imports, `// structural`) | **0 violations** across `ui/` |
| `Scaffold(` outside PvScaffold.kt | **0** (only the one inside PvScaffold.kt) |
| `./gradlew clean test assembleDebug assembleRelease` | **GREEN**, 96/96 tests |
| Permission grants auto-advance (no Skip needed) | PASS — real dialog round-trip verified on-device, twice (access via resume, notifications via ActivityResult + resume) |
| Role selected at step 2 persists; visible on Home; no selector | PASS — Employee Home shows "Employee / Waiting" pill; owner Home shows "Owner / Ready" |
| Wizard is one-step-per-screen (no checklist) | PASS — `when(step)` driver over six composables |
| Pipeline regression | NONE — debug GPay simulation ran end-to-end (capture → parse → TTS → history row "₹500 from Rahul Sharma"); `git diff` over service/, core/, supabase/, manifest, gradle files since `ccef6a9~1` is empty |

## Deliberate deviations from the request

1. **Compat shims during Phase 2, removed in Phase 3g.** The prompt's
   Phase 2 "replace anything in ui/components/" would not have compiled —
   seven screens still referenced the old component APIs. Primitives were
   added under their spec names while temporary compat overloads kept the
   tree green; every shim was deleted once the last screen rebuilt
   (`4505923`).
2. **`PermissionStep` is flow-driven, not a LifecycleEventObserver.** The
   prompt's sketch would double-fire `step += 1` next to the existing
   ON_RESUME-fed status flows and skip steps. The shipped observer keys
   the advance on the false→true grant transition only; composed-already-
   granted steps render "Continue" and tap through. Same ON_RESUME
   re-check the prompt wanted (MainActivity refreshes status on resume).
3. **Structural sizes are token-built sums with `// structural` markers**
   (56dp touch rows = 32+16+8; 4dp progress track; 1dp borders; icon
   circle diameters). The prompt's grep as written also bans these — and
   Material 3 itself (56dp buttons, 40dp+ targets) cannot be expressed
   without some concrete dimension. The enforcement gate + DESIGN_SYSTEM.md
   document the convention.
4. **Recent payments retained on Home.** It's the product's core surface
   (announcement history) and the prompt's v3 Home dropped it; it renders
   through PvPaymentRow/MoneyText on the new primitives.

## Dead-UI and misleading-label pass (5 fixes, one commit each)

| # | Problem | Fix | Commit | On-device proof |
|---|---------|-----|--------|-----------------|
| 1 | Header row showed the device name (`sdk_gphone64_arm64`), a dead chevron, and an avatar circle with no account behind it | Replaced with a transparent 48dp row: `PayVoice` wordmark (`titleLarge`, `onBackground`) + `Icons.Filled.Settings` gear (48dp `IconButton`) | `c1ee503` | Dump shows only "PayVoice" + "Settings" |
| 2 | Quick links: "QR code" (no QR feature exists) and "Support" (opened Reliability) | Diagnostics / Employees / Settings / Reliability | `8cc3d20` | Dump shows exactly those four labels |
| 3 | Hero used ambiguous floating shapes | Option A: phone silhouette + 3 concentric sound-wave arcs on a `primaryContainer` wash | `5774911` | [fix5-home-light.png](screens/v3/fix5-home-light.png) |
| 4 | "Connect to your owner" card showed a bell (reads as *notifications*) | `BellIllustration` takes an `ImageVector`; pairing card uses `Icons.Filled.Link`, notification card uses `Icons.Filled.Notifications` + badge | `0631382` | [fix4-card-bell.png](screens/v3/fix4-card-bell.png) |
| 5 | Empty state stacked a 72dp circle illustration + a sentence repeating the button | One centered line "No payments yet" (`bodyMedium`, `onSurfaceVariant`) + the existing disabled `PvSecondaryButton("Show all payments")` | `d53a73b` | [fix5-home-light.png](screens/v3/fix5-home-light.png) |
| — | Greeting read "Hello, sdk_gphone64_arm64" | `displayName` derivation removed entirely; greeting is "Hello", or "Welcome back" once payments exist | `d53a73b` | Dump: `text="Hello"`, zero `sdk_gphone` matches |

### Spec gap found during verification

Fix 3 specified a ₹ inside the phone. The committed hero drew it from three
`drawLine` strokes, which rendered as two horizontal bars and a diagonal slash
— not a recognizable rupee. Caught by screenshot review, not by the build.
`StorefrontHero` now measures and draws the actual `₹` glyph via
`rememberTextMeasurer` (`bd00789`), so it is correct for any installed font.

`PaymentsEmptyIllustration` was deleted from `Illustrations.kt` (Fix 5) rather
than left as dead code.

### Verification

| Check | Result |
|-------|--------|
| Token gate (`.dp` / `Color(0x` / `.sp` outside token files and `// structural` markers) | **0** hits |
| `Scaffold(` outside `PvScaffold.kt` | **0** (onboarding uses `StepScaffold`, which wraps `PvScaffold`) |
| `./gradlew test assembleDebug` | **GREEN**, 96/96 tests, 0 failures, 0 errors |
| Light + dark Home screenshots | [light](screens/v3/fix5-home-light.png), [dark](screens/v3/fix5-home-dark.png) — both reviewed |
| Device-name leak (`grep -c sdk_gphone` on the UI dump) | **0** |

Only files under `ui/` changed; `service/`, `core/`, `supabase/`,
`AndroidManifest.xml`, and every `.gradle.kts` are untouched.
