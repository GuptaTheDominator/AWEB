# AWEB Phase 1 + Phase 2 Audit Report

Date: 2026-06-17  
Scope: Phase 1 baseline/build/release audit + Phase 2 static code audit.  
Repo commit audited: `76b8a8f` (`v2.6.4` source state)

---

## Post-audit fix status

The source tree now includes a v2.6.5 fix batch for the Critical/High Phase 1/2 findings. After applying fixes:

- `./gradlew clean assembleRelease testReleaseUnitTest` passed.
- `apksigner verify` passed.
- `zipalign -c` passed.
- `./gradlew lintRelease` reports `0 errors` (remaining items are warnings/info such as icon quality, dependency updates, ARM64-only ChromeOS warning, and Play policy warning for battery-optimization guidance).

The findings below are preserved as the original audit record.

---

## Executive Summary

AWEB currently builds successfully after the previous v2.6.4 release-build fixes, and the ARM64 split is correctly produced. Unit tests pass. However, lint still reports 2 errors and 65 warnings because lint is configured not to fail builds.

The static audit found several high-impact runtime and product bugs. The most important are:

1. Gecko browser feature handlers are not wired into `GeckoSessionWrapper`, so permissions/downloads/file-upload features described in the README are largely disconnected.
2. AWEB advertises itself as a browser for `http`/`https` links, but `MainActivity` does not handle `Intent.ACTION_VIEW` URLs or `onNewIntent()`.
3. Gecko session operations are still performed from background/IO coroutines in lifecycle code, creating main-thread violation risk.
4. `GeckoSessionWrapper` owns a coroutine scope that is never cancelled, causing wrapper/session leaks over time.
5. Memory settings UI does not fully affect the actual memory policy; custom max recent live tabs are ignored.
6. Workspace “Clear data” does not actually clear Gecko profile cookies/storage/cache for that workspace.
7. Crash recovery is incomplete because `CrashRecoveryManager.install()` is never called.
8. Release signing in GitHub Actions is unstable because a new hardcoded-password key is generated for every release.

Recommended next step: fix Critical/High findings before major UI redesign.

---

# Phase 1 — Baseline Audit

## 1. Git / project state

- Local branch: `main`
- Remote synced: yes
- Current audited commit: `76b8a8f Fix release build and bump to v2.6.4`
- Tag available: `v2.6.4`
- No `ghp_` token found in tracked source.
- `.gitignore` ignores:
  - `keystore.properties`
  - `*.jks`

## 2. Toolchain observed

- Gradle wrapper: `8.7`
- Java used for audit: OpenJDK `21.0.11`
- Android Gradle Plugin: `8.6.1`
- Kotlin: `2.0.0`
- KSP: `2.0.0-1.0.21`
- Compile SDK: `35`
- Target SDK: `35`
- Min SDK: `29`
- GeckoView: `geckoview-nightly-omni 132.0.20240929094629`

## 3. Build validation

Commands run:

```bash
./gradlew clean assembleDebug --no-daemon --stacktrace --warning-mode all
./gradlew clean assembleRelease testReleaseUnitTest --no-daemon --stacktrace --warning-mode all
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-arm64-v8a-release.apk
zipalign -c -p 4 app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

Results:

| Check | Result |
|---|---:|
| Clean debug build | Pass |
| Clean release build | Pass |
| Release unit tests | Pass |
| APK signature verification | Pass |
| APK zipalign check | Pass |
| ARM64-only output | Pass |

Artifacts from audit build:

| Artifact | Size |
|---|---:|
| `app-arm64-v8a-debug.apk` | ~239 MiB |
| `app-arm64-v8a-release.apk` | ~199 MiB |

Audit-build SHA-256:

```text
995befd3afe9bb6dc8bfcb3f9ce284f61f903b7ad5f4087d3fed21241d57a04e  app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

Note: this hash differs from the already published v2.6.4 APK because the audit used a temporary signing key.

## 4. Unit tests

JUnit results:

```text
tests=33 failures=0 errors=0 skipped=0
```

Covered test files:

- `DownloadHandlerTest` — 7 tests
- `MemoryPolicyTest` — 8 tests
- `SearchEngineTest` — 7 tests
- `TabLifecycleStateTest` — 5 tests
- `WorkspaceIsolationTest` — 6 tests

Coverage gaps remain for:

- real `DownloadHandler` Android implementation
- URL/domain detection in `TabViewModel`
- Keep Alive cap race cases
- lifecycle manager threading behavior
- Room migration behavior
- intent/deep-link handling
- permission request flow
- workspace data clearing

## 5. Lint results

Command run:

```bash
./gradlew lintRelease --no-daemon --stacktrace
```

Result: build task completes because lint is configured with:

```kotlin
lint {
    abortOnError = false
    checkReleaseBuilds = false
}
```

But lint reports:

```text
2 errors, 65 warnings
```

### Lint errors

1. `MainActivity.kt:131` — `AnimatedContent` target state parameter unused.
2. `AndroidManifest.xml:30` — `CAMERA` permission implies required camera hardware unless `uses-feature android.hardware.camera required=false` is declared.

### Important lint warnings

- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Play Store policy warning.
- Many outdated dependencies.
- Launcher icons are oversized/duplicated/wrong density.
- Compose `Modifier` parameter order warnings.
- Obsolete SDK checks because minSdk is already 29.
- ChromeOS ABI warning due ARM64-only build.

Because this app is targeted at a Redmi/ARM64 personal install, the ChromeOS ABI warning is acceptable. The camera hardware feature warning should still be fixed.

## 6. Release pipeline audit

File: `.github/workflows/release.yml`

Findings:

- The workflow generates a new release signing key every run.
- Signing passwords are hardcoded as `password`.
- This causes Android update conflicts between releases because every APK can be signed by a different certificate.
- No stable keystore is stored in GitHub Secrets.
- No SHA-256 file generation in workflow.
- No lint/test gate before release.
- No tag/versionName consistency check.

Severity: High.

Recommendation:

- Store base64 encoded release keystore in GitHub Secrets.
- Use secret passwords/alias.
- Add `testReleaseUnitTest` before release upload.
- Add version/tag consistency check.
- Generate and upload `.sha256`.
- Consider uploading R8 mapping file privately or as release artifact.

---

# Phase 2 — Static Code Audit Findings

Severity levels:

- Critical: likely crash/data loss/privacy/security/core feature broken.
- High: significant runtime failure or major feature mismatch.
- Medium: noticeable bug, degraded UX, or future stability risk.
- Low: cleanup, warnings, maintainability.

---

## Critical Findings

### C1 — Browser permission/download/file-upload handlers are not wired into Gecko sessions

Files:

- `TabSessionManager.kt:35-39`
- `GeckoSessionWrapper.kt:31-33`
- `GeckoSessionWrapper.kt:173-175`
- `GeckoSessionWrapper.kt:216-219`

Problem:

`GeckoSessionWrapper` accepts these constructor params:

```kotlin
private val downloadHandler: DownloadHandler? = null
private val permissionHandler: BrowserPermissionHandler? = null
private val fileUploadHandler: FileUploadHandler? = null
```

But `TabSessionManager` creates wrappers like this:

```kotlin
val w = GeckoSessionWrapper(
    contextId = workspace.contextId,
    appContext = context,
    initialUaMode = uaMode
)
```

So:

- `permissionDelegate` is never set.
- `onExternalResponse` does not enqueue downloads because `downloadHandler == null`.
- permission request UI flows are not reached.
- file upload handler is not attached.

Impact:

README-listed features such as downloads, camera/mic/location permission dialogs, web notifications, and file upload are likely non-functional or only partially functional.

Recommendation:

Inject `DownloadHandler`, `BrowserPermissionHandler`, and `FileUploadHandler` into `TabSessionManager`, then pass them to `GeckoSessionWrapper`. Add instrumentation/manual tests for:

- PDF/ZIP download
- camera permission page
- microphone permission page
- geolocation page
- `<input type="file">`

---

### C2 — File upload is effectively not implemented for GeckoView prompts

Files:

- `FileUploadHandler.kt:13-19`
- `GeckoSessionWrapper.kt:169-176`
- `BrowserScreen.kt:147-174`

Problem:

The UI has an `OpenMultipleDocuments()` launcher and `FileUploadHandler`, but no `GeckoSession.promptDelegate` / file prompt callback is attached to the session. The handler only exposes a SharedFlow; nothing in Gecko triggers it.

Impact:

`<input type="file">` upload is likely broken despite being advertised.

Recommendation:

Implement GeckoView `PromptDelegate` file prompt handling for GeckoView 132 and route callbacks to `FileUploadHandler`, preserving pending callback state until the Activity result returns.

---

### C3 — External browser URLs are ignored

Files:

- `AndroidManifest.xml:62-69`
- `MainActivity.kt:39-54`

Problem:

The manifest registers AWEB for `http` and `https` URLs, but `MainActivity` never reads `intent.data` and does not override `onNewIntent()` despite `launchMode="singleTask"`.

Impact:

If the user opens a link from another app or sets AWEB as default browser, AWEB launches but likely does not load the requested URL.

Recommendation:

Add intent handling:

- read `intent?.dataString` in `onCreate`
- override `onNewIntent(intent)`
- forward URL to `TabViewModel.openNewTab(url)` or active tab depending desired behavior
- add tests/manual validation with `adb shell am start -a android.intent.action.VIEW -d https://example.com com.aweb.browser`

---

### C4 — Gecko session operations can run off the main thread

Files:

- `TabLifecycleManager.kt:47`
- `TabLifecycleManager.kt:79-80`
- `TabLifecycleManager.kt:163-166`
- `TabLifecycleManager.kt:193-200`
- `TabLifecycleManager.kt:263-276`
- `TabSessionManager.kt:65-67`
- `GeckoSessionWrapper.kt:125-132`

Problem:

`TabLifecycleManager` uses:

```kotlin
CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

Inside that IO scope it calls Gecko-related operations through `TabSessionManager`, including `getOrCreate`, `session?.setActive`, and `unload`. `GeckoSessionWrapper.close()` does not dispatch to the main thread before calling `GeckoSession.close()`.

Impact:

GeckoView APIs often enforce main-thread access. This can cause `IllegalThreadStateException`, session close failures, or inconsistent lifecycle state under tab switches/memory pressure.

Recommendation:

Centralize all GeckoSession operations on `Dispatchers.Main.immediate`:

- make `GeckoSessionWrapper.close()` post/switch to Main
- make `setActive`, `open`, `loadUri`, `reload`, `stop`, `goBack`, `goForward` main-confined
- let `TabLifecycleManager` calculate policy on IO if needed, but apply session operations on Main
- add a debug assertion helper for main-thread Gecko calls

---

### C5 — `GeckoSessionWrapper` leaks because its coroutine scope is never cancelled

Files:

- `GeckoSessionWrapper.kt:44`
- `GeckoSessionWrapper.kt:66-74`
- `GeckoSessionWrapper.kt:125-132`

Problem:

Every wrapper creates:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
```

It collects `GeckoRuntimeManager.isReady` forever. `close()` closes the GeckoSession and sets it to null, but never cancels the scope/job.

Impact:

Closed tabs can remain retained by the flow collector. Over long usage, tab open/close cycles can leak wrappers, callbacks, context references, and memory.

Recommendation:

Store the `SupervisorJob`, cancel it on permanent close/dispose, and separate “temporary unload session” from “destroy wrapper”. Alternatively, avoid per-wrapper infinite collection and have callers explicitly open when runtime is ready.

---

### C6 — Workspace “Clear data” does not clear workspace browser data

Files:

- `WorkspaceViewModel.kt:148-164`
- `WorkspaceSessionManager.kt:58`

Problem:

`clearWorkspaceData()` only closes live sessions:

```kotlin
tabSessionManager.closeAllForWorkspace(tabs)
workspaceSessionManager.closeAndRemove(ws.id)
```

It does not clear GeckoView storage associated with `workspace.contextId`:

- cookies
- localStorage
- IndexedDB
- cache
- permissions/logins/session data

Impact:

This is a privacy/data-isolation bug. The UI implies data is cleared, but web logins and site storage may remain.

Recommendation:

Implement actual per-context storage clearing using GeckoView storage APIs for the workspace `contextId`. If GeckoView does not support full per-context deletion directly, document limitations and consider rotating contextId with explicit warning that it creates a fresh profile.

---

### C7 — Crash recovery manager is not installed

Files:

- `CrashRecoveryManager.kt:51-70`
- `AwebApplication.kt:141-151`
- `HardeningModule.kt:21-26`

Problem:

`CrashRecoveryManager.install()` exists but is never called. `AwebApplication.installExceptionLogger()` installs a separate uncaught exception handler that only logs and does not write crash info to DataStore.

Impact:

The crash recovery banner may show unclean-exit state but will not have accurate crash message/time. `restoreSession()` is also not called anywhere.

Recommendation:

Inject/install `CrashRecoveryManager` in `AwebApplication` or consolidate both crash handlers into one. Ensure the default handler chain is preserved. Call restore flow intentionally on next launch if needed.

---

### C8 — Release signing is unstable across GitHub Action releases

File:

- `.github/workflows/release.yml:23-31`

Problem:

The release workflow generates a new key with hardcoded password for every tag build.

Impact:

Users cannot update normally between APKs signed by different certificates. They must uninstall, losing app data.

Recommendation:

Use a stable release keystore stored in GitHub Secrets.

---

## High Findings

### H1 — Custom “max recent live tabs” setting is ignored by the actual lifecycle policy

Files:

- `SettingsViewModel.kt:89-95`
- `WorkspaceViewModel.kt:77-86`
- `MemoryMode.kt:21-25`
- `TabLifecycleManager.kt:54-56`

Problem:

The UI lets users set `maxRecentLiveTabs`, but `WorkspaceViewModel` only combines:

```kotlin
settingsRepo.memoryMode,
settingsRepo.maxKeepAliveTabs
```

It does not observe `maxRecentLiveTabs`. `MemoryPolicy.fromKey()` also always uses preset `maxRecentLive` values.

Impact:

The memory dashboard/settings screen lies: recent-live tab changes do not actually affect lifecycle behavior.

Recommendation:

Change `MemoryPolicy.fromKey(key, maxRecentLive, maxKeepAlive)` or add custom overrides. Observe all three settings and immediately call `rebalance` after policy changes.

---

### H2 — Keep Alive cap enforcement does not unload or rebalance excess sessions

File:

- `KeepAliveManager.kt:151-162`

Problem:

`enforceCap()` unsets DB flags for excess Keep Alive tabs but does not call rebalance/unload sessions.

Impact:

After lowering the cap, extra sessions can remain live until the next tab switch/memory event.

Recommendation:

After cap enforcement, call lifecycle rebalance or unload excess sessions immediately.

---

### H3 — Permission handling can grant more than Android allowed

Files:

- `BrowserScreen.kt:134-144`
- `BrowserFeatureViewModel.kt:98-110`

Problem:

The permission launcher checks:

```kotlin
featureViewModel.grantMedia(mediaReq, cam || mic)
```

Then `grantMedia()` grants both requested video/audio device IDs based only on requested type, not based on per-permission grant result.

Impact:

If a page requests camera+mic and the user grants only one, Gecko is still told both are granted. This can fail unpredictably or violate user expectations.

Recommendation:

Pass separate `camGranted` and `micGranted` booleans and grant only the matching devices. Reject the request if a requested permission was denied.

---

### H4 — Location and web notification permissions do not request Android runtime permission

Files:

- `BrowserPermissionHandler.kt:60-95`
- `BrowserScreen.kt:335-362`

Problem:

For geolocation, the UI grants Gecko permission directly without requesting `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`. For notifications on Android 13+, it does not request `POST_NOTIFICATIONS`.

Impact:

Geolocation and web notifications may fail or behave inconsistently.

Recommendation:

Add dedicated ActivityResult launchers for location and notification permissions, then resolve the Gecko callback based on Android permission result.

---

### H5 — Popup/new-tab handling likely rejects Gecko’s requested session

File:

- `GeckoSessionWrapper.kt:190-192`

Problem:

`onNewSession()` posts `onNewTabRequested` but returns `null`.

Impact:

Depending on GeckoView semantics, returning `null` may reject the popup/new-window request. Even if a new AWEB tab is created manually, Gecko’s original request may not attach to it.

Recommendation:

Confirm GeckoView 132 contract. Prefer returning a `GeckoResult<GeckoSession>` with a newly created/opened session or explicitly handle the URI navigation while returning a deny/allow result consistent with docs.

---

### H6 — Room uses destructive migration in release

File:

- `DatabaseModule.kt:20-23`

Problem:

```kotlin
.fallbackToDestructiveMigration()
```

is enabled for the production database.

Impact:

Any future schema version bump without migrations will wipe all user workspaces, tabs, bookmarks, and settings.

Recommendation:

Remove destructive migration for release builds and define real migrations. Set `exportSchema = true` and commit schema JSON.

---

### H7 — Deleted workspaces do not close/remove their live tab sessions

Files:

- `WorkspaceViewModel.kt:134-145`
- `TabSessionManager.kt:70-72`

Problem:

`deleteWorkspace()` deletes the workspace from Room but does not close tab sessions for that workspace. Because Room cascades tab rows, the app may lose the tab IDs needed to close live wrappers.

Impact:

Live sessions from deleted workspaces can remain in memory until app restart.

Recommendation:

Before deleting a workspace, query its tabs, close all sessions for those IDs, close workspace session/context, then delete DB rows.

---

### H8 — Search URL generation is not URL-encoded

File:

- `SettingsRepository.kt:95`

Problem:

```kotlin
query.trim().replace(" ", "+")
```

This does not encode characters like `&`, `?`, `#`, `%`, `+`, Unicode, etc.

Impact:

Searches can break or be interpreted as extra query parameters.

Recommendation:

Use `URLEncoder.encode(query.trim(), "UTF-8")` or Android `Uri.Builder`.

---

### H9 — Foreground-service restart paths are fragile on newer Android versions

Files:

- `ServiceHealthWorker.kt:73-80`
- `ServiceHealthWorker.kt:86-97`
- `ServiceManager.kt:45-59`
- `AwebForegroundService.kt:37-44`

Problem:

`ServiceManager.requestNotificationUpdate()` correctly avoids `startForegroundService()` for update-only intents, but `ServiceHealthWorker` still uses `startForegroundService()` from a background worker. Also, `AwebForegroundService.onCreate()` catches `startForeground()` failures but does not stop itself.

Impact:

On Android 12+, background foreground-service starts can be denied. If `startForeground()` fails after a `startForegroundService()` call, Android may kill the service/app.

Recommendation:

Review Android 12/13/14 FGS restrictions. Consider WorkManager foreground worker APIs or only restart the service from allowed user-visible entry points. If `startForegroundCompat()` fails, call `stopSelf()`.

---

## Medium Findings

### M1 — `BootReceiver` is `exported=false`

File:

- `AndroidManifest.xml:91-98`

Problem:

The boot receiver listens for system broadcasts while `android:exported="false"`.

Impact:

Depending on Android/system behavior, boot/package replaced broadcasts may not be delivered.

Recommendation:

Verify on target HyperOS. Many boot receiver examples use `exported="true"` for system broadcasts. If changed to true, keep only system actions and no custom external attack surface.

---

### M2 — Desktop/mobile mode menu compares wrong string case

Files:

- `BrowserScreen.kt:86`
- `BrowserScreen.kt:553-566`
- `TabViewModel.kt:300-310`

Problem:

`TabEntity.userAgentMode` uses lowercase `"mobile"` / `"desktop"`, but the menu checks uppercase `"DESKTOP"`.

Impact:

When already in desktop mode, the menu can still show “Switch to Desktop” with wrong icon/label.

Recommendation:

Compare `uaMode.equals("desktop", ignoreCase = true)` or use an enum.

---

### M3 — Domain detection is limited and can misclassify URLs

File:

- `TabViewModel.kt:26-55`

Problems:

- Hardcoded TLD allowlist misses many valid TLDs.
- IPv4 regex accepts invalid addresses like `999.999.999.999`.
- Host validation strips port before deciding but then returns `https://$trimmed`, so invalid ports can slip through.

Impact:

Some valid domains search instead of opening, and some invalid inputs are treated as URLs.

Recommendation:

Use `android.util.Patterns.WEB_URL`, `IDN.toASCII`, or a more robust parser. Add tests.

---

### M4 — Workspace data isolation diagnostic is not meaningful

File:

- `DiagnosticsScreen.kt:103-112`

Problem:

The “isolation check” compares workspace IDs from tabs, not Gecko `contextId` values.

Impact:

It can report success without verifying actual Gecko context isolation.

Recommendation:

Read all `WorkspaceEntity.contextId` values and verify uniqueness/non-blank. Optionally run a live cookie/localStorage isolation test with two sessions.

---

### M5 — `WorkspaceSessionManager` appears mostly unused

Files:

- `WorkspaceSessionManager.kt`
- `WorkspaceViewModel.kt:37,158`

Problem:

The workspace session manager is only used for clear-data cleanup, not for normal browser tab sessions. It may give a false impression that workspace-level sessions are active.

Impact:

Maintainability risk and possible confusion around actual isolation architecture.

Recommendation:

Either remove it or clearly define its role. Normal isolation currently happens through tab sessions using `workspace.contextId`.

---

### M6 — Setup checklist state is mostly local UI state

Files:

- `HyperOsSetupScreen.kt:301-312`
- `SetupViewModel.kt:47-55`

Problem:

Individual setup step “done” toggles live in remembered Compose state. Only the final all-done flag persists.

Impact:

If the user completes some steps, dismisses, and returns later, partial progress is lost.

Recommendation:

Persist per-step completion flags or convert checklist into a manual guide without fake progress state.

---

### M7 — Keep-screen-awake setting is not “while charging”

Files:

- `SettingsScreen.kt:140-146`
- `MainActivity.kt:75-78`

Problem:

The UI says “While charging”, but `MainActivity` applies `FLAG_KEEP_SCREEN_ON` whenever the setting is true, without checking battery/charging status.

Impact:

Can drain battery if enabled while unplugged.

Recommendation:

Observe battery charging state and only apply flag when charging, or change the label to match behavior.

---

### M8 — Close All has no confirmation

File:

- `TabOverviewScreen.kt:101-103`

Problem:

The full-screen tab overview has a direct “Close All” action.

Impact:

Easy accidental destructive action.

Recommendation:

Add confirmation, or undo snackbar.

---

### M9 — Several touch targets are below recommended size

Files:

- `TabStrip.kt:191-204`
- `TabOverviewScreen.kt:235-245`

Problem:

Close affordances are 16dp/24dp, far below the recommended 48dp touch target.

Impact:

Hard to use on tablet and easy to mis-tap.

Recommendation:

Increase hitbox while keeping icon visually small.

---

## Low / Cleanup Findings

### L1 — Build warnings from Kotlin/Compose

Observed warnings:

- deprecated `onTrimMemory` override warning
- deprecated `Icons.Filled.ArrowForward`
- deprecated `Icons.Filled.OpenInNew`
- deprecated `Modifier.animateItemPlacement`
- deprecated `SharedFlow.catch`

Recommendation: fix while doing UI cleanup.

### L2 — Dependency versions are old

Lint reports newer versions for AGP, Compose BOM, Lifecycle, Room, WorkManager, DataStore, etc.

Recommendation: upgrade carefully in a separate branch after stability fixes; GeckoView compatibility should be tested.

### L3 — Launcher icons are oversized/duplicated

Lint reports launcher icon density/shape issues.

Recommendation: regenerate adaptive launcher icons.

### L4 — ProGuard/R8 keep rules are very broad

File:

- `app/proguard-rules.pro`

Problem:

Rules keep all `org.mozilla`, all `androidx.compose`, all data classes, etc.

Impact:

May reduce shrink effectiveness. APK is dominated by GeckoView, so size impact may be modest.

Recommendation:

Keep broad Gecko rules if needed, but review Compose/data keep rules after confirming R8 compatibility.

---

# Recommended Fix Order

## Batch 1 — Core browser correctness

1. Wire `DownloadHandler`, `BrowserPermissionHandler`, and `FileUploadHandler` into `GeckoSessionWrapper`.
2. Implement Gecko file upload prompt handling.
3. Implement external URL intent handling in `MainActivity`.
4. Fix media/location/notification runtime permission handling.
5. Fix popup/new-tab Gecko contract.

## Batch 2 — Gecko lifecycle stability

1. Main-confine all GeckoSession operations.
2. Cancel/own `GeckoSessionWrapper` scope correctly.
3. Fix session cleanup on workspace delete and clear-data.
4. Add lifecycle stress tests/manual test matrix.

## Batch 3 — Privacy/data integrity

1. Implement real workspace data clearing.
2. Remove destructive migration from release and add Room migrations.
3. Install/consolidate crash recovery handler.
4. Fix release signing pipeline.

## Batch 4 — Memory policy correctness

1. Apply `maxRecentLiveTabs` setting to `TabLifecycleManager`.
2. Rebalance immediately after memory policy/cap changes.
3. Add tests for Keep Alive cap and memory policy behavior.

## Batch 5 — UI/UX polish

1. Fix desktop-mode menu label.
2. Improve address bar URL/search handling and encoding.
3. Add Close All confirmation/undo.
4. Improve touch targets.
5. Persist setup checklist progress or simplify the guide.
6. Regenerate launcher icons.

---

# Audit Logs

Generated local audit logs:

```text
audit_logs/phase1_assembleDebug.log
audit_logs/phase1_release_and_tests.log
audit_logs/phase1_lintRelease.log
audit_logs/phase1_apksigner.log
audit_logs/phase1_aapt_badging.log
audit_logs/phase1_apk.sha256
audit_logs/phase2_static_patterns.txt
```

Some `.log` files are ignored by `.gitignore`; the report itself is untracked until committed.
