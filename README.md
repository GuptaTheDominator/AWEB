<div align="center">

# AWEB

**A personal Android tablet browser built around isolated workspaces**

[![Latest Release](https://img.shields.io/github/v/release/GuptaTheDominator/AWEB?label=download&color=9C6FFF&style=for-the-badge)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![APK Size](https://img.shields.io/badge/APK-~196%20MB-4FC3F7?style=for-the-badge)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![Android](https://img.shields.io/badge/Android-10%2B%20%28API%2029%29-81C784?style=for-the-badge)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![Engine](https://img.shields.io/badge/Engine-GeckoView%20132-FF6611?style=for-the-badge)](https://mozilla.github.io/geckoview/)

*Built for Redmi Pad SE 4G / HyperOS · ARM64 only · Not on the Play Store*

---

### [⬇ Download AWEB-v1.0.7-arm64.apk](https://github.com/GuptaTheDominator/AWEB/releases/latest)

</div>

---

## What is AWEB?

AWEB is a personal browser where every **workspace** is a completely separate browser profile — its own cookies, logins, localStorage, IndexedDB, cache and tabs. No two workspaces share any data.

It uses **Mozilla GeckoView** (the Firefox engine) for true `contextId`-level isolation that Android WebView cannot offer. You can be logged into different Google accounts, different WhatsApp numbers, or different banking sessions simultaneously.

---

## Features

### 🗂️ Workspace Isolation
- Each workspace has completely isolated **cookies, localStorage, IndexedDB, logins**
- Powered by GeckoView `contextId` — a permanent, immutable profile key
- Log into **different accounts simultaneously** across workspaces
- Create, rename, reorder, delete workspaces with 7 colour labels
- Clear one workspace's data without affecting others

### 📑 Tab Management
- Persistent tabs **per workspace** — each workspace remembers its own tabs
- Scrollable tab strip with pinned tabs always at front
- Tab overview grid — see all tabs and manage in bulk
- Long-press any tab: Pin / Keep Alive / Close
- Lifecycle state badge on every tab: `● Active` `◐ Recent` `○ Unloaded` `◆ Keep Alive`

### ⚡ Keep Alive Tabs
- Mark any tab to stay running when you switch away
- Perfect for **WhatsApp Web, dashboards, uploads, music players**
- Amber animated bolt indicator on all Keep Alive tabs
- Cap configurable up to 10 (default 3)
- Keep Alive panel shows all alive tabs + slot usage bar

### 🧠 Automatic Memory Management
| Mode | Recent live | Keep Alive cap |
|---|---|---|
| **Conservative** | 0 | 2 |
| **Balanced** *(default)* | 2 | 3 |
| **Performance** | 5 | 5 |

Tabs automatically move `Active → Recent → Unloaded` based on usage. Unloaded tabs still appear in the strip and reload when selected. Android memory pressure (`onTrimMemory`) handled with a cascade from mild unloading to emergency-only-active mode.

### 🔁 24/7 Survival (HyperOS)
- **Foreground service starts at app launch** — elevates process priority immediately, preventing LMKD kills
- WorkManager health check every 15 minutes
- Boot receiver restarts service after device reboot
- Guided HyperOS setup screen (Autostart, No restrictions, Lock in recents)
- Keep Alive tab count shown in persistent notification

### 🌐 Browser Features
- **Downloads** — Android DownloadManager with confirm dialog
- **File upload** — system file picker for `<input type="file">`
- **Fullscreen video** — toolbar/tabs hide, immersive system UI
- **Find in page** — GeckoView SessionFinder, prev/next, match count
- **Bookmarks** — add/remove/open, star button in toolbar
- **Desktop mode** — per-tab UA toggle, auto-reloads
- **Security indicator** — 🔒 green (HTTPS) / 🔓 grey (HTTP)
- **Permission dialogs** — camera, mic, location, web notifications

### ⚙️ Settings
- Memory mode picker with preset descriptions
- Fine-grain steppers for max recent live tabs and max Keep Alive tabs
- Live memory dashboard with animated ring chart and session count
- Pressure simulation buttons (Mild / Low / Critical / Severe) for testing
- Default homepage and search engine (DuckDuckGo / Google / Bing)
- Keep screen awake while charging
- HyperOS Setup Guide (5-step checklist with deep-links)
- Developer diagnostics screen — isolation check, crash info, session state

### 💥 Crash Recovery
- Uncaught exception handler writes crash info to DataStore before process dies
- Next launch detects unclean exit and shows amber recovery banner
- All tabs and workspaces restore cleanly from Room DB — no data loss

---

## Install

```bash
adb install -r AWEB-v1.0.7-arm64.apk
```

Or download directly to your device and install via a file manager (enable **Install unknown apps** for your file manager).

---

## ⚠️ HyperOS Setup — Required for 24/7 Survival

After installing, open AWEB → **Settings → HyperOS Setup Guide** and complete all 5 steps:

| Step | Setting | Why |
|---|---|---|
| 1 | **Autostart → ON** | Lets AWEB restart after reboot |
| 2 | **Battery Optimization → No Restrictions** | Prevents HyperOS freezing background processes |
| 3 | **HyperOS Battery Saver → No Restrictions** | **Critical** — without this LMKD can still kill AWEB |
| 4 | **Lock in Recent Apps** | Prevents MIUI from clearing the process from recents |
| 5 | **Allow Notifications** | Required for the foreground service notification |

> **Without step 3 especially**, HyperOS's Low Memory Killer (LMKD) will kill AWEB during GeckoView startup when RAM usage spikes, causing the "closes after 1 second" symptom.

---

## Architecture

```
UI Layer
├── MainActivity             Single Activity, tablet sidebar layout
├── BrowserScreen            GeckoView + all overlays
├── WorkspaceSidebar         Left-rail workspace switcher + memory bar
├── TabStrip / TabOverview   Horizontal strip + full-screen grid
├── KeepAlivePanel           Bottom-sheet KA tab manager
└── SettingsScreen           Memory, browser prefs, HyperOS guide

Browser Layer
├── GeckoRuntimeManager      Singleton GeckoRuntime (main thread only)
├── GeckoSessionWrapper      Per-tab session with StateFlow observables
├── TabSessionManager        Maps tabId → GeckoSessionWrapper
└── WorkspaceSessionManager  Maps workspaceId → session

Lifecycle Layer
├── TabLifecycleManager      LRU eviction engine, memory pressure cascade
├── MemoryPressureReceiver   ComponentCallbacks2 → TabLifecycleManager
├── KeepAliveManager         Toggle, cap enforcement, event relay
└── MemoryPolicy             CONSERVATIVE / BALANCED / PERFORMANCE presets

Data Layer (Room v2)
├── WorkspaceEntity / DAO    id, name, contextId (permanent), color, order
├── TabEntity / DAO          url, title, lifecycle state, keepAlive, pinned
├── BookmarkEntity / DAO     url, title, created_at
└── AppSettingEntity / DAO   Key-value settings store

Background Layer
├── AwebForegroundService    Persistent notification, START_STICKY
├── BootReceiver             Restarts service on boot / package replace
├── ServiceHealthWorker      WorkManager periodic health check (15 min)
└── ServiceManager           Single point for all service intent building
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for a full deep-dive.

---

## Build

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for the complete guide.

**Quick start:**
```bash
git clone https://github.com/GuptaTheDominator/AWEB.git && cd AWEB
cp keystore.properties.template keystore.properties
# edit keystore.properties with your signing passwords
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

---

## Version History

| Version | Key change |
|---|---|
| **v1.0.7** | Deep codebase scan — `Divider→HorizontalDivider`, `ArrowBack→AutoMirrored`, `@Suppress` for deprecated APIs, `@Transaction` on `TabDao.setActive`, typed `GeckoResult<Void>`, `.catch{}` on all Flows |
| v1.0.6 | **LMKD kill fix** — foreground service starts synchronously in `Application.onCreate()` before GeckoRuntime, elevating process priority before the RAM spike |
| v1.0.5 | `loadUrl()` race condition fix, 8-retry GeckoSession backoff, `withContext(Main)` for session creation |
| v1.0.4 | Root cause: `GeckoRuntime.create()` must run on Main thread — all thread safety enforced |
| v1.0.3 | Comprehensive startup crash hardening — all try-catch wrapping |
| v1.0.2 | Missing launcher icons, Hilt injection guards |
| v1.0.1 | APK size 639 MB → 196 MB via ARM64-only ABI split |
| v1.0.0 | All 9 development phases complete |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Browser engine | Mozilla GeckoView 132 (nightly-omni) |
| Database | Room 2.6 |
| DI | Hilt 2.51 |
| Async | Kotlin Coroutines + Flow |
| Background | WorkManager 2.9 + Foreground Service |
| Persistence | DataStore Preferences |
| Build | AGP 8.6.1, Gradle 8.7, KSP |

---

## License

Personal use only. Not for redistribution or Play Store submission.  
GeckoView is licensed under [MPL 2.0](https://www.mozilla.org/en-US/MPL/2.0/).
