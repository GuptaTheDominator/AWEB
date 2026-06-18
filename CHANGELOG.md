## [v2.6.5] — 2026-06-17

### Fixed — Phase 1/2 audit issues

- Wired Gecko sessions to download, permission, and file-upload handlers.
- Added Gecko file prompt handling for `<input type="file">`.
- Added external `ACTION_VIEW` URL handling for default-browser launches.
- Main-confined Gecko session active/close operations and cancelled wrapper scopes on close.
- Added per-workspace Gecko session-context data clearing via `StorageController.clearDataForSessionContext`.
- Installed crash recovery uncaught-exception persistence.
- Applied custom recent-live tab limits to the actual memory policy.
- Enforced Keep Alive cap changes by removing flags and unloading excess sessions.
- Fixed media/location/notification runtime permission flow.
- Hardened popup/new-window handling.
- Replaced destructive Room fallback with explicit 1→2 and 2→3 migrations.
- Fixed search query URL encoding.
- Added source-side release workflow support for stable secret-backed signing.
- Added Close All confirmation and several UI correctness fixes.

---

## [v2.6.4] — 2026-06-17

### Fixed — release build compilation

- Updated GeckoView `onSecurityChange` callback type for GeckoView 132.
- Restored missing `UserAgentManager` import in `TabSessionManager`.
- Fixed Compose `Modifier.padding` usage in the toolbar URL field.
- Added URL/domain detection helper used by `TabViewModel.loadUrl()`.
- Bumped Android version to `versionCode 39` / `versionName 2.6.4`.

---

## [V2.1] — 2026-06-12

### Fixed — Multi-process crash (AwebApplication in child processes)

CONFIRMED from logcat (logcat_recording_2026-06-12_06-30-31.txt):

  pid 29650 = main process com.aweb.browser  
    → GeckoRuntime ready on main  ✓

  pid 29733 = com.aweb.browser:tab13 (GeckoView child)
    → AwebApplication.onCreate() runs again  ← THE BUG
    → GeckoRuntimeManager.init() called again
    → GeckoRuntime.create() throws:
       IllegalStateException: Failed to initialize GeckoRuntime
       (GeckoThread already launched)
    → Crash cascade → all child processes die → app closes

GeckoView spawns multiple child processes for content rendering
(tab0..tab39), GPU compositing (:gpu), networking (:socket), etc.
Android calls Application.onCreate() in EVERY process, including these
child processes. Our AwebApplication was calling GeckoRuntime.create()
in every child process, which always fails because GeckoRuntime was
already created in the main process.

FIX — isMainProcess() guard in AwebApplication:
  if (!isMainProcess()) return  // child processes do nothing extra

isMainProcess() checks:
  - API 28+: Application.getProcessName()
  - API < 28: read /proc/{pid}/cmdline
  Returns true only if processName == packageName (no ":tab0" suffix)

Only the main process runs:
  - installExceptionLogger()
  - createNotificationChannel()
  - serviceManager.startService()
  - registerComponentCallbacks()
  - GeckoRuntimeManager.init()
  - ServiceHealthWorker.schedule()

Child processes return immediately after super.onCreate(),
letting GeckoView manage them cleanly.

## [v1.0.8] — 2026-06-12

### Fixed — CONFIRMED CRASH from logcat analysis (v1.0.6/v1.0.7)

**Root cause identified from logcat (logcat_recording_2026-06-12_05-33-22.txt):**

  FATAL EXCEPTION: DefaultDispatcher-worker-2
  java.lang.IllegalThreadStateException:
    Expected thread 2 ("main"), but running on thread 2884 ("DefaultDispatcher-worker-2")
    at GeckoSession.getSurfaceBounds()
    at Autofill$Session.getDefaultDimensions()
    at Autofill$Session.<init>()
    at GeckoSession.<init>()         <-- GeckoSession() constructor requires Main thread
    at GeckoSessionWrapper.createSession()
    at TabSessionManager.getOrCreate()  <-- called from IO dispatcher

Previous fix (v1.0.4) put open() on Main thread but GeckoSession() CONSTRUCTOR
also requires Main thread. TabLifecycleManager and KeepAliveManager both call
getOrCreate() from CoroutineScope(Dispatchers.IO), bypassing withContext(Main).

**Fix — GeckoSessionWrapper lazy session pattern:**
- session property changed from val (eager, in constructor) to lazy @Volatile var
- GeckoSession() is now created only inside open(), which always dispatches to Main
- The constructor no longer touches GeckoView at all — safe to call from any thread
- open() first creates the GeckoSession on Main, then opens it with GeckoRuntime
- loadUrl() checks session.isOpen and schedules postDelayed if not yet open

**Additional fixes:**
- GeckoViewComposable: null-safe access to wrapper.session (now nullable)
- TabSessionManager.setActiveTab: .session?.setActive() null-safe
- TabLifecycleManager: .session?.setActive() null-safe
- BrowserScreen: only renders GeckoViewComposable when session != null
- GeckoViewComposable update block: !(===) identity check before setSession

## [v1.0.7] — 2026-06-12

### Fixed — deep codebase scan (22 issues resolved)

**Deprecated API fixes (compiler warnings → clean):**
- Replaced `Divider()` with `HorizontalDivider()` in 9 Compose files
  (BookmarksPanel, DiagnosticsScreen, KeepAlivePanel, MemoryDashboardScreen,
  SettingsScreen, HyperOsSetupScreen, TabOverviewScreen, TabStrip, WorkspaceSidebar)
- Replaced `Icons.Filled.ArrowBack` with `Icons.AutoMirrored.Filled.ArrowBack`
  in 5 files (BrowserScreen, DiagnosticsScreen, MemoryDashboardScreen,
  SettingsScreen, HyperOsSetupScreen) — fixes RTL layout mirroring
- Added `@file:Suppress("DEPRECATION")` to 3 files using `TRIM_MEMORY_COMPLETE`
  (MemoryPressureReceiver, TabLifecycleManager, MemoryDashboardScreen)
- Added `@Suppress("OVERRIDE_DEPRECATION")` to `onTrimMemory` override
  in MemoryPressureReceiver

**Data integrity fix:**
- Added `@Transaction` to `TabDao.setActive()` — prevents race condition
  where is_active flags could be left in an inconsistent state if two
  concurrent writes happened simultaneously

**Type safety fix:**
- `GeckoResult.fromValue(null)` → `GeckoResult.fromValue<Void>(null)`
  in FindInPageHandler — explicit type parameter for GeckoView 132

**Code clarity:**
- `runBlocking` in CrashRecoveryManager annotated with
  `@Suppress("BlockingMethodInNonBlockingContext")` and documented:
  it is intentional — the process is dying and we must write synchronously

**README.md:**
- Full rewrite with badges, version history table, architecture diagram,
  HyperOS setup table with explanations, install instructions
- Added warning about step 3 (HyperOS Battery Saver No Restrictions)
  being the critical setting to prevent LMKD kills

---

## [v1.0.5] — 2026-06-11

### Fixed — startup crash (UI shows for 1 second then closes)

Deep static analysis found 5 additional crash causes on top of v1.0.4:

1. **workManagerConfiguration getter** — accessed `workerFactory` (lateinit var)
   without a guard. If WorkManager somehow calls this getter before Hilt injection,
   it throws UninitializedPropertyAccessException.
   FIX: Wrapped in try-catch, returns a default Configuration as fallback.

2. **loadUrl() race condition** — `GeckoSessionWrapper.loadUrl()` called `open()`
   (which dispatches to Main thread via Handler.post asynchronously), then
   immediately called `session.loadUri()` on the current thread. Since `open()`
   hadn't executed yet, `loadUri()` threw IllegalStateException: session not open.
   FIX: If session not open, `loadUrl()` calls `open()` then schedules
   `loadUri()` via `mainHandler.postDelayed(500ms)` ensuring open completes first.

3. **safeGetSession retry count too low** — 5 retries × 500ms = 2.5s max wait.
   GeckoRuntime can take longer on first cold start on a real device.
   FIX: Increased to 8 retries with 300ms × (attempt+1) progressive backoff.

4. **TabSessionManager missing Context** — constructor wasn't passing appContext
   to GeckoSessionWrapper in some code paths.
   FIX: All GeckoSessionWrapper constructors now receive appContext consistently.

5. **GeckoSessionWrapper mainHandler** — repeated `Handler(Looper.getMainLooper())`
   creation. Moved to a single field to avoid overhead.

# Changelog

All notable changes to AWEB are documented here.

---

## [v1.0.1] — 2026-06-11

### Changed
- **APK size reduced from 639 MB → 194 MB** by building ARM64-only ABI split
  - `splits { abi { include("arm64-v8a") } }` strips unused x86/x86_64/armeabi-v7a GeckoView native libs
  - Suitable for Redmi Pad SE 4G and all other ARM64 Android devices

### Fixed
- ProGuard/R8: Added `-dontwarn` rules for `java.beans.*` and `org.yaml.snakeyaml.*`
  (GeckoView transitive dependencies that reference Java Desktop APIs unavailable on Android)
- `@Suppress("OVERRIDE_DEPRECATION")` on `onTrimMemory` override to satisfy strict mode

---

## [v1.0.0] — 2026-06-11

### 🎉 Initial Release — All 9 development phases complete

#### Phase 1 — Basic Browser Foundation
- GeckoView 132 integration (Firefox engine)
- Single-tab browser shell with URL bar, back/forward/reload/stop
- Loading progress bar, DuckDuckGo search fallback
- Dark purple Material 3 theme

#### Phase 2 — Workspace Isolation
- Multiple isolated browser workspaces via GeckoView `contextId`
- Create, rename, reorder, delete workspaces
- Workspace sidebar (220dp left rail) with colour labels
- Clear workspace data without affecting others
- 7-colour workspace palette

#### Phase 3 — Tabs Per Workspace
- Persistent tab list per workspace (Room-backed)
- Horizontal tab strip with auto-scroll to active tab
- Tab overview grid (2-column, slide-up)
- Pin tabs, open/close/reorder
- Tabs persist across app restarts

#### Phase 4 — Automatic Tab Lifecycle
- `TabLifecycleManager` — LRU eviction engine
- States: Active → Recent → Unloaded with automatic transitions
- Android `onTrimMemory` cascade (mild → severe)
- App restore: only active + Keep Alive tabs get live sessions
- Memory status bar in sidebar (● ◆ ◐ ○ counts)

#### Phase 5 — Keep Alive Tabs
- Mark any tab to stay running in background
- Amber animated bolt indicator on Keep Alive tabs
- Keep Alive panel with cap progress bar
- Cap-exceeded dialog with navigation to settings
- `KeepAliveManager` with cap enforcement and event relay

#### Phase 6 — Memory Modes & Stability
- Settings screen: Conservative / Balanced / Performance presets
- Fine-grain steppers for max recent live and max Keep Alive tabs
- Memory dashboard with animated ring chart
- Pressure simulation buttons for developer testing
- Default homepage, search engine, keep-screen-awake toggle

#### Phase 7 — 24/7 Background Survival (HyperOS)
- Foreground service with dynamic persistent notification
- WorkManager health check every 15 minutes
- Boot receiver restarts service after device reboot
- HyperOS setup guide (5-step checklist with deep-links)
- `SetupViewModel` with DataStore-persisted completion state

#### Phase 8 — Browser Completeness
- Downloads via Android `DownloadManager` with confirm dialog
- File upload via `ActivityResultContracts.OpenMultipleDocuments`
- Fullscreen video (immersive UI, toolbar/tabs hidden)
- Find in page (GeckoView `SessionFinder`, match counter)
- Bookmarks (add/remove, star button, panel)
- Desktop/mobile mode toggle (per-tab UA, auto-reload)
- HTTPS security indicator (🔒 green / 🔓 grey)
- Permission dialogs for camera, mic, location, web push

#### Phase 9 — Hardening & Personal APK
- Crash recovery: uncaught exception handler + clean-exit detection
- Amber crash banner on next launch (auto-dismisses 8s)
- Diagnostics screen: app info, session state, isolation test, persistence test
- Unit tests: 5 test classes, 27+ assertions
- Signed APK build config with `keystore.properties` template
- GitHub Actions CI: release + debug workflows
