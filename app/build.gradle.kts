import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // Compose Preview Screenshot Testing — renders the app's @Preview
    // functions host-side and diffs them against committed baselines.
    alias(libs.plugins.screenshot)
}

// ---------------------------------------------------------------------------
// Release signing config.
//
// keystore.properties lives at the repo root and is GITIGNORED — it holds the
// plaintext password for the release keystore.
//
// A missing keystore must NOT silently produce a debug-signed release. The
// debug key is public and identical on every machine, so such an APK is
// installable by anyone and will refuse to update over a real release — and
// nothing about it looks wrong until the day it ships. Instead the release
// tasks fail, and the fallback survives only as an explicit opt-in for local
// performance measurement:
//
//     ./gradlew assembleRelease -PallowDebugSignedRelease=true
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
// Read through providers.fileContents rather than File.exists() so the
// configuration cache tracks the file: a plain exists() check is invisible to
// the cache, which would then keep reusing "keystore present" after the file
// is deleted and skip the guard below.
val keystorePropsText = providers
    .fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText
val hasReleaseKeystore = keystorePropsText.isPresent
val keystoreProps = Properties().apply {
    if (keystorePropsText.isPresent) {
        keystorePropsText.get().reader().use { load(it) }
    }
}
val allowDebugSignedRelease = providers.gradleProperty("allowDebugSignedRelease")
    .map { it.toBoolean() }
    .getOrElse(false)

android {
    namespace = "com.vivekray898.payvoice"
    compileSdk {
        version = release(37)
    }

    // Enables the `screenshotTest` source set and the
    // update*/validate*ScreenshotTest tasks (paired with the flag in
    // gradle.properties so the IDE and the CLI agree).
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

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
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-phase1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 full mode. `optimization.enable` is the AGP 9 replacement for
            // the old `isMinifyEnabled`; R8 has defaulted to full mode since
            // AGP 8.0, so no separate fullMode flag is needed.
            //
            // Before this, the release APK shipped fully unminified at 17.0 MiB
            // — over the < 15 MB budget (audit C1). Shrinking resources as well
            // strips the unused Material icon set (icons-extended contributes
            // tens of MB unshrunk; see the dependency note below).
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The real release keystore when keystore.properties exists;
            // otherwise the debug key, which `verifyReleaseSigning` (below)
            // refuses to let a release task use unless explicitly allowed.
            signingConfig = if (hasReleaseKeystore) {
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
    // -----------------------------------------------------------------------
    // Lint gate.
    //
    // Per-check severity lives in app/lint.xml (AGP's `warningsAsErrors` is a
    // single boolean for ALL warnings, which would fail the build on version
    // -currency and style noise and teach people to ignore it). The XML
    // promotes the security-relevant checks to errors and silences the two
    // deliberate exceptions, each with its reason recorded there.
    // -----------------------------------------------------------------------
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Lint the app's own sources only. Dependency sources produce a flood
        // of issues the app cannot fix, and mixing them in makes real findings
        // invisible.
        checkDependencies = false
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        // MigrationTestHelper reads the exported schemas from the androidTest
        // APK's assets. The Room Gradle plugin normally wires this up; with the
        // schema location set through KSP args it has to be declared explicitly,
        // otherwise the migration tests cannot find 1.json/3.json and silently
        // skip validation.
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// ---------------------------------------------------------------------------
// Refuse to package a debug-signed release artifact.
//
// Runs only when a release task is actually in the graph, so `./gradlew tasks`
// and every debug task keep working on a fresh clone with no keystore.
// ---------------------------------------------------------------------------
val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description =
        "Fails if a release artifact would be signed with the public debug key."
    // The booleans are passed in as task inputs rather than read from the
    // script inside doLast: a doLast that touches a top-level script val
    // captures the Gradle script object, which the configuration cache refuses
    // to serialize.
    val wouldUseDebugKey = objects.property(Boolean::class.javaObjectType)
        .convention(!hasReleaseKeystore)
    val explicitlyAllowed = objects.property(Boolean::class.javaObjectType)
        .convention(allowDebugSignedRelease)
    doLast {
        if (!wouldUseDebugKey.get()) return@doLast
        if (explicitlyAllowed.get()) {
            println(
                "PayVoice: assembling a DEBUG-SIGNED release build " +
                    "(-PallowDebugSignedRelease=true). Local measurement only " +
                    "- never ship this.",
            )
            return@doLast
        }
        throw GradleException(
            "Refusing to build a release artifact signed with the debug key.\n" +
                "keystore.properties is missing from the repo root, so no release " +
                "identity is available. Create it from a secure store, or pass " +
                "-PallowDebugSignedRelease=true to produce a throwaway build for " +
                "local size/startup measurement only.",
        )
    }
}

tasks.matching {
    val n = it.name
    (n.startsWith("assemble") || n.startsWith("bundle") || n.startsWith("package")) &&
        n.contains("Release")
}.configureEach {
    dependsOn(verifyReleaseSigning)
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
    inputs.file(src).optional()
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
    implementation(libs.androidx.profileinstaller)

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
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Debug builds ONLY — never in a release artifact. LeakCanary is how the
    // retained-Activity and un-cancelled-coroutine class of bug gets caught
    // during development instead of showing up as an OOM on a customer's
    // 1 GB phone. It installs its own UI and heap watcher, which is exactly
    // what must not ship.
    debugImplementation(libs.leakcanary.android)
}

// ---------------------------------------------------------------------------
// Design-system enforcement (docs/DESIGN_SYSTEM.md §Enforcement gate).
//
// DESIGN.md used to be enforced by a bash one-liner pasted at the bottom of a
// document, which nobody ran. These tasks make the rules executable so a
// future change cannot silently opt out of the design system.
//
//   verifyDesignTokens     — the three hard rules from DESIGN_SYSTEM.md.
//                            Wired into `check` and into CI.
//   verifyDesignComponents — "screens compose Pv* components only, and never
//                            do arithmetic on Spacing tokens". Created now,
//                            wired into CI together with the screen migration.
//
// Both scan only ui/ and skip ui/theme (the one place .dp/.sp/Color(0x are
// legal) and ui/components (the one place raw Material widgets are legal).
//
// Everything the task action needs is captured as String/List values at
// configuration time: a Gradle script object reference inside doLast would
// break the configuration cache, which this build uses.
// ---------------------------------------------------------------------------
val uiRootPath = file("src/main/java/com/vivekray898/payvoice/ui").absolutePath
val uiSkipDirs = listOf("theme", "components")
val PILL_SHAPE = Regex("""RoundedCornerShape\s*\(\s*percent\s*=\s*50""")

// (ruleName, regex, appliesToScreenFilesOnly)
val pvHardRules = listOf(
    Triple("hardcoded-color", Regex("""Color\(\s*0[xX]"""), false),
    Triple("raw-dp-sp", Regex("""\b\d+(\.\d+)?\.(dp|sp)\b"""), false),
    Triple("bare-scaffold", Regex("""(?<![A-Za-z])Scaffold\s*\("""), false),
)

val pvComponentRules = listOf(
    Triple(
        "raw-material-widget",
        Regex(
            """(?<![A-Za-z])(Button|OutlinedButton|TextButton|IconButton|""" +
                """FloatingActionButton|ExtendedFloatingActionButton|""" +
                """ModalBottomSheet|AlertDialog|OutlinedTextField|TextField|""" +
                """Switch|Slider|FilterChip|Card|Surface|""" +
                """CircularProgressIndicator|LinearProgressIndicator)\s*\(""",
        ),
        true,
    ),
    Triple("spacing-arithmetic", Regex("""Spacing\.\w+\s*[+\-]\s*Spacing\."""), true),
)

fun pvRegisterDesignCheck(
    taskName: String,
    taskDescription: String,
    rules: List<Triple<String, Regex, Boolean>>,
) {
    val rootPath = uiRootPath
    val skipDirs = uiSkipDirs
    val patterns = rules.map { Triple(it.first, it.second.pattern, it.third) }
    tasks.register(taskName) {
        group = "verification"
        description = taskDescription
        doLast {
            val root = File(rootPath)
            val found = mutableListOf<String>()
            if (root.isDirectory) {
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .sortedBy { it.path }
                    .forEach { f ->
                        val rel = f.relativeTo(root).path.replace('\\', '/')
                        if (rel.substringBefore('/') in skipDirs) return@forEach
                        // theme/ and components/ are already skipped above,
                        // so every remaining file under ui/ is a screen (or a
                        // screen-local step/flow file) and must use Pv* parts.
                        val screenOnly = true
                        f.readLines().forEachIndexed { i, raw ->
                            val code = raw.substringBefore("//")
                            patterns.forEach { (name, pattern, screensOnly) ->
                                if (screensOnly && !screenOnly) return@forEach
                                if (Regex(pattern).containsMatchIn(code)) {
                                    found += "  $rel:${i + 1}  [$name]  ${raw.trim()}"
                                }
                            }
                        }
                    }
            }
            if (found.isNotEmpty()) {
                throw GradleException(
                    "$taskName failed with ${found.size} violation(s):\n" +
                        found.joinToString("\n") +
                        "\n\nOnly ui/theme/*.kt may declare .dp/.sp/Color(0x); only " +
                        "ui/components/PvScaffold.kt may call Scaffold(); screens may " +
                        "only compose Pv* components.",
                )
            }
            logger.lifecycle("$taskName: OK")
        }
    }
}

pvRegisterDesignCheck(
    "verifyDesignTokens",
    "Fails on Color(0x…, raw .dp/.sp, or a bare Scaffold outside PvScaffold.",
    pvHardRules,
)

pvRegisterDesignCheck(
    "verifyDesignComponents",
    "Fails on raw Material widgets and spacing arithmetic inside screen files.",
    pvComponentRules,
)

/**
 * DESIGN.md gives `{rounded.pill}` (9999px) to **buttons and tag pills** and
 * nothing else, and reserves solid indigo for one filled element per band.
 * Nothing stops a selection group, a value readout or a nav indicator from
 * being drawn as a fully-rounded bubble again — six of them sat on the
 * payments screen before this gate existed.
 *
 * So the radius itself is gated, across the whole of `ui/` and not just the
 * component library: `verifyDesignComponents` bars raw Material widgets in a
 * screen but says nothing about shapes, so without this a screen could draw
 * its own pill. A `RoundedCornerShape(percent = 50)` is legal only in the
 * five component files below, each of which is a button or a tag pill. One
 * file per sanctioned use, so allowlisting a file cannot quietly license a
 * new pill next to a legal one.
 */
val pvPillSanctionedFiles = setOf(
    "PvPrimaryButton.kt",
    "PvSecondaryButton.kt",
    "PvTextButton.kt",
    "PvFab.kt",
    "StatusPill.kt",
)

tasks.register("verifyDesignShapes") {
    group = "verification"
    description =
        "Fails on rounded.pill outside the buttons and tag pills DESIGN.md sanctions."
    val rootPath = uiRootPath
    val skipDirs = uiSkipDirs - "components"
    val sanctioned = pvPillSanctionedFiles
    // Captured as a String, not the Regex object: a doLast cannot close over
    // a Gradle script reference when the configuration cache is on.
    val pillPattern = PILL_SHAPE.pattern
    doLast {
        val pill = Regex(pillPattern)
        val root = File(rootPath)
        val found = mutableListOf<String>()
        if (root.isDirectory) {
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.path }
                .forEach { f ->
                    val rel = f.relativeTo(root).path.replace('\\', '/')
                    if (rel.substringBefore('/') in skipDirs) return@forEach
                    val name = f.name
                    f.readLines().forEachIndexed { i, raw ->
                        val code = raw.substringBefore("//")
                        if (pill.containsMatchIn(code) && name !in sanctioned) {
                            found += "  $rel:${i + 1}  [rounded.pill]  ${raw.trim()}"
                        }
                    }
                }
        }
        if (found.isNotEmpty()) {
            throw GradleException(
                "verifyDesignShapes failed with ${found.size} violation(s):\n" +
                    found.joinToString("\n") +
                    "\n\nDESIGN.md reserves {rounded.pill} for buttons and tag " +
                    "pills. Use MaterialTheme.shapes.small/medium for a control, " +
                    "or put the pill in one of: ${sanctioned.sorted().joinToString()}.",
            )
        }
        logger.lifecycle("verifyDesignShapes: OK")
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("verifyDesignTokens", "verifyDesignComponents", "verifyDesignShapes")
}

// ---------------------------------------------------------------------------
// verifyInstrumentationResults
//
// `connectedAndroidTest` reports BUILD SUCCESSFUL even when the runner's
// `-Pandroid.testInstrumentationRunnerArguments.class=` filter matches nothing:
// the Gradle task succeeds, and the only evidence is a JUnit XML carrying
// tests="0". That silently turns a whole device run into a green no-op, which
// is how "the replay harness passed" can be claimed while no payment was ever
// replayed.
//
// This gate reads the XML that task produces and fails the build when the run
// contained zero tests, or when any test failed or errored. It is wired as a
// finalizer of connectedAndroidTest rather than into `check`, because the XML
// only exists once a device has actually been used.
// ---------------------------------------------------------------------------
val androidTestResultsDir = layout.buildDirectory.dir("outputs/androidTest-results")

tasks.register("verifyInstrumentationResults") {
    group = "verification"
    description = "Fails if an instrumentation run executed zero tests or reported a failure."
    val resultsDir = androidTestResultsDir
    doLast {
        val dir = resultsDir.get().asFile
        val xml = dir.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        if (xml.isEmpty()) {
            throw GradleException(
                "verifyInstrumentationResults: no instrumentation results under $dir.\n" +
                    "connectedAndroidTest either did not run or did not write results.",
            )
        }
        val suites = xml.flatMap { f ->
            Regex("""<testsuite\b[^>]*>""").findAll(f.readText()).map { m ->
                fun attr(n: String) =
                    Regex("""\b$n="(\d+)"""").find(m.value)?.groupValues?.get(1)?.toInt() ?: 0
                Triple(attr("tests"), attr("failures"), attr("errors"))
            }.toList()
        }
        val tests = suites.sumOf { it.first }
        val failures = suites.sumOf { it.second }
        val errors = suites.sumOf { it.third }
        if (tests == 0) {
            throw GradleException(
                "verifyInstrumentationResults: instrumentation ran ZERO tests " +
                    "(${xml.size} result file(s) in $dir) but the build reported success.\n" +
                    "This is the -Pandroid.testInstrumentationRunnerArguments.class filter " +
                    "matching no class or method. Fix the filter before trusting a green build.",
            )
        }
        if (failures + errors > 0) {
            throw GradleException(
                "verifyInstrumentationResults: $failures failure(s), $errors error(s) " +
                    "across $tests test(s). See $dir.",
            )
        }
        logger.lifecycle("verifyInstrumentationResults: OK ($tests test(s), 0 failures)")
    }
}

tasks.matching { it.name == "connectedDebugAndroidTest" || it.name == "connectedReleaseAndroidTest" }
    .configureEach { finalizedBy("verifyInstrumentationResults") }
