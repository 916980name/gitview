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
