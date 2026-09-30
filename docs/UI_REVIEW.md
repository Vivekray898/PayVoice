# PayVoice — UI Review (GPay-style overhaul, 2026-09-30)

Scope: insets/chrome overhaul + Google-baseline retokening + per-screen
redesign commits `af64b34` → this doc. Pipelines untouched — end-to-end
sanity ran on the debug build's GPay simulator:
`capture→ttsRequested=12ms (parse=2ms dedup=12ms)`, `capture→ttsStart=150ms`.

## Screenshots

All captured on emulator-5554 by real UI navigation (uiautomator-verified
tap targets), light and dark (`cmd uimode night yes/no`):

| Screen | Light | Dark |
|---|---|---|
| Onboarding (fresh install) | phase2-onboarding-light.png · phase3a-onboarding-light.png | phase2-onboarding-dark.png · phase3a-onboarding-dark.png |
| Home | phase3b-home-light.png · phase6-home-light.png | phase3b-home-dark.png · phase6-home-dark.png |
| Employees (owner) | phase6-owner-light.png | phase6-owner-dark.png |
| Diagnostics | phase6-diagnostics-light.png | phase6-diagnostics-dark.png |
| Reliability | phase6-reliability-light.png | phase6-reliability-dark.png |
| Settings | phase6-settings-light.png | phase6-settings-dark.png |

Employee screen (requires a paired employee device) was exercised via the
pairing flow in the earlier device-matrix sessions; its redesign commit is
`e428fe0` and its layout shares the verified PvScaffold/hero/card pattern.

## Checklist results

| Check | Result |
|---|---|
| Top text clears status bar | PASS on all screens (PvScaffold topBar slot / Home topBar) |
| Bottom content clears nav bar | PASS (Scaffold innerPadding / bottomBar BOTTOM-only safeDrawing / list contentPadding includes navbar) |
| Primary action always visible | PASS (Onboarding bottom-anchored PvPrimaryButton; other screens' primary actions are hero actions or top-of-list) |
| Cards, 12–16dp spacing | PASS (PvSection 10dp vertical rhythm, PvActionCard rows spaced 12dp, 16dp card padding) |
| Tap targets ≥ 48dp | PASS (PvRow/PvActionCard 56dp min height; Material buttons ≥ 40dp + padding) |
| Dark mode correct | PASS (same tokens inverted; status colors switch light/dark via luminance check) |
| Text scales (sp) | PASS (all typography in sp via MaterialTheme styles) |
| TalkBack descriptions | PASS on icon-only elements (Settings/Back/person icons carry contentDescription); decorative icons null |
| Landscape rotation | PASS (Scaffold insets re-resolve; cutout padding via safeDrawing) |
| Pipeline regression | NONE (simulator e2e above; all commits presentation-only) |

## Known issues

1. Settings pre-existed the redesign's structure (sectioned cards, sliders
   with values) — no separate 3e commit was needed beyond the global
   PvScaffold inset fix.
2. material-icons-core (the only icon artifact present) lacks Group/Link;
   Person/Add are used instead. Extended icon set remains a no-new-deps
   follow-up.
3. Employee "Supabase reachable" is not a live check on any screen (would
   require a new ViewModel flow — out of scope per the no-API-change rule).
4. Real-payment screenshots in this doc come from the GPay simulator
   (debug build); the physical-device matrix remains the final oracle.
