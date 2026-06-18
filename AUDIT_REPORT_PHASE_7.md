# AWEB Phase 7 Audit Report — Security & Privacy

Date: 2026-06-18  
Scope: Manifest permissions/exported components, backups, crash/log privacy, downloads, browser privacy, and workspace data handling.

---

## Areas reviewed

- Android manifest permissions and exported components
- Cloud/device backup settings
- Foreground service and PendingIntent exposure
- External URL handling
- Download URL and filename handling
- Gecko permission request logging
- Crash persistence/logging
- Workspace data clearing and Gecko session context isolation
- Bookmarks/global data behavior

---

## Security/privacy state already good before Phase 7

- `android:allowBackup="false"`
- `android:fullBackupContent="false"`
- `data_extraction_rules.xml` excludes all cloud backup/device transfer data.
- Foreground service is `android:exported="false"`.
- Startup provider is `android:exported="false"`.
- MainActivity exported only for launcher + browser `http/https` intents.
- PendingIntents use `FLAG_IMMUTABLE`.
- Gecko remote debugging is disabled in runtime settings.
- Workspace clearing now calls `StorageController.clearDataForSessionContext(contextId)` from earlier phases.

---

## Findings fixed

### P7-F1 — Full URLs could be written to logs or persisted crash data

Problem:

Several paths could log sensitive URLs or query strings:

- crash exception messages
- persisted crash recovery banner messages
- Gecko pending navigation logs
- Gecko `loadUri` failure logs
- permission-origin logs

Fix:

Added:

```text
app/src/main/java/com/aweb/browser/security/PrivacySanitizer.kt
```

It redacts:

- full `http/https` URL path and query
- standalone query parameter values

Examples:

```text
https://example.com/private/path?token=abc → https://example.com/…
?token=abc&account=123 → ?token=<redacted>&account=<redacted>
```

Updated:

- `AwebApplication`
- `CrashRecoveryManager`
- `GeckoSessionWrapper`
- `BrowserPermissionHandler`

Result:

AWEB no longer intentionally logs or persists full sensitive browser URLs in its own crash/log paths.

---

### P7-F2 — Raw Throwable logging in crash recovery could expose sensitive message text

Problem:

`CrashRecoveryManager.install()` logged the raw Throwable object. Android's Throwable logging includes the raw exception message.

Fix:

Removed raw Throwable logging from the crash recovery handler and logs only sanitized message text.

Result:

The app's own crash handler avoids leaking sensitive URL/query data through Throwable messages.

---

### P7-F3 — Download handler accepted non-web URL schemes

Problem:

`DownloadHandler.enqueueDownload()` accepted any URL string and passed it to `DownloadManager.Request`.

Fix:

Now only these schemes are accepted:

```text
http
https
```

Unsupported schemes fail before reaching DownloadManager.

Result:

Reduces risk from unexpected `file:`, `content:`, `intent:`, or other non-web download URLs.

---

### P7-F4 — Download logs could expose source URLs

Problem:

Download code did not log full source URLs, but Phase 7 tightened logging anyway to keep only a truncated sanitized filename, MIME type, and size.

Result:

Download logs avoid source URL disclosure.

---

## Tests added

Added:

```text
app/src/test/java/com/aweb/browser/PrivacySanitizerTest.kt
```

Coverage:

- full URL path/query redaction
- host/port-preserving `redactUrl()`
- standalone query-value redaction
- invalid URL fallback redaction

---

## Remaining security/privacy recommendations

These are intentionally not implemented in this pass because they need product decisions or device testing:

1. Add an optional app lock / biometric gate.
2. Add per-workspace bookmark mode if global bookmarks are considered too revealing.
3. Add site-permission dashboard with per-workspace clear/revoke controls.
4. Add a privacy option to hide URLs/titles in screenshots/recents.
5. Add a diagnostics toggle to disable verbose logging in release builds.
6. Add instrumentation tests to prove workspace cookie/localStorage isolation on device.
7. Consider a safer deep-link/default-browser chooser UX for external URLs.

---

## Validation status

Completed before release tagging:

- `./gradlew compileReleaseKotlin testReleaseUnitTest` — passed
- Unit tests: `48 passed, 0 failed`
- `./gradlew lintRelease` — passed with `0 errors, 64 warnings`

Release build/publish is performed by GitHub Actions when tag `v2.6.9` is pushed.
