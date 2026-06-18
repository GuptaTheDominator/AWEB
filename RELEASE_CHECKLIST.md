# AWEB Release Checklist

Use this checklist before publishing a new tagged APK release.

## 1. Source/version checks

- [ ] `app/build.gradle.kts` `versionName` matches the intended tag without the leading `v`.
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
grep -R "ghp_\|github_pat_" -n --exclude-dir=.git .
```

## 2. Local validation

Run before tagging:

```bash
./gradlew compileReleaseKotlin testReleaseUnitTest --no-daemon --stacktrace
./gradlew lintRelease --no-daemon --stacktrace
```

Optional local package validation with a temporary key:

```bash
./gradlew assembleRelease --no-daemon --stacktrace
apksigner verify --verbose --print-certs app/build/outputs/apk/release/*arm64-v8a-release.apk
zipalign -c -p 4 app/build/outputs/apk/release/*arm64-v8a-release.apk
```

## 3. GitHub Actions secrets

Release workflow requires these repository secrets:

```text
AWEB_KEYSTORE_BASE64
AWEB_KEYSTORE_PASSWORD
AWEB_KEY_ALIAS
AWEB_KEY_PASSWORD
```

The keystore should be stable across releases. Do not regenerate it unless you intentionally accept Android update/signature incompatibility.

## 4. Tag and release

After merging to `main`:

```bash
git tag -a vX.Y.Z -m "AWEB vX.Y.Z"
git push origin vX.Y.Z
```

The `Build and Release` workflow will:

1. validate `versionName == tag`
2. restore signing secrets
3. run unit tests
4. run lint
5. build release APK
6. verify signature and zip alignment
7. prepare `AWEB-vX.Y.Z-arm64.apk`
8. generate `.sha256`
9. upload GitHub Release assets
10. upload build artifacts, including R8 mapping file, to the workflow run

## 5. Post-release verification

Verify the release page has:

- [ ] `AWEB-vX.Y.Z-arm64.apk`
- [ ] `AWEB-vX.Y.Z-arm64.apk.sha256`

Then verify checksum:

```bash
curl -L -o AWEB-vX.Y.Z-arm64.apk https://github.com/GuptaTheDominator/AWEB/releases/download/vX.Y.Z/AWEB-vX.Y.Z-arm64.apk
curl -L -o AWEB-vX.Y.Z-arm64.apk.sha256 https://github.com/GuptaTheDominator/AWEB/releases/download/vX.Y.Z/AWEB-vX.Y.Z-arm64.apk.sha256
sha256sum -c AWEB-vX.Y.Z-arm64.apk.sha256
```

## 6. Device smoke test

On Redmi Pad SE / HyperOS:

```bash
adb install -r AWEB-vX.Y.Z-arm64.apk
```

Test:

- [ ] cold launch
- [ ] default workspace loads
- [ ] open URL from omnibox
- [ ] open external Android `ACTION_VIEW` link
- [ ] create workspace
- [ ] workspace isolation login/cookie check
- [ ] file upload picker
- [ ] download confirmation + file appears in Downloads
- [ ] camera/mic/location permission prompt
- [ ] fullscreen video enter/exit
- [ ] Keep Alive tab survives switching
- [ ] notification Exit stops service
- [ ] app relaunch restarts service
- [ ] HyperOS checklist progress persists

## 7. Emergency rollback

If a release is bad:

1. Mark it as pre-release or delete the release asset.
2. Do not delete the tag unless absolutely necessary.
3. Revert the bad commit on `main`.
4. Bump `versionCode` and publish a new patch version.

Never reuse the same version code for a different APK.
