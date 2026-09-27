package com.vivekray898.payvoice

import android.content.Context
import com.vivekray898.payvoice.core.announce.AnnouncementComposer
import com.vivekray898.payvoice.core.database.PayVoiceDatabase
import com.vivekray898.payvoice.core.database.RetentionCleaner
import com.vivekray898.payvoice.core.parser.PaymentParserRegistry
import com.vivekray898.payvoice.core.settings.SettingsRepository
import com.vivekray898.payvoice.core.settings.SettingsRepositoryImpl
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

    val pipeline: PaymentPipeline by lazy {
        PaymentPipeline(
            scope = applicationScope,
            settings = settings,
            parsers = parserRegistry,
            speaker = speaker,
            db = database,
        )
    }
}
