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

# General Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
