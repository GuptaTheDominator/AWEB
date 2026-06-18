# AWEB Build Instructions

AWEB is an Android/Kotlin project that builds an **ARM64-only APK** for Redmi Pad SE / Android 10+ devices.

Current source version: `v2.6.12`

Package name: `com.aweb.browser`

Debug package name: `com.aweb.browser.debug`

---

## Requirements

| Tool | Required |
|---|---|
| JDK | 17 recommended |
| Android SDK | API 35 |
| Android Build Tools | 34/35 |
| Gradle | wrapper included (`./gradlew`) |
| Device | ARM64 Android 10+ |

The project downloads dependencies from:

- Google Maven
- Maven Central
- Mozilla Maven (`https://maven.mozilla.org/maven2`)

GeckoView is large; first sync can take several minutes.

---

## Clone

```bash
git clone https://github.com/GuptaTheDominator/AWEB.git
cd AWEB
chmod +x gradlew
```

---

## Fast validation

Run this before pushing changes:

```bash
./gradlew compileReleaseKotlin testReleaseUnitTest --no-daemon --stacktrace
./gradlew lintRelease --no-daemon --stacktrace
```

Expected current status:

- unit tests pass
- lint has `0 errors` but may show warnings/info

---

## Debug APK

```bash
./gradlew assembleDebug --no-daemon --stacktrace
```

Output:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

The debug app has:

```text
applicationId = com.aweb.browser.debug
```

so it can coexist with a release install.

---

## Release APK with local signing key

### 1. Generate a keystore once

```bash
keytool -genkeypair -v \
  -keystore aweb-release.jks \
  -alias aweb \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500 \
  -storepass YOUR_STORE_PASS \
  -keypass YOUR_KEY_PASS \
  -dname "CN=AWEB, OU=Personal, O=Personal, L=-, ST=-, C=IN"
```

Keep this file safe. Losing it means you cannot update APKs signed with it.

### 2. Configure signing

```bash
cp keystore.properties.template keystore.properties
```

Edit:

```properties
storeFile=../aweb-release.jks
storePassword=YOUR_STORE_PASS
keyAlias=aweb
keyPassword=YOUR_KEY_PASS
```

`keystore.properties` and `*.jks` are ignored by git.

### 3. Build release APK

```bash
./gradlew assembleRelease --no-daemon --stacktrace
```

Output:

```text
app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

---

## Verify local release APK

```bash
APK=app/build/outputs/apk/release/app-arm64-v8a-release.apk
apksigner verify --verbose --print-certs "$APK"
zipalign -c -p 4 "$APK"
sha256sum "$APK"
```

If `apksigner` or `zipalign` is not on PATH, use the versions inside Android Build Tools:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose --print-certs "$APK"
$ANDROID_HOME/build-tools/35.0.0/zipalign -c -p 4 "$APK"
```

---

## Unit tests

```bash
./gradlew testReleaseUnitTest --no-daemon --stacktrace
```

Current test coverage includes:

- memory policy presets
- tab lifecycle state keys
- workspace context ID isolation logic
- search engine URL encoding
- omnibox URL/search parsing
- HyperOS setup step IDs
- privacy sanitizer redaction
- download filename logic

---

## GitHub Actions CI

### `CI` workflow

File:

```text
.github/workflows/ci.yml
```

Triggers:

- push to `main`
- pull request to `main`

Runs:

```text
testReleaseUnitTest
lintRelease
assembleDebug
```

Uploads a debug ARM64 APK artifact for 7 days.

### `Build and Release` workflow

File:

```text
.github/workflows/release.yml
```

Trigger:

```text
push tag vX.Y.Z
```

Runs:

1. version/tag consistency check
2. keystore restore from GitHub Actions secrets
3. unit tests
4. lint
5. release APK build
6. `apksigner` verification
7. `zipalign` verification
8. artifact rename to `AWEB-vX.Y.Z-arm64.apk`
9. SHA256 generation
10. GitHub Release upload

---

## GitHub release secrets

Repository secrets required:

```text
AWEB_KEYSTORE_BASE64
AWEB_KEYSTORE_PASSWORD
AWEB_KEY_ALIAS
AWEB_KEY_PASSWORD
```

Create base64 keystore value:

```bash
base64 -w 0 aweb-release.jks
```

Do not rotate/recreate this keystore unless you accept Android update signature incompatibility.

---

## Publish a tagged release

1. Update `versionName`, `versionCode`, and `CHANGELOG.md`.
2. Push to `main` and wait for CI success.
3. Tag exactly matching `versionName`:

```bash
git tag -a vX.Y.Z -m "AWEB vX.Y.Z"
git push origin vX.Y.Z
```

The release workflow creates:

```text
AWEB-vX.Y.Z-arm64.apk
AWEB-vX.Y.Z-arm64.apk.sha256
```

See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) before publishing.

---

## APK size notes

| Build | Approx size | Notes |
|---|---:|---|
| Universal/fat Gecko APK | ~600+ MiB | all native ABIs |
| AWEB ARM64 release | ~199 MiB | `arm64-v8a` only |

ARM64 split is configured in `app/build.gradle.kts`:

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

## Device smoke test

After install on Redmi Pad SE:

- open app cold
- complete HyperOS setup checklist
- open a URL
- create a second workspace
- verify separate account/session isolation
- open multiple tabs
- mark one tab Keep Alive
- test notification Exit/relaunch behavior
- test file upload and download
- test fullscreen video
- test camera/mic/location permission prompts

---

## Common problems

### Signature conflict while installing

Cause: installed APK was signed with a different key.

Fix:

```bash
adb uninstall com.aweb.browser
adb install -r AWEB-vX.Y.Z-arm64.apk
```

Uninstalling removes app data.

### App closes during Gecko startup on HyperOS

Complete HyperOS setup:

- Autostart ON
- Battery / No restrictions
- HyperOS Battery Saver / No restrictions
- Lock in Recent Apps
- Notifications allowed

### Release workflow fails at signing

Check repository secrets and ensure `AWEB_KEY_PASSWORD` matches the key entry password inside the keystore. For PKCS12 keystores, using the same store/key password is simplest.
