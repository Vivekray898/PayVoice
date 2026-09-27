package com.vivekray898.payvoice.core.parser

import java.security.MessageDigest

/**
 * Deterministic event fingerprints for duplicate protection (spec §7, §42).
 * The same notification reposted by the system, reprocessed after a listener
 * reconnect, or re-sent offline must always produce the same id — and the
 * backend (Phase 2) relies on the same idempotency key.
 */
object Fingerprinter {

    /** 60-second bucket: reposts within the same minute collapse together. */
    const val TIME_BUCKET_MS = 60_000L

    /**
     * SHA-256 over source package + amount + timestamp bucket + reference id
     * (when present) + normalized notification text. Hex-encoded, prefixed
     * "evt_" for readability in logs and history screens.
     */
    fun fingerprint(
        packageId: String,
        amountMinor: Long?,
        timestampMs: Long,
        title: String?,
        text: String?,
        referenceId: String? = null,
        bucketMs: Long = TIME_BUCKET_MS,
    ): String {
        val bucket = timestampMs / bucketMs
        val normalizedTitle = DirectionClassifier.normalize(title.orEmpty())
        val normalizedText = DirectionClassifier.normalize(text.orEmpty())
        val payload = buildString {
            append(packageId)
            append('|')
            append(amountMinor ?: -1L)
            append('|')
            append(bucket)
            append('|')
            append(referenceId.orEmpty())
            append('|')
            append(normalizedTitle)
            append('|')
            append(normalizedText)
        }
        return "evt_" + sha256Hex(payload)
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
