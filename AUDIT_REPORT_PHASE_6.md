# AWEB Phase 6 Audit Report — Background Service / HyperOS Survival

Date: 2026-06-18  
Scope: Foreground service, boot receiver, WorkManager health checks, notification lifecycle, and HyperOS setup UX.

---

## Areas reviewed

- `AwebForegroundService`
- `ServiceManager`
- `BootReceiver`
- `ServiceHealthWorker`
- `AwebApplication` service startup path
- `HyperOsSetupScreen`
- `SetupViewModel`
- Manifest service/receiver/permission declarations

---

## Findings fixed

### P6-F1 — Notification Exit could be undone by START_STICKY / health worker

Problem:

The notification Exit action stopped the foreground service, but the service always returned `START_STICKY`, and periodic health checks could later restart it. This meant a deliberate user stop was not respected.

Fix:

- Added `ServicePreferences` persistent service-enabled state.
- Exit action now sets service enabled to `false`.
- Exit action cancels `ServiceHealthWorker`.
- Exit action returns `START_NOT_STICKY`.
- `onDestroy()` logs whether the service was user-disabled or may be restarted.

Result:

The service no longer immediately resurrects after the user explicitly taps Exit.

---

### P6-F2 — App launch should re-enable survival mode

Problem:

If Exit disables survival mode, the app still needs an obvious path to re-enable it.

Fix:

- `AwebApplication.onCreate()` sets survival service enabled to `true` on explicit app launch.

Result:

Opening AWEB re-enables the foreground survival service.

---

### P6-F3 — Boot receiver startup was not guarded

Problem:

`BootReceiver` directly called `startForegroundService()` and `ServiceHealthWorker.schedule()` without exception guards. Some Android/HyperOS builds can reject background FGS starts from boot/package-replaced paths.

Fix:

- `BootReceiver` now checks the persistent service-enabled state.
- It safely cancels worker if disabled.
- It wraps service startup and worker scheduling in `try/catch`.
- It still schedules WorkManager when immediate service start is rejected.

Result:

Boot/package-replaced broadcasts should not crash the receiver, and WorkManager remains the fallback path.

---

### P6-F4 — Health worker could restart service after user-disabled state

Problem:

The health worker did not know whether the user explicitly stopped the service.

Fix:

- `ServiceHealthWorker.doWork()` checks `ServicePreferences.isEnabled()`.
- If disabled, it cancels its unique work and exits successfully.
- `ensureServiceRunning()` also checks the state.

Result:

WorkManager respects the user's Exit action.

---

### P6-F5 — Notification update requests could resurrect disabled service

Problem:

`ServiceManager.requestNotificationUpdate()` could start/deliver an intent to the service even after the user disabled survival mode.

Fix:

- Notification update requests now return immediately when survival mode is disabled.

Result:

Routine UI state changes do not resurrect the disabled service.

---

### P6-F6 — HyperOS setup checklist progress was volatile

Problem:

The setup checklist used local Compose state for each step. Partial progress was lost when the guide was closed before all required steps were completed.

Fix:

- Added persisted `completed_steps` string set in `SetupViewModel` DataStore.
- Added stable step IDs in `HyperOsSetupStepIds`.
- `HyperOsSetupScreen` now receives `completedSteps` and `onStepDoneChange` from `SetupViewModel`.
- `markSetupDone()` persists all required steps.

Result:

HyperOS setup progress is preserved across app restarts and partial completion.

---

## Source-level status

Phase 6 source fixes are complete. Remaining validation required on the real Redmi Pad SE / HyperOS device:

| Area | Real-device validation |
|---|---|
| Notification Exit | Service stops and does not return after 15+ min |
| App relaunch | Foreground service returns after opening AWEB |
| Reboot | Boot receiver starts/schedules survival path if enabled |
| Package replace | Service survives/restarts after APK update |
| HyperOS Autostart | Device-specific autostart setting works |
| Battery No Restrictions | Gecko startup no longer killed by LMKD |
| Recents lock | Clearing recents does not kill Keep Alive tabs |
| Notification permission denied | FGS still starts without app crash |

---

## Validation status

Completed after Phase 6 edits:

- `./gradlew compileReleaseKotlin testReleaseUnitTest` — passed
- `./gradlew lintRelease` — passed with `0 errors, 64 warnings`
- `./gradlew assembleRelease` with a temporary local signing key — passed
- `apksigner verify` — passed
- `zipalign -c -p 4` — passed

Audit-build APK SHA-256 with temporary local signing key:

```text
6bb87e6607ef6612a2779cc779112e9a3e8ee14f1308690443322124d429faf2
```

Note: this hash is for local validation only. A GitHub Actions release build will differ because it uses the repository release signing key.
