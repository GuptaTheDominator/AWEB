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
