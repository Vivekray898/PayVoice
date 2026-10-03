# PayVoice — Design Direction (Step 1)

Base = the Stripe `DESIGN.md` in this repo (kept). Visual-language reference =
shadcn/ui + Origin UI — **language only, no web dependency**: neutral canvas,
1dp hairline borders, 8–12dp radius, near-zero shadow, calm type, whitespace,
explicit empty/loading/error states. Behaviour = Material 3 Expressive +
Android accessibility (48dp targets, 4.5:1, TalkBack labels).

## Palette — brand kept, semantics added

| Role | Light | Dark |
|---|---|---|
| `primary` (one filled CTA per screen) | Indigo `#533afd` | Indigo Soft `#665efd` |
| `background` / `surface` | Canvas `#ffffff` | Brand Dark 900 `#1c1e54` |
| `onSurface` / `onSurfaceVariant` | `#0d253d` / `#64748d` | `#ffffff` / `#64748d` |
| `outline` / `outlineVariant` | `#e3e8ee` / `#a8c3de` | `#64748d` / `#61718a` |
| `error` (destructive only) | Ruby `#ea2261` | Ruby |
| **`success`** *(new)* | green tuned to the indigo hue, AA on white | lightened for AA on navy |
| **`warning`** *(new)* | Lemon `#9b6829` | lightened |

No gradient mesh in-app (marketing-only device, GPU cost on 1GB phones).
Dynamic colour **off by default**, offered as a Settings toggle.

## Type — Stripe's scale on system `SansSerif`

Sohne is proprietary. Display tiers keep **Light 300 + negative tracking**;
body Normal; money always through `MoneyText` (`tnum`).

| Stripe | Compose | size / weight / track |
|---|---|---|
| `display-xl` | `displayMedium` | 48sp / 300 / −0.96 |
| `display-lg` | `displaySmall` | 32sp / 300 / −0.64 |
| `heading-lg` | `headlineMedium` | 22sp / 300 / −0.22 |
| `heading-md` | `headlineSmall` | 20sp / 300 / −0.2 |
| `body-lg` / `body-md` | `bodyLarge` / `bodyMedium` | 16sp / 15sp |
| `button-md` | `labelLarge` | 16sp / 500 |
| `caption` / `micro` | `bodySmall` / `labelSmall` | 13sp / 11sp |

**Money micro-scale** *(new)*: hero amount `displayMedium`, row amount
`titleMedium`, both tabular — "big readable numbers" is a product goal.

## Shape & elevation

Radius `4 · 6 · 8 · 12 · 16 · pill` (Stripe, `Shape.kt`). **Cards = 12dp +
1dp hairline + no shadow.** Elevation: `0` surface · `1` tonal only ·
`2` sheet/FAB. No `shadowElevation` anywhere — mud on cheap panels, GPU cost.

## Spacing

Base 8 + 2/4 sub-tokens (Stripe). **Gap fix:** add the structural sizes
screens currently derive by arithmetic as named tokens —
`gutter 20 · iconCircle 40 · rowBar 48 · touch 56 · listRow 72 · hero 200` —
so the 50 `Spacing.xl - Spacing.xs` expressions collapse. Screens still never
write `.dp`.

## Motion *(new — Stripe is silent)*

≤**200ms**, `FastOutSlowInEasing`. Allowed: press feedback, sheet slide-in,
item enter, banner expand. Banned: blur, parallax, shimmer loops, endless
spinners (skeletons instead). Everything reads `animator_duration_scale`, so
reduced-motion degrades to instant for free.

## Iconography

One family, `Icons.Filled.*` — already the single family in use across the
app, and R8 keeps only the glyphs that are referenced. Filled only inside a
tinted circle (`PvIconCircle`), outline/`AutoMirrored` otherwise. No emoji,
no multicolour icons. Every icon-only control has a `contentDescription`.

## Screen grammar (all screens)

1. `PvScaffold` → exactly one `PvTopBar` **or** the `StorefrontHero` header.
2. One primary action, bottom-anchored, 56dp, visible without scrolling.
3. Money block first, then list, then secondary actions.
4. Every list ships **skeleton → empty (with CTA) → error (with Retry)**.
5. Plain words in `strings.xml`: "Payment received", never "Event delivered".
6. Home health banner: green *"All good, ready for payments"* or amber/red
   *"2 things need fixing"* → expands to `PvPermissionCard` rows.
7. `PvSecureWindow()` on pairing, payments and the owner dashboard.

## Deviations to write back into `DESIGN.md` (on approval)

| # | `DESIGN.md` | Direction | Why |
|---|---|---|---|
| 1 | touch targets ≥40/44px | **≥48dp** | AGENTS.md + TalkBack; 44px fails |
| 2 | button padding `8px 16px` | **56dp min height, 16/12dp content** | one-hand thumb reach |
| 3 | gradient mesh hero non-negotiable | **not used in-app** | GPU cost on 1GB devices |
| 4 | "no semantic palette" | **+ `success` / `warning`** | health system needs 4 tones |
| 5 | no dark-theme section | **full dark contract** | already shipping |
| 6 | no motion section | **≤200ms, reduced-motion safe** | low-end requirement |
