# Edit Repo URL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to edit a cloned repo's URL (and optionally name) from the repo list via long-press, reusing the AddRepoScreen in edit mode.

**Architecture:** AddRepo route gains optional `repoId` param. When present, AddRepoViewModel loads existing repo and operates in edit mode. SyncService.decrypts → GitManager updates remote config → re-encrypts → updates Room. RepoListScreen long-press dialog gains an Edit button.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Room, JGit, MVVM

---

### Task 1: Add updateUrlAndName to RepoDao

**Files:**
- Modify: `app/src/main/java/com/example/gitview/data/db/RepoDao.kt`

- [ ] **Step 1: Add the update query**

```kotlin
@Query("UPDATE repos SET remoteUrl = :url, name = :name WHERE id = :id")
suspend fun updateUrlAndName(id: Long, url: String, name: String)
```

Add this after the `updateLastSync` method (line 29):

```kotlin
package com.example.gitview.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {

    @Query("SELECT * FROM repos ORDER BY lastSyncTime DESC")
    fun getAll(): Flow<List<RepoEntity>>

    @Query("SELECT * FROM repos WHERE id = :id")
    suspend fun getById(id: Long): RepoEntity?

    @Query("SELECT * FROM repos WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): RepoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repo: RepoEntity): Long

    @Delete
    suspend fun delete(repo: RepoEntity)

    @Query("UPDATE repos SET lastSyncTime = :syncTime, lastCommitMessage = :commitMessage WHERE id = :id")
    suspend fun updateLastSync(id: Long, syncTime: Long, commitMessage: String)

    @Query("UPDATE repos SET remoteUrl = :url, name = :name WHERE id = :id")
    suspend fun updateUrlAndName(id: Long, url: String, name: String)

    @Query("DELETE FROM repos WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)
}
```

### Task 2: Add updateUrlAndName passthrough to RepoRepository

**Files:**
- Modify: `app/src/main/java/com/example/gitview/data/repository/RepoRepository.kt`

- [ ] **Step 1: Add the passthrough method**

```kotlin
suspend fun updateUrlAndName(id: Long, url: String, name: String) =
    repoDao.updateUrlAndName(id, url, name)
```

Add after the `updateLastSync` method (line 19-20):

```kotlin
package com.example.gitview.data.repository

import com.example.gitview.data.db.RepoDao
import com.example.gitview.data.db.RepoEntity
import kotlinx.coroutines.flow.Flow

class RepoRepository(private val repoDao: RepoDao) {

    val allRepos: Flow<List<RepoEntity>> = repoDao.getAll()

    suspend fun getById(id: Long): RepoEntity? = repoDao.getById(id)

    suspend fun getByUuid(uuid: String): RepoEntity? = repoDao.getByUuid(uuid)

    suspend fun insert(repo: RepoEntity): Long = repoDao.insert(repo)

    suspend fun delete(repo: RepoEntity) = repoDao.delete(repo)

    suspend fun updateLastSync(id: Long, syncTime: Long, commitMessage: String) =
        repoDao.updateLastSync(id, syncTime, commitMessage)

    suspend fun updateUrlAndName(id: Long, url: String, name: String) =
        repoDao.updateUrlAndName(id, url, name)

    suspend fun deleteByUuid(uuid: String) = repoDao.deleteByUuid(uuid)
}
```

### Task 3: Add updateRemoteUrl to GitManager

**Files:**
- Modify: `app/src/main/java/com/example/gitview/data/git/GitManager.kt`

- [ ] **Step 1: Add the updateRemoteUrl method**

Add after the `pullRepo` method (after line 66):

```kotlin
fun updateRemoteUrl(repoDir: File, newUrl: String) {
    Git.open(repoDir).use { git ->
        val config = git.repository.config
        config.setString("remote", "origin", "url", newUrl)
        config.save()
    }
}
```

### Task 4: Add updateRepoRemote to SyncService

**Files:**
- Modify: `app/src/main/java/com/example/gitview/data/SyncService.kt`

- [ ] **Step 1: Add the updateRepoRemote method**

Add after the `deleteRepo` method (after line 102):

```kotlin
suspend fun updateRepoRemote(
    repo: RepoEntity,
    newUrl: String,
    newName: String?
): SyncResult = withContext(Dispatchers.IO) {
    return@withContext try {
        val tempDir = cryptoManager.getRepoTempDir(repo.uuid)
        val encryptedDir = cryptoManager.getRepoEncryptedDir(repo.uuid)

        if (tempDir.exists()) tempDir.deleteRecursively()

        if (encryptedDir.exists()) {
            try {
                cryptoManager.decryptRepoFiles(encryptedDir, tempDir)
                gitManager.updateRemoteUrl(tempDir, newUrl)
                cryptoManager.encryptRepoFiles(tempDir, encryptedDir)
            } finally {
                cryptoManager.cleanTempDir(repo.uuid)
            }
        }

        repoRepository.updateUrlAndName(
            repo.id,
            newUrl,
            newName ?: repo.name
        )
        SyncResult.Success("Repository updated")
    } catch (e: Exception) {
        SyncResult.Error("Failed to update: ${e.message}")
    }
}
```

### Task 5: Add optional repoId to AddRepo route in Screen.kt

**Files:**
- Modify: `app/src/main/java/com/example/gitview/ui/navigation/Screen.kt`

- [ ] **Step 1: Update AddRepo route with optional repoId**

```kotlin
data object AddRepo : Screen("add_repo?repoId={repoId}") {
    fun createRoute() = "add_repo"
    fun createRoute(repoId: Long) = "add_repo?repoId=$repoId"
}
```

Replace line 5 (`data object AddRepo : Screen("add_repo")`):

```kotlin
package com.example.gitview.ui.navigation

sealed class Screen(val route: String) {
    data object RepoList : Screen("repo_list")
    data object AddRepo : Screen("add_repo?repoId={repoId}") {
        fun createRoute() = "add_repo"
        fun createRoute(repoId: Long) = "add_repo?repoId=$repoId"
    }
    data object Settings : Screen("settings")
    data object FileTree : Screen("file_tree/{repoId}") {
        fun createRoute(repoId: Long) = "file_tree/$repoId"
    }
    data object MarkdownViewer : Screen("markdown_viewer/{repoId}/{filePath}") {
        fun createRoute(repoId: Long, filePath: String) = "markdown_viewer/$repoId/$filePath"
    }
}
```

### Task 6: Update NavGraph to pass repoId to AddRepoViewModel

**Files:**
- Modify: `app/src/main/java/com/example/gitview/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Update AddRepo composable to extract repoId**

Replace the AddRepo composable block (lines 51-60):

```kotlin
composable(
    route = Screen.AddRepo.route,
    arguments = listOf(navArgument("repoId") { type = NavType.LongType; defaultValue = -1L })
) { backStackEntry ->
    val repoId = backStackEntry.arguments?.getLong("repoId") ?: -1L
    val editRepoId = if (repoId == -1L) null else repoId
    val viewModel: AddRepoViewModel = viewModel(
        factory = AddRepoViewModel.Factory(editRepoId, repoRepository, syncService)
    )
    AddRepoScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() },
        onSuccess = { navController.popBackStack() }
    )
}
```

- [ ] **Step 2: Update RepoList composable to pass onEditRepo and use createRoute for onAddRepo**

Replace the RepoList composable block (lines 37-49):

```kotlin
composable(Screen.RepoList.route) {
    val viewModel: RepoListViewModel = viewModel(
        factory = RepoListViewModel.Factory(repoRepository, syncService)
    )
    RepoListScreen(
        viewModel = viewModel,
        onAddRepo = { navController.navigate(Screen.AddRepo.createRoute()) },
        onRepoClick = { repoId ->
            navController.navigate(Screen.FileTree.createRoute(repoId))
        },
        onEditRepo = { repoId ->
            navController.navigate(Screen.AddRepo.createRoute(repoId))
        },
        onSettings = { navController.navigate(Screen.Settings.route) }
    )
}
```

### Task 7: Add edit mode to AddRepoViewModel

**Files:**
- Modify: `app/src/main/java/com/example/gitview/ui/addrepo/AddRepoViewModel.kt`

- [ ] **Step 1: Update the full file with edit mode support**

Replace the entire file:

```kotlin
package com.example.gitview.ui.addrepo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gitview.data.SyncService
import com.example.gitview.data.db.RepoEntity
import com.example.gitview.data.repository.RepoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddRepoUiState(
    val url: String = "git://192.168.0.1/",
    val name: String = "",
    val isCloning: Boolean = false,
    val resultMessage: String? = null,
    val isSuccess: Boolean = false,
    val isEditMode: Boolean = false
)

class AddRepoViewModel(
    private val repoId: Long?,
    private val repoRepository: RepoRepository?,
    private val syncService: SyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRepoUiState())
    val uiState: StateFlow<AddRepoUiState> = _uiState.asStateFlow()

    private var originalRepo: RepoEntity? = null

    init {
        val id = repoId
        val repository = repoRepository
        if (id != null && repository != null) {
            viewModelScope.launch {
                val repo = repository.getById(id)
                if (repo != null) {
                    originalRepo = repo
                    _uiState.value = _uiState.value.copy(
                        url = repo.remoteUrl,
                        name = repo.name,
                        isEditMode = true
                    )
                }
            }
        }
    }

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url, resultMessage = null)
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name, resultMessage = null)
    }

    fun isValidUrl(): Boolean {
        val url = _uiState.value.url.trim()
        return url.startsWith("git://") && url.length > 6
    }

    fun saveRepo() {
        val url = _uiState.value.url.trim()
        val name = _uiState.value.name.trim().ifEmpty { extractRepoName(url) }

        if (_uiState.value.isEditMode) {
            updateRepo(url, name)
        } else {
            cloneRepo(url, name)
        }
    }

    private fun cloneRepo(url: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCloning = true, resultMessage = null)
            val result = syncService.addRepo(url, name)
            _uiState.value = _uiState.value.copy(
                isCloning = false,
                resultMessage = when (result) {
                    is SyncService.SyncResult.Success -> result.message
                    is SyncService.SyncResult.Error -> result.message
                    else -> null
                },
                isSuccess = result is SyncService.SyncResult.Success
            )
        }
    }

    private fun updateRepo(newUrl: String, newName: String) {
        val repo = originalRepo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCloning = true, resultMessage = null)
            val result = syncService.updateRepoRemote(repo, newUrl, newName)
            _uiState.value = _uiState.value.copy(
                isCloning = false,
                resultMessage = when (result) {
                    is SyncService.SyncResult.Success -> result.message
                    is SyncService.SyncResult.Error -> result.message
                    else -> null
                },
                isSuccess = result is SyncService.SyncResult.Success
            )
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(resultMessage = null)
    }

    private fun extractRepoName(url: String): String {
        val path = url.removePrefix("git://").trimEnd('/')
        val segments = path.split("/").filter { it.isNotEmpty() }
        return segments.lastOrNull()?.removeSuffix(".git") ?: "repo"
    }

    class Factory(
        private val repoId: Long?,
        private val repoRepository: RepoRepository?,
        private val syncService: SyncService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddRepoViewModel(repoId, repoRepository, syncService) as T
        }
    }
}
```

### Task 8: Update AddRepoScreen for edit mode rendering

**Files:**
- Modify: `app/src/main/java/com/example/gitview/ui/addrepo/AddRepoScreen.kt`

- [ ] **Step 1: Update title, button text, and button action to use saveRepo**

```kotlin
package com.example.gitview.ui.addrepo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoScreen(
    viewModel: AddRepoViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Repository" else "Add Repository") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = { viewModel.updateUrl(it) },
                label = { Text("Git URL") },
                placeholder = { Text("git://192.168.0.1/my-repo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isCloning
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Repository Name (optional)") },
                placeholder = { Text("Will be auto-detected from URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isCloning
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.saveRepo() },
                enabled = viewModel.isValidUrl() && !uiState.isCloning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isCloning) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text(if (uiState.isEditMode) "Saving..." else "Cloning...")
                } else if (uiState.isEditMode) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Save Changes")
                } else {
                    Text("Clone Repository")
                }
            }

            uiState.resultMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.isSuccess)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
```

### Task 9: Add Edit button to RepoListScreen long-press dialog

**Files:**
- Modify: `app/src/main/java/com/example/gitview/ui/repolist/RepoListScreen.kt`

- [ ] **Step 1: Add onEditRepo parameter to function signature**

Add `onEditRepo: (Long) -> Unit` parameter after `onRepoClick`:

```kotlin
@Composable
fun RepoListScreen(
    viewModel: RepoListViewModel,
    onAddRepo: () -> Unit,
    onRepoClick: (Long) -> Unit,
    onEditRepo: (Long) -> Unit,
    onSettings: () -> Unit
) {
```

- [ ] **Step 2: Change variable name from repoToDelete to selectedRepo, pass onEditRepo to RepoCard**

Replace `var repoToDelete by remember { mutableStateOf<RepoEntity?>(null) }` with `var selectedRepo by remember { mutableStateOf<RepoEntity?>(null) }`. Update the RepoCard call and dialog:

In the items block (lines 130-141), replace:

```kotlin
                items(uiState.repos, key = { it.id }) { repo ->
                    val isSyncing = uiState.syncingRepoId == repo.id
                    RepoCard(
                        repo = repo,
                        isSyncing = isSyncing,
                        onClick = { onRepoClick(repo.id) },
                        onSync = { viewModel.syncRepo(repo) },
                        onDelete = { repoToDelete = repo },
                        modifier = Modifier
                    )
                }
```

With:

```kotlin
                items(uiState.repos, key = { it.id }) { repo ->
                    val isSyncing = uiState.syncingRepoId == repo.id
                    RepoCard(
                        repo = repo,
                        isSyncing = isSyncing,
                        onClick = { onRepoClick(repo.id) },
                        onSync = { viewModel.syncRepo(repo) },
                        onLongPress = { selectedRepo = repo },
                        modifier = Modifier
                    )
                }
```

- [ ] **Step 3: Replace the delete dialog with a multi-action dialog**

Replace the entire `repoToDelete?.let { ... }` block (lines 145-164):

```kotlin
    selectedRepo?.let { repo ->
        AlertDialog(
            onDismissRequest = { selectedRepo = null },
            title = { Text(repo.name) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { selectedRepo = null }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = {
                        selectedRepo = null
                        viewModel.deleteRepo(repo)
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = {
                        selectedRepo = null
                        onEditRepo(repo.id)
                    }) {
                        Text("Edit")
                    }
                }
            }
        )
    }
```

- [ ] **Step 4: Update RepoCard to use onLongPress callback instead of onDelete**

Change the `RepoCard` function signature and body. Replace lines 167-226:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepoCard(
    repo: RepoEntity,
    isSyncing: Boolean,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = repo.remoteUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (repo.lastSyncTime > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last synced: ${formatTimestamp(repo.lastSyncTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = onSync) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
```

**Note:** The `Icons.Default.Delete` import at line 21 is no longer needed and can be removed as it's not used in the UI anymore (delete is handled via TextButton text). However, leaving it doesn't cause any compilation errors.

### Task 10: Build and verify

**Files:** None — verification only

- [ ] **Step 1: Build the project**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit all changes**

```bash
git add app/src/main/java/com/example/gitview/data/db/RepoDao.kt \
        app/src/main/java/com/example/gitview/data/repository/RepoRepository.kt \
        app/src/main/java/com/example/gitview/data/git/GitManager.kt \
        app/src/main/java/com/example/gitview/data/SyncService.kt \
        app/src/main/java/com/example/gitview/ui/navigation/Screen.kt \
        app/src/main/java/com/example/gitview/ui/navigation/NavGraph.kt \
        app/src/main/java/com/example/gitview/ui/addrepo/AddRepoViewModel.kt \
        app/src/main/java/com/example/gitview/ui/addrepo/AddRepoScreen.kt \
        app/src/main/java/com/example/gitview/ui/repolist/RepoListScreen.kt
git commit -m "feat: allow editing repo URL and name via long-press"
```
