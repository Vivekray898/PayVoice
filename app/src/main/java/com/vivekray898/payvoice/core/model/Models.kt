package com.vivekray898.payvoice.core.model

/**
 * Package IDs for payment apps. The Google Pay package is stable and verified;
 * the Kotak package is a placeholder until a real Kotak notification is captured
 * on-device (diagnostics screen records the package name). Never guess this.
 */
object KnownPackages {
    const val GOOGLE_PAY = "com.google.android.apps.nbu.paisa.user"

    /**
     * Placeholder. Replaced by the verified Kotak Bank package id once captured
     * via the diagnostics capture flow. While unset, Kotak is not detectable.
     */
    const val KOTAK_PLACEHOLDER = "com.kotak.app.unverified.placeholder"
}

/** Runtime holder for the verified Kotak package id (set from diagnostics capture). */
object PaymentPackages {
    @Volatile
    var kotakPackageId: String? = null
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
            pkg == PaymentPackages.kotakPackageId -> KOTAK
            else -> null
        }
    }
}

/** Classification direction of a payment notification. */
enum class Direction { RECEIVED, SENT, UNKNOWN }

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
) {
    val isAnnounceable: Boolean
        get() = direction == Direction.RECEIVED &&
            confidence == Confidence.HIGH &&
            amountMinor != null &&
            amountMinor > 0
}
