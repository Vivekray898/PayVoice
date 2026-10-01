# PayVoice Design System (Compose)

Source of truth: `DESIGN.md` at the project root. This document is the
token→Compose mapping **as implemented** (2026-10 rebuild). Every color,
dimension, radius, and text style in `ui/` flows through these symbols —
the grep gate at the bottom is enforced per commit.

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

`com.vivekray898.payvoice.ui.theme.Spacing.{xxs,xs,sm,md,lg,xl,xxl,huge}` →
`2, 4, 8, 12, 16, 24, 32, 64` dp. Composed sizes are allowed only as
token sums, e.g. the 56dp touch row is `Spacing.xxl + Spacing.lg + Spacing.sm`.

## Shapes

`MaterialTheme.shapes.{extraSmall,small,medium,large,extraLarge}` →
`4, 6, 8, 12, 16` dp (`ui/theme/Shape.kt`).
Buttons/pills use `RoundedCornerShape(percent = 50)` (rounded.pill).

## Components

| DESIGN.md | Compose |
|---|---|
| `button-primary-pill` | `PvPrimaryButton` |
| `button-secondary` | `PvSecondaryButton` |
| `card-feature-light` | `PvActionCard`; carded groups use `Surface(shape = MaterialTheme.shapes.large, color = colorScheme.surface, tonalElevation = Spacing.xxs)` |
| `nav-bar-on-mesh` | `PvTopBar` (+ `LargeTopAppBar` on Home) |
| `pill-tag-soft` | `StatusPill` (`StatusTone.Neutral`); semantic tones map Success/Warning/Error |
| `body-tabular` money | `MoneyText(amountMinor, currency)` — formats via `AmountExtractor` |

Screens always nest in `PvScaffold` (the only `Scaffold` in the module);
inner screens use `PvTopBar`; the wizard uses `StepScaffold` + `PermissionStep`.

## Do's and Don'ts

See `DESIGN.md` §Do's and Don'ts. Additional PayVoice-specific rules:

- Never hard-code a `.dp` outside `Spacing.kt`/`Shape.kt` and token-sum
  expressions marked `// structural` (touch heights, hairlines, icon
  circle sizes).
- Never `Color(0x…)` inline — always `MaterialTheme.colorScheme.*`.
- Money and counts render through `MoneyText` (tabular figures).
- Every screen uses `PvScaffold` — no bare `Scaffold`, no manual insets,
  no `systemBarsPadding()`/`safeDrawingPadding()`.
- No `Surface(Modifier.fillMaxSize())` at a screen root.
- Permission steps auto-advance only through `PermissionStep` (false→true
  transition; composed-already-granted renders "Continue").

## Enforcement gate

Run after every UI change — output must be empty:

```bash
grep -rnE "\.dp\b|Color\(0x|\.sp\b" app/src/main/java/com/vivekray898/payvoice/ui/ \
  --include="*.kt" \
  | grep -vE "ui/theme/(Color|Spacing|Shape|Type|Theme)\.kt" \
  | grep -v "import androidx.compose.ui.unit.dp" \
  | grep -v "// structural"
```
