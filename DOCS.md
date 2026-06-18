# AWEB Documentation Index

This repository contains several top-level documents. Start here if you are looking for a specific topic.

| Document | Use it for |
|---|---|
| [README.md](README.md) | GitHub front page, feature overview, install link, quick build notes |
| [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) | local build, signing, tests, CI/release workflow instructions |
| [ARCHITECTURE.md](ARCHITECTURE.md) | system design, Gecko/session lifecycle, data/background architecture |
| [SECURITY.md](SECURITY.md) | security/privacy model, backup policy, signing key handling |
| [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) | pre-release validation, tag workflow, device smoke tests |
| [CHANGELOG.md](CHANGELOG.md) | version history and source changes |

## Audit reports

| Report | Area |
|---|---|
| [AUDIT_REPORT_PHASE_1_2.md](AUDIT_REPORT_PHASE_1_2.md) | baseline build + static code audit |
| [AUDIT_REPORT_PHASE_3.md](AUDIT_REPORT_PHASE_3.md) | runtime startup/memory source pass |
| [AUDIT_REPORT_PHASE_4_5.md](AUDIT_REPORT_PHASE_4_5.md) | data layer + browser features |
| [AUDIT_REPORT_PHASE_6.md](AUDIT_REPORT_PHASE_6.md) | background service / HyperOS survival |
| [AUDIT_REPORT_PHASE_7.md](AUDIT_REPORT_PHASE_7.md) | security and privacy |
| [AUDIT_REPORT_PHASE_8_9.md](AUDIT_REPORT_PHASE_8_9.md) | UI/UX and tests |
| [AUDIT_REPORT_PHASE_10.md](AUDIT_REPORT_PHASE_10.md) | performance source-level pass |
| [AUDIT_REPORT_PHASE_11_12.md](AUDIT_REPORT_PHASE_11_12.md) | CI/release hardening and final plan |

## Most common tasks

### Install latest APK

Use the latest GitHub Release:

```text
https://github.com/GuptaTheDominator/AWEB/releases/latest
```

### Validate source locally

```bash
./gradlew compileReleaseKotlin testReleaseUnitTest --no-daemon --stacktrace
./gradlew lintRelease --no-daemon --stacktrace
```

### Publish a release

Follow:

```text
RELEASE_CHECKLIST.md
```

### Understand workspace isolation

Read:

```text
ARCHITECTURE.md → Workspace Isolation
SECURITY.md → Workspace privacy
```

### HyperOS survival setup

Read:

```text
README.md → Required HyperOS setup
ARCHITECTURE.md → Background Survival
```
