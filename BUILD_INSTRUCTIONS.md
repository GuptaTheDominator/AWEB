# AWEB — Build Instructions

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer | Ladybug / Meerkat also fine |
| JDK | 17 | Temurin recommended |
| Android SDK | 35 | Via SDK Manager |
| Kotlin | 2.0.0 | Auto-downloaded by Gradle |
| ADB | Any recent | For device install |
| Device | ARM64 Android 10+ | Redmi Pad SE 4G is the target |

---

## 1 — Clone

```bash
git clone https://github.com/GuptaTheDominator/AWEB.git
cd AWEB
```

---

## 2 — Debug APK (fastest, no signing needed)

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK has `applicationId = com.aweb.browser.debug` so it can coexist with a release install.

---

## 3 — Release APK (signed, for daily use)

### 3a — Create a keystore (one time only)

```bash
keytool -genkey -v \
  -keystore aweb-release.jks \
  -alias aweb \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500 \
  -storepass YOUR_STORE_PASS \
  -keypass  YOUR_KEY_PASS \
  -dname "CN=AWEB, OU=Personal, O=Personal, L=-, S=-, C=IN"
```

Place `aweb-release.jks` in the **root** of the project (same folder as `settings.gradle.kts`).

### 3b — Create keystore.properties

```bash
cp keystore.properties.template keystore.properties
```

Edit `keystore.properties`:

```properties
storeFile=../aweb-release.jks
storePassword=YOUR_STORE_PASS
keyAlias=aweb
keyPassword=YOUR_KEY_PASS
```

> ⚠️ `keystore.properties` is in `.gitignore` — it will never be committed.

### 3c — Build

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`

### 3d — Install

```bash
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

---

## 4 — Run unit tests

```bash
./gradlew testReleaseUnitTest
```

Tests:
- `MemoryPolicyTest` — preset correctness (8 assertions)
- `TabLifecycleStateTest` — DB key round-trip (5 assertions)
- `WorkspaceIsolationTest` — contextId uniqueness, 50-iteration stress (6 assertions)
- `DownloadHandlerTest` — filename sanitisation (7 scenarios)
- `SearchEngineTest` — URL building for DDG / Google / Bing (7 assertions)

---

## 5 — GeckoView note

GeckoView is fetched from `https://maven.mozilla.org/maven2`.  
First Gradle sync downloads **~268 MB** — allow 5–10 minutes.  
Subsequent syncs use the Gradle cache.

Current version: `geckoview-nightly-omni:132.0.20240929094629`

---

## 6 — CI / GitHub Actions

Two workflows are included:

| Workflow | Trigger | Output |
|---|---|---|
| `release.yml` | Push tag `v*.*.*` or manual dispatch | Signed ARM64 APK + GitHub Release |
| `debug.yml` | Push to `main` | Unsigned debug APK artifact |

### Secrets required for release workflow

Set these in **GitHub → Settings → Secrets → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 aweb-release.jks` |
| `KEYSTORE_PASSWORD` | Your store password |
| `KEY_ALIAS` | `aweb` |
| `KEY_PASSWORD` | Your key password |

### Trigger a release manually

```bash
git tag -a v1.0.2 -m "AWEB v1.0.2"
git push origin v1.0.2
```

Or use **GitHub → Actions → Build & Release APK → Run workflow**.

---

## 7 — APK size breakdown

| Build type | Size | Why |
|---|---|---|
| Fat APK (all ABIs) | ~639 MB | GeckoView ships arm64 + x86 + x86_64 + armeabi-v7a |
| ARM64-only (current) | ~194 MB | Only arm64-v8a native libs |
| After R8 + resource shrink | ~194 MB | Kotlin/Compose already well-shrunk |

The ARM64-only build is produced by:
```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("arm64-v8a")
        isUniversalApk = false
    }
}
```

---

## 8 — Project structure

```
AWEB/
├── app/
│   ├── src/main/
│   │   ├── java/com/aweb/browser/
│   │   │   ├── AwebApplication.kt
│   │   │   ├── AppState.kt
│   │   │   ├── browser/          ← Download, FileUpload, Permissions, FindInPage, Fullscreen, UA
│   │   │   ├── crash/            ← CrashRecoveryManager
│   │   │   ├── data/             ← Room entities, DAOs, repositories
│   │   │   ├── di/               ← Hilt modules
│   │   │   ├── gecko/            ← GeckoRuntime, GeckoSessionWrapper, TabSessionManager
│   │   │   ├── lifecycle/        ← TabLifecycleManager, MemoryPolicy, MemoryPressureReceiver
│   │   │   ├── service/          ← ForegroundService, BootReceiver, ServiceHealthWorker
│   │   │   └── ui/
│   │   │       ├── BrowserScreen.kt
│   │   │       ├── MainActivity.kt
│   │   │       ├── browser/      ← Bookmarks, FindInPageBar, PermissionDialogs
│   │   │       ├── hardening/    ← CrashBanner, DiagnosticsScreen, HardeningViewModel
│   │   │       ├── keepalive/    ← KeepAliveManager, Panel, Indicator, CapDialog
│   │   │       ├── settings/     ← SettingsScreen, MemoryDashboard, SettingsViewModel
│   │   │       ├── setup/        ← HyperOsSetupScreen, SetupViewModel
│   │   │       ├── tabs/         ← TabStrip, TabOverview, MemoryStatusBar, TabViewModel
│   │   │       ├── theme/        ← AwebTheme (dark purple Material 3)
│   │   │       └── workspace/    ← WorkspaceSidebar, WorkspaceDialogs, WorkspaceViewModel
│   │   ├── AndroidManifest.xml
│   │   └── res/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml        ← Version catalog
│   └── wrapper/
├── .github/workflows/
│   ├── release.yml               ← Signed APK + GitHub Release
│   └── debug.yml                 ← Debug APK on every push to main
├── gradle.properties
├── settings.gradle.kts
├── keystore.properties.template
├── BUILD_INSTRUCTIONS.md
├── ARCHITECTURE.md
└── README.md
```

---

## 9 — App data locations (on device)

```
/data/data/com.aweb.browser/
├── databases/
│   └── aweb.db                   ← Room: workspaces, tabs, bookmarks, settings
├── files/datastore/
│   ├── aweb_setup.preferences_pb ← Setup completed flag
│   └── aweb_crash.preferences_pb ← Crash detection state
└── (GeckoView profile data managed internally by Gecko per contextId)
```

All cloud backup is disabled. No data ever leaves the device.
