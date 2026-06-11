# AWEB — Personal Android Tablet Browser

**Target device:** Redmi Pad SE 4G / HyperOS  
**Engine:** Mozilla GeckoView  
**Language:** Kotlin + Jetpack Compose  

---

## What AWEB Is

AWEB is a personal browser built around **isolated browser workspaces**.  
Each workspace has its own cookies, localStorage, IndexedDB, login sessions, and tab list — completely separate from every other workspace.

---

## Architecture Summary

```
UI Layer         →  Compose screens (BrowserScreen, WorkspaceSidebar, TabStrip, …)
ViewModel Layer  →  BrowserViewModel, WorkspaceViewModel, TabViewModel
Browser Layer    →  GeckoRuntimeManager, GeckoSessionWrapper, TabLifecycleManager
Data Layer       →  Room (workspaces, tabs, app_settings)
Background Layer →  AwebForegroundService, BootReceiver, WorkManager
```

---

## Build Status by Phase

| Phase | Description                         | Status       |
|-------|-------------------------------------|--------------|
| 1     | Basic browser shell (GeckoView)     | ✅ Built      |
| 2     | Workspace isolation                 | ✅ Built      |
| 3     | Tabs per workspace                  | ✅ Built      |
| 4     | Automatic tab lifecycle             | ✅ Built      |
| 5     | Keep Alive tabs                     | ✅ Built      |
| 6     | Memory modes + stability            | ✅ Built      |
| 7     | 24/7 background survival (HyperOS) | ✅ Built      |
| 8     | Browser completeness                | ✅ Built      |
| 9     | Hardening + personal APK            | ✅ Built      |

---

## Phase 1 Deliverable

A single-tab browser shell that:
- Loads GeckoView (Firefox engine)
- Shows a URL/search bar
- Supports back, forward, reload, stop
- Shows page loading progress
- Handles URL input and DuckDuckGo search fallback
- Dark themed Compose UI
- Room database schema defined
- Hilt DI wired
- Manifest ready for foreground service + boot receiver

## 🎉 All 9 Phases Complete — AWEB v1.0.0

AWEB is a fully functional personal Android tablet browser for the Redmi Pad SE 4G.
See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) to generate your signed APK.

---

## Phase 9 Deliverable

Hardening & Personal APK — production-ready personal build:

**CrashRecoveryManager** (@Singleton):
- `install()`: registers `Thread.UncaughtExceptionHandler` — writes crash message
  + timestamp to DataStore before process dies
- `markSessionStarted()` / `markSessionClean()`: detect abnormal exits on next launch
- `checkForCrash()`: returns `CrashInfo?` — null if previous session was clean
- `restoreSession()`: full Room restore for active workspace + tabs on cold start
- Calls `TabLifecycleManager.onAppRestore()` — only active + keep_alive tabs get
  live sessions; everything else stays unloaded

**CrashRecoveryBanner** (Compose):
- Non-intrusive amber banner at top of BrowserScreen after crash
- Shows crash message + time; auto-dismisses after 8 seconds
- Positioned to the right of the sidebar (padding-start 220dp)

**HardeningViewModel** (@HiltViewModel):
- Checks crash state on init, exposes `showCrashBanner` + `crashInfo`
- `markClean()` called via `DisposableEffect(onDispose)` in `AwebRootLayout`
- `dismissCrashBanner()` clears crash DataStore entry

**DiagnosticsScreen** (Developer tool via Settings → Diagnostics):
- App info: package name, version name, version code
- Session state: active workspace, total tabs, KA tabs, active tab title, context IDs
- Crash info: last crash message + formatted timestamp
- Self-tests:
  - Isolation check: verifies each workspace has unique contextId
  - Persistence check: verifies Room has workspaces + tabs
- All results from live `AppState` snapshot — no DB queries needed

**Unit tests** (5 test classes, 25+ assertions):
- `MemoryPolicyTest`: CONSERVATIVE/BALANCED/PERFORMANCE presets, fromKey(), custom overrides
- `TabLifecycleStateTest`: all 4 states, DB key round-trip, unknown key fallback
- `WorkspaceIsolationTest`: contextId uniqueness (50 iterations), UUID validity,
  tabs within same workspace share contextId, tabs across workspaces differ
- `DownloadHandlerTest`: filename sanitisation logic (6 scenarios)
- `SearchEngineTest`: URL building for DDG/Google/Bing, fromString, trim, spaces

**Signed APK build config** (`app/build.gradle.kts`):
- `signingConfigs.release` reads from `keystore.properties` (git-ignored)
- `keystore.properties.template` committed with instructions
- `versionCode = 9`, `versionName = "1.0.0"`
- `isMinifyEnabled = true`, `isShrinkResources = true`
- Fat APK (no ABI splits) — ideal for personal sideload

**ProGuard rules** (hardened):
- GeckoView, Room, Hilt, WorkManager, Startup, Coroutines all kept
- Crash recovery classes explicitly kept (crash handler must survive obfuscation)
- `AppState` kept (volatile cross-thread access)
- Source file + line number attributes kept for readable crash traces
- `-renamesourcefileattribute SourceFile` for obfuscated builds

**BUILD_INSTRUCTIONS.md**: step-by-step keytool + Gradle commands for personal APK

**SettingsScreen**: Diagnostics entry in Developer section (gear icon → Diagnostics card)
**AwebApplication**: `crashManager.install()` is now the very first call in `onCreate()`
**MainActivity**: `DisposableEffect(onDispose)` marks session clean on composition exit

## Phase 8 Deliverable

Browser completeness — AWEB is now a full daily-use browser:

**Downloads** (`DownloadHandler`):
- `GeckoSession.ContentDelegate.onExternalResponse` → Android `DownloadManager`
- Filename sanitisation + MIME type inference from URL/extension
- System notification shown for download progress and completion
- Saves to public `Downloads/` folder

**File Upload** (`FileUploadHandler`):
- `ContentDelegate.onFilePickerRequest` → emits `PickRequest` event
- `BrowserScreen` launches `ActivityResultContracts.OpenMultipleDocuments`
- Selected URIs passed back to Gecko via `FilePickerCallback`

**Fullscreen Video** (`FullscreenHandler`):
- `ContentDelegate.onFullScreen` → `enterFullscreen()` / `exitFullscreen()`
- API 30+: `WindowInsetsController` hide/show system bars
- API 29: `SYSTEM_UI_FLAG_IMMERSIVE_STICKY` fallback
- `isFullscreen` StateFlow hides toolbar + tab strip in Compose

**Permission System** (`BrowserPermissionHandler`):
- Camera + Microphone: `PermissionDelegate.onMediaPermissionRequest`
  → emit event → `BrowserScreen` launches Android runtime permission request
  → result fed back to `MediaCallback.grant()/reject()`
- Geolocation: `PERMISSION_GEOLOCATION` → dialog → grant/deny
- Web push notifications: `PERMISSION_DESKTOP_NOTIFICATION` → dialog
- Download confirm: before each download, user sees filename/size/type dialog

**Permission Dialogs** (`PermissionDialogs.kt`):
- Camera/Mic: blue camera icon, "Allow camera and microphone access?"
- Location: green pin icon
- Notifications: amber bell icon
- Download confirm: purple download icon with filename, MIME, size

**Find in Page** (`FindInPageHandler` + `FindInPageBar`):
- `GeckoSession.finder.find()` with forward/backward
- `FindResult(current, total, found)` StateFlow
- Compose bar: ▲ ▼ query field, N/M counter (purple=found, red=not found), ✕

**Desktop/Mobile mode** (`UserAgentManager`):
- `GeckoSessionSettings.USER_AGENT_MODE_DESKTOP / MOBILE`
- Toggle reloads page automatically
- Toolbar overflow shows "Switch to Desktop" / "Switch to Mobile"

**Bookmarks** (`BookmarkEntity`, `BookmarkDao`, `BookmarkRepository`):
- Room table: id, url, title, created_at; `Index(url)` for fast lookup
- `BookmarksPanel`: slide-up sheet, newest-first list, tap-to-open, delete button
- Toolbar star button: ★ filled (amber) = bookmarked, ☆ outline = not
- `toggleBookmark()` adds or removes in one tap
- `checkBookmark(url)` called on every URL change

**Toolbar overhaul**:
- 🔒 / 🔓 security lock icon (green=HTTPS, grey=HTTP) in URL field leading icon
- ★ bookmark star with amber fill when bookmarked
- ⋮ overflow menu: Find in page / Desktop mode / Bookmarks

**`GeckoSessionWrapper`** (Phase 8 upgrade):
- `downloadHandler` + `permissionHandler` + `fileUploadHandler` constructor params
- `isSecure` StateFlow from `onSecurityChange`
- `onFullscreenChange` callback lambda wired to `FullscreenHandler`
- `onExternalResponse` → download handler
- `onFilePickerRequest` → file upload handler

**`BrowserFeatureViewModel`** (new):
- Owned by `BrowserScreen` via `hiltViewModel()`
- Consolidates bookmarks, find, UA, fullscreen, permissions, downloads, file picker

**Database**: version 2 adds `bookmarks` table; `BookmarkDao` provided in `DatabaseModule`
**DI**: `BrowserModule` provides all 6 new browser-layer singletons

## Phase 7 Deliverable

24/7 Background Survival — full foreground service + boot recovery + HyperOS guide:

**AwebForegroundService** (completed from stub):
- `startForeground()` with silent low-priority notification immediately on `onCreate()`
- Notification shows dynamic Keep Alive count + total tabs from `AppState`
- `ACTION_UPDATE_NOTIF`: updates notification in-place without restarting the service
- `ACTION_STOP_SERVICE`: clean shutdown intent
- `onTaskRemoved()`: user swiped from recents → schedules `ServiceHealthWorker` restart
- `START_STICKY`: Android restarts the service after process kill

**BootReceiver** (completed from stub):
- Handles `ACTION_BOOT_COMPLETED` + `ACTION_MY_PACKAGE_REPLACED`
- Starts `AwebForegroundService` via `startForegroundService()` (API 26+)
- Re-schedules `ServiceHealthWorker` (WorkManager tasks don't always survive reboot)

**ServiceHealthWorker** (`@HiltWorker`, PeriodicWorkRequest):
- Runs every 15 minutes (minimum WorkManager interval)
- `doWork()`: calls `startForegroundService()` to ensure service is alive
- Sends `ACTION_UPDATE_NOTIF` to refresh notification Keep Alive count
- `schedule()`: `enqueueUniquePeriodicWork(KEEP)` — idempotent, safe to call repeatedly
- `cancel()`: clean shutdown helper

**ServiceManager** (@Singleton):
- `startService()`, `stopService()`, `requestNotificationUpdate()` — single place
  for all service intent-building, safe to inject anywhere

**AwebApplication** (upgraded):
- Implements `Configuration.Provider` with `HiltWorkerFactory` for Hilt+WorkManager
- Startup sequence: GeckoRuntime → MemoryPressureReceiver → NotificationChannel →
  startService() → ServiceHealthWorker.schedule()

**HyperOsSetupScreen** (new, 6-step guide):
- Step 1: Autostart → deep-link to App Details
- Step 2: Battery Optimization → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Step 3: HyperOS Battery Saver No Restrictions → App Details (manual)
- Step 4: Lock in Recent Apps → manual instruction (no deep link possible)
- Step 5: Allow Notifications → `ACTION_APP_NOTIFICATION_SETTINGS`
- Step 6: Keep Screen Awake (Optional) → AWEB Settings toggle
- Each step: expandable card, "Open Settings" deep-link, "Mark done" toggle
- Cap progress: `done / total required` with green checkmark when complete
- "All done — AWEB is ready!" button enabled only when all required steps checked

**SetupViewModel**: DataStore-persisted `setup_done` flag; live `batteryOptimizationIgnored` check

**MainActivity** (upgraded):
- Injects `ServiceManager`; `LaunchedEffect(tabState.tabs)` → `requestNotificationUpdate()`
  so notification count stays accurate as tabs open/close
- `LaunchedEffect(setupDone)` → shows `HyperOsSetupScreen` on first ever launch
- Setup screen added as `slideInVertically` animated overlay (dismissible)
- Settings screen now has "HyperOS Setup Guide" row → tappable card → opens setup screen

**Manifest**: `xmlns:tools` added; Hilt WorkManager `InitializationProvider` with
`tools:node="remove"` on default WorkManager initialiser (required for custom init)

## Phase 6 Deliverable

Memory modes & stability — full settings + memory dashboard:

**SettingsViewModel**
- Collects all settings as a single combined StateFlow<SettingsUiState>
- setMemoryMode(): applies preset limits (Conservative 0/2, Balanced 2/3, Performance 5/5)
- setMaxRecentLiveTabs() / setMaxKeepAliveTabs(): fine-grain manual overrides
- setDefaultHomepage() / setDefaultSearchEngine() / setKeepScreenAwake()
- simulatePressure(trimLevel): triggers real TabLifecycleManager.onMemoryPressure()
  for testing — same code path as the Android system callback
- refreshLiveSessionCount(): reads TabSessionManager.liveSessionCount on demand

**SettingsScreen**
- Memory Mode selector: 3 radio cards (Conservative / Balanced ★ / Performance)
  with preset description, auto-applies limits on selection
- Fine-grain steppers: max recent live tabs (0–10), max Keep Alive tabs (1–10)
- Memory Dashboard card: live session count, policy summary, lifecycle legend key
  with expand arrow → full MemoryDashboardScreen
- Browser prefs: homepage editor (inline OutlinedTextField), search engine chips
- Display: keep screen awake toggle

**MemoryDashboardScreen** (full-screen detail view)
- Animated ring chart: live sessions / total tabs, colour shifts green→amber→red
- Per-state animated progress bars: Active / Keep Alive / Recent / Unloaded
- Policy grid cards: Mode / Max Recent / Max KA
- Pressure simulation panel: Mild / Low / Critical / Severe buttons
  — each calls simulatePressure() and refreshes count after 300ms
- Real-time tab-state list from AppState snapshot (active first, then KA, then recency)

**MainActivity** (Phase 6 upgrade)
- Injects SettingsViewModel; LaunchedEffect reacts to keepScreenAwake →
  window.addFlags / clearFlags(FLAG_KEEP_SCREEN_ON) live without restart
- Settings rendered as slide-in AnimatedVisibility overlay (no NavHost needed)

**WorkspaceSidebar** (Phase 6 upgrade)
- Settings gear button at bottom below New Workspace
- onOpenSettings callback plumbed through to MainActivity

## Phase 5 Deliverable

Keep Alive tab feature — fully integrated end to end:
- `KeepAliveManager` (@Singleton): toggle(), enforceCap(), getKeepAliveTabs()
  - Checks cap before enabling; emits CapExceeded / Enabled / Disabled events
  - On enable: creates live GeckoSession immediately + sets KEEP_ALIVE state in Room
  - On disable: reverts to RECENT state, triggers rebalance (may get unloaded by LRU)
  - enforceCap(): called on settings cap-reduction — removes oldest KA tabs beyond new limit
- `KeepAliveIndicator`: animated pulsing amber bolt, 3 sizes (SMALL/MEDIUM/LARGE)
- `KeepAliveBadge`: amber pill badge used in tab overview cards
- `KeepAlivePanel`: bottom-sheet overlay — cap progress bar, KA tab list, empty hint
- `KeepAliveCapDialog`: shown when user tries to exceed the cap — explains options
- `KeepAliveToast`: 2-second auto-dismiss bottom-right toast confirming on/off
- `TabStrip`: amber border + animated bolt on KA tabs; proper toggle label in context menu
- `TabOverviewScreen`: KA tabs sorted first, amber border, KeepAliveBadge in card
- `BrowserToolbar`: amber animated bolt button — opens KeepAlivePanel; grey when none active
- `WorkspaceViewModel`: enforces cap when maxKeepAliveTabs setting changes (AppState snapshot)
- `TabViewModel`: routes toggleKeepAlive / disableKeepAlive through KeepAliveManager;
  exposes keepAliveEvent StateFlow for BrowserScreen to react to

## Phase 4 Deliverable

Automatic tab lifecycle management:
- `TabLifecycleState` enum: ACTIVE / RECENT / KEEP_ALIVE / UNLOADED
- `MemoryPolicy` data class: maxRecentLive + maxKeepAlive per mode
- `TabLifecycleManager` — the lifecycle brain:
  - `onTabSelected()` — promotes new active, demotes old to RECENT, rebalances all
  - `onTabClosed()` — frees session, rebalances remaining
  - `onMemoryPressure()` — responds to Android TRIM_MEMORY levels (mild→severe cascade)
  - `onAppRestore()` — on restart: live session for active + keep_alive only, rest unloaded
  - `rebalance()` — LRU eviction with pinned tab resistance, enforces MemoryPolicy limits
- `MemoryPressureReceiver` — implements ComponentCallbacks2, registered in Application
- `AppState` — volatile in-process snapshot for cross-thread tab/workspace reads
- `MemoryStatusBar` — live ● active ◆ keep_alive ◐ recent ○ unloaded counts in sidebar
- Settings changes (MemoryMode, maxKeepAliveTabs) auto-propagate to lifecycle manager

## Phase 2 Deliverable

Fully isolated multiple workspaces:
- `WorkspaceRepository` — CRUD, default workspace seed, reorder
- `SettingsRepository` — typed settings with defaults (MemoryMode, SearchEngine)
- `WorkspaceSessionManager` — maps workspace → GeckoSessionWrapper via permanent contextId
- `WorkspaceViewModel` — drives all workspace + browser state
- `WorkspaceSidebar` — persistent left-rail with tap-to-switch, long-press menu (rename/clear/delete)
- `CreateWorkspaceDialog`, `RenameWorkspaceDialog`, `DeleteWorkspaceDialog`, `ClearWorkspaceDataDialog`
- `BrowserScreen` upgraded to be workspace-aware; active session changes on workspace switch
- Workspace colour indicator in toolbar changes per active workspace
- Each workspace cookie/storage is fully isolated via GeckoView `contextId`

---

## How to Build

### Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 35
- JDK 17
- ADB connected to Redmi Pad SE 4G (for device testing)

### Steps

```bash
# Clone / open this folder in Android Studio
# Sync Gradle
# Run on device:
./gradlew installDebug
adb shell am start -n com.aweb.browser.debug/com.aweb.browser.ui.MainActivity
```

### GeckoView Note

GeckoView is fetched from `maven.mozilla.org`.  
Make sure you have internet access during the first Gradle sync.  
The dependency is large (~70 MB AAR) — the first sync takes a few minutes.

---

## Key Files — Phase 1

```
app/src/main/java/com/aweb/browser/
├── AwebApplication.kt              # App entry, GeckoRuntime init
├── gecko/
│   ├── GeckoRuntimeManager.kt      # Singleton GeckoRuntime holder
│   └── GeckoSessionWrapper.kt      # Wraps GeckoSession, exposes StateFlows
├── ui/
│   ├── MainActivity.kt             # Single Activity
│   ├── BrowserScreen.kt            # Compose browser UI + GeckoView interop
│   ├── BrowserViewModel.kt         # ViewModel — owns session, exposes state
│   └── theme/AwebTheme.kt          # Dark purple theme
├── data/
│   ├── entity/                     # Room entities (workspace, tab, setting)
│   └── db/                         # DAOs
├── di/
│   └── DatabaseModule.kt           # Hilt providers
└── service/
    ├── AwebForegroundService.kt    # Stub — completed in Phase 7
    └── BootReceiver.kt             # Stub — completed in Phase 7
```

---

## Security Defaults

- No incognito mode
- No cloud backup (`allowBackup="false"`)
- HTTPS preferred
- Workspace cookie isolation via GeckoView `contextId`
- Camera/mic/location require runtime permission

---

## HyperOS Survival (Phase 7)

After Phase 7, AWEB will guide you through:
1. Autostart → ON
2. Battery optimization → No restrictions
3. Lock in recent apps
4. Optional: keep screen awake while charging
