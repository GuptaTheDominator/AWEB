# AWEB Phase 3 Runtime Stability Audit — Startup + Memory

Date: 2026-06-18  
Commit series started after Phase 1/2 fix upload.

## Scope started

Phase 3 focuses on runtime stability:

1. App startup sequence
2. GeckoRuntime initialization timing
3. Foreground service startup/restart behavior
4. Crash recovery installation
5. WorkManager health checks
6. Android/HyperOS memory pressure behavior
7. Tab restore and live-session policy correctness

## Validation already run after Phase 1/2 fixes

- `./gradlew clean assembleRelease testReleaseUnitTest` — passed
- `apksigner verify` — passed
- `zipalign -c` — passed
- `./gradlew lintRelease` — 0 errors, warnings only

## Phase 3 startup sequence map

Current startup path:

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

## Phase 3 issues found and fixed immediately

### P3-F1 — Workspace restore gate was global instead of per-workspace

File:

- `TabLifecycleManager.kt`

Problem:

`isFirstRestoreDone` was a single global boolean. After the first workspace restored, switching to another workspace would skip restore logic for that workspace.

Fix:

Replaced the boolean with a concurrent set of restored workspace IDs:

```kotlin
private val restoredWorkspaceIds = ConcurrentHashMap.newKeySet<String>()
```

Now each workspace can restore active/Keep Alive session state once.

### P3-F2 — Memory pressure fallback active tab used list order, not newest access

File:

- `TabLifecycleManager.kt`

Problem:

If no tab had `isActive`, memory pressure fallback used `lastOrNull { lastAccessed > 0 }`, which depends on list order and can choose the wrong tab.

Fix:

Changed fallback to:

```kotlin
allTabs.maxByOrNull { it.lastAccessed }
```

### P3-F3 — Application logged foreground-service success even when ServiceManager swallowed failure

Files:

- `ServiceManager.kt`
- `AwebApplication.kt`

Problem:

`ServiceManager.startService()` caught startup exceptions internally and returned `Unit`, but `AwebApplication` always logged success afterward.

Fix:

`startService()` now returns `Boolean`, and `AwebApplication` logs success/rejection accurately.

### P3-F4 — `onTaskRemoved()` health-worker scheduling could throw

File:

- `AwebForegroundService.kt`

Problem:

`ServiceHealthWorker.schedule()` was called without a guard inside `onTaskRemoved()`.

Fix:

Wrapped scheduling in try/catch so service teardown does not crash.

## Remaining Phase 3 audit items to continue

These still need deeper runtime/device validation:

1. Verify Hilt injection overhead in Gecko child processes despite main-process guard.
2. Confirm foreground-service behavior on Android 13/14 when notification permission is denied.
3. Test WorkManager foreground-service restart behavior under background restrictions.
4. Stress-test tab switching with 10+ tabs on Redmi Pad SE / 4 GB RAM.
5. Validate Keep Alive tabs under `TRIM_MEMORY_RUNNING_LOW`, `CRITICAL`, and `COMPLETE`.
6. Validate crash banner behavior after:
   - real uncaught exception
   - LMKD/background kill
   - clean user exit
7. Confirm external URL handling does not race with first workspace creation.
8. Confirm Gecko storage clearing fully clears cookies/localStorage/IndexedDB for a workspace context.

## Next recommended Phase 3 actions

1. Add debug-only runtime diagnostics counters for:
   - opened wrappers
   - closed wrappers
   - live Gecko sessions
   - runtime-ready state
   - last memory trim level
2. Add a manual Redmi test script/checklist.
3. Add unit tests for per-workspace restore gating and memory fallback selection.
4. Add a controlled crash trigger behind diagnostics for validating crash recovery.
