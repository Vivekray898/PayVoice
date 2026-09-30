# PayVoice → DESIGN.md mapping (Stripe reference)

`DESIGN.md` at the project root (Stripe, from awesome-design-md) is the
**visual source of truth**. This doc records exactly where each token
lands in Compose. Web-only concepts and their disposition:

## Colors → [Color.kt](../app/src/main/java/com/vivekray898/payvoice/ui/theme/Color.kt)

| DESIGN.md token | Compose role | Value |
|---|---|---|
| `colors.primary` #533AFD | `colorScheme.primary` | verbatim |
| `colors.primary-soft` #665EFD | dark `primary` | verbatim |
| `colors.primary-deep` / `primary-press` | dark `primaryContainer` | verbatim |
| `colors.primary-bg-subdued-hover` #B9B9F9 | light `primaryContainer` | verbatim |
| `colors.brand-dark-900` #1C1E54 | dark `surfaceVariant`/containers | verbatim |
| `colors.ink` #0D253D | `onSurface` / `onBackground` | verbatim |
| `colors.ink-secondary` #273951 | `secondary` | verbatim |
| `colors.ink-mute` #64748D | `onSurfaceVariant` | verbatim |
| `colors.canvas` #FFFFFF | `surface` | verbatim |
| `colors.canvas-soft` #F6F9FC | `background` | verbatim |
| `colors.hairline` #E3E8EE | `outlineVariant` | verbatim |
| `colors.hairline-input` #A8C3DE | `outline` | verbatim |
| `colors.ruby` #EA2261 | `error` | verbatim |
| — | dark scheme navy family | **derived** (reference's "dark-app dashboard track" polarity); marked `(derived)` in Color.kt |

Success green (`Positive`) is an **Android-added semantic** — the Stripe
reference keeps success states inside product UI only, but PayVoice needs
on-screen "Connected/ON" states.

## Typography → [Type.kt](../app/src/main/java/com/vivekray898/payvoice/ui/theme/Type.kt)

| DESIGN.md | Compose | Adaptation |
|---|---|---|
| `body-tabular` 14px/tnum | — | Money values render via `MoneyText` (tabular figures on Roboto) |
| 300-weight display tier | kept at Normal/SemiBold | 300 weight + negative tracking reads thin/gaunt at Android sizes and breaks WCAG contrast on the off-white canvas — documented deviation |
| — | `titleMedium` (section headers) | SemiBold per reference heading hierarchy |
| — | body/label styles | Android-standard rhythm, sp units |

## Spacing → [Spacing.kt](../app/src/main/java/com/vivekray898/payvoice/ui/theme/Spacing.kt)

`Spacing.xxs 2 / xs 4 / sm 8 / md 12 / lg 16 / xl 24 / xxl 32 / huge 64`
— the only sanctioned values; no inline `13.dp`/`7.dp`.

## Shapes

| DESIGN.md | Compose `Shapes` |
|---|---|
| `rounded.sm` 6px | `small` |
| `rounded.md` 8px | `medium` |
| `rounded.lg` 12px | `large` |
| `rounded.xl` 16px | `extraLarge` |
| `rounded.pill` | `CircleShape`/pill for chips (per component) |

## Components

| DESIGN.md | PayVoice composable |
|---|---|
| `button-primary-pill` | `PvPrimaryButton` |
| `card-feature-light` / `card-pricing` | `PvActionCard`, `SectionCard` (16dp internal padding, hairline/tonal surface) |
| `pill-tag-soft` | `StatusPill` |
| `link-on-light` | inline `TextButton` / primary-tinted text |
| `card-dashboard-mockup` (tnum) | `MoneyText` for all amounts |

## Do's / Don'ts (Android adaptation)

- **Do** keep indigo for actions/links only, one filled button per view region.
- **Do** render money with tabular figures (`MoneyText`).
- **Do** stay on the token spacing/shapes — no ad-hoc values.
- **Don't** use indigo as body-text color (reference rule, kept).
- **Don't** add colors outside the documented set; dark navy family only for dark scheme.
- Web-only parts of the reference (gradient mesh heroes, Sohne/ss01,
  marketing breakpoints) are **not applicable** to this native app.
