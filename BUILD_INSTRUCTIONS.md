# AWEB — Build Instructions (Personal APK)

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35
- ADB connected to Redmi Pad SE 4G

---

## Debug APK (fastest — for testing)

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Release APK (personal signed build)

### Step 1 — Create a keystore (one time only)

```bash
keytool -genkey -v \
  -keystore aweb-release.jks \
  -alias aweb \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500 \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=AWEB Personal, OU=Personal, O=Personal, L=-, S=-, C=IN"
```

Place `aweb-release.jks` in the **root** of the project (next to `settings.gradle.kts`).

### Step 2 — Create keystore.properties

```bash
cp keystore.properties.template keystore.properties
```

Edit `keystore.properties` with your actual passwords.
**Never commit this file.**

### Step 3 — Build release APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Step 4 — Install on device

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## Run unit tests

```bash
./gradlew test
```

Tests cover:
- `MemoryPolicyTest` — preset correctness
- `TabLifecycleStateTest` — DB key round-trip
- `WorkspaceIsolationTest` — contextId uniqueness
- `DownloadHandlerTest` — filename sanitisation
- `SearchEngineTest` — URL building

---

## GeckoView first sync

GeckoView is fetched from `maven.mozilla.org`. First Gradle sync takes
3–5 minutes and downloads ~70 MB. Subsequent syncs are cached.

---

## HyperOS setup after install

After installing on your Redmi Pad SE 4G, open AWEB and follow the
setup guide (or go to Settings → HyperOS Setup):

1. Autostart → ON
2. Battery Optimization → No Restrictions
3. HyperOS Battery Saver → No Restrictions
4. Lock AWEB in Recent Apps
5. Allow Notifications

---

## App data location

```
/data/data/com.aweb.browser/databases/aweb.db     ← Room DB (workspaces, tabs, bookmarks)
/data/data/com.aweb.browser/files/datastore/       ← Settings, setup state, crash state
```

All cloud backup is disabled (`allowBackup="false"`).
Browser cookies/sessions are managed by GeckoView per workspace contextId.
