# PayVoice Design System (Compose)

Source of truth: `DESIGN.md` at the project root. This document is the
token→Compose mapping **as implemented** (2026-10 rebuild). Every color,
dimension, radius, and text style in `ui/` flows through these symbols, and
the two Gradle gates at the bottom of this document enforce it on every CI
run.

## Colors

DESIGN.md role → Compose `MaterialTheme.colorScheme.*` (see `ui/theme/Theme.kt`).
Raw DESIGN.md tokens live in `ui/theme/Color.kt` (Indigo, Ruby, Canvas,
Hairline, Ink, …) and are referenced only by the scheme definitions.

| DESIGN.md | Compose |
|---|---|
| `colors.primary` | `colorScheme.primary` |
| `colors.on-primary` | `colorScheme.onPrimary` |
| `colors.primary-deep` | `colorScheme.onPrimaryContainer` (light) |
| `colors.primary-soft` | `colorScheme.primary` (dark) |
| `colors.primary-bg-subdued-hover` | `colorScheme.primaryContainer` |
| `colors.brand-dark-900` | `colorScheme.tertiary` (light) / `background`+`surface` (dark) |
| `colors.ink` | `colorScheme.onBackground` / `onSurface` |
| `colors.ink-secondary` | `colorScheme.secondary` / dark `surfaceVariant` |
| `colors.ink-mute` | `colorScheme.onSurfaceVariant` |
| `colors.canvas` | `colorScheme.surface` / `background` (light) |
| `colors.canvas-soft` | `colorScheme.surfaceVariant` / `surfaceContainer` |
| `colors.canvas-cream` | `colorScheme.tertiaryContainer` |
| `colors.hairline` | `colorScheme.outline` |
| `colors.hairline-input` | `colorScheme.outlineVariant` |
| `colors.ruby` | `colorScheme.error` |
| `colors.lemon` | warning foreground (`StatusTone.Warning` content) |

Dark scheme: the reference's dashboard-shell navy polarity — `brand-dark-900`
surfaces, canvas text, soft indigo action; derived container roles filled
from the ink tiers.

## Typography

Sohne is proprietary; the scale preserves its weights (Light display tiers,
Normal body, Medium labels) and negative tracking on a SansSerif fallback.
`ui/theme/Type.kt`.

| DESIGN.md | Compose |
|---|---|
| `display-xl` | `typography.displayMedium` (48sp Light, −0.96) |
| `display-lg` | `typography.displaySmall` (32sp Light, −0.64) |
| `display-md` | `typography.headlineLarge` (26sp Light, −0.26) |
| `heading-lg` | `typography.headlineMedium` (22sp Light, −0.22) |
| `heading-md` | `typography.headlineSmall` (20sp Light, −0.2) |
| `body-lg` | `typography.bodyLarge` (16sp Normal) |
| `body-md` | `typography.bodyMedium` (15sp Normal) |
| `body-tabular` | `MoneyText` composable (`fontFeatureSettings = "tnum"`) |
| `button-md` | `typography.labelLarge` (16sp Medium) |
| `caption` | `typography.bodySmall` (13sp Light) |
| `micro` | `typography.labelSmall` (11sp Normal) |

## Spacing

`com.vivekray898.payvoice.ui.theme.Spacing` is the only place a `.dp`
literal may appear. Two groups:

**DESIGN.md `spacing.*`** — `Spacing.{xxs,xs,sm,md,lg,xl,xxl,huge}` →
`2, 4, 8, 12, 16, 24, 32, 64` dp.

**Structural tokens** — the sizes the base-8 scale has no step for. They
exist because the same 56dp touch row was previously written three
different ways as an arithmetic expression:

| Token | dp | Use |
|---|---|---|
| `Spacing.hairline` | 1 | borders, dividers, the progress track |
| `Spacing.dot` | 6 | status pill dot, event-log dot |
| `Spacing.icon` | 24 | glyph box |
| `Spacing.gutter` | 20 | screen side inset |
| `Spacing.iconCircle` | 40 | circular tile behind a glyph |
| `Spacing.rowBar` | 48 | minimum interactive bar/row |
| `Spacing.touch` | 56 | minimum tap target for any control |
| `Spacing.listRow` | 72 | settings list row |
| `Spacing.iconPlate` | 72 | onboarding step art plate |
| `Spacing.hero` | 200 | Home hero band |
| `Spacing.maxContent` | 640 | content width cap on wide layouts |

**Spacing arithmetic is banned in screen files.** `Spacing.xl - Spacing.xs`
is a value the reader has to compute in their head; if a screen needs a
size the table above does not have, add the token here rather than
composing it at the call site. `verifyDesignComponents` fails the build on
`Spacing.a + Spacing.b` outside `ui/theme/` and `ui/components/`.

## Shapes

`MaterialTheme.shapes.{extraSmall,small,medium,large,extraLarge}` →
`4, 6, 8, 12, 16` dp (`ui/theme/Shape.kt`).
Buttons/pills use `RoundedCornerShape(percent = 50)` (rounded.pill).

## Components

| DESIGN.md | Compose |
|---|---|
| `button-primary-pill` | `PvPrimaryButton` |
| `button-secondary` | `PvSecondaryButton` |
| `button-tertiary` | `PvTextButton`, `PvLink` |
| `card-feature-light` | `PvCard` (hairline border + tonal step, no shadow); `PvActionCard` for the tappable variant |
| `nav-bar-on-mesh` | `PvBottomNav` + `PvTabScaffold` (tab destinations); `PvTopBar` (pushed screens) |
| `pill-tag-soft` | `StatusPill` (`StatusTone.Neutral`); semantic tones map Success/Warning/Error; `StatusDot` for the dot alone |
| `body-tabular` money | `MoneyText(amountMinor, currency)` — formats via `AmountExtractor` |
| `list-row` | `PvListItem` (leading/trailing slots), `PvSwitchRow` |
| `chip` | `PvChoiceChip` (selected = filled, unselected = hairline outline) |
| `input` | `PvTextField`, `PvCodeField`, `PvSliderRow`, `PvProgressBar` |
| `fab` | `PvFab` (in the scaffold's `floatingActionButton` slot) |
| icon-only control | `PvIconButton` (48dp, `contentDescription` required) |
| states | `PvLoading` / `PvLoadingList` / `PvEmptyState` / `PvErrorState` / `PvSnackbar` |

Screens always nest in `PvScaffold` (the only `Scaffold` in the module).
The four tab destinations nest in `PvTabScaffold`, which adds the shared
role-aware `PvBottomNav`; pushed screens (Health, Diagnostics) use
`PvTopBar` + `onBack`. The wizard uses `StepScaffold` + `PermissionStep`.

**Navigation.** Four bottom destinations per role, never more:
Home · Payments/Recent · Team/Pair · Settings (`PvTab` in
`ui/components/PvBottomNav.kt`). Only the label, icon and the screen behind
slots 2 and 3 change with the role. `Diagnostics` and `Health` are pushed
from Settings → Support; `Onboarding` sits ahead of the tabs.

**Insets.** `PvScaffold` sets `contentWindowInsets = WindowInsets.safeDrawing`
and hands the insets to the caller as `PaddingValues`. A screen MUST consume
`inner.calculateTopPadding()` (or draw its header inside the topBar slot) —
ignoring it puts the title under the status bar. The only sanctioned exception
is Home's full-bleed hero, which pads its own app-bar row by the status-bar
inset and takes only the bottom inset from the scaffold.

## Do's and Don'ts

See `DESIGN.md` §Do's and Don'ts. Additional PayVoice-specific rules:

- Never hard-code a `.dp` outside `Spacing.kt`/`Shape.kt`. Use a
  structural token for a fixed size; never do the arithmetic at the call
  site.
- Never `Color(0x…)` inline — always `MaterialTheme.colorScheme.*`.
- Money and counts render through `MoneyText` (tabular figures).
- Every screen uses `PvScaffold` (tab destinations: `PvTabScaffold`) — no
  bare `Scaffold`, no `systemBarsPadding()`/`safeDrawingPadding()`.
- Screen files compose **only** `Pv*` components. A raw `Button(`,
  `Surface(`, `Slider(`, `FilterChip(`, `IconButton(`, `ModalBottomSheet(`,
  `TextField(` or `Switch(` in `ui/<screen>/` fails the build: that is what
  the component gate is for, and adding one back means the library is
  missing a component, not that the screen should call Material directly.
- No `Surface(Modifier.fillMaxSize())` at a screen root.
- Tap targets ≥ 48dp; shared rows and cards ≥ 56dp.
- One primary action per screen, bottom-anchored and visible without
  scrolling (`PvPrimaryButton` inline, `PvFab` for the tab screens).
- Permission steps auto-advance only through `PermissionStep` (false→true
  transition; composed-already-granted renders "Continue").

## Screenshots

Every component in the library has a preview in four variants (light,
dark, 1.3× font, 320dp wide) and a committed baseline, so restyling a
component fails CI instead of quietly changing every screen:

```bash
./gradlew validateDebugScreenshotTest   # fail on any visual change
./gradlew updateDebugScreenshotTest    # regenerate baselines after an intended change
```

Baselines live in `app/src/screenshotTestDebug/reference/`. The public
`PvPreviewX()` composables in `ui/components/PvComponentPreviews.kt` are
what the IDE previews and the screenshot test both render, so the two can
never drift.

## Enforcement gates

Two Gradle tasks run in CI and in `check`:

```bash
./gradlew verifyDesignTokens verifyDesignComponents
```

- **`verifyDesignTokens`** — no `Color(0x…)`, no raw `.dp`/`.sp`, and no
  bare `Scaffold(` outside `ui/theme/`, `ui/components/PvScaffold.kt`.
- **`verifyDesignComponents`** — no raw Material widget and no spacing
  arithmetic inside a screen file.

Both replace the old copy-paste `grep` that lived in this document and that
nobody ran.
