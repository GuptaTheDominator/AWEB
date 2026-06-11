<div align="center">

# AWEB

**A personal Android tablet browser built around isolated workspaces**

[![Release](https://img.shields.io/github/v/release/GuptaTheDominator/AWEB?label=latest&color=9C6FFF)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![APK Size](https://img.shields.io/badge/APK-~194%20MB-4FC3F7)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-81C784)](https://github.com/GuptaTheDominator/AWEB/releases/latest)
[![Engine](https://img.shields.io/badge/engine-GeckoView%20132-FF6611)](https://mozilla.github.io/geckoview/)
[![License](https://img.shields.io/badge/license-Personal%20Use-FFB74D)](#license)

*Built for Redmi Pad SE 4G / HyperOS · Not on the Play Store*

</div>

---

## What is AWEB?

AWEB is a personal browser where every **workspace** is a completely separate browser profile — its own cookies, logins, localStorage, IndexedDB, cache and tabs. No two workspaces share any data. You can be logged into different Google accounts, different WhatsApp numbers, or different banking sessions simultaneously.

It uses **Mozilla GeckoView** (the Firefox engine) as its core, which provides true contextId-level isolation that Android WebView cannot offer.

---

## Download

<div align="center">

### [⬇ Download Latest APK](https://github.com/GuptaTheDominator/AWEB/releases/latest)

**ARM64 only** — for Redmi Pad SE 4G and other ARM64 Android tablets/phones

```bash
adb install -r AWEB-v1.0.1-arm64.apk
```

</div>

---

## Screenshots

> *Dark tablet UI — sidebar + browser pane*

```
┌─────────────────────────────────────────────────────────────────┐
│ AWEB          │  [3▦] [⚡] [←] [→] [↺]  🔒 duckduckgo.com  ☆ ⋮ │
│ ─────────────  ─────────────────────────────────────────────────│
│ WORKSPACES    │  ● work  ○ personal  ○ study     [+]           │
│               │ ─────────────────────────────────────────────── │
│ ● Work        │                                                  │
│ ○ Personal    │                                                  │
│ ○ Study       │           GeckoView browser pane                │
│               │                                                  │
│ ─────────────  │                                                  │
│ ● 1 active    │                                                  │
│ ◆ 2 alive     │                                                  │
│ ○ 3 unloaded  │                                                  │
│ ─────────────  │                                                  │
│ + New         │                                                  │
│ ⚙ Settings    │                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Features

### 🗂️ Workspace Isolation
- Each workspace has its own **cookies, localStorage, IndexedDB, and login sessions**
- Powered by GeckoView `contextId` — true browser profile separation
- Log into **different Google / WhatsApp / bank accounts** simultaneously
- Create, rename, reorder, delete workspaces
- Clear one workspace's data without affecting others
- 7 colour labels to tell workspaces apart at a glance

### 📑 Tab Management
- Persistent tabs **per workspace** — each workspace remembers its own tabs
- Scrollable tab strip with **pinned tabs** always at the front
- Tab overview grid — see all tabs, their state, and manage in bulk
- Long-press any tab for context menu (Pin / Keep Alive / Close)
- Lifecycle state badge on every tab: `● Active` `◐ Recent` `○ Unloaded` `◆ Keep Alive`

### ⚡ Keep Alive Tabs
- Mark any tab to stay running even when you switch away
- Perfect for **WhatsApp Web, trading dashboards, upload pages, music players**
- Amber bolt indicator (animated) on all Keep Alive tabs
- Cap: up to 3 Keep Alive tabs by default (configurable up to 10)
- Keep Alive panel shows all alive tabs + slot usage progress bar
- If cap is reached a dialog explains the options

### 🧠 Automatic Memory Management
| Mode | Recent live | Keep Alive cap |
|---|---|---|
| **Conservative** | 0 | 2 |
| **Balanced** *(default)* | 2 | 3 |
| **Performance** | 5 | 5 |

Tabs automatically move between **Active → Recent → Unloaded** based on usage. Unloaded tabs still appear in the strip — they reload when selected. Android memory pressure (`onTrimMemory`) is handled with a cascade from mild unloading to emergency-only-active mode.

### 🔁 24/7 Survival (HyperOS)
- Persistent foreground service with a silent notification
- WorkManager health check every 15 minutes
- Boot receiver restarts service after device reboot
- Guided setup screen for HyperOS-specific settings (Autostart, No restrictions, Lock in recents)
- Keep Alive tab count shown in the notification at all times

### 🌐 Browser Features
- **Downloads** — Android DownloadManager integration with confirm dialog
- **File upload** — system file picker for `<input type="file">`
- **Fullscreen video** — toolbar/tabs hide, immersive system UI
- **Find in page** — GeckoView SessionFinder, prev/next, match count
- **Bookmarks** — add/remove/open, star button in toolbar
- **Desktop mode** — per-tab UA toggle, auto-reloads page
- **Security indicator** — 🔒 green (HTTPS) / 🔓 grey (HTTP) in URL bar
- **Permission dialogs** — camera, microphone, location, web notifications

### ⚙️ Settings
- Memory mode picker with preset descriptions
- Fine-grain steppers for max recent live tabs and max Keep Alive tabs
- Memory dashboard with animated ring chart and live session count
- Pressure simulation buttons (Mild / Low / Critical / Severe) for testing
- Default homepage and search engine (DuckDuckGo / Google / Bing)
- Keep screen awake while charging (for always-on dashboards)
- HyperOS Setup Guide (5-step checklist with deep-links)
- Diagnostics screen — app version, session state, isolation check, crash info

### 💥 Crash Recovery
- Uncaught exception handler writes crash info to DataStore before process dies
- Next launch detects unclean exit and shows an amber recovery banner
- All tabs and workspaces restore cleanly from Room DB — no data loss

---

## Architecture

```
UI Layer
├── MainActivity             Single Activity, tablet sidebar layout
├── BrowserScreen            GeckoView + all overlays (tabs, KA, find, bookmarks)
├── WorkspaceSidebar         Left-rail workspace switcher + memory status bar
├── TabStrip / TabOverview   Horizontal strip + full-screen grid
├── KeepAlivePanel           Bottom-sheet KA tab manager
├── SettingsScreen           Memory, browser prefs, HyperOS guide
└── DiagnosticsScreen        Developer self-test and crash info

Browser Layer
├── GeckoRuntimeManager      Singleton GeckoRuntime (one per process)
├── GeckoSessionWrapper      Per-tab session with StateFlow observables
├── TabSessionManager        Maps tabId → GeckoSessionWrapper
└── WorkspaceSessionManager  Maps workspaceId → session (Phase 2 compat)

Lifecycle Layer
├── TabLifecycleManager      LRU eviction engine, onAppRestore, memory pressure
├── MemoryPressureReceiver   ComponentCallbacks2 → TabLifecycleManager
├── KeepAliveManager         Toggle, cap enforcement, event relay
└── MemoryPolicy             CONSERVATIVE / BALANCED / PERFORMANCE presets

Data Layer (Room)
├── WorkspaceEntity / DAO    id, name, contextId (permanent), color, order
├── TabEntity / DAO          url, title, lifecycle state, keepAlive, pinned
├── BookmarkEntity / DAO     url, title, created_at
└── AppSettingEntity / DAO   Key-value settings store

Background Layer
├── AwebForegroundService    Persistent notification, START_STICKY
├── BootReceiver             Restarts service on boot / package replace
├── ServiceHealthWorker      WorkManager periodic health check (15 min)
└── ServiceManager           Single place for all service intent building

Security
├── No cloud backup          allowBackup="false", dataExtractionRules exclude all
├── No incognito mode        By design — workspaces cover the use case
├── Runtime permissions      Camera, mic, location — asked per site request
└── Workspace isolation      GeckoView contextId = permanent profile boundary
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for a full deep-dive.

---

## Build

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for the complete guide.

**Quick start:**
```bash
git clone https://github.com/GuptaTheDominator/AWEB.git
cd AWEB
cp keystore.properties.template keystore.properties
# fill in your signing passwords
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

**Requirements:** Android Studio Hedgehog+, JDK 17, Android SDK 35, ARM64 device.

---

## HyperOS Setup (required for 24/7 survival)

After install, open AWEB → **Settings → HyperOS Setup Guide** and complete all 5 steps:

| Step | Setting | Where |
|---|---|---|
| 1 | **Autostart → ON** | Settings → Apps → AWEB → Autostart |
| 2 | **Battery Optimization → No Restrictions** | Settings → Apps → AWEB → Battery |
| 3 | **HyperOS Battery Saver → No Restrictions** | Settings → Apps → AWEB → Battery Saver |
| 4 | **Lock in Recent Apps** | Open recents → long-press AWEB → Lock |
| 5 | **Allow Notifications** | Settings → Apps → AWEB → Notifications |

AWEB's in-app guide has a deep-link button for each step where Android allows it.

---

## Project Status

| Phase | Description | Status |
|---|---|---|
| 1 | Basic browser shell (GeckoView) | ✅ |
| 2 | Workspace isolation | ✅ |
| 3 | Tabs per workspace | ✅ |
| 4 | Automatic tab lifecycle | ✅ |
| 5 | Keep Alive tabs | ✅ |
| 6 | Memory modes + stability | ✅ |
| 7 | 24/7 background survival (HyperOS) | ✅ |
| 8 | Browser completeness | ✅ |
| 9 | Hardening + personal APK | ✅ |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
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
