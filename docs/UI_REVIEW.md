# PayVoice — UI Review (Premium v2 overhaul, 2026-10-01)

Scope: the six-phase premium overhaul — multi-step setup wizard, gradient
hero + status chips, carded sections with hairline borders, icon circles,
status icons, spacing-token sweep. Commits `7249e53` (tokens) → `df8435e`
(one commit per phase, see git log). Pipelines untouched — the earlier
end-to-end sanity on the debug build's GPay simulator still stands:
`capture→ttsRequested=12ms (parse=2ms dedup=12ms)`, `capture→ttsStart=150ms`.

## Design language

- Stripe palette remains the DESIGN.md source of truth (`#533AFD` primary,
  navy ink, off-white canvas, hairline borders). Dark scheme derives from
  the reference's navy polarity.
- One card language everywhere: `PvSection(carded = true)` renders content
  in a surface card with a 1dp hairline border (`outlineVariant` @ 50%);
  hero uses an extraLarge card with a subtle primary→surface gradient wash.
- One icon language: every leading icon sits in a tinted circle (hero 48dp,
  action cards 48dp, avatars 40dp, status icons 22dp).
- Status is communicated twice, calmly: a colored glyph (check/cross/warn)
  plus a pill chip (`Ready`, `Live`, `4 of 5 checks passing`).
- All spacing flows through `Spacing` tokens (2/4/8/12/16/24/32/48/64);
  remaining raw `.dp` values are intrinsic sizes only (icon sizes, 56dp
  primary button height, 1dp hairline, tonal elevations) — grep-verified.

## Screenshots

All captured on emulator-5554 by real UI navigation (uiautomator-verified
tap targets), light and dark (`cmd uimode night yes/no`), under
`docs/screens/v2/`:

| Screen | Light | Dark |
|---|---|---|
| Wizard 1 — Welcome | wizard-1-welcome.png | wizard-1-welcome-dark.png |
| Wizard 2 — Role | wizard-2-role.png | wizard-2-role-dark.png |
| Wizard 2b — Pairing (employee) | wizard-3b-pairing.png | — (sheet flow, same card system) |
| Wizard 3 — Access (pending) | wizard-3-access.png | wizard-3-access-dark.png |
| Wizard 3 — Permissions | wizard-4-permissions.png | wizard-4-permissions-dark.png |
| Wizard 4 — Battery | wizard-5-battery.png | wizard-5-battery-dark.png |
| Wizard 5 — Ready | wizard-6-ready.png | wizard-6-ready-dark.png |
| Home (after wizard) | home-light.png | home-dark.png · wizard-7-home-dark.png |
| Settings (+ bottom action) | settings-light.png · settings-bottom.png | settings-dark.png |
| Diagnostics | diagnostics-light.png | diagnostics-dark.png |
| Reliability | reliability-light.png | reliability-dark.png |
| Employees (owner, empty) | owner-empty-light.png | owner-empty-dark.png |
| This device (employee) | employee-light.png | employee-dark.png |
| Theme reference (Phase 1) | theme-home-light.png | — |

## Checklist results

| Check | Result |
|---|---|
| Multi-step wizard with step indicator, back arrow, role selection | PASS (WizardHeader: back + progress bar + "Step N of 4"; RoleStep has two large role cards; auto-advance on grant verified twice) |
| Home hero with status chip + premium action cards | PASS (gradient hero, `Ready`/`Action needed`/`Live` chips, icon-circle PvActionCards) |
| Consistent TopAppBar + sectioned cards on inner screens | PASS (shared PvScaffold; Settings/Diagnostics/Reliability/Employee/Owner all carded PvSections) |
| Dark mode correct on every screen | PASS (every screen captured in both modes; status colors switch via luminance check; gradient uses theme colors) |
| No hard-coded .dp spacing in ui/ | PASS (grep: only intrinsic sizes/strokes/elevations remain; all spacing via Spacing tokens) |
| Primary action always visible | PASS (Settings "Preview voice" bottom-anchored via scaffold bottomBar; wizard bottom-anchored buttons; Home hero action above the fold) |
| Top/bottom inset correctness | PASS (PvScaffold safeDrawing everywhere; wizard verified with UI dumps on cold start) |
| Tap targets ≥ 48dp | PASS (rows/cards 56dp min; buttons ≥ 40dp + padding; full-card click surfaces) |
| Empty states + load indicators | PASS (PvEmptyHint on Diagnostics captures/log + Employee last-payment; PvLoadingRow on pairing prepare + joining; PvEmptyState on Home/Owner) |
| Text scales (sp) | PASS (all typography in sp via MaterialTheme styles; amounts via MoneyText tabular figures) |
| TalkBack descriptions | PASS (icon-only actions carry contentDescription; decorative icons null) |
| Pipeline regression | NONE (all commits presentation-only; 96/96 unit tests at every phase) |

## Build & test evidence

- `./gradlew clean test assembleDebug assembleRelease` green at Phase 6;
  96/96 unit tests (test-results XML).
- Every phase commit was preceded by `test assembleDebug` plus an
  on-emulator walk of the changed screen (uiautomator dump evidence in the
  commit messages).
- Wizard walk exercised the real flows twice: fresh install (pending
  ACCESS → "Why do I need this?" → grant → auto-advance) and pre-granted
  (auto-advance on step entry), plus employee pairing-code entry earlier
  in Phase 2.
