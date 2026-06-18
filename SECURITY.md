# AWEB Security and Privacy Notes

AWEB is a personal-use browser APK. It is not a public SaaS product and is not distributed through the Play Store. This document explains the security/privacy decisions in the app and how to handle sensitive release material.

---

## Security model

| Area | Implementation |
|---|---|
| Workspace isolation | GeckoView `contextId` per workspace |
| Backup protection | `android:allowBackup="false"`, `fullBackupContent="false"`, data extraction rules exclude all data |
| Native bridge exposure | No JavaScript bridge / no `addJavascriptInterface()` |
| Browser engine | Mozilla GeckoView, not Android WebView |
| Downloads | `http`/`https` only, filename sanitization, user confirmation |
| Runtime permissions | Camera, mic, location and notifications requested only when needed |
| Crash privacy | App crash/log paths redact URLs and query values |
| Release signing | GitHub Actions secrets, no keystore committed |
| External components | Foreground service/provider not exported; launcher/browser activity exported intentionally |

---

## Workspace privacy

Every workspace receives a permanent GeckoView context ID:

```text
aweb_ws_<uuid>
```

All tabs in that workspace use the same context ID. Different workspaces use different context IDs, giving separate cookies, logins, localStorage, IndexedDB and cache.

Workspace clearing closes live sessions and calls Gecko storage clearing for the workspace context.

---

## Backup and device transfer

AWEB disables Android backup:

```xml
android:allowBackup="false"
android:fullBackupContent="false"
android:dataExtractionRules="@xml/data_extraction_rules"
```

`data_extraction_rules.xml` excludes all cloud backup and device-transfer domains. Browser sessions should remain local to the device.

---

## Logging and crash privacy

AWEB redacts sensitive URL data in its own crash/log paths through `PrivacySanitizer`.

Examples:

```text
https://example.com/private/path?token=abc → https://example.com/…
?token=abc&account=123 → ?token=<redacted>&account=<redacted>
```

Crash recovery stores only sanitized exception text and a timestamp.

Important limitation: Android/system/Gecko internals may still log their own diagnostics. Avoid sharing full logcat captures publicly unless reviewed.

---

## Permissions

AWEB declares browser-related permissions only for features it supports:

- Internet/network state
- foreground service/data sync
- notifications
- boot completed
- wake lock / battery optimization request
- camera/microphone/location runtime permissions
- legacy storage permissions only for older Android versions

Camera and microphone hardware are marked optional so the APK is not restricted to devices that advertise those features.

---

## Downloads

Download hardening includes:

- only `http` and `https` download URLs are accepted
- path separators/control characters are removed from filenames
- empty or unsafe filenames fall back to safe names
- user confirmation is surfaced before enqueueing
- Android `DownloadManager` performs the actual transfer

---

## Foreground service privacy/control

AWEB uses a foreground service to improve survival under HyperOS memory pressure. The persistent notification includes an Exit action.

Exit behavior:

- disables survival service state
- cancels the health worker
- stops foreground mode
- avoids immediate resurrection

Opening AWEB again re-enables survival mode.

---

## Signing key security

For GitHub releases, these repository Actions secrets are required:

```text
AWEB_KEYSTORE_BASE64
AWEB_KEYSTORE_PASSWORD
AWEB_KEY_ALIAS
AWEB_KEY_PASSWORD
```

Rules:

- never commit `*.jks`
- never commit `keystore.properties`
- never paste PATs/tokens in issues/commits/docs
- back up the release keystore securely offline
- do not rotate the key unless you accept Android update incompatibility

---

## What is not implemented

- no built-in VPN
- no app lock/biometric gate yet
- no site permission dashboard yet
- no per-workspace bookmark option yet
- no certificate pinning beyond GeckoView's standard TLS validation

These are future enhancements, not current guarantees.

---

## Reporting security issues

This is a personal project, so there is no formal bounty program. If you find a serious issue:

1. Do not publish sensitive details publicly.
2. Open a private communication channel with the maintainer if possible.
3. Include reproduction steps, affected version, and expected/actual behavior.

---

## Token/PAT hygiene

If a GitHub PAT or signing secret is ever exposed:

1. revoke it immediately in GitHub settings
2. create a new least-privilege token/secret
3. review recent GitHub Actions and release activity
4. rotate any affected signing/release material if needed
