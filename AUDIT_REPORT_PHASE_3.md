# AWEB Phase 3 Runtime Stability Audit — Completed Source-Level Pass

Date: 2026-06-18  
Audited/fixed through commit series ending at `043f259` before `v2.6.5` tag.

> Note: This is a source/build/static-runtime audit. Real LMKD/HyperOS kill behavior still must be verified on the Redmi Pad SE hardware because the sandbox cannot emulate Xiaomi/HyperOS background policy or real 4 GB RAM pressure.

---

## Phase 3 scope

Phase 3 covered runtime stability around:

1. App startup sequence
2. GeckoRuntime initialization timing
3. Foreground service startup/restart behavior
4. Crash recovery installation
5. WorkManager health checks
6. Android/HyperOS memory pressure behavior
7. Tab restore and live-session policy correctness

---

## Validation run during Phase 1/2 + Phase 3 fix cycle

After the major Phase 1/2 fixes:

- `./gradlew clean assembleRelease testReleaseUnitTest` — passed
- `apksigner verify` — passed
- `zipalign -c` — passed
- `./gradlew lintRelease` — 0 errors, warnings only

After Phase 3 startup/memory fixes:

- `./gradlew compileReleaseKotlin testReleaseUnitTest` — passed

The final `v2.6.5` release is intended to be built by GitHub Actions using the stable repository secrets.

---

## Startup sequence map

```text
AwebApplication.onCreate()
├─ super.onCreate() / Hilt injection
├─ child-process guard using process name
├─ crash logger + CrashRecoveryManager.install()
├─ notification channel creation
├─ ServiceManager.startService()
├─ register MemoryPressureReceiver
├─ mainHandler.post { GeckoRuntimeManager.init() }
└─ mainHandler.postDelayed { ServiceHealthWorker.schedule() }

MainActivity.onCreate()
├─ edge-to-edge
├─ ACTION_VIEW URL capture
├─ battery/charging receiver for keep-screen-awake gating
└─ Compose root
   ├─ WorkspaceViewModel ensures default workspace
   ├─ TabViewModel attaches active workspace and sessions
   ├─ SettingsViewModel applies settings
   └─ HardeningViewModel checks crash banner state
```

---

## Completed Phase 3 fixes

### P3-F1 — Workspace restore gate is now per-workspace

File:

- `TabLifecycleManager.kt`

Problem:

A single global `isFirstRestoreDone` flag meant only the first workspace restored active/Keep Alive sessions. Other workspaces could skip restore behavior after switching.

Fix:

Replaced with a concurrent restored-workspace ID set:

```kotlin
private val restoredWorkspaceIds = ConcurrentHashMap.newKeySet<String>()
```

Result:

Each workspace can restore once, independently.

---

### P3-F2 — Memory pressure active fallback uses latest access time

File:

- `TabLifecycleManager.kt`

Problem:

If no tab had `isActive`, memory pressure fallback used list order:

```kotlin
lastOrNull { it.lastAccessed > 0 }
```

Fix:

Changed to:

```kotlin
allTabs.maxByOrNull { it.lastAccessed }
```

Result:

The most recently accessed tab is protected when the active flag is unavailable.

---

### P3-F3 — Foreground service startup result is now accurate

Files:

- `ServiceManager.kt`
- `AwebApplication.kt`

Problem:

`ServiceManager.startService()` caught exceptions internally and returned `Unit`, but `AwebApplication` always logged success afterward.

Fix:

`startService()` now returns `Boolean`. `AwebApplication` logs success only when startup was actually requested successfully.

Result:

Startup diagnostics now distinguish real foreground-service start from rejected startup.

---

### P3-F4 — Service `onTaskRemoved()` scheduling is guarded

File:

- `AwebForegroundService.kt`

Problem:

`ServiceHealthWorker.schedule()` could throw during service/task teardown.

Fix:

Wrapped scheduling in `try/catch`.

Result:

Service teardown does not crash if WorkManager scheduling fails.

---

### P3-F5 — Crash recovery persistence is installed during app startup

Files:

- `AwebApplication.kt`
- `CrashRecoveryManager.kt`

Problem from Phase 2:

`CrashRecoveryManager.install()` existed but was not called.

Fix:

`AwebApplication` now installs it in the main process after the process guard.

Result:

Uncaught crash message/time can be written before process death.

---

### P3-F6 — Gecko session lifecycle operations are safer under memory pressure

Files:

- `GeckoSessionWrapper.kt`
- `TabSessionManager.kt`
- `TabLifecycleManager.kt`
- `WorkspaceSessionManager.kt`

Fixes completed:

- Wrapper `close()` dispatches Gecko close work to Main.
- Wrapper scope is cancelled on permanent close to prevent collector leaks.
- Active/inactive changes go through wrapper `setActive()` main-thread guard.
- Lifecycle manager no longer directly calls `GeckoSession.setActive()` from IO.

Result:

Reduced risk of Gecko main-thread violations during tab switching, restore, and memory pressure.

---

### P3-F7 — Memory policy settings now affect actual runtime policy

Files:

- `MemoryMode.kt`
- `TabLifecycleManager.kt`
- `WorkspaceViewModel.kt`

Problem from Phase 2:

`maxRecentLiveTabs` setting was shown in UI but not applied to `TabLifecycleManager`.

Fix:

`WorkspaceViewModel` observes memory mode, max recent tabs, and max Keep Alive tabs together, then applies all three to the runtime policy.

Result:

Settings UI and runtime memory behavior now match.

---

### P3-F8 — Keep Alive cap reduction unloads excess sessions

File:

- `KeepAliveManager.kt`

Problem from Phase 2:

Lowering the Keep Alive cap only changed DB flags; extra live sessions could remain open until later.

Fix:

Excess Keep Alive tabs are unmarked, marked unloaded, and their sessions are closed immediately.

Result:

Keep Alive cap changes now produce immediate memory relief.

---

### P3-F9 — Workspace data clearing uses Gecko session-context clearing

Files:

- `GeckoRuntimeManager.kt`
- `WorkspaceViewModel.kt`

Problem from Phase 2:

Clear workspace data only closed sessions, leaving Gecko context storage intact.

Fix:

Added:

```kotlin
storageController.clearDataForSessionContext(contextId)
```

Result:

Workspace clear/delete now requests Gecko to clear data for that workspace context.

---

### P3-F10 — External URL startup race is handled

File:

- `MainActivity.kt`

Fix:

External `ACTION_VIEW` URLs are stored in a `StateFlow` and opened only after an active workspace exists.

Result:

Default-browser launches no longer lose the URL during first workspace creation.

---

## Remaining hardware-only validation checklist

These cannot be fully proven in the sandbox and should be tested on the Redmi Pad SE:

| Area | Manual test |
|---|---|
| Cold launch | App starts, service notification appears, first tab loads |
| Default browser | `ACTION_VIEW` link opens as new tab |
| Gecko child process | No crash cascade during heavy browsing |
| Memory pressure | 10+ tabs, switch rapidly, no active-tab unload |
| Keep Alive | 3 Keep Alive tabs survive workspace/tab switching |
| Cap reduction | Reducing Keep Alive cap unloads excess sessions immediately |
| Crash recovery | Forced crash shows useful banner on next launch |
| HyperOS | Autostart and service after reboot |
| Workspace clear | Cookies/localStorage cleared for only that workspace |

---

## Phase 3 source-level conclusion

Phase 3 is complete at source/build/static-runtime level. The code now has corrected startup logging, crash persistence installation, per-workspace restore, safer Gecko session threading, and memory policy correctness.

The next phase should be real-device runtime validation and UI/UX enhancement work.
