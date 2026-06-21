package com.example.gitview.ui.filetree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gitview.data.SyncService
import com.example.gitview.data.db.RepoEntity
import com.example.gitview.data.git.GitManager
import com.example.gitview.data.repository.RepoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileTreeUiState(
    val repoName: String = "",
    val fileTree: List<GitManager.FileNode> = emptyList(),
    val expandedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class FileTreeViewModel(
    private val repoId: Long,
    private val repoRepository: RepoRepository,
    private val syncService: SyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileTreeUiState(isLoading = true))
    val uiState: StateFlow<FileTreeUiState> = _uiState.asStateFlow()

    private var repoEntity: RepoEntity? = null

    init {
        loadRepo()
    }

    private fun loadRepo() {
        viewModelScope.launch {
            val repo = repoRepository.getById(repoId)
            if (repo != null) {
                repoEntity = repo

                val openResult = syncService.openRepoForBrowsing(repo)
                if (openResult is SyncService.SyncResult.Error) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = openResult.message
                    )
                    return@launch
                }

                val tree = syncService.getFileTree(repo)
                _uiState.value = _uiState.value.copy(
                    repoName = repo.name,
                    fileTree = tree,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Repository not found"
                )
            }
        }
    }

    fun toggleExpand(path: String) {
        val expanded = _uiState.value.expandedPaths.toMutableSet()
        if (expanded.contains(path)) {
            expanded.remove(path)
        } else {
            expanded.add(path)
        }
        _uiState.value = _uiState.value.copy(expandedPaths = expanded)
    }

    override fun onCleared() {
        super.onCleared()
        repoEntity?.let { syncService.closeRepo(it) }
    }

    class Factory(
        private val repoId: Long,
        private val repoRepository: RepoRepository,
        private val syncService: SyncService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FileTreeViewModel(repoId, repoRepository, syncService) as T
        }
    }
}
