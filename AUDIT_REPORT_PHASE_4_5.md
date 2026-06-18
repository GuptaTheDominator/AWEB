# AWEB Phase 4 + Phase 5 Audit Report — Data Layer + Browser Features

Date: 2026-06-18  
Scope: Phase 4 data layer/persistence audit + Phase 5 browser feature audit.

---

## Phase 4 — Data Layer Audit

### Areas reviewed

- Room entities: workspace, tab, bookmark, app settings
- DAOs: active row updates, queries, deletes, migrations
- Repositories: workspace creation/deletion/reorder, tab lifecycle persistence, bookmark add/remove
- Data integrity: active workspace/tab invariants, unique context IDs, bookmark uniqueness, tab ordering
- Persistence/resume: tab restore state, active flags, Keep Alive state

### Findings fixed

#### P4-F1 — Bookmark URLs were not unique

`BookmarkEntity` had a non-unique `url` index, so duplicate bookmarks for the same URL were possible.

Fix:

- Made `BookmarkEntity.url` index unique.
- Bumped Room DB version to 4.
- Added migration 3→4 to deduplicate existing bookmark rows and recreate the unique index.

#### P4-F2 — Bookmark removal scanned the whole bookmark list

The UI toggle removed a bookmark by loading all bookmarks and finding the first matching URL.

Fix:

- Added `BookmarkDao.getByUrl()` and `BookmarkDao.deleteByUrl()`.
- Added `BookmarkRepository.removeByUrl()`.
- Updated bookmark toggle flow to remove by normalized URL.

#### P4-F3 — Active tab update could clear a workspace if given an invalid tab ID

`TabDao.setActive()` cleared all active flags in a workspace, then set the target tab active by ID only.

Fix:

- Added `existsInWorkspace(workspaceId, tabId)` guard.
- Updated `setActiveRaw()` to constrain by both tab ID and workspace ID.

#### P4-F4 — Active workspace update could clear all active flags if given an invalid workspace ID

`WorkspaceDao.switchActive()` cleared all active workspaces, then set the requested ID.

Fix:

- Added `WorkspaceDao.exists(id)` guard before clearing active flags.

#### P4-F5 — Workspace order was not compacted after deletion

Deleting a workspace could leave gaps in `orderIndex`.

Fix:

- `WorkspaceRepository.deleteWorkspace()` now recompacts order indexes after delete.

#### P4-F6 — Runtime tab queries needed better indexes

Frequent queries filter by `workspace_id + is_active` and order by `workspace_id + order_index`.

Fix:

- Added Room indexes:
  - `workspace_id, is_active`
  - `workspace_id, order_index`
- Migration 3→4 creates these indexes for existing installs.

---

## Phase 5 — Browser Feature Audit

### Areas reviewed

- Omnibox/search URL handling
- Downloads and filename handling
- Permission request flow
- File upload prompt path
- Bookmarks UI/data flow
- Find-in-page and fullscreen handling
- External URL opening from Android intents

### Findings fixed

#### P5-F1 — Download filenames were not safely sanitized

The old download filename logic accepted hints with path separators and other invalid characters.

Fix:

- Sanitized path traversal/separators/control characters.
- Removed leading/trailing dots/spaces/underscores.
- Added fallback filename and extension.
- Limited generated filenames to a safe length.

#### P5-F2 — Gecko permission requests could hang if UI event emission failed

`MutableSharedFlow.tryEmit()` return values were ignored. If the event buffer was full, Gecko permission `GeckoResult`/callbacks could remain unresolved.

Fix:

- Location/notification requests now complete with deny if emission fails.
- Media permission requests now reject if emission fails.
- Download confirmation now returns a Boolean so callers can fallback.

#### P5-F3 — Download enqueue errors were not logged at the browser feature boundary

Fix:

- `BrowserFeatureViewModel.confirmDownload()` now catches/logs `DownloadManager` enqueue failures.

#### P5-F4 — Bookmark toggle used inefficient/global scan

Fix included in Phase 4 bookmark repository changes.

---

## Validation status

Completed after Phase 4/5 edits:

- `./gradlew compileReleaseKotlin testReleaseUnitTest` — passed
- `./gradlew lintRelease` — passed with `0 errors, 64 warnings`
- `./gradlew assembleRelease` with a temporary local signing key — passed
- `apksigner verify` — passed
- `zipalign -c -p 4` — passed

Audit-build APK SHA-256 with temporary local signing key:

```text
9f4ab9d2df72f63c0d948a8c9725f8b3a02b0d220cc032212f7043e6bb711f9d
```

Note: this hash is for local validation only. A GitHub Actions release build will differ because it uses the repository release signing key.

---

## Remaining Phase 4/5 recommendations

These require deeper device/instrumentation testing or broader feature work:

1. Add instrumentation tests for Room migrations using real pre-v4 schemas.
2. Add Android/device tests for DownloadManager enqueue behavior.
3. Add a user-visible download failure snackbar/toast.
4. Add bookmark export/import in a later UX phase.
5. Add per-workspace bookmark mode if desired; current bookmarks remain global by design.
6. Add site permission persistence/management UI.
7. Add a dedicated downloads panel with progress and open/retry actions.
8. Add robust omnibox suggestions/history in a future browser UX pass.
