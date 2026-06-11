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
| 7     | 24/7 background survival (HyperOS) | 🔲 Next      |
| 8     | Browser completeness                | 🔲 Planned   |
| 9     | Hardening + personal APK            | 🔲 Planned   |

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
