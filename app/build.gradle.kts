import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Release signing config.
//
// keystore.properties lives at the repo root and is GITIGNORED — it holds the
// plaintext password for the release keystore. If the file is missing (fresh
// clone, CI without secrets), release falls back to the debug key so the build
// still succeeds; only the signing identity changes.
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.vivekray898.payvoice"
    compileSdk {
        version = release(37)
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.vivekray898.payvoice"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-phase1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 + resource shrinking land in Phase 6 (hardening), after the full
            // feature set exists. Release stays unminified for now.
            optimization {
                enable = false
            }
            // Sign with the real release keystore when keystore.properties exists;
            // otherwise fall back to the debug key so a fresh clone / CI without
            // secrets can still `assembleRelease` for perf verification.
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// ---------------------------------------------------------------------------
// Manual Firebase init support (NO google-services plugin, per AGENTS.md).
//
// The Firebase CLIENT config lives at the repo-root-adjacent path
// `app/google-services.json`, which is GITIGNORED (client config only —
// never any server credential; the service-account JSON is a different
// file and must never be placed here). This task copies it into
// src/main/assets at configuration time so manual FirebaseApp
// initialization finds it in BOTH debug and release APKs. Without it the
// APK ships without the asset and FCM registration fails on direct-APK
// installs while wireless-debug installs (which had it via a previous
// local copy) appeared to work.
// ---------------------------------------------------------------------------
tasks.register("copyGoogleServicesJson") {
    group = "setup"
    description = "Copies the gitignored app/google-services.json into assets for manual FirebaseApp init, and generates the google_app_id resource Analytics upload requires."
    // Plain Files resolved at configuration time (config-cache serializable).
    val src = File(projectDir, "google-services.json")
    val dest = File(projectDir, "src/main/assets/google-services.json")
    val faRes = File(projectDir, "src/main/res/values/payvoice_firebase.xml")
    inputs.file(src)
    outputs.file(dest)
    outputs.file(faRes)
    doLast {
        if (src.exists()) {
            dest.parentFile.mkdirs()
            Files.copy(
                src.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            println("PayVoice: copied google-services.json into assets (manual Firebase init).")
            // Analytics upload (manual init, NO google-services plugin): the
            // GMS measurement SERVICE reads the client's `google_app_id`
            // string resource — without it the service refuses uploads
            // ("Uploading is not possible. App measurement disabled").
            // Generated from the same public client config (an app id is
            // NOT a credential). Absent JSON → no resource → Analytics
            // degrades silently, matching the asset-absent behavior.
            try {
                val root = groovy.json.JsonSlurper().parse(src) as Map<*, *>
                val client = (root["client"] as List<*>).first() as Map<*, *>
                val info = client["client_info"] as Map<*, *>
                val projectInfo = root["project_info"] as Map<*, *>
                val appId = info["mobilesdk_app_id"] as String
                val firstKey = (client["api_key"] as List<*>).first() as Map<*, *>
                val apiKey = firstKey["current_key"] as String
                val projectId = projectInfo["project_id"] as String
                val senderId = projectInfo["project_number"] as String
                val bucket = projectInfo["storage_bucket"] as String
                // FULL plugin-equivalent set: with only google_app_id present,
                // FirebaseInitProvider's auto-init registers an INCOMPLETE
                // [DEFAULT] (no project id) BEFORE Application.onCreate — the
                // manual-init guard then skips and FIS/FCM fails with
                // "Please set your Project ID" (observed). Generating the same
                // values the google-services plugin would makes the provider
                // init complete and identical to plugin builds.
                faRes.parentFile.mkdirs()
                Files.writeString(
                    faRes.toPath(),
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<!-- Generated by copyGoogleServicesJson — do not edit. -->\n" +
                        "<resources>\n" +
                        "    <string name=\"google_app_id\">$appId</string>\n" +
                        "    <string name=\"google_api_key\">$apiKey</string>\n" +
                        "    <string name=\"project_id\">$projectId</string>\n" +
                        "    <string name=\"gcm_defaultSenderId\">$senderId</string>\n" +
                        "    <string name=\"google_storage_bucket\">$bucket</string>\n" +
                        "</resources>\n",
                )
                println("PayVoice: generated Firebase resource values (Analytics upload + complete provider init).")
            } catch (e: Exception) {
                println("PayVoice WARNING: could not generate Firebase resource values — Analytics upload will be disabled (${e.javaClass.simpleName}).")
            }
        } else {
            // Honest failure: the build succeeds but the asset stays absent;
            // the app degrades gracefully (no remote layer) and logs it.
            println("PayVoice WARNING: app/google-services.json not found — FCM receive will be unavailable.")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("copyGoogleServicesJson")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // User-approved (2026-09-30): full Material icon set for Group/Link etc.
    // NOTE: R8 is intentionally OFF this release, so the whole artifact
    // ships — APK size grows by tens of MB; enabling R8 later strips it.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // FCM RECEIVE ONLY (no google-services plugin): PayVoiceMessagingService
    // gets token + data messages for the Supabase edge-function gateway.
    // Identity + data live in Supabase (REST); only FCM transport remains.
    implementation(libs.firebase.messaging)
    // Analytics (user-approved scope): structural-only events, catalog in
    // docs/ANALYTICS.md, DISABLED in debug builds at init. The BOM supplies
    // the aligned analytics version; the explicit firebase-messaging pin
    // above still overrides the BOM so the FCM path cannot drift.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    // Supabase: hand-rolled REST + Realtime clients on kotlinx-serialization
    // and OkHttp (current sb_publishable_ key system; no Supabase SDK dep).
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation("androidx.security:security-crypto:1.1.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}