package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.remote.PairingCode
import com.vivekray898.payvoice.core.remote.PairingCodeGenerator
import com.vivekray898.payvoice.core.remote.RemoteCollections
import com.vivekray898.payvoice.core.remote.RemoteEventValidator
import com.vivekray898.payvoice.core.remote.RemoteEventType
import com.vivekray898.payvoice.core.remote.RemotePaymentEvent
import com.vivekray898.payvoice.core.remote.RemoteConfig
import com.vivekray898.payvoice.core.remote.SupabaseRealtime
import com.vivekray898.payvoice.core.remote.toPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Remote-layer unit tests (spec §30): pairing code generation, payload
 * validation, wire-format constraints, and the announcement wording contract
 * for remote events.
 */
class RemoteLayerTest {

    private val now = 1_790_000_000_000L

    // ---- Pairing codes (spec §4, §17) ----

    @Test
    fun `pairing code format and expiry`() {
        val pc = PairingCodeGenerator.generate(now)
        assertTrue(pc.code.startsWith("PAY-"))
        assertEquals(10, pc.code.length) // PAY-XXXXXX
        assertEquals(now + PairingCodeGenerator.TTL_MS, pc.expiresAtMs)
        assertTrue(PairingCodeGenerator.TTL_MS in 5 * 60_000L..10 * 60_000L) // 5-10 min window
    }

    @Test
    fun `pairing codes are crypto-random and non-repeating`() {
        val codes = (1..200).map { PairingCodeGenerator.generate(now).code }
        assertEquals(200, codes.toSet().size)
        // Unambiguous alphabet only (no 0/O/1/I/L after the prefix)
        codes.forEach { c ->
            val body = c.removePrefix("PAY-")
            assertTrue(body.none { it in "01ILO" })
        }
    }

    @Test
    fun `code regex accepts display form and rejects garbage`() {
        assertTrue(PairingCodeGenerator.CODE_REGEX.matches("PAY-7K4M92"))
        assertFalse(PairingCodeGenerator.CODE_REGEX.matches("PAY-0O1IL"))
        assertFalse(PairingCodeGenerator.CODE_REGEX.matches("7K4M92"))
        assertFalse(PairingCodeGenerator.CODE_REGEX.matches("PAY-7K4M9"))
    }

    // ---- Payload validation (spec §30: invalid / missing amount / bad id) ----

    private fun paymentData(
        eventId: String = "evt_abc123def456",
        amount: String = "100000",
        sender: String? = "Ms Usha Das",
        timestamp: Long = now,
    ): Map<String, String> = buildMap {
        put(RemotePaymentEvent.KEY_TYPE, "PAYMENT_RECEIVED")
        put(RemotePaymentEvent.KEY_EVENT_ID, eventId)
        put(RemotePaymentEvent.KEY_AMOUNT, amount)
        put(RemotePaymentEvent.KEY_CURRENCY, "INR")
        sender?.let { put(RemotePaymentEvent.KEY_SENDER, it) }
        put(RemotePaymentEvent.KEY_SOURCE, "GOOGLE_PAY")
        put(RemotePaymentEvent.KEY_TIMESTAMP, timestamp.toString())
    }

    @Test
    fun `valid payment payload passes`() {
        val e = RemoteEventValidator.validate(paymentData(), now)
        assertNotNull(e)
        assertEquals(100_000L, e!!.amountMinor)
        assertEquals("Ms Usha Das", e.senderName)
        assertEquals(RemoteEventType.PAYMENT_RECEIVED, e.type)
    }

    @Test
    fun `missing amount is rejected`() {
        val d = paymentData().minus(RemotePaymentEvent.KEY_AMOUNT)
        assertNull(RemoteEventValidator.validate(d, now))
    }

    @Test
    fun `non-numeric or non-positive amount rejected`() {
        assertNull(RemoteEventValidator.validate(paymentData(amount = "abc"), now))
        assertNull(RemoteEventValidator.validate(paymentData(amount = "0"), now))
        assertNull(RemoteEventValidator.validate(paymentData(amount = "-500"), now))
    }

    @Test
    fun `invalid event id rejected`() {
        assertNull(RemoteEventValidator.validate(paymentData(eventId = "garbage"), now))
        assertNull(RemoteEventValidator.validate(paymentData(eventId = ""), now))
        val oversizedId = "evt_" + "x".repeat(100)
        assertNull(RemoteEventValidator.validate(paymentData(eventId = oversizedId), now))
    }

    @Test
    fun `non-INR currency and stale or future timestamps rejected`() {
        val d = paymentData().toMutableMap()
        d[RemotePaymentEvent.KEY_CURRENCY] = "USD"
        assertNull(RemoteEventValidator.validate(d, now))

        assertNull(RemoteEventValidator.validate(paymentData(timestamp = now - 25 * 3600_000L), now))
        assertNull(RemoteEventValidator.validate(paymentData(timestamp = now + 10 * 60_000L), now))
    }

    @Test
    fun `missing sender is tolerated as amount-only`() {
        val e = RemoteEventValidator.validate(paymentData(sender = null), now)
        assertNotNull(e)
        assertNull(e!!.senderName)
    }

    @Test
    fun `oversized sender is dropped to amount-only`() {
        val e = RemoteEventValidator.validate(paymentData(sender = "S".repeat(80)), now)
        assertNotNull(e)
        assertNull(e!!.senderName)
    }

    @Test
    fun `oversized payload rejected`() {
        val d = paymentData().toMutableMap()
        repeat(20) { d.putIfAbsent("junk$it", "x") }
        assertNull(RemoteEventValidator.validate(d, now))
    }

    // ---- Test events (spec §22): never amounts, never payment wording ----

    @Test
    fun `test event carries no amount and validates`() {
        val d = mapOf(
            RemotePaymentEvent.KEY_TYPE to "TEST_ANNOUNCEMENT",
            RemotePaymentEvent.KEY_EVENT_ID to "evt_test0001",
        )
        val e = RemoteEventValidator.validate(d, now)
        assertNotNull(e)
        assertEquals(RemoteEventType.TEST_ANNOUNCEMENT, e!!.type)
        assertEquals(0L, e.amountMinor)

        // A test event carrying an amount field is malformed.
        assertNull(
            RemoteEventValidator.validate(
                d + (RemotePaymentEvent.KEY_AMOUNT to "100000"), now,
            )
        )
    }

    // ---- Wire format (spec §10): compact, no raw content ----

    @Test
    fun `data map is compact and complete`() {
        val e = RemotePaymentEvent(
            eventId = "evt_abc123", type = RemoteEventType.PAYMENT_RECEIVED,
            amountMinor = 100_000, currency = "INR", senderName = "Ms Usha Das",
            source = "GOOGLE_PAY", timestampMs = now,
        )
        val map = RemotePaymentEvent.toDataMap(e)
        assertEquals(7, map.size)
        assertTrue(map.values.all { it.length <= 40 })
    }

    @Test
    fun `test event data map omits amount fields`() {
        val e = RemotePaymentEvent(
            eventId = "evt_t1", type = RemoteEventType.TEST_ANNOUNCEMENT,
            amountMinor = 0, currency = "INR", senderName = null,
            source = "TEST", timestampMs = now,
        )
        val map = RemotePaymentEvent.toDataMap(e)
        assertTrue(!map.containsKey(RemotePaymentEvent.KEY_AMOUNT))
    }

    // ---- Announcement contract for remote events (spec §6, §11, §22) ----

    @Test
    fun `remote payment with sender uses deterministic sms format`() {
        val text = com.vivekray898.payvoice.core.announce.AnnouncementComposer
            .composeSms(100_000, "Ms Usha Das")
        assertEquals("Received 1000 rupees from Ms Usha Das.", text)
    }

    @Test
    fun `remote payment without sender is amount-only - never unknown user`() {
        val text = com.vivekray898.payvoice.core.announce.AnnouncementComposer
            .composeSms(100_000, null)
        assertEquals("Received 1000 rupees.", text)
        assertTrue(
            !text.contains("unknown", ignoreCase = true) &&
                !text.contains("someone", ignoreCase = true)
        )
    }

    @Test
    fun `test announcement wording is fixed and never an amount`() {
        val en = com.vivekray898.payvoice.core.announce.AnnouncementComposer.TEST_ANNOUNCEMENT_EN
        assertTrue(en.contains("test"))
        assertTrue(!en.any { it.isDigit() })
    }

    @Test
    fun `new event ids are unique and prefixed`() {
        val ids = (1..100).map { RemoteCollections.newEventId() }
        assertEquals(100, ids.toSet().size)
        ids.forEach { assertTrue(it.startsWith("evt_")) }
    }

    // ---- Supabase wire format (payment_events insert payload) ----

    @Test
    fun `payment payload maps to snake_case supabase row`() {
        val e = RemotePaymentEvent(
            eventId = "evt_abc123def456", type = RemoteEventType.PAYMENT_RECEIVED,
            amountMinor = 100_000, currency = "INR", senderName = "Ms Usha Das",
            source = "SMS_KOTAK", timestampMs = now,
        )
        val payload = e.toPayload("owner-uid-1", 123L).toString()
        assertTrue(payload.contains("\"id\":\"evt_abc123def456\""))
        assertTrue(payload.contains("\"owner_uid\":\"owner-uid-1\""))
        assertTrue(payload.contains("\"amount_minor\":100000"))
        assertTrue(payload.contains("\"currency\":\"INR\""))
        assertTrue(payload.contains("\"sender_name\":\"Ms Usha Das\""))
        assertTrue(payload.contains("\"source\":\"SMS_KOTAK\""))
        assertTrue(payload.contains("\"timestamp_ms\":$now"))
        assertTrue(payload.contains("\"local_tts_requested_at_ms\":123"))
        // Never raw content: no account numbers, refs, URLs, or SMS bodies.
        assertTrue(!payload.contains("XX"))
    }

    @Test
    fun `test payload omits amount fields like the fcm wire format`() {
        val e = RemotePaymentEvent(
            eventId = "evt_t1", type = RemoteEventType.TEST_ANNOUNCEMENT,
            amountMinor = 0, currency = "INR", senderName = null,
            source = "TEST", timestampMs = now,
        )
        val payload = e.toPayload("owner-uid-1", null).toString()
        assertTrue(!payload.contains("amount_minor"))
        assertTrue(!payload.contains("currency"))
        assertTrue(!payload.contains("sender_name"))
        assertTrue(!payload.contains("timestamp_ms"))
    }

    @Test
    fun `supabase config uses the current publishable key system only`() {
        // Current system: URL + sb_publishable_ key. No legacy anon/
        // service_role JWT keys, no secret key, no FCM server key anywhere.
        val fields = RemoteConfig::class.java.declaredFields.map { it.name }
        assertTrue("SUPABASE_URL" in fields)
        assertTrue("SUPABASE_PUBLISHABLE_KEY" in fields)
        assertTrue(fields.none {
            it.contains("ANON", ignoreCase = true) ||
                it.contains("SERVICE", ignoreCase = true) ||
                it.contains("SECRET", ignoreCase = true) ||
                it.contains("SERVER_KEY", ignoreCase = true)
        })
    }

    @Test
    fun `publishable key must carry the current prefix and reject legacy keys`() {
        assertEquals("sb_publishable_", RemoteConfig.publishableKey().take("sb_publishable_".length))
        RemoteConfig.publishableKeyOverride = "eyJhbGciOiJIUzI1NiJ9.legacy"
        try {
            runCatching { RemoteConfig.publishableKey() }.onSuccess {
                throw AssertionError("legacy eyJ key accepted")
            }
        } finally {
            RemoteConfig.publishableKeyOverride = null
        }
    }

    @Test
    fun `realtime filter json matches the postgres_changes contract`() {
        val filter = SupabaseRealtime.RealtimeFilter(
            event = "*", table = "employees", filter = "owner_id=eq.abc",
        )
        val json = filter.toJson().toString()
        assertTrue(json.contains("\"event\":\"*\""))
        assertTrue(json.contains("\"schema\":\"public\""))
        assertTrue(json.contains("\"table\":\"employees\""))
        assertTrue(json.contains("\"filter\":\"owner_id=eq.abc\""))
    }

    private fun assertFalse(b: Boolean) = org.junit.Assert.assertFalse(b)
}
