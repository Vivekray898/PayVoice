package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.model.KnownPackages
import com.vivekray898.payvoice.core.model.PaymentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reliability fix 1: GPay notification package variants. The whitelist must
 * keep matching the canonical package and also resolve OEM/regional variants,
 * while never admitting bank-app or unrelated packages.
 */
class KnownPackagesTest {

    @Test
    fun `canonical gpay package matches exactly`() {
        assertTrue(KnownPackages.isGooglePayPackage("com.google.android.apps.nbu.paisa.user"))
        assertEquals(
            PaymentSource.GOOGLE_PAY,
            PaymentSource.fromPackage("com.google.android.apps.nbu.paisa.user"),
        )
    }

    @Test
    fun `regional variant resolves to GOOGLE_PAY`() {
        val variant = "com.google.android.apps.nbu.paisa.user.india"
        assertTrue(KnownPackages.isGooglePayPackage(variant))
        assertEquals(PaymentSource.GOOGLE_PAY, PaymentSource.fromPackage(variant))
    }

    @Test
    fun `suffix variants resolve so future regional flavors keep working`() {
        assertTrue(KnownPackages.isGooglePayPackage("com.google.android.apps.nbu.paisa.user.xyz"))
    }

    @Test
    fun `unrelated packages never match`() {
        assertFalse(KnownPackages.isGooglePayPackage("com.kotak811"))
        assertFalse(KnownPackages.isGooglePayPackage("com.whatsapp"))
        assertFalse(
            KnownPackages.isGooglePayPackage("com.google.android.apps.nbu.paisa.userimposter"),
        )
        assertEquals(null, PaymentSource.fromPackage("com.kotak811"))
        assertEquals(null, PaymentSource.fromPackage(""))
    }

    @Test
    fun `similar-prefix foreign packages never match`() {
        // A package that merely CONTAINS the name must not match (suffix-only rule).
        assertFalse(
            KnownPackages.isGooglePayPackage("evil.com.google.android.apps.nbu.paisa.user"),
        )
    }
}
