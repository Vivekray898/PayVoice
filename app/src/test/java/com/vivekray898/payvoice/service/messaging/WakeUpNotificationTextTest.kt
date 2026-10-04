package com.vivekray898.payvoice.service.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The employee's wake-up notification wording.
 *
 * The defect this pins: the employee leg posted a fixed
 * "Payment announcement incoming" no matter what, so an employee who had
 * opted IN to lock-screen details still saw no amount while the owner's phone
 * showed one — and opting OUT only downgraded visibility, leaving the amount in
 * the notification body.
 */
class WakeUpNotificationTextTest {

    private val announcement = "Payment of 500 rupees received from Ravi"

    @Test
    fun `details on shows the real announcement`() {
        assertEquals(announcement, wakeUpNotificationText(announcement, showDetails = true))
    }

    @Test
    fun `details off falls back to the generic wording`() {
        val text = wakeUpNotificationText(announcement, showDetails = false)
        assertEquals("Payment announcement incoming", text)
    }

    @Test
    fun `details off leaks neither amount nor sender`() {
        val text = wakeUpNotificationText(announcement, showDetails = false)
        assertFalse(text.contains("500"))
        assertFalse(text.lowercase().contains("ravi"))
    }

    @Test
    fun `generic wording still announces something`() {
        // It must not be empty: the notification exists to wake the device so
        // TTS can run, and an empty body is a worse receipt than a vague one.
        assertTrue(wakeUpNotificationText(announcement, showDetails = false).isNotBlank())
    }
}