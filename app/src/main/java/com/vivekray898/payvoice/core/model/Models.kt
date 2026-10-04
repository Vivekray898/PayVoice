package com.vivekray898.payvoice.core.model

/**
 * Package IDs for payment apps. GPay's package is stable and verified on
 * device — the only UPI-app capture source.
 */
object KnownPackages {
    const val GOOGLE_PAY = "com.google.android.apps.nbu.paisa.user"

    /**
     * Known GPay package variants. The canonical package is what Play ships
     * in India; suffix variants have appeared on some OEM builds. Any
     * variant must keep the SAME parser — the notification content format is
     * identical.
     */
    private val GOOGLE_PAY_VARIANTS = listOf(
        "com.google.android.apps.nbu.paisa.user.india",
    )

    /**
     * True when [pkg] is a GPay notification source. Canonical package is an
     * exact match; anything else must be an observed variant or a
     * dot-suffix of the canonical package (future regional flavors), never a
     * package that merely CONTAINS the name. Unknown packages always return
     * false.
     */
    fun isGooglePayPackage(pkg: String): Boolean {
        if (pkg == GOOGLE_PAY) return true
        if (GOOGLE_PAY_VARIANTS.any { pkg == it }) return true
        // Dot-boundary suffix of the canonical package only:
        //  ✓ com.google.android.apps.nbu.paisa.user.india
        //  ✗ evil.com.google.android.apps.nbu.paisa.user
        //  ✗ com.google.android.apps.nbu.paisa.userimposter
        return pkg.startsWith("$GOOGLE_PAY.")
    }
}

/**
 * Human-readable label for where a payment came from.
 *
 * Architecture: GPay is the ONLY UPI-app notification source. No bank-app
 * package matcher exists anywhere in the notification path.
 */
enum class PaymentSource(val packageId: String, val displayName: String) {
    GOOGLE_PAY(KnownPackages.GOOGLE_PAY, "Google Pay");

    companion object {
        /**
         * Notification packages route by exact match OR recognized GPay
         * variant (all variants announce as Google Pay). Deliberately contains
         * no other packages: a com.kotak811 (or any other app) notification
         * must never become a payment event.
         */
        fun fromPackage(pkg: String): PaymentSource? =
            entries.firstOrNull { it.packageId == pkg }
                ?: if (KnownPackages.isGooglePayPackage(pkg)) GOOGLE_PAY else null
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
    /**
     * `StatusBarNotification.tag` for a notification capture; null for SMS.
     * Together with [notificationId] this is the stable source-event identity
     * the dedupe key is built from — see `Fingerprinter.captureFingerprint`.
     */
    val notificationTag: String? = null,
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
