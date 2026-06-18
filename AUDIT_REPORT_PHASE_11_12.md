# AWEB Phase 11 + Phase 12 Report — CI/Release Hardening + Final Plan

Date: 2026-06-18  
Scope: Phase 11 release pipeline hardening and Phase 12 final prioritization/release planning.

---

## Phase 11 — Release pipeline hardening

### P11-F1 — Added CI workflow for main/PR validation

Added:

```text
.github/workflows/ci.yml
```

Triggers:

- push to `main`
- pull request to `main`

CI now runs:

1. `testReleaseUnitTest`
2. `lintRelease`
3. `assembleDebug`
4. uploads the debug ARM64 APK as a short-retention artifact

Purpose:

- catch compile/test/lint regressions before release tags
- provide a debug APK artifact for quick smoke testing

---

### P11-F2 — Hardened release workflow

Updated:

```text
.github/workflows/release.yml
```

Improvements:

- workflow-level `contents: write` permission only
- release concurrency group
- job timeout
- version/tag consistency gate
- deterministic, permission-locked keystore restoration
- unit-test gate before release build
- lint gate before release build
- release APK signature verification with `apksigner`
- APK alignment verification with `zipalign`
- consistent release artifact naming:
  - `AWEB-vX.Y.Z-arm64.apk`
  - `AWEB-vX.Y.Z-arm64.apk.sha256`
- workflow artifact upload for APK/SHA and R8 mapping file
- named GitHub Release: `AWEB vX.Y.Z ARM64`

---

### P11-F3 — Release checklist added

Added:

```text
RELEASE_CHECKLIST.md
```

Covers:

- version checks
- local validation
- GitHub Actions secrets
- tag/release procedure
- post-release checksum verification
- Redmi device smoke testing
- emergency rollback guidance

---

## Phase 12 — Final prioritization and release planning

### Current source status

Current source version after this pass:

```text
versionName = 2.6.11
versionCode = 46
```

Latest published release at time of this report:

```text
v2.6.9
```

A future `v2.6.11` release can be published by pushing tag `v2.6.11` after any desired final checks.

---

## Completed audit/development phases

| Phase | Status | Summary |
|---|---:|---|
| Phase 1 | Done | Baseline build/release audit |
| Phase 2 | Done | Static code audit and major bug fixes |
| Phase 3 | Done | Runtime startup/memory stability source pass |
| Phase 4 | Done | Data layer and persistence fixes |
| Phase 5 | Done | Browser feature reliability fixes |
| Phase 6 | Done | Background service / HyperOS survival fixes |
| Phase 7 | Done | Security/privacy log/download hardening |
| Phase 8 | Done | UI/UX improvements |
| Phase 9 | Done | Unit test expansion |
| Phase 10 | Done | Performance source-level optimizations |
| Phase 11 | Done | CI/release workflow hardening |
| Phase 12 | Done | Final prioritization/release plan |

---

## Final priority list

### Must test on Redmi Pad SE before calling a release “daily driver”

1. Cold launch and GeckoRuntime startup on HyperOS.
2. Foreground notification appears and remains stable.
3. Notification Exit stays stopped for 15+ minutes.
4. Opening the app after Exit re-enables survival service.
5. HyperOS setup checklist persists progress.
6. Workspace isolation with two different logins to the same site.
7. Clear workspace data removes cookies/localStorage for only that workspace.
8. File upload works on real websites.
9. Downloads save to Downloads with safe filenames.
10. Camera/mic/location prompts grant/deny correctly.
11. 10+ tabs with 2–3 Keep Alive tabs under memory pressure.
12. APK update over previous stable signed build.

### High-priority future improvements

1. Site permission dashboard per workspace.
2. Optional app lock / biometric unlock.
3. User-visible download failure snackbar and downloads panel.
4. Real migration instrumentation tests with prebuilt v3/v4 DBs.
5. Favicon/title snapshot support for tab overview.
6. Privacy mode to hide URLs/titles in Android recents/screenshots.
7. Device-side performance counters in Diagnostics.

### Medium-priority future improvements

1. Bookmark import/export.
2. Per-workspace bookmarks option.
3. Tab search in tab overview.
4. More polished onboarding flow.
5. Theme variants: AMOLED black, system, light.
6. Better launcher/adaptive icon set.
7. Site data usage view per workspace.

### Lower-priority cleanup

1. Remove obsolete minSdk checks flagged by lint.
2. Move hardcoded test dependencies into version catalog.
3. Review broad ProGuard keep rules once GeckoView child-process stability is proven.
4. Upgrade dependencies in a separate compatibility branch.

---

## Release recommendation

Recommended next release candidate:

```text
v2.6.11
```

Why:

- includes all phase fixes through performance, security, UI/test, service survival, and CI hardening
- CI and release workflows are now stronger
- latest source docs/checklists are current

Before releasing:

```bash
./gradlew compileReleaseKotlin testReleaseUnitTest --no-daemon --stacktrace
./gradlew lintRelease --no-daemon --stacktrace
```

Then:

```bash
git tag -a v2.6.11 -m "AWEB v2.6.11"
git push origin v2.6.11
```

---

## Final note

Source-level work is complete through the requested phases. The remaining critical confidence step is hardware validation on the Redmi Pad SE / HyperOS environment, because the sandbox cannot emulate Xiaomi background restrictions, LMKD behavior, foreground service policy quirks, or real Gecko memory pressure.

---

## Validation completed for Phase 11/12 changes

After workflow/docs/version updates:

- `./gradlew compileReleaseKotlin testReleaseUnitTest --no-daemon --stacktrace` — passed
- Unit tests: `48 passed, 0 failed`
- `./gradlew lintRelease --no-daemon --stacktrace` — passed with `0 errors, 64 warnings`

No APK build/release was run for this phase.
