package com.vivekray898.payvoice

import android.content.Context
import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.database.PayVoiceDatabase
import com.vivekray898.payvoice.core.database.RetentionCleaner
import com.vivekray898.payvoice.core.parser.PaymentParserRegistry
import com.vivekray898.payvoice.core.remote.DeviceRole
import com.vivekray898.payvoice.core.remote.EmployeeRepository
import com.vivekray898.payvoice.core.remote.PayVoiceAuth
import com.vivekray898.payvoice.core.remote.PairingRepository
import com.vivekray898.payvoice.core.remote.RemoteEventSender
import com.vivekray898.payvoice.core.remote.DeviceRepository
import com.vivekray898.payvoice.core.remote.RemoteConfig
import com.vivekray898.payvoice.core.remote.SupabaseClient
import com.vivekray898.payvoice.core.remote.SupabaseRealtime
import com.vivekray898.payvoice.core.settings.SettingsRepository
import com.vivekray898.payvoice.core.settings.SettingsRepositoryImpl
import com.vivekray898.payvoice.service.messaging.MessagingRepository
import com.vivekray898.payvoice.service.notification.ListenerRuntimeState
import com.vivekray898.payvoice.service.tts.AnnouncementSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled DI container. Deliberately tiny: Phase 1 needs exactly one of
 * everything, lazily created. No framework, no reflection, no startup cost
 * until first use.
 */
class AppContainer(private val appContext: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }

    val database: PayVoiceDatabase by lazy { PayVoiceDatabase.build(appContext) }

    val retentionCleaner: RetentionCleaner by lazy { RetentionCleaner(database) }

    val parserRegistry: PaymentParserRegistry by lazy { PaymentParserRegistry.withDefaults() }

    val speaker: AnnouncementSpeaker by lazy { AnnouncementSpeaker(appContext, settings) }

    /**
     * Live listener binding state (grant vs connected). Application-scoped so
     * the Reliability screen, the app-startup repair and the service itself
     * all observe the SAME state.
     */
    val listenerRuntime: ListenerRuntimeState by lazy {
        ListenerRuntimeState.forThisApp(appContext)
    }

    val messaging: MessagingRepository by lazy { MessagingRepository(appContext) }

    // ---- Owner→Employee remote layer (additional path; local flow never depends on it) ----
    // Backend is Supabase (REST + edge functions). Identity/data never touch
    // Firebase; FCM is transport only, driven by the fcm-gateway function.

    val supabase: SupabaseClient by lazy {
        // Allow local (non-source) publishable-key configuration. Publishable
        // keys are public by design — this never handles secrets.
        RemoteConfig.applyResourceOverrides(appContext)
        SupabaseClient(appContext)
    }

    val auth: PayVoiceAuth by lazy { PayVoiceAuth(supabase) }

    val devices: DeviceRepository by lazy { DeviceRepository(auth, supabase) }

    val realtime: SupabaseRealtime by lazy { SupabaseRealtime() }

    val pairing: PairingRepository by lazy { PairingRepository(auth, supabase, devices, applicationScope) }

    val employees: EmployeeRepository by lazy { EmployeeRepository(auth, supabase, realtime) }

    val remoteSender: RemoteEventSender by lazy { RemoteEventSender(auth, supabase, applicationScope) }

    val pipeline: PaymentPipeline by lazy {
        PaymentPipeline(
            scope = applicationScope,
            settings = settings,
            parsers = parserRegistry,
            speaker = speaker,
            db = database,
            isDebugBuild = (appContext.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            remoteSender = remoteSender,
            roleProvider = { settings.settings.value.role },
            remoteEnabledProvider = { settings.settings.value.remoteAnnouncementsEnabled },
        )
    }
}
