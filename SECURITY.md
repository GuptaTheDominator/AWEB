# Security Policy

## Scope

AWEB is a personal-use browser. It is not published on the Play Store and has no external users. This document describes the security design decisions made in the app.

## Security Design

| Principle | Implementation |
|---|---|
| **No cloud backup** | `android:allowBackup="false"` + `dataExtractionRules` exclude all domains |
| **No incognito mode** | Workspace isolation covers this use case — each workspace is already a separate profile |
| **Workspace isolation** | GeckoView `contextId` — permanent per-workspace UUID, never shared or changed |
| **No JavaScript bridge** | `addJavascriptInterface()` is never called — no native API exposure to web pages |
| **Runtime permissions only** | Camera, microphone, location — requested per-site when GeckoView's PermissionDelegate fires |
| **HTTPS indicator** | `onSecurityChange` exposes `isSecure` — shown as 🔒/🔓 in the URL bar |
| **Download confirmation** | User always sees filename, MIME type and size before `DownloadManager.enqueue()` is called |
| **No stored credentials** | Passwords and OAuth tokens are handled entirely inside GeckoView's browser context |
| **Signed APK** | Release builds use a locally-generated keystore; signing keys are never committed to git |
| **Data stays on device** | All Room DB and DataStore data is under `/data/data/com.aweb.browser/` with no backup |

## What Is NOT Implemented (by design)

- **No password manager** — use the browser's built-in form fill inside each workspace
- **No VPN integration** — use a system-level VPN alongside AWEB
- **No ad blocking** — can be added via GeckoView's `ContentBlocking` API in a future update
- **No certificate pinning** — GeckoView's default SSL handling is used

## Keystore Security

The signing keystore (`aweb-release.jks`) should:
- Be backed up securely offline (losing it means you cannot update the installed APK)
- Never be committed to git (it is in `.gitignore`)
- Use a strong password (at least 16 characters)
