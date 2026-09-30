package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.remote.RemoteEventValidator
import com.vivekray898.payvoice.core.remote.RemotePaymentEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the payment_events id shape (remote-delivery fix,
 * 2026-09-30): RLS (0001), the fcm-gateway parseEvent, and the employee
 * RemoteEventValidator ALL require `evt_`-prefixed ids of 8..64 chars. The
 * owner path previously sent the raw dedup fingerprint (64-hex, no prefix)
 * and every fan-out insert failed RLS — remote delivery was dead.
 */
class RemoteEventIdContractTest {

    private val fingerprint64 =
        "9f2b1c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809"

    @Test
    fun `raw fingerprint gets evt_ prefix and stays within backend limits`() {
        val id = RemoteEventValidator.prefixEventId(fingerprint64)
        assertTrue(id.startsWith("evt_"))
        assertTrue("id must be <= 64 chars, was ${id.length}", id.length in 8..64)
        // evt_ + the first 60 hex chars of the fingerprint (deterministic cut).
        assertEquals("evt_" + fingerprint64.take(60), id)
    }

    @Test
    fun `prefixing is idempotent and deterministic`() {
        val once = RemoteEventValidator.prefixEventId(fingerprint64)
        assertEquals(once, RemoteEventValidator.prefixEventId(once))
        assertEquals(once, RemoteEventValidator.prefixEventId(fingerprint64))
    }

    @Test
    fun `prefixed id passes the employee-side validator`() {
        val prefixed = RemoteEventValidator.prefixEventId(fingerprint64)
        // FCM data keys are camelCase (RemotePaymentEvent.KEY_*).
        val payload = mapOf(
            "eventId" to prefixed,
            "type" to "PAYMENT_RECEIVED",
            "amountMinor" to "500",
            "currency" to "INR",
            "timestampMs" to System.currentTimeMillis().toString(),
            "ownerUid" to "owner-uid",
        )
        val event: RemotePaymentEvent? = RemoteEventValidator.validate(payload)
        assertEquals(prefixed, event?.eventId)
    }
}
