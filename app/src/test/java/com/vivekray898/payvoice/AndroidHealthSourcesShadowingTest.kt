package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.health.AndroidHealthSources
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Guards a bug class, not one bug.
 *
 * [AndroidHealthSources] takes its non-local facts as `() -> Boolean`
 * constructor properties and exposes each one as a method of the same name:
 *
 * ```kotlin
 * class AndroidHealthSources(private val fcmTokenRegistered: () -> Boolean)
 *     : HealthSources {
 *     override fun fcmTokenRegistered(): Boolean =
 *         runCatching { fcmTokenRegistered() }.getOrDefault(false)
 * }
 * ```
 *
 * Inside that body the unqualified `fcmTokenRegistered()` resolves to the
 * MEMBER FUNCTION, not to invoking the property, so it called itself. The
 * resulting StackOverflowError was swallowed by `runCatching` and reported as
 * a clean `false`, so Health permanently claimed "this device isn't registered
 * for alerts" on a device whose token had registered on every single launch.
 * Nothing crashed, no log fired, and the JVM unit tests all passed — the fake
 * sources used by [AppHealthCheckerTest] sit on the other side of the
 * interface and never touch this class.
 *
 * The fix was to rename the property. This test is the cheap part: any future
 * lambda-parameter shadowed by a same-named method fails here instead of
 * silently reporting `false` forever.
 */
class AndroidHealthSourcesShadowingTest {

    @Test
    fun `no member function shadows a constructor property`() {
        val type = AndroidHealthSources::class.java
        val propertyNames = type.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()
        val methodNames = type.declaredMethods.map { it.name }.toSet()

        val shadowed = propertyNames intersect methodNames
        assertTrue(
            "AndroidHealthSources declares a member function that shadows a " +
                "constructor property, so the body calls itself instead of the " +
                "lambda: $shadowed",
            shadowed.isEmpty(),
        )
    }

    @Test
    fun `fcm source property keeps its distinct name`() {
        val names = AndroidHealthSources::class.java.declaredFields.map { it.name }
        assertTrue(
            "The FCM predicate must not be named fcmTokenRegistered again.",
            "fcmTokenRegisteredSource" in names,
        )
    }
}