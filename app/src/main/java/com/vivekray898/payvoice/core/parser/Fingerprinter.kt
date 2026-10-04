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

    /** [CaptureEvent.notificationId] sentinel for channels with no sbn id (SMS). */
    const val NO_NOTIFICATION_ID = -1

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

    /**
     * Payment fingerprint, built ONLY from stable source identifiers.
     *
     * The previous key fell back to `AMT|<amount>|<sender>|<postTime/5min>`.
     * That bucket is not a source identifier — it is a wall-clock window — so
     * every same-amount payment from the same sender inside five minutes
     * collapsed into one row and **silently lost all but the first**
     * (docs/PAYMENT_PIPELINE_FINDINGS.md, `dedup-amount-bucket-collision`).
     * The rule this replaces: two legitimate same-amount payments must BOTH
     * announce; only a true re-delivery of the *same source event* may drop.
     *
     * Three tiers, most authoritative first:
     *  1. `REF|` — a transaction reference (UTR / UPI Ref / RRN). The same
     *     reference is by definition the same payment, on any channel.
     *  2. `SRC|` — the platform's own event identity: package +
     *     StatusBarNotification id + tag + the EXACT post time. A re-delivery
     *     (listener-rebind snapshot, platform double-post) repeats all four
     *     byte-for-byte; two distinct payments cannot.
     *  3. `TXT|` — last resort for channels with no notification identity
     *     (SMS today). Normalized content + the exact post time, no bucket.
     *
     * [senderName] and [captureSource] are deliberately NOT in the key: a GPay
     * notification and a bank SMS describing one payment are the same payment,
     * and reconciling those two channels is `CrossChannelMerger`'s job, not
     * the key's.
     */
    fun captureFingerprint(
        packageId: String,
        amountMinor: Long,
        timestampMs: Long,
        title: String?,
        text: String?,
        referenceId: String? = null,
        senderName: String? = null,
        captureSource: String? = null,
        notificationId: Int = NO_NOTIFICATION_ID,
        notificationTag: String? = null,
    ): String {
        val reference = referenceId?.trim()?.uppercase()
        if (!reference.isNullOrBlank()) {
            return sha256Fingerprint(
                buildString {
                    append("REF|")
                    append(reference)
                    append('|')
                    append(amountMinor)
                }
            )
        }
        val tag = notificationTag?.trim()
        if (hasStableSourceIdentity(notificationId, notificationTag)) {
            return sha256Fingerprint(
                buildString {
                    append("SRC|")
                    append(packageId)
                    append('|')
                    append(notificationId)
                    append('|')
                    append(tag.orEmpty())
                    append('|')
                    append(timestampMs)
                    append('|')
                    append(amountMinor)
                }
            )
        }
        return sha256Fingerprint(
            buildString {
                append("TXT|")
                append(packageId)
                append('|')
                append(DirectionClassifier.normalize(title.orEmpty()).trim())
                append('|')
                append(DirectionClassifier.normalize(text.orEmpty()).trim())
                append('|')
                append(timestampMs)
                append('|')
                append(amountMinor)
            }
        )
    }

    /** True when [captureFingerprint] would key on a real system event identity. */
    fun hasStableSourceIdentity(notificationId: Int, notificationTag: String?): Boolean =
        notificationId != NO_NOTIFICATION_ID || !notificationTag?.trim().isNullOrEmpty()

    /**
     * 60 hex chars → 64-char total id. The id must fit EVERY layer's cap:
     * `payment_events` RLS insert policy (`length(id) between 8 and 64`),
     * the fcm-gateway `parseEvent` check, and the employee-side
     * RemoteEventValidator. The full 64-hex digest (68 with prefix) was
     * rejected by RLS — silently killing EVERY real payment fan-out while
     * short test-event ids kept passing.
     */
    private fun sha256Fingerprint(payload: String): String =
        "evt_" + sha256Hex(payload).take(60)

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
