package com.vivekray898.payvoice.core.database

/**
 * Channel tags written into `processed_events.sourcePackage` by the pipeline
 * and matched by the ProcessedEventDao cross-channel queries.
 *
 * Deliverable 2c (reliability): the cross-channel suppression window must be
 * an EXACT channel match, not a heuristic over sender IDs / package names.
 * The writer and the readers MUST share these constants — a rename on one
 * side only would silently kill suppression (the exact silent-failure class
 * the adversarial audit flagged), so a unit test pins them together.
 *
 * LIKE patterns live here (not inline in @Query strings) so the test can
 * assert the writer/reader contract against compile-time constants.
 */
object CrossChannelTags {
    /** Prefix written by PaymentPipeline for bank-SMS captures. */
    const val SMS_PREFIX = "SMS:"

    /** Prefix written by PaymentPipeline for GPay notification captures. */
    const val NOTIF_PREFIX = "NOTIF:"

    /** SQL LIKE pattern matching every tagged SMS row (DAO reader). */
    const val SMS_LIKE = "SMS:%"

    /** SQL LIKE pattern matching every tagged notification row (DAO reader). */
    const val NOTIF_LIKE = "NOTIF:%"

    /** The tag the pipeline writes for a capture of the given channel. */
    fun tag(isSms: Boolean, originId: String): String =
        (if (isSms) SMS_PREFIX else NOTIF_PREFIX) + originId
}
