# AWEB ProGuard rules

# Keep GeckoView intact — it uses reflection internally
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**

# Keep Room entity and DAO classes
-keep class com.aweb.browser.data.** { *; }

# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class **_HiltModules* { *; }

# Keep coroutine internals
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep Compose runtime
-keep class androidx.compose.** { *; }

# Keep WorkManager workers (needed for Hilt injection)
-keep class com.aweb.browser.service.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Keep startup library
-keep class androidx.startup.** { *; }

# General Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
