# GitView — Project Context

## What It Does

Android app that syncs Git repos from a local network `git daemon` to a phone. Clones via `git://` protocol (JGit), encrypts all files with AES-256-GCM, stores encrypted on device. Allows browsing file trees and viewing rendered Markdown files.

## Architecture

**Pattern:** MVVM with manual DI (no Hilt/Dagger).

```
MainActivity (wires everything)
  ├── AppDatabase → RepoDao → RepoRepository
  ├── CryptoManager (Android Keystore KEK/DEK)
  ├── GitManager (JGit wrapper)
  └── SyncService (orchestrates clone/pull + encrypt/decrypt)
       ↓
  AppNavGraph (injects services into ViewModels via Factory pattern)
```

**Reactive:** ViewModels expose `StateFlow<XxxUiState>`, collected as Compose state.

**Threading:** Git + crypto ops on `Dispatchers.IO` via `withContext`.

## Key Files

```
data/
  db/RepoEntity.kt      — Room @Entity: id, uuid, name, remoteUrl, lastSyncTime, lastCommitMessage
  db/RepoDao.kt          — Room @Dao: CRUD + Flow<List<RepoEntity>> + updateUrlAndName
  db/AppDatabase.kt      — Room DB singleton, version 1
  repository/RepoRepository.kt — Thin wrapper over RepoDao
  git/GitManager.kt      — JGit: clone, pull, getFileTree, readFile, getLastCommitInfo, updateRemoteUrl
  crypto/CryptoManager.kt — AES-256-GCM: KEK(Keystore)/DEK(encrypted prefs), encrypt/decrypt files
  crypto/EncryptionInfo.kt — EncryptionLevel enum (StrongBox/TEE/Software)
  SyncService.kt         — Orchestrator: addRepo, syncRepo, deleteRepo, updateRepoRemote, open/close repo

ui/
  navigation/Screen.kt   — 5 sealed routes: RepoList, AddRepo, Settings, FileTree, MarkdownViewer
  navigation/NavGraph.kt — NavHost wiring, ViewModel factory injection
  repolist/              — RepoListViewModel + RepoListScreen (cards, long-press: Edit/Delete/Cancel)
  addrepo/               — AddRepoViewModel + AddRepoScreen (add & edit modes via optional repoId)
  filetree/              — FileTreeViewModel + FileTreeScreen (recursive expandable tree)
  markdown/              — MarkdownViewerViewModel + MarkdownViewerScreen (Markwon renderer)
  settings/              — SettingsViewModel + SettingsScreen (encryption status)
  theme/                 — Material 3 dynamic color + static fallback
```

## Key Patterns

1. Each screen has a `Screen + ViewModel` pair with `MutableStateFlow<UiState>`
2. ViewModels have inner `Factory` class taking deps, used with `viewModel(factory = ...)`
3. SyncService is the single orchestrator — ViewModels never call GitManager/CryptoManager directly
4. Repos stored encrypted at `files/repos/{uuid}/`, decrypted to `files/temp/{uuid}/` on use
5. Temp dirs always cleaned up in `finally` blocks
6. Only `git://` protocol — designed for local network git daemon
7. Read-only git ops — clone, pull; no push
8. `pullRepo` computes diff between old/new HEAD trees for change reporting

## Navigation

| Route | Pattern | Params |
|-------|---------|--------|
| `repo_list` | — | — |
| `add_repo?repoId={repoId}` | optional | repoId: Long, default -1 |
| `settings` | — | — |
| `file_tree/{repoId}` | required | repoId: Long |
| `markdown_viewer/{repoId}/{filePath}` | required | repoId: Long, filePath: String (URL-encoded) |

## Dependencies

JGit, Room + KSP, Navigation Compose, Kotlin Serialization, Markwon, Material 3 + Icons Extended, Compose BOM, DataStore Preferences

## Build

```bash
./gradlew assembleDebug
```

Minimum SDK requires Android Keystore support (API 23+ for hardware-backed keys).
