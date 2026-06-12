# ════════════════════════════════════════════════════════════════════════════
# AWEB ProGuard / R8 rules — V2
# ════════════════════════════════════════════════════════════════════════════

# ── GeckoView ─────────────────────────────────────────────────────────────
# CRITICAL: Keep ALL org.mozilla classes.
# GeckoView child process services are referenced by name in AndroidManifest:
#   org.mozilla.gecko.process.GeckoChildProcessServices$tab0 ... $tab39
#   org.mozilla.gecko.process.GeckoChildProcessServices$gpu
#   org.mozilla.gecko.process.GeckoChildProcessServices$socket
#   org.mozilla.gecko.process.GeckoChildProcessServices$utility
#   org.mozilla.gecko.process.GeckoChildProcessServices$gmplugin
#   org.mozilla.gecko.media.MediaManager
# If R8 renames any of these, PackageManager.getServiceInfo() throws
# NameNotFoundException and the Gecko child process crashes (SIGSEGV).

-keep class org.mozilla.** { *; }
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.gecko.process.** { *; }
-keep class org.mozilla.gecko.media.** { *; }
-keepnames class org.mozilla.gecko.process.GeckoChildProcessServices
-keepnames class org.mozilla.gecko.process.GeckoChildProcessServices$*
-keepnames class org.mozilla.gecko.media.MediaManager
-dontwarn org.mozilla.**

# ── Room entities, DAOs ───────────────────────────────────────────────────
-keep class com.aweb.browser.data.** { *; }
-keepclassmembers class com.aweb.browser.data.** { *; }

# ── Hilt / Dagger ─────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class **_HiltModules* { *; }
-keep class **_MembersInjector { *; }
-keep class **_Factory { *; }
-dontwarn dagger.**

# ── WorkManager + Hilt workers ────────────────────────────────────────────
-keep class com.aweb.browser.service.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ── Startup library ───────────────────────────────────────────────────────
-keep class androidx.startup.** { *; }

# ── Crash recovery ────────────────────────────────────────────────────────
-keep class com.aweb.browser.crash.** { *; }
-keep class com.aweb.browser.AppState { *; }

# ── snakeyaml (GeckoView transitive dep) ─────────────────────────────────
-dontwarn org.yaml.snakeyaml.**
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn java.beans.**

# ── Missing classes from GeckoView deps ──────────────────────────────────
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.MethodDescriptor
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.beans.SimpleBeanInfo
-dontwarn com.google.android.gms.fido.**

# ── Kotlin coroutines ─────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Kotlin metadata ───────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# ── Compose ───────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── DataStore ─────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── Debug information ─────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-renamesourcefileattribute SourceFile
