# AWEB Phase 10 Audit Report — Performance

Date: 2026-06-18  
Scope: startup/runtime performance, Compose allocation/recomposition paths, Room/DataStore usage, foreground service update traffic, session lifecycle cost, and APK/memory considerations.

---

## Areas reviewed

- `BrowserScreen` state collection and fallback flows
- `MainActivity` foreground-service notification update trigger
- `TabViewModel` tab-list collection and settings lookup
- Close-all tab lifecycle path
- Room query indexes added in earlier phases
- GeckoView/APK size constraints
- Existing memory lifecycle policy behavior

---

## Findings fixed

### P10-F1 — BrowserScreen created fallback flows during recomposition

Problem:

When no active Gecko session existed, `BrowserScreen` used `emptyStateFlow(default)` directly in the composable expression. That helper created a new `MutableStateFlow` each time the expression ran.

Fix:

Replaced per-call fallback flow creation with remembered fallback flows:

```kotlin
val emptyUrlFlow = remember { MutableStateFlow("") }
...
val url by (session?.url ?: emptyUrlFlow).collectAsState()
```

Result:

Reduced allocation churn during startup/session-null recompositions.

---

### P10-F2 — Foreground-service notification update was triggered by every tab-row change

Problem:

`MainActivity` keyed notification updates on the full `tabState.tabs` list. Page title/URL DB updates can create new tab lists and send service intents even though the notification only displays total tabs and Keep Alive count.

Fix:

Added a compact notification state key:

```kotlin
"${tabState.tabs.size}:${tabState.tabs.count { it.keepAlive }}"
```

The notification update effect now runs only when these displayed values change.

Result:

Fewer service intents during normal browsing and page title updates.

---

### P10-F3 — Search engine was read from DataStore on every tab-list emission

Problem:

`TabViewModel.safeCollect()` called `settingsRepo.defaultSearchEngine.first()` each time tabs changed. Tab updates can be frequent due to title/url/lifecycle writes.

Fix:

`TabViewModel` now collects `defaultSearchEngine` once into a cached `MutableStateFlow` and uses the cached value for AppState and omnibox navigation.

Result:

Reduced DataStore flow work during normal tab state updates.

---

### P10-F4 — Close All Tabs launched one lifecycle job per tab

Problem:

`closeAllTabs()` previously called `lifecycleManager.onTabClosed()` for every tab, which launched multiple asynchronous lifecycle jobs and redundant rebalances before deleting all tabs.

Fix:

`closeAllTabs()` now:

1. bulk closes all live wrappers for the workspace via `TabSessionManager.closeAllForWorkspace(ids)`
2. deletes all tabs from Room
3. creates one replacement tab

Result:

Lower CPU and coroutine overhead for bulk tab cleanup.

---

## Existing performance positives observed

- ABI split is ARM64-only, avoiding universal APK bloat.
- GeckoView dominates APK size; app code changes have relatively small APK-size impact.
- R8 minify/shrink resources are enabled for release builds.
- Room now has indexes for frequent workspace/active/order tab queries from Phase 4/5.
- Keep Alive and recent-live caps are now applied to runtime memory policy from earlier phases.
- Gecko session close/active calls are main-thread guarded from earlier phases.

---

## Remaining Phase 10 real-device profiling checklist

These require Redmi Pad SE / HyperOS hardware:

| Area | Measurement |
|---|---|
| Cold startup | time to first frame, time to GeckoRuntime ready, time to first page load |
| Warm startup | app process already alive → time to visible browser |
| Tab switch | active tab switch latency with 3, 5, 10 tabs |
| Keep Alive | memory footprint with 1, 3, 5 Keep Alive tabs |
| Memory pressure | live sessions before/after TRIM_MEMORY_LOW/CRITICAL |
| Notification updates | verify no noisy updates during title-only page loads |
| Close all tabs | latency with 10+ tabs before/after bulk close optimization |
| Battery/thermal | foreground service + Gecko idle drain over 30–60 min |

---

## Validation status

Completed after Phase 10 edits:

- `./gradlew compileReleaseKotlin testReleaseUnitTest` — passed
- Unit tests: `48 passed, 0 failed`
- `./gradlew lintRelease` — passed with `0 errors, 64 warnings`

No APK build/release was run because it was not requested for Phase 10.
