package com.vivekray898.payvoice.core.remote

import com.vivekray898.payvoice.core.model.KnownPackages
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.util.UUID

/**
 * Device role (spec §1). UNSET until the user chooses during onboarding;
 * settings persist it. Owner detects payments locally; Employee receives
 * events remotely via FCM and announces them with the same TTS engine.
 */
enum class DeviceRole(val label: String) {
    UNSET("Not set"),
    OWNER("Owner"),
    EMPLOYEE("Employee"),
}

/** Remote event types. TEST_ANNOUNCEMENT must never be spoken as a payment. */
enum class RemoteEventType { PAYMENT_RECEIVED, TEST_ANNOUNCEMENT }

/** Employee device record as seen by the Owner (Firestore `employees/{uid}`). */
data class EmployeeDevice(
    val uid: String,
    val name: String,
    val status: String, // ACTIVE | REVOKED
    val pairedAtMs: Long,
    val lastSeenAtMs: Long,
    val lastEventDeliveredAtMs: Long?,
) {
    val isActive: Boolean get() = status == "ACTIVE"
    val isConnected: Boolean get() = isActive
}

/** A pairing invitation created by the Owner. */
data class PairingCode(
    val code: String,
    val expiresAtMs: Long,
)

/**
 * Pure, testable pairing-code generation (spec §4/§17): cryptographically
 * random, unambiguous alphabet, `PAY-XXXXXX` display form. The code carries
 * NO identity/secret material — it is only a lookup key validated
 * server-side with an expiry and single-use flag.
 */
object PairingCodeGenerator {

    // No 0/O/1/I/L — avoids transcription errors when read aloud.
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val CODE_LENGTH = 6

    /** Display-form acceptance pattern: PAY-XXXXXX (dash optional). */
    val CODE_REGEX = Regex("^PAY-[2-9A-HJ-NP-Z]{6}$")

    private val random = SecureRandom()

    fun generate(nowMs: Long = System.currentTimeMillis(), ttlMs: Long = TTL_MS): PairingCode {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) { sb.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return PairingCode(code = "PAY-$sb", expiresAtMs = nowMs + ttlMs)
    }

    const val TTL_MS = 10 * 60_000L
}

/**
 * Wire format for the FCM data payload and the Supabase `payment_events`
 * row. Contains ONLY announcement-required fields (spec §6/§10): never
 * account numbers, balances, SMS URLs, or raw notification/SMS text.
 */
data class RemotePaymentEvent(
    val eventId: String,
    val type: RemoteEventType,
    val amountMinor: Long,
    val currency: String,
    val senderName: String?,
    val source: String,
    val timestampMs: Long,
) {
    companion object {
        const val KEY_TYPE = "type"
        const val KEY_EVENT_ID = "eventId"
        // Canonical FCM data keys — MUST match the fcm-gateway payload
        // (amountMinor/timestampMs, per RemoteConfig.FCM_KEY_* and the spec
        // payload). The old "amount"/"timestamp" names made the employee
        // validator drop every remote payment as an invalid payload.
        const val KEY_AMOUNT = "amountMinor"
        const val KEY_CURRENCY = "currency"
        const val KEY_SENDER = "senderName"
        const val KEY_SOURCE = "source"
        const val KEY_TIMESTAMP = "timestampMs"

        /** Builds the canonical FCM/Firestore data map. Compact by design. */
        fun toDataMap(event: RemotePaymentEvent): Map<String, String> = buildMap {
            put(KEY_TYPE, event.type.name)
            put(KEY_EVENT_ID, event.eventId)
            if (event.type == RemoteEventType.PAYMENT_RECEIVED) {
                put(KEY_AMOUNT, event.amountMinor.toString())
                put(KEY_CURRENCY, event.currency)
                event.senderName?.takeIf { it.isNotBlank() }?.let { put(KEY_SENDER, it.take(MAX_SENDER_LEN)) }
                put(KEY_SOURCE, event.source.take(24))
                put(KEY_TIMESTAMP, event.timestampMs.toString())
            }
        }

        const val MAX_SENDER_LEN = 40
    }
}

/**
 * Payload validation for messages arriving on the employee device (spec §30:
 * invalid payload / missing amount / invalid event id are rejected BEFORE any
 * announcement). Pure and unit-tested.
 */
object RemoteEventValidator {

    private const val EVENT_ID_PREFIX = "evt_"
    private const val MAX_FIELD_LEN = 64
    private const val MAX_AMOUNT_MINOR = 100_000_000_000L // ₹1e9 guard
    private const val MAX_AGE_MS = 24 * 60 * 60_000L
    private const val MAX_FUTURE_SKEW_MS = 5 * 60_000L

    fun validate(data: Map<String, String>, nowMs: Long = System.currentTimeMillis()): RemotePaymentEvent? {
        if (data.size > 16) return null
        val id = data[RemotePaymentEvent.KEY_EVENT_ID] ?: return null
        if (!id.startsWith(EVENT_ID_PREFIX) || id.length !in 8..MAX_FIELD_LEN) return null
        val type = data[RemotePaymentEvent.KEY_TYPE]?.let { t ->
            runCatching { RemoteEventType.valueOf(t) }.getOrNull()
        } ?: return null

        if (type == RemoteEventType.TEST_ANNOUNCEMENT) {
            // Test events carry no amount fields; anything else is malformed.
            return if (data.containsKey(RemotePaymentEvent.KEY_AMOUNT)) {
                null
            } else {
                RemotePaymentEvent(
                    eventId = id, type = type, amountMinor = 0, currency = "INR",
                    senderName = null, source = "TEST", timestampMs = nowMs,
                )
            }
        }

        val amount = data[RemotePaymentEvent.KEY_AMOUNT]?.toLongOrNull() ?: return null
        if (amount <= 0 || amount > MAX_AMOUNT_MINOR) return null
        val currency = data[RemotePaymentEvent.KEY_CURRENCY] ?: return null
        if (currency != "INR") return null
        val timestamp = data[RemotePaymentEvent.KEY_TIMESTAMP]?.toLongOrNull() ?: return null
        if (timestamp > nowMs + MAX_FUTURE_SKEW_MS || timestamp < nowMs - MAX_AGE_MS) return null
        val sender = data[RemotePaymentEvent.KEY_SENDER]?.takeIf { it.isNotBlank() }?.let {
            if (it.length <= MAX_FIELD_LEN) it else null
        } ?: return RemotePaymentEvent(
            eventId = id, type = type, amountMinor = amount, currency = currency,
            senderName = null, source = data[RemotePaymentEvent.KEY_SOURCE]
                ?.takeIf { s -> s.isNotEmpty() }?.take(24) ?: KnownPackages.GOOGLE_PAY,
            timestampMs = timestamp,
        )
        return RemotePaymentEvent(
            eventId = id, type = type, amountMinor = amount, currency = currency,
            senderName = sender, source = data[RemotePaymentEvent.KEY_SOURCE]
                ?.takeIf { s -> s.isNotEmpty() }?.take(24) ?: KnownPackages.GOOGLE_PAY,
            timestampMs = timestamp,
        )
    }
}

/** Backend tables — centralized so SQL/RLS and client agree. */
object RemoteCollections {
    const val PAIRING_CODES = "pairing_codes"
    const val EMPLOYEES = "employees"
    const val PAYMENT_EVENTS = "payment_events"
    const val DEVICES = "devices"

    fun newEventId(): String = "evt_" + UUID.randomUUID().toString().replace("-", "").take(20)
}

/** Builds the `payment_events` insert payload (snake_case for PostgREST). */
fun RemotePaymentEvent.toPayload(
    ownerUid: String,
    localTtsRequestedAtMs: Long?,
): kotlinx.serialization.json.JsonObject {
    val base = RemotePaymentEvent.toDataMap(this)
    return kotlinx.serialization.json.buildJsonObject {
        put("id", eventId)
        put("owner_uid", ownerUid)
        put("type", type.name)
        if (type == RemoteEventType.PAYMENT_RECEIVED) {
            put("amount_minor", amountMinor)
            put("currency", currency)
            senderName?.takeIf { it.isNotBlank() }?.let {
                put("sender_name", it.take(RemotePaymentEvent.MAX_SENDER_LEN))
            }
            put("source", source.take(24))
            put("timestamp_ms", timestampMs)
        }
        localTtsRequestedAtMs?.let { put("local_tts_requested_at_ms", it) }
    }
}
