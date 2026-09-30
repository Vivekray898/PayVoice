# PayVoice — Analytics Event Catalog

Firebase Analytics events sent by the app. Every event is:

- **Structural** — counts, booleans, enum names, stage names, integer latencies
- **Never carrying payment content** — no amounts, no senders, no UPI IDs,
  no account numbers, no SMS bodies, no notification text

The single entry point is
[`PayVoiceAnalytics`](../app/src/main/java/com/vivekray898/payvoice/core/analytics/PayVoiceAnalytics.kt).
Every event name and every parameter key is a compile-time constant in that
file — free-form event logging is impossible by construction. All calls are
fire-and-forget (`runCatching`-wrapped) and no-ops until `init()` succeeds,
so a failed analytics call can never affect the payment path.

## Events

| Event name | When fired | Parameters | Purpose |
|------------|------------|------------|---------|
| `pv_app_opened` | Every app foreground (startup coroutine) | none | DAU/WAU |
| `pv_app_cold_started` | After first frame of MainActivity | `cold_start_ms` | Perf tracking |
| `pv_notif_permission` | After POST_NOTIFICATIONS dialog resolves | `result=GRANTED\|DENIED` | Funnel: permission grant rate |
| `pv_sms_permission` | After RECEIVE_SMS dialog resolves (onboarding + reliability) | `result=GRANTED\|DENIED` | Funnel |
| `pv_battery_exempt` | Battery-fix flow opens | `result=already\|prompted` | Funnel |
| `pv_pairing_started` | Pairing code submitted | none | Funnel: pairing attempts |
| `pv_pairing_completed` | Pairing claim succeeded | `duration_ms` | Funnel + latency |
| `pv_pairing_failed` | Pairing claim failed | `reason=INVALID\|EXPIRED\|ALREADY_USED\|UNAUTHENTICATED\|NETWORK` | Funnel: failure breakdown |
| `pv_employee_revoked` | Owner revoke succeeded | none | Feature usage |
| `pv_payment_captured_local` | Capture parsed, passed gates, won dedup | none | Volume |
| `pv_payment_announced_local` | TTS requested (local path) | `latency_ms` (capture→tts-request) | Perf |
| `pv_remote_delivery_succeeded` | FCM payment reached `tts_started` | `latency_ms` (fcm→tts-request) | Remote delivery success rate |
| `pv_remote_delivery_failed` | FCM payment denied / duplicate / errored | `stage=denied\|dedup\|failed` | Failure breakdown |
| `pv_cross_channel_suppressed` | Cross-channel dedup suppressed a capture | `source=<CaptureSource enum>` | Dedup effectiveness |
| `pv_non_fatal_error` | Reserved (defined, not yet fired) | `area`, `error_class` | Error monitoring |

Notes:

- `pv_remote_delivery_failed` intentionally does NOT fire for the
  `TEST_ANNOUNCEMENT` path and does NOT fire for the invalid-payload drop
  (no event id exists to correlate) — volume reconciliation uses
  `pv_payment_captured_local` on the owner side vs. delivery events on
  employee sides.
- `pv_sms_permission` cannot distinguish a soft denial from a permanently
  denied permission (no `shouldShowRationale` probe in the flow); denials
  are reported as `DENIED`.
- `pv_non_fatal_error` is defined but has no call site yet; it exists so
  the catalog is reviewed before the code lands (see below).

## What is NEVER logged

- Payment amounts (in any currency or unit)
- Sender names or phone numbers
- UPI IDs, account numbers, IFSC codes
- SMS body text
- Notification title/text/big text
- Event IDs (the `evt_…` fingerprint) or dedup fingerprints
- Supabase user IDs, employee IDs
- FCM tokens (full or partial)
- Device IMEI / Android ID / advertising ID
- GPS or location
- User identity of any kind (`setUserId(null)` is called at init; the app
  has no accounts)

## Disabling

Analytics is disabled in debug builds at init time
(`setAnalyticsCollectionEnabled(!FLAG_DEBUGGABLE)` — this project does not
generate `BuildConfig`, so the build-type check uses the app's
`ApplicationInfo.FLAG_DEBUGGABLE`, the same convention as `DebugLog`).
Debug sessions therefore never pollute production dashboards.

To disable in a specific release build, add a build-type flag and pass it
to `PayVoiceAnalytics.init`.

## Reviewing additions

Any new event must be added to this table **before** the code commit. The
reviewer checks that the event carries no content from the "NEVER logged"
list, that its name and parameter keys are constants in
`PayVoiceAnalytics`, and that the call fires AFTER the state change it
describes — never inside `AnnouncementSpeaker.speak`,
`PayVoiceNotificationListener.onNotificationPosted`, or before the work in
`PayVoiceMessagingService.onMessageReceived` completes.
