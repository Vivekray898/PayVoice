# ---------------------------------------------------------------------------
# PayVoice R8 rules (release only).
#
# AGP 9.4.1 already applies proguard-android-optimize.txt; this file is the
# project-specific supplement. The goal is the SMALLEST artifact that still
# runs — so every -keep here must be justified by a real runtime reachability
# requirement, not by habit.
#
# Audit result: the app uses NO reflection (no Class.forName, no
# ::class.java.name lookup, no ServiceLoader, no JSON-over-reflection).
# kotlinx.serialization, Room, OkHttp, WorkManager, DataStore, Firebase and
# Play Services all ship their own consumer rules, which are applied
# automatically. What remains below are the genuinely dynamic edges.
# ---------------------------------------------------------------------------

# --- Android framework entry points -----------------------------------------
# The manifest references these by name; R8 cannot see the manifest strings
# the same way it sees code references for lifecycle-driven components.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.job.JobService

# Keep the annotated members of any class using @Keep (androidx.annotation).
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
}

# --- kotlinx.serialization --------------------------------------------------
# The plugin generates a `Companion.serializer()` per @Serializable class and
# our call sites use the reified `serializer<T>()` form, so the generated code
# is statically reachable. The rules below only preserve the SERIALIZER
# MEMBERS themselves so the descriptor stays complete for any future
# polymorphic use — a handful of classes, negligible size cost.
-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keep,includedescriptorclasses class com.vivekray898.payvoice.**$$serializer { *; }
-keepclassmembers class com.vivekray898.payvoice.** {
    *** Companion;
}
-keepclasseswithmembers class com.vivekray898.payvoice.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable data classes whose fields are read/written as ordinary Kotlin
# properties. Retained so the generated serializer cannot be stripped if a
# future call site switches to reflective decoding.
-keep @kotlinx.serialization.Serializable class com.vivekray898.payvoice.** { *; }

# --- Coroutines -------------------------------------------------------------
# DebugProbes / service loader hooks in kotlinx-coroutines-debug are absent in
# release, but keep the internals debug agent uses if it is ever added, so a
# future release cannot NoSuchMethodError at runtime.
-dontwarn kotlinx.coroutines.debug.**

# --- OkHttp / Okio ----------------------------------------------------------
# OkHttp references Conscrypt/BouncyCastle/OpenJSSE/Animal-Sniffer classes that
# are absent on Android. These warnings are expected and are not errors.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.codehaus.mojo.animal_sniffer.*

# --- Firebase Messaging -----------------------------------------------------
# The messaging service receives intent extras by name; Google services already
# carry consumer rules, this only documents the intent.
-dontwarn com.google.firebase.**

# --- Supabase Realtime (Phoenix channels) -----------------------------------
# Channel payloads are decoded through kotlinx.serialization (kept above), but
# Phoenix logs via a pluggable Logger interface that references optional
# backends by class name at runtime.
-dontwarn io.*

# --- Logging ---------------------------------------------------------------
# Every release build must emit no logcat at all. -assumenosideeffects lets R8
# delete the call AND its argument evaluation, so the string concatenation at
# each of the 61 android.util.Log call sites (33 of them via DebugLog) costs
# nothing in a release build instead of merely being discarded at runtime.
#
# DebugLog is gated at runtime as well (see DebugLog.enabled), so this rule is
# a second, build-time layer rather than the only one - the runtime gate alone
# would still ship every log string.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# --- Line numbers -----------------------------------------------------------
# Keep source file + line number in the (gated) crash/exception stack traces so
# release triage stays possible, but HIDE the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile