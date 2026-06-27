# Edit Repo URL — Design Spec

**Date:** 2025-06-27
**Status:** Approved

## Overview

Allow users to modify the `remoteUrl` of an already-cloned repository. The change updates both the Room database record and the git remote origin URL in the locally stored (encrypted) repository's `.git/config`.

## Approach

Reuse `AddRepoScreen` with an edit mode, triggered by an "Edit" option in the long-press menu on `RepoListScreen`.

## Data Layer Changes

### RepoDao
Add query method:
```kotlin
@Query("UPDATE repos SET remoteUrl = :newUrl WHERE id = :id")
suspend fun updateRemoteUrl(id: Long, newUrl: String)
```

### RepoRepository
Add passthrough:
```kotlin
suspend fun updateRemoteUrl(id: Long, newUrl: String) = dao.updateRemoteUrl(id, newUrl)
```

### GitManager
Add method to update git remote config via JGit:
```kotlin
fun updateRemoteUrl(repoDir: File, newUrl: String) {
    val git = Git.open(repoDir)
    val config = git.repository.config
    config.setString("remote", "origin", "url", newUrl)
    config.save()
    git.close()
}
```

### SyncService
Add method `suspend fun updateRepoRemote(repo: RepoEntity, newUrl: String, newName: String?)`:
1. Decrypt encrypted repo files from `repos/{uuid}/` to `temp/{uuid}/`
2. Call `GitManager.updateRemoteUrl(tempDir, newUrl)`
3. Re-encrypt temp directory back to `repos/{uuid}/`
4. Call `repoRepository.updateRemoteUrl(repo.id, newUrl)`
   - If `newName != null` and differs from current, also update via new DAO method
5. Clean up temp directory in `finally`

## UI & Navigation Changes

### Screen.kt
Modify `AddRepo` route to accept optional `repoId`:
```kotlin
// route: "add_repo?repoId={repoId}"
data object AddRepo : Screen("add_repo?repoId={repoId}")
```

### RepoListScreen
Long-press `AlertDialog` gains an "Edit" button before "Delete":
```
Edit | Delete | Cancel
```
Clicking Edit navigates: `navController.navigate("add_repo?repoId=${repo.id}")`

### AddRepoViewModel
Constructor takes optional `repoId: Long?`:
- **Edit mode** (`repoId != null`): load `RepoEntity` from Room in `init`, pre-fill `url` and `name` state fields, set `isEditMode = true`
- **Add mode** (`repoId == null`): behavior unchanged
- On save: when `isEditMode`, call `syncService.updateRepoRemote(repo, newUrl, newName)`; otherwise call `syncService.addRepo(url, name)` as before

UiState additions:
```kotlin
val isEditMode: Boolean = false
val originalRepo: RepoEntity? = null
```

### AddRepoScreen
Conditional rendering based on `isEditMode`:
- TopAppBar title: "Add Repository" (add) vs "Edit Repository" (edit)
- Button text: "Clone Repository" (add) vs "Save Changes" (edit)
- Button icon: clone icon vs check/save icon
- URL field validation remains identical (must start with `git://`)

## End-to-End Flow

1. User long-presses a repo card on `RepoListScreen`
2. AlertDialog shows: **Edit** | **Delete** | **Cancel**
3. User taps **Edit** → navigates to `add_repo?repoId={id}`
4. `AddRepoViewModel` loads existing `RepoEntity`, pre-fills form
5. Screen shows "Edit Repository" title with populated fields
6. User modifies URL (and optionally name), taps **Save Changes**
7. ViewModel validates URL format, calls `SyncService.updateRepoRemote()`
8. Service decrypts → updates git remote config → re-encrypts → updates Room
9. On success, `popBackStack` to RepoList (list auto-refreshes via Flow)

## Edge Cases & Error Handling

| Scenario | Handling |
|----------|----------|
| Encrypted repo files missing (manual deletion) | Decryption fails; catch error, still update Room record, show success toast |
| Temp directory creation fails | Show error toast, do not update Room |
| User presses back during edit | No changes saved; ViewModel discards state |
| URL unchanged on save | Skip decrypt/re-encrypt, only update Room (or no-op) |
| Name unchanged on save | Only URL is updated |
| URL validation fail (not `git://` prefix) | Save button remains disabled (same as add flow) |

## Files Modified

| File | Change |
|------|--------|
| `data/db/RepoDao.kt` | Add `updateRemoteUrl()` query |
| `data/repository/RepoRepository.kt` | Add `updateRemoteUrl()` passthrough |
| `data/git/GitManager.kt` | Add `updateRemoteUrl()` method |
| `data/SyncService.kt` | Add `updateRepoRemote()` method |
| `ui/navigation/Screen.kt` | Add optional `repoId` arg to AddRepo route |
| `ui/navigation/NavGraph.kt` | Pass optional `repoId` to AddRepoViewModel factory |
| `ui/addrepo/AddRepoViewModel.kt` | Constructor takes `repoId`, edit mode logic |
| `ui/addrepo/AddRepoScreen.kt` | Conditional rendering for edit mode |
| `ui/repolist/RepoListScreen.kt` | Add "Edit" to long-press dialog |
