import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vivekray898.payvoice"
    compileSdk {
        version = release(37)
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
            // Phase-1 perf verification only: lets us install a locally AOT-compiled
            // release build with the debug key to measure real startup cost.
            signingConfig = signingConfigs.getByName("debug")
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
    description = "Copies the gitignored app/google-services.json into assets for manual FirebaseApp init."
    // Plain Files resolved at configuration time (config-cache serializable).
    val src = File(projectDir, "google-services.json")
    val dest = File(projectDir, "src/main/assets/google-services.json")
    inputs.file(src)
    outputs.file(dest)
    doLast {
        if (src.exists()) {
            dest.parentFile.mkdirs()
            Files.copy(
                src.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            println("PayVoice: copied google-services.json into assets (manual Firebase init).")
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
