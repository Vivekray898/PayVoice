package com.vivekray898.payvoice

import com.vivekray898.payvoice.core.health.AppHealthChecker
import com.vivekray898.payvoice.core.health.ChannelImportance
import com.vivekray898.payvoice.core.health.DefaultAppHealthChecker
import com.vivekray898.payvoice.core.health.HealthIds
import com.vivekray898.payvoice.core.health.HealthLevel
import com.vivekray898.payvoice.core.health.HealthRequest
import com.vivekray898.payvoice.core.health.HealthSources
import com.vivekray898.payvoice.core.health.HealthSummary
import com.vivekray898.payvoice.core.health.InAppAction
import com.vivekray898.payvoice.core.health.SettingsAction
import com.vivekray898.payvoice.core.remote.DeviceRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision table behind the Home health banner.
 *
 * Everything is driven through [FakeSources], so this is a pure JVM test: no
 * Robolectric, no emulator, no Android SDK on the classpath.
 */
class AppHealthCheckerTest {

    /** A phone in perfect health; each test flips only what it is about. */
    private class FakeSources : HealthSources {
        var hasNotifications = true
        var channelImportance = ChannelImportance.HIGH
        var dndOn = false
        var exempt = true
        var hasListener = true
        var hasTts = true
        var hasVoice = true
        var volumeLevel = 3
        var maxVolume = 7
        var isOnline = true
        var hasFcm = true
        var hasSession = true
        var hasPlayServices = true
        var hasPaymentApp = true
        var oemKills = false

        override fun notificationsEnabled() = hasNotifications
        override fun runtimeNotificationPermission() = "android.permission.POST_NOTIFICATIONS"
        override fun paymentChannelImportance() = channelImportance
        override fun doNotDisturbActive() = dndOn
        override fun batteryExempt() = exempt
        override fun listenerAccessGranted() = hasListener
        override fun ttsEngineInstalled() = hasTts
        override fun ttsVoiceAvailable(languageTag: String) = hasVoice
        override fun notificationVolume() = volumeLevel
        override fun maxNotificationVolume() = maxVolume
        override fun online() = isOnline
        override fun fcmTokenRegistered() = hasFcm
        override fun supabaseSessionValid() = hasSession
        override fun playServicesAvailable() = hasPlayServices
        override fun paymentAppInstalled() = hasPaymentApp
        override fun oemNeedsAutostartHint() = oemKills
    }

    private val owner = HealthRequest(DeviceRole.OWNER, "en-IN", paired = true)
    private val employeeUnpaired = HealthRequest(DeviceRole.EMPLOYEE, "hi-IN", paired = false)

    private fun check(sources: FakeSources = FakeSources(), request: HealthRequest = owner) =
        runBlocking { DefaultAppHealthChecker(sources).check(request) }

    private fun ids(items: List<com.vivekray898.payvoice.core.health.HealthItem>) =
        items.map { it.id }

    // ---- Healthy ----------------------------------------------------------

    @Test
    fun `healthy owner phone reports the full checklist and nothing needs attention`() {
        val items = check()
        // The checker returns EVERY check with its status (OK/WARN/BLOCKED),
        // so Settings can show a complete list rather than only the failures.
        assertTrue(items.isNotEmpty())
        assertTrue(items.none { it.needsAttention })
        assertTrue(items.none { it.fixLabel != null })
        assertTrue(HealthSummary(items).healthy)
    }

    @Test
    fun `healthy paired employee reports the full checklist with no problems`() {
        val req = HealthRequest(DeviceRole.EMPLOYEE, "hi-IN", paired = true)
        val items = check(request = req)
        assertTrue(items.none { it.needsAttention })
        assertTrue(HealthSummary(items).healthy)
    }

    @Test
    fun `every check carries a stable id and both sentences`() {
        check().forEach { item ->
            assertTrue("empty id for ${item.title}", item.id.isNotBlank())
            assertTrue("missing title res for ${item.id}", item.title != 0)
            assertTrue("missing why res for ${item.id}", item.why != 0)
        }
        assertEquals(
            "ids must be unique so LazyColumn keys do not collide",
            check().size,
            check().map { it.id }.distinct().size,
        )
    }

    // ---- Notifications ----------------------------------------------------

    @Test
    fun `notifications off is blocking and offers the runtime permission`() {
        val item = check(FakeSources().apply { hasNotifications = false })
            .single { it.id == HealthIds.NOTIFICATIONS }
        assertEquals(HealthLevel.BLOCKED, item.level)
        assertEquals(SettingsAction.POST_NOTIFICATIONS, item.settingsAction)
        // On API 33+ the fix starts with the system dialog, not a Settings page.
        assertEquals("android.permission.POST_NOTIFICATIONS", item.runtimePermission)
    }

    @Test
    fun `a lowered payment channel is a warning, not a blocker`() {
        val item = check(FakeSources().apply { channelImportance = ChannelImportance.LOW })
            .single { it.id == HealthIds.CHANNEL }
        assertEquals(HealthLevel.WARN, item.level)
        assertEquals(SettingsAction.CHANNEL_NOTIFICATIONS, item.settingsAction)
    }

    @Test
    fun `a missing payment channel counts as silent`() {
        val item = check(FakeSources().apply { channelImportance = ChannelImportance.NONE })
            .single { it.id == HealthIds.CHANNEL }
        assertEquals(HealthLevel.WARN, item.level)
    }

    @Test
    fun `no channel check is raised when notifications are already off`() {
        val items = check(
            FakeSources().apply {
                hasNotifications = false
                channelImportance = ChannelImportance.NONE
            },
        )
        assertFalse(ids(items).contains(HealthIds.CHANNEL))
    }

    // ---- Do Not Disturb ---------------------------------------------------

    @Test
    fun `do not disturb blocks announcements`() {
        val item = check(FakeSources().apply { dndOn = true }).single { it.id == HealthIds.DND }
        assertEquals(HealthLevel.BLOCKED, item.level)
        assertEquals(SettingsAction.DND_ACCESS, item.settingsAction)
    }

    // ---- Background survival ----------------------------------------------

    @Test
    fun `missing battery exemption is a warning on the play-safe list screen`() {
        val item = check(FakeSources().apply { exempt = false })
            .single { it.id == HealthIds.BATTERY }
        assertEquals(HealthLevel.WARN, item.level)
        // Play-safe: the LIST screen, never the direct exemption dialog.
        assertEquals(SettingsAction.BATTERY_OPTIMIZATION, item.settingsAction)
    }

    @Test
    fun `no autostart row on a stock device`() {
        assertFalse(ids(check(FakeSources().apply { oemKills = false })).contains(HealthIds.AUTOSTART))
    }

    @Test
    fun `autostart row appears on an oem device that kills background apps`() {
        val items = check(FakeSources().apply { oemKills = true
            exempt = false
        })
        assertTrue(ids(items).contains(HealthIds.AUTOSTART))
    }

    @Test
    fun `autostart row is quiet once the phone is already exempt`() {
        val items = check(FakeSources().apply { oemKills = true
            exempt = true
        })
        val item = items.single { it.id == HealthIds.AUTOSTART }
        // Still listed (the checklist is complete) but nothing to do.
        assertEquals(HealthLevel.OK, item.level)
        assertNull(item.fixLabel)
        assertEquals(SettingsAction.NONE, item.settingsAction)
    }

    // ---- Role split -------------------------------------------------------

    @Test
    fun `owner without notification listener access cannot capture payments`() {
        val item = check(FakeSources().apply { hasListener = false })
            .single { it.id == HealthIds.LISTENER }
        assertEquals(HealthLevel.BLOCKED, item.level)
        assertEquals(SettingsAction.NOTIFICATION_LISTENER, item.settingsAction)
    }

    @Test
    fun `employee phone is never asked for listener access`() {
        // Employees receive remote alerts; they do not read notifications.
        val req = HealthRequest(DeviceRole.EMPLOYEE, "en-IN", paired = true)
        assertFalse(ids(check(FakeSources().apply { hasListener = false }, req))
            .contains(HealthIds.LISTENER))
    }

    @Test
    fun `unpaired employee is blocked with an in-app pairing fix`() {
        val item = check(FakeSources(), employeeUnpaired).single { it.id == HealthIds.PAIRING }
        assertEquals(HealthLevel.BLOCKED, item.level)
        assertEquals(InAppAction.PAIR_DEVICE, item.inAppAction)
        assertNull(item.runtimePermission)
    }

    @Test
    fun `owner is never asked to pair`() {
        assertFalse(ids(check()).contains(HealthIds.PAIRING))
    }

    @Test
    fun `missing payment app only concerns the detecting phone`() {
        assertFalse(ids(check(FakeSources().apply { hasPaymentApp = false }, employeeUnpaired))
            .contains(HealthIds.PAYMENT_APP))
        assertTrue(ids(check(FakeSources().apply { hasPaymentApp = false }))
            .contains(HealthIds.PAYMENT_APP))
    }

    // ---- Speaking ---------------------------------------------------------

    @Test
    fun `no tts engine blocks speaking and offers to install a voice`() {
        val item = check(FakeSources().apply { hasTts = false }).single { it.id == HealthIds.TTS }
        assertEquals(HealthLevel.BLOCKED, item.level)
        assertEquals(SettingsAction.TTS_INSTALL_DATA, item.settingsAction)
    }

    @Test
    fun `missing voice data for the chosen language is reported separately`() {
        val item = check(FakeSources().apply { hasVoice = false }).single { it.id == HealthIds.VOICE }
        assertEquals(SettingsAction.TTS_INSTALL_DATA, item.settingsAction)
        // The engine itself is fine — do not also claim it is missing.
        assertFalse(ids(check(FakeSources().apply { hasVoice = false })).contains(HealthIds.TTS))
    }

    @Test
    fun `zero notification volume is blocking with a volume fix`() {
        val item = check(FakeSources().apply { volumeLevel = 0 }).single { it.id == HealthIds.VOLUME }
        assertEquals(HealthLevel.BLOCKED, item.level)
        assertEquals(SettingsAction.VOLUME, item.settingsAction)
    }

    @Test
    fun `a device with no notification stream reports no volume problem`() {
        // maxVolume 0 means the stream does not exist on this hardware.
        val items = check(FakeSources().apply { volumeLevel = 0
            maxVolume = 0
        })
        assertFalse(ids(items).contains(HealthIds.VOLUME))
    }

    // ---- Reachability -----------------------------------------------------

    @Test
    fun `offline is blocking and points at wireless settings`() {
        val item = check(FakeSources().apply { isOnline = false }).single { it.id == HealthIds.INTERNET }
        assertEquals(HealthLevel.BLOCKED, item.level)
        assertEquals(SettingsAction.WIRELESS, item.settingsAction)
    }

    @Test
    fun `missing play services is blocking but has no fix button`() {
        val item = check(FakeSources().apply { hasPlayServices = false })
            .single { it.id == HealthIds.PLAY_SERVICES }
        assertEquals(HealthLevel.BLOCKED, item.level)
        assertNull(item.fixLabel)
        assertEquals(SettingsAction.NONE, item.settingsAction)
    }

    @Test
    fun `unregistered fcm token is only a warning`() {
        val item = check(FakeSources().apply { hasFcm = false }).single { it.id == HealthIds.FCM }
        assertEquals(HealthLevel.WARN, item.level)
        assertNull(item.fixLabel)
    }

    @Test
    fun `missing session is only a warning`() {
        val item = check(FakeSources().apply { hasSession = false }).single { it.id == HealthIds.SESSION }
        assertEquals(HealthLevel.WARN, item.level)
    }

    // ---- Summary ----------------------------------------------------------

    @Test
    fun `summary counts blocked and warning rows separately`() {
        val summary = HealthSummary(check(FakeSources().apply {
            hasNotifications = false
            dndOn = true
            exempt = false
            hasFcm = false
        }))
        assertEquals(2, summary.blocked)
        assertEquals(2, summary.warnings)
        assertEquals(4, summary.attentionCount)
        assertFalse(summary.healthy)
        assertEquals(HealthLevel.BLOCKED, summary.level)
    }

    @Test
    fun `summary is healthy when nothing needs attention`() {
        val summary = HealthSummary(check())
        assertTrue(summary.healthy)
        assertEquals(HealthLevel.OK, summary.level)
        assertEquals(0, summary.attentionCount)
    }

    @Test
    fun `ordered puts problems before the healthy rows`() {
        val summary = HealthSummary(check(FakeSources().apply { dndOn = true }))
        val ordered = summary.ordered().map { it.id }
        assertEquals(HealthIds.DND, ordered.first())
        // Healthy rows still follow, so the screen can render the full list.
        assertTrue(ordered.size > 1)
    }

    @Test
    fun `an empty item list is healthy rather than alarming`() {
        val summary = HealthSummary(emptyList())
        assertTrue(summary.healthy)
        assertEquals(HealthLevel.OK, summary.level)
    }

    // ---- Contract ---------------------------------------------------------

    @Test
    fun `every fix that opens settings also carries a visible label`() {
        val broken = check(
            FakeSources().apply {
                hasNotifications = false
                dndOn = true
                exempt = false
                hasTts = false
                volumeLevel = 0
                isOnline = false
            },
        )
        broken.filter { it.settingsAction != SettingsAction.NONE }.forEach { item ->
            assertNotNullish(item.id, item.fixLabel)
        }
    }

    @Test
    fun `checker interface is what the view model consumes`() {
        val checker: AppHealthChecker = DefaultAppHealthChecker(FakeSources())
        val items = runBlocking { checker.check(owner) }
        assertTrue(items.all { it.level == HealthLevel.OK })
    }

    private fun assertNotNullish(id: String, label: Int?) {
        assertTrue("health item '$id' opens Settings but shows no button", label != null)
    }
}
