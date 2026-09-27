package com.vivekray898.payvoice.core.model

/**
 * Package IDs for payment apps. Google Pay's package is stable and verified on
 * device before use; Kotak's candidate (com.kotak811) comes from the user's
 * installed app and is confirmed via PackageManager + capture flow.
 */
object KnownPackages {
    const val GOOGLE_PAY = "com.google.android.apps.nbu.paisa.user"

    /** User-identified candidate for the installed Kotak app (v5.4.7). */
    const val KOTAK_CANDIDATE = "com.kotak811"

    /** Legacy placeholder kept only for settings migration of old captures. */
    const val KOTAK_PLACEHOLDER = "com.kotak.app.unverified.placeholder"
}

/** Runtime holder for the verified Kotak package id (set from diagnostics capture). */
object PaymentPackages {
    @Volatile
    var kotakPackageId: String? = null

    /**
     * Resolves the active Kotak package: a user-verified capture wins; else the
     * installed com.kotak811 candidate; else null (Kotak not detectable yet).
     */
    fun resolveKotakPackage(isPackageInstalled: (String) -> Boolean): String? {
        kotakPackageId?.let { return it }
        return if (isPackageInstalled(KnownPackages.KOTAK_CANDIDATE)) {
            KnownPackages.KOTAK_CANDIDATE
        } else {
            null
        }
    }
}

/** Where a payment notification came from. */
enum class PaymentSource(val packageId: String, val displayName: String) {
    GOOGLE_PAY(KnownPackages.GOOGLE_PAY, "Google Pay"),
    KOTAK(KnownPackages.KOTAK_PLACEHOLDER, "Kotak");

    val isVerified: Boolean
        get() = packageId != KnownPackages.KOTAK_PLACEHOLDER

    companion object {
        fun fromPackage(pkg: String): PaymentSource? = when {
            pkg == KnownPackages.GOOGLE_PAY -> GOOGLE_PAY
            // Verified capture wins; otherwise the installed com.kotak811
            // candidate is accepted (it can only fire if actually installed).
            pkg == PaymentPackages.kotakPackageId || pkg == KnownPackages.KOTAK_CANDIDATE -> KOTAK
            else -> null
        }
    }
}

/** Classification direction of a payment notification. */
enum class Direction { RECEIVED, SENT, UNKNOWN }

/**
 * Which capture channel produced an event. Notifications and SMS converge in
 * the same pipeline; the channel participates in deduplication so a Kotak
 * notification + Kotak SMS for one payment announce only once.
 */
enum class CaptureSource {
    GPAY_NOTIFICATION,
    KOTAK_NOTIFICATION,
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
