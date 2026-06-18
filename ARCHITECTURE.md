# AWEB Architecture

This document explains how AWEB is structured internally — useful if you want to understand, extend, or debug the app.

---

## Core Principle

> Every workspace is a separate browser profile. Tabs within one workspace share that profile. Nothing crosses workspace boundaries.

This is enforced by GeckoView's `contextId`. Each workspace gets a UUID-based `contextId` on creation. This value is **permanent** — it never changes, because changing it would destroy the workspace's browser storage. GeckoView ties all cookies, localStorage, IndexedDB, cache, and login sessions to this ID.

---

## Layer Map

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Compose UI Layer                            │
│  MainActivity/NavHost  HomeScreen  WorkspacesScreen  BrowserScreen  │
│  TabsManagerScreen  SettingsScreen  SetupGuide  Diagnostics         │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ StateFlow / events
┌───────────────────────────▼─────────────────────────────────────────┐
│                        ViewModel Layer                              │
│  WorkspaceViewModel  TabViewModel  BrowserFeatureViewModel          │
│  SettingsViewModel  HardeningViewModel  SetupViewModel              │
└──────┬────────────────────┬────────────────────────────────────────┘
       │                    │
┌──────▼──────┐    ┌────────▼────────────────────────────────────────┐
│  Data Layer │    │              Browser / Lifecycle Layer           │
│  Room DB    │    │  GeckoRuntimeManager  GeckoSessionWrapper        │
│  WorkspaceR │    │  TabSessionManager    TabLifecycleManager        │
│  TabRepo    │    │  KeepAliveManager     MemoryPressureReceiver     │
│  SettingsR  │    └─────────────────────────────────────────────────┘
│  BookmarkR  │
└─────────────┘
┌─────────────────────────────────────────────────────────────────────┐
│                      Background Layer                               │
│  AwebForegroundService  BootReceiver  ServiceHealthWorker           │
│  ServiceManager                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## GeckoView Integration

### Runtime (one per process)
`GeckoRuntimeManager` holds a singleton `GeckoRuntime`. GeckoView enforces this — creating more than one throws. It is initialised in `AwebApplication.onCreate()` before any Activity draws.

### Sessions (one per tab)
`GeckoSessionWrapper` wraps a single `GeckoSession`. It:
- Holds `contextId` (the workspace's isolation key)
- Exposes `url`, `title`, `progress`, `loading`, `canGoBack`, `canGoForward`, `isSecure` as `StateFlow`s
- Wires `NavigationDelegate`, `ProgressDelegate`, `ContentDelegate`, `PermissionDelegate`
- Handles `onExternalResponse` (downloads) and `onFullScreen` (fullscreen video)

### Session Lifecycle
```
Tab created
    └── GeckoSessionWrapper created with workspace contextId
        └── session.open(GeckoRuntimeManager.runtime)
        └── session.loadUri(url)

Tab switched away
    └── TabLifecycleManager.onTabSelected()
        └── old tab → session.setActive(false) → state = RECENT
        └── new tab → session.setActive(true)  → state = ACTIVE
        └── rebalance() → unload tabs beyond policy limits

Tab unloaded
    └── TabSessionManager.unload(tabId)
        └── session.close()
        └── entry removed from sessions map

Tab reselected (was unloaded)
    └── TabSessionManager.getOrCreate(tab, workspace)
        └── new GeckoSessionWrapper created
        └── session.open() + loadUri(tab.url)
```

---

## Workspace Isolation — How It Works

```kotlin
// On workspace creation (WorkspaceRepository.createWorkspace):
val contextId = "aweb_ws_${UUID.randomUUID()}"   // permanent, never changes

// On every GeckoSession for a tab in this workspace:
GeckoSessionSettings.Builder().contextId(contextId)

// Result: all tabs in workspace A share contextId A
//         all tabs in workspace B share contextId B
//         GeckoView enforces total storage separation between A and B
```

Clearing workspace data closes live sessions for that workspace and calls GeckoView `StorageController.clearDataForSessionContext(contextId)`, so cookies/storage/cache for that workspace context are cleared without changing the permanent workspace identity.

---

## Tab Lifecycle State Machine

```
                    ┌─────────────────┐
                    │    UNLOADED     │◄──────────────────────────┐
                    │  (Room only)    │                            │
                    └────────┬────────┘                            │
                             │ user selects tab                    │
                    ┌────────▼────────┐                            │
              ┌────►│     ACTIVE      │                            │
              │     │ (live, focused) │                            │ memory
              │     └────────┬────────┘                            │ pressure /
              │              │ switch away                         │ LRU eviction
              │     ┌────────▼────────┐    user marks tab         │
              │     │     RECENT      │───────────────────┐        │
              │     │ (live, paused)  │                   │        │
              │     └────────┬────────┘                   │        │
              │              │ LRU eviction                │        │
              │              └────────────────────────────►┼────────┘
              │                                            │
              │     ┌────────────────────┐                 │
              └─────│    KEEP_ALIVE      │◄────────────────┘
                    │ (live, high prio)  │  user long-presses tab
                    └────────────────────┘  → "Keep tab alive"
```

States are persisted to Room (`TabEntity.lastLifecycleState`) after every transition so they survive app restarts.

---

## Memory Policy (TabLifecycleManager)

`rebalance()` runs after every tab switch:

```
Priority order (highest to lowest):
  1. ACTIVE tab          — always live, always 1
  2. KEEP_ALIVE tabs     — live up to policy.maxKeepAlive, sorted by lastAccessed
  3. RECENT tabs (pinned resist eviction more) — live up to policy.maxRecentLive
  4. Everything else     — UNLOADED (session.close())
```

`onMemoryPressure(level)` responds to Android TRIM_MEMORY signals:

| Android level | AWEB action |
|---|---|
| `UI_HIDDEN` | Trim 1 oldest recent tab |
| `RUNNING_LOW` | Trim all recent tabs |
| `RUNNING_CRITICAL` | Trim everything except ACTIVE + KEEP_ALIVE |
| `COMPLETE` / `onLowMemory` | Trim everything except ACTIVE |

---

## Data Layer

### Room Database (`aweb.db`)

```sql
workspaces  id, name, context_id, color_hex, icon_name, order_index, is_active
tabs        id, workspace_id, url, title, order_index, is_active, is_pinned,
            keep_alive, last_lifecycle_state, last_accessed, user_agent_mode
app_settings  key, value
bookmarks   id, url UNIQUE, title, created_at
```

`context_id` has a `UNIQUE` index. It is set once and never updated.

All DAOs expose `Flow<List<T>>` for live observation and `suspend fun` for mutations.

### AppState (in-process volatile snapshot)

`AppState` is a Kotlin `object` backed by an `AtomicReference<Snapshot>` updated by `TabViewModel` on every relevant state collect. It exists so `MemoryPressureReceiver` and service notification code can read a consistent tab/workspace/search-engine snapshot without suspending.

---

## Background Survival

```
App process running
    AwebForegroundService  ← priority: FOREGROUND (harder for system to kill)
        └── Persistent notification with KA tab count
        └── START_STICKY while survival mode is enabled
        └── notification Exit disables survival mode and returns START_NOT_STICKY
        └── onTaskRemoved → ServiceHealthWorker.schedule() only when enabled

Every 15 minutes (WorkManager)
    ServiceHealthWorker.doWork()
        └── checks ServicePreferences.isEnabled()
        └── startForegroundService() (restart if enabled and dead)
        └── ACTION_UPDATE_NOTIF (refresh notification count)

On device boot / app update
    BootReceiver.onReceive(BOOT_COMPLETED | MY_PACKAGE_REPLACED)
        └── startForegroundService()
        └── ServiceHealthWorker.schedule()

On HyperOS kill (battery saver, recents clear)
    → Service dies → WorkManager fires on next 15-min slot → restarts service
    → All tabs restore from Room via TabLifecycleManager.onAppRestore()
```

---

## Crash Recovery

```
App start
    CrashRecoveryManager.install()
        └── Thread.setDefaultUncaughtExceptionHandler()
    CrashRecoveryManager.markSessionStarted()
        └── DataStore: session_started=true, session_clean=false

App running normally
    DisposableEffect.onDispose (MainActivity)
        └── CrashRecoveryManager.markSessionClean()
            └── DataStore: session_clean=true

App crashes
    UncaughtExceptionHandler fires
        └── DataStore: session_clean=false, last_crash_message, last_crash_time
        └── Default handler runs → Android crash dialog

Next launch
    CrashRecoveryManager.checkForCrash()
        └── session_started=true AND session_clean=false → CrashInfo returned
    HardeningViewModel shows CrashRecoveryBanner (amber, auto-dismisses 8s)
```

---

## Security Design Decisions

| Decision | Reason |
|---|---|
| No `allowBackup` | Browser sessions must never leave the device |
| No incognito mode | Workspace isolation covers this use case better |
| No native Google Sign-In | Users log in inside the browser context — tokens never touch app code |
| Runtime camera/mic/location | Never pre-granted; asked per-site when GeckoView requests |
| No JavaScript bridge | No `addJavascriptInterface()` ever called on GeckoView |
| `dataExtractionRules` exclude all | Cloud backup and device transfer both blocked |
| HTTPS preferred | `onSecurityChange` exposes `isSecure` to show lock/unlock indicator |

---

## Key Files Quick Reference

| File | Purpose |
|---|---|
| `AwebApplication.kt` | App entry; init order: crash handler → Gecko → memory receiver → service → WorkManager |
| `GeckoSessionWrapper.kt` | Per-tab Gecko session; all delegates; StateFlows |
| `TabLifecycleManager.kt` | LRU eviction, memory pressure cascade, app restore |
| `KeepAliveManager.kt` | Keep Alive toggle, cap enforcement, event relay |
| `BrowserScreen.kt` | Real Gecko browser UI composition; all overlays wired |
| `TabViewModel.kt` | Tab CRUD, workspace switching, session wiring |
| `WorkspaceViewModel.kt` | Workspace CRUD, all-tab dashboard snapshot, settings propagation |
| `AwebForegroundService.kt` | Persistent notification; user-respectful survival mode |
| `ServiceHealthWorker.kt` | WorkManager periodic restart check |
| `CrashRecoveryManager.kt` | Clean/unclean exit detection; crash banner trigger |
