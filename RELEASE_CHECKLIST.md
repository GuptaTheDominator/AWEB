# AWEB Release Checklist

Use this checklist before publishing a tagged APK release.

Current release workflow output:

```text
AWEB-vX.Y.Z-arm64.apk
AWEB-vX.Y.Z-arm64.apk.sha256
```

---

## 1. Source/version checks

- [ ] `app/build.gradle.kts` `versionName` matches the intended tag without leading `v`.
- [ ] `versionCode` is greater than all previously released APKs.
- [ ] `CHANGELOG.md` has an entry for the version.
- [ ] `README.md` does not point to stale hard-coded APK names.
- [ ] No secret files are present:
  - `keystore.properties`
  - `*.jks`
  - PAT/token strings

Recommended checks:

```bash
grep -n "versionCode\|versionName" app/build.gradle.kts
git diff --check
grep -R "ghp_\|github_pat_" -n --exclude-dir=.git --exclude-dir=app/build .
```

---

## 2. Local validation

Run before tagging:

```bash
./gradlew compileReleaseKotlin testReleaseUnitTest --no-daemon --stacktrace
./gradlew lintRelease --no-daemon --stacktrace
```

Optional local package validation with a temporary key:

```bash
./gradlew assembleRelease --no-daemon --stacktrace
APK=app/build/outputs/apk/release/app-arm64-v8a-release.apk
apksigner verify --verbose --print-certs "$APK"
zipalign -c -p 4 "$APK"
sha256sum "$APK"
```

---

## 3. CI check

Push to `main` and wait for the `CI` workflow to pass.

CI runs:

- release unit tests
- lint
- debug ARM64 APK build
- debug APK artifact upload

---

## 4. GitHub Actions release secrets

The release workflow requires:

```text
AWEB_KEYSTORE_BASE64
AWEB_KEYSTORE_PASSWORD
AWEB_KEY_ALIAS
AWEB_KEY_PASSWORD
```

The release keystore must remain stable across versions. Regenerating it will break Android update compatibility for users who installed APKs signed with the previous key.

---

## 5. Tag and release

After `main` is ready:

```bash
git tag -a vX.Y.Z -m "AWEB vX.Y.Z"
git push origin vX.Y.Z
```

The release workflow will:

1. validate `versionName == tag`
2. restore signing secrets
3. run unit tests
4. run lint
5. build release APK
6. verify APK signature
7. verify zip alignment
8. prepare `AWEB-vX.Y.Z-arm64.apk`
9. generate `.sha256`
10. upload GitHub Release assets
11. upload workflow artifacts, including R8 mapping file

---

## 6. Post-release verification

Release page should contain:

- [ ] `AWEB-vX.Y.Z-arm64.apk`
- [ ] `AWEB-vX.Y.Z-arm64.apk.sha256`

Verify checksum:

```bash
curl -L -o AWEB-vX.Y.Z-arm64.apk \
  https://github.com/GuptaTheDominator/AWEB/releases/download/vX.Y.Z/AWEB-vX.Y.Z-arm64.apk
curl -L -o AWEB-vX.Y.Z-arm64.apk.sha256 \
  https://github.com/GuptaTheDominator/AWEB/releases/download/vX.Y.Z/AWEB-vX.Y.Z-arm64.apk.sha256
sha256sum -c AWEB-vX.Y.Z-arm64.apk.sha256
```

---

## 7. Redmi Pad SE smoke test

Install:

```bash
adb install -r AWEB-vX.Y.Z-arm64.apk
```

Test:

- [ ] cold launch
- [ ] foreground service notification appears
- [ ] default workspace loads
- [ ] open URL from omnibox
- [ ] open external Android `ACTION_VIEW` link
- [ ] create and switch workspace
- [ ] verify account/session isolation across two workspaces
- [ ] clear workspace data and confirm cookies/storage clear only for that workspace
- [ ] open 5–10 tabs
- [ ] mark Keep Alive tab and switch away
- [ ] download a PDF/file
- [ ] upload a file through `<input type="file">`
- [ ] camera/mic/location permission grant and deny
- [ ] fullscreen video enter/exit
- [ ] notification Exit stops service
- [ ] app relaunch restarts service
- [ ] HyperOS checklist progress persists

---

## 8. Emergency rollback

If a release is bad:

1. Mark the release as pre-release or delete the release asset.
2. Do not reuse the same version code.
3. Revert the bad commit on `main`.
4. Bump `versionCode` and patch `versionName`.
5. Publish a new patch tag.

Never publish two different APKs with the same version code.
