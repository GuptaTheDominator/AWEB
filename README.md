<div align="center">

# AWEB

**A personal Android tablet browser built around isolated workspaces**

[![Latest Release](https://img.shields.io/github/v/release/GuptaTheDominator/AWEB?label=download&color=9C6FFF&style=for-the-badge)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![APK Size](https://img.shields.io/badge/APK-~199%20MiB-4FC3F7?style=for-the-badge)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![Android](https://img.shields.io/badge/Android-10%2B%20%28API%2029%29-81C784?style=for-the-badge)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![Engine](https://img.shields.io/badge/Engine-GeckoView%20132-FF6611?style=for-the-badge)](https://mozilla.github.io/geckoview/)

**Built for Redmi Pad SE / HyperOS · ARM64 only · Personal side-load APK · Not on Play Store**

### [⬇ Download latest ARM64 APK](https://github.com/GuptaTheDominator/AWEB/releases/latest)

</div>

---

## Current status

| Item | Status |
|---|---|
| Current source version | `v2.6.13` |
| Latest published APK | [GitHub Releases](https://github.com/GuptaTheDominator/AWEB/releases/latest) |
| Target device | Redmi Pad SE / ARM64 Android 10+ |
| Browser engine | Mozilla GeckoView 132 nightly-omni |
| Audits completed | Phases 1–12 complete |
| CI | Unit tests, lint, debug APK artifact on `main` |
| Release pipeline | Signed ARM64 APK + SHA256 on `v*` tag |

> The `main` branch can be ahead of the latest published APK. Use **Releases** for installable APKs.

---

## What is AWEB?

AWEB is a personal Android browser where every **workspace** is a separate GeckoView browser context. Each workspace has its own cookies, logins, localStorage, IndexedDB, cache and tabs.

That means you can keep different Google accounts, WhatsApp Web sessions, dashboards, banking sessions, or research contexts separated without relying on Android WebView profiles.

---

## Highlights

### Isolated workspaces

- GeckoView `contextId` per workspace
- permanent immutable workspace profile keys
- independent cookies, storage, sessions and tabs
- create, rename, reorder, clear and delete workspaces
- workspace data clearing uses Gecko session-context clearing

### Command-center navigation

- five primary destinations: Home, Spaces, Browse, Tabs and Settings
- Home dashboard with workspace, tab, Keep Alive and setup status
- Spaces manager for isolated profiles
- Tabs manager for open tabs, pinning, Keep Alive and closing
- Setup Guide and Diagnostics are connected secondary routes

### Tablet-first tabs

- persistent tabs per workspace
- horizontal tab strip
- full tab overview grid
- pinned tab support
- Keep Alive tab support
- lifecycle badges:
  - `● Active`
  - `◐ Recent`
  - `○ Unloaded`
  - `◆ Keep Alive`

### Keep Alive + memory policy

AWEB is designed for low-RAM HyperOS tablets where Gecko can be expensive to keep alive.

| Mode | Recent live tabs | Keep Alive cap |
|---|---:|---:|
| Conservative | 0 | 2 |
| Balanced | 2 | 3 |
| Performance | 5 | 5 |

Tabs are automatically promoted/demoted/unloaded based on activity, Keep Alive state and Android memory pressure.

### HyperOS survival

- foreground survival service
- persistent notification with Keep Alive count
- WorkManager health check every 15 minutes
- boot/package-replaced receiver
- user-respectful notification **Exit** action
- persisted HyperOS setup checklist

### Browser features

- downloads through Android `DownloadManager`
- download confirmation dialog
- safe filename sanitization
- file upload picker for `<input type="file">`
- fullscreen video mode
- find in page
- bookmarks
- desktop/mobile UA per tab
- omnibox URL/search parser
- copy current URL
- HTTPS security indicator
- camera/mic/location/web notification permission flow

### Privacy/security hardening

- no cloud backup
- no device-transfer backup
- no JavaScript bridge
- `http/https` only download URLs
- URL/query redaction in app crash/log paths
- per-workspace Gecko context isolation
- release APKs signed through GitHub Actions secrets

---

## Install

Download the latest APK from:

```text
https://github.com/GuptaTheDominator/AWEB/releases/latest
```

Install with ADB:

```bash
adb install -r AWEB-vX.Y.Z-arm64.apk
```

If Android reports a signature conflict, uninstall the older AWEB build first. This can happen if you installed a temporary locally signed APK before the stable GitHub Actions signing key was configured.

---

## Required HyperOS setup

After installing, open:

```text
AWEB → Settings → HyperOS Setup Guide
```

Complete these required steps:

| Step | Setting | Why |
|---|---|---|
| 1 | Autostart ON | restart after reboot |
| 2 | Battery optimization / No restrictions | prevent background freezing |
| 3 | HyperOS Battery Saver / No restrictions | critical for Gecko startup RAM spike |
| 4 | Lock in Recent Apps | reduce recents-clear kills |
| 5 | Allow notifications | foreground service notification |

Optional:

| Step | Setting |
|---|---|
| 6 | Keep screen awake while charging |

> Step 3 is especially important on HyperOS. Without it, LMKD can kill AWEB during Gecko startup.

---

## Build locally

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md).

Quick validation:

```bash
./gradlew compileReleaseKotlin testReleaseUnitTest --no-daemon --stacktrace
./gradlew lintRelease --no-daemon --stacktrace
```

Build a release APK with your own keystore:

```bash
cp keystore.properties.template keystore.properties
# edit keystore.properties
./gradlew assembleRelease
```

Release output:

```text
app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

---

## Documentation

| Document | Purpose |
|---|---|
| [DOCS.md](DOCS.md) | documentation index |
| [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) | local build, signing, CI and release instructions |
| [ARCHITECTURE.md](ARCHITECTURE.md) | app architecture and lifecycle overview |
| [SECURITY.md](SECURITY.md) | security/privacy policy and hardening notes |
| [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) | release procedure and device smoke tests |
| [CHANGELOG.md](CHANGELOG.md) | source/release history |

Audit reports are also included at the repository root for the completed review phases.

---

## Completed audit phases

| Phase | Status | Area |
|---|---:|---|
| 1 | Done | baseline build/release audit |
| 2 | Done | static code audit |
| 3 | Done | runtime startup/memory stability |
| 4 | Done | data layer/persistence |
| 5 | Done | browser feature reliability |
| 6 | Done | background service / HyperOS survival |
| 7 | Done | security/privacy |
| 8 | Done | UI/UX improvements |
| 9 | Done | unit test expansion |
| 10 | Done | performance source-level pass |
| 11 | Done | CI/release hardening |
| 12 | Done | final planning/checklists |

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Engine | Mozilla GeckoView 132 nightly-omni |
| Database | Room v4 schema, Room 2.6 runtime |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Background | Foreground Service + WorkManager |
| Preferences | DataStore Preferences |
| Build | Gradle 8.7, AGP 8.6.1, KSP |

---

## License / use

Personal use only. Not intended for redistribution or Play Store submission.

GeckoView is licensed under [MPL 2.0](https://www.mozilla.org/MPL/2.0/).
