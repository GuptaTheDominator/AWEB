# ── AWEB ProGuard / R8 rules ──────────────────────────────────────────────

# GeckoView — uses reflection internally
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**

# Room entities, DAOs, and database
-keep class com.aweb.browser.data.** { *; }
-keepclassmembers class com.aweb.browser.data.** { *; }

# Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class **_HiltModules* { *; }
-keep class **_MembersInjector { *; }
-keep class **_Factory { *; }
-dontwarn dagger.**

# WorkManager workers and Hilt injection
-keep class com.aweb.browser.service.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Startup library
-keep class androidx.startup.** { *; }

# Crash recovery — must survive obfuscation
-keep class com.aweb.browser.crash.** { *; }

# AppState — accessed from multiple threads
-keep class com.aweb.browser.AppState { *; }

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Compose runtime
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# DataStore
-keep class androidx.datastore.** { *; }

# Keep source file info for crash traces
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# Rename source file attribute for obfuscated builds
-renamesourcefileattribute SourceFile
