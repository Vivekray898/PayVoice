package com.vivekray898.payvoice.ui.components

import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves [PvSecureWindow] actually sets FLAG_SECURE while composed and puts
 * the window back the way it found it afterwards.
 *
 * The pairing code is a live single-use credential, and FLAG_SECURE is the
 * only thing keeping it out of Recents thumbnails, screenshots and screen
 * recordings — so this needs a real Window and a real assertion rather than an
 * inspection of the source.
 *
 * Deliberately avoids `createAndroidComposeRule`: its idle sync goes through
 * Espresso, and Espresso 3.5.1 cannot initialize against API 36
 * (`NoSuchMethodException: InputManager.getInstance`), which fails the test
 * before any assertion runs. `ActivityScenario` gives the same real Window
 * without that dependency.
 */
@RunWith(AndroidJUnit4::class)
class PvSecureWindowTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun secureFlag(window: Window): Int =
        window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE

    /**
     * Reads the flag from the test thread, pumping the main looper first.
     *
     * Compose disposes effects on the frame clock, which is not the same thing
     * as the looper being idle, so this retries rather than sampling once.
     */
    private fun awaitSecureFlag(window: Window, expected: Int) {
        repeat(60) {
            instrumentation.runOnMainSync { }
            if (secureFlag(window) == expected) return
            Thread.sleep(50)
        }
    }

    @Test
    fun setsSecureFlagWhileComposedAndRestoresOnDispose() {
        lateinit var window: Window
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                window = activity.window
                assertEquals(
                    "precondition: the host window must start unsecured",
                    0,
                    secureFlag(window),
                )

                // Composing the effect must secure the window.
                activity.setContent {
                    PvSecureWindow()
                    Text("pairing code goes here")
                }
            }
            instrumentation.waitForIdleSync()
            awaitSecureFlag(window, expected = WindowManager.LayoutParams.FLAG_SECURE)

            assertTrue(
                "FLAG_SECURE must be set while a secure screen is composed",
                secureFlag(window) != 0,
            )

            // Leaving composition must restore the previous value, otherwise
            // securing one screen leaves the whole app blacked out for the rest
            // of the session.
            scenario.onActivity { it.setContent { Text("back to a normal screen") } }
            awaitSecureFlag(window, expected = 0)

            assertEquals(
                "FLAG_SECURE must be cleared when the secure screen leaves",
                0,
                secureFlag(window),
            )
        }
    }
}
