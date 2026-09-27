package com.vivekray898.payvoice.core.model

/**
 * Package IDs for payment apps. GPay's package is stable and verified on
 * device. No bank-app packages exist here by design: bank payments (Kotak and
 * others) arrive exclusively via bank SMS, never via app notifications.
 */
object KnownPackages {
    const val GOOGLE_PAY = "com.google.android.apps.nbu.paisa.user"
}

/**
 * Human-readable label for where a payment came from.
 *
 * Architecture (post-cleanup): GPay is the ONLY UPI-app notification source.
 * Bank payments — including Kotak — arrive exclusively via bank SMS
 * ([CaptureSource.SMS_KOTAK] / [CaptureSource.SMS_BANK]). There is no Kotak
 * app-notification source and no bank-app package matcher anywhere in the
 * notification path.
 */
enum class PaymentSource(val packageId: String, val displayName: String) {
    GOOGLE_PAY(KnownPackages.GOOGLE_PAY, "Google Pay");

    companion object {
        /**
         * Notification packages route by exact match. Deliberately contains no
         * bank packages: a com.kotak811 (or any other bank app) notification
         * must never become a payment event — only SMS carries bank payments.
         */
        fun fromPackage(pkg: String): PaymentSource? =
            entries.firstOrNull { it.packageId == pkg }
    }
}

/** Classification direction of a payment notification. */
enum class Direction { RECEIVED, SENT, UNKNOWN }

/**
 * Which capture channel produced an event. Notifications (GPay only) and bank
 * SMS converge in the same pipeline; the channel participates in
 * deduplication so a bank SMS and any co-arriving evidence for one payment
 * announce only once.
 */
enum class CaptureSource {
    GPAY_NOTIFICATION,
    SMS_KOTAK,
    SMS_BANK,
    OTHER_NOTIFICATION,
}

/** Immutable capture input: one raw event from any channel, pre-parsing. */
data class CaptureEvent(
    val captureSource: CaptureSource,
    /** Notification package, or SMS sender address (e.g. "KKBKXXXX"). */
    val originId: String,
    val title: String?,
    val body: String,
    val bigText: String? = null,
    val subText: String? = null,
    val notificationId: Int = -1,
    val postedAtMs: Long,
)

/** Parser confidence. Only HIGH auto-announces by default (spec §6). */
enum class Confidence { HIGH, MEDIUM, LOW }

/**
 * A parsed, classified, deduplicated payment event — the single unit that
 * flows through the pipeline. Money is integer minor units (paise): ₹500.50
 * is 50050. Never floating point.
 */
data class PaymentEvent(
    val eventId: String,
    val source: PaymentSource,
    val amountMinor: Long,
    val currency: String,
    val senderName: String?,
    val timestamp: Long,
    val announcementText: String,
) {
    /** Major units (rupees) as Long; minor remainder is preserved in [amountMinor]. */
    val majorUnits: Long
        get() = amountMinor / 100
}

/**
 * Raw parse output before fingerprinting/announcement composition. Kept as a
 * separate type so parsers stay pure and testable.
 */
data class ParsedNotification(
    val source: PaymentSource,
    val direction: Direction,
    val confidence: Confidence,
    val amountMinor: Long?,
    val currency: String,
    val senderName: String?,
    val referenceId: String?,
    /** Diagnostic-only: raw title/text stay on-device, never leave the app. */
    val rawTitle: String,
    val rawText: String,
    /** Optional display label (e.g. the resolved bank for generic SMS). */
    val sourceLabel: String? = null,
) {
    val isAnnounceable: Boolean
        get() = direction == Direction.RECEIVED &&
            confidence == Confidence.HIGH &&
            amountMinor != null &&
            amountMinor > 0
}
