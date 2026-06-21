package com.example.gitview.ui.repolist

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

data class RepoListUiState(
    val repos: List<RepoEntity> = emptyList(),
    val isLoading: Boolean = false,
    val syncMessage: String? = null,
    val errorMessage: String? = null,
    val syncingRepoId: Long? = null
)

class RepoListViewModel(
    private val repoRepository: RepoRepository,
    private val syncService: SyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoListUiState(isLoading = true))
    val uiState: StateFlow<RepoListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repoRepository.allRepos.collect { repos ->
                _uiState.value = _uiState.value.copy(repos = repos, isLoading = false)
            }
        }
    }

    fun syncRepo(repo: RepoEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncingRepoId = repo.id)
            val result = syncService.syncRepo(repo)
            _uiState.value = _uiState.value.copy(
                syncingRepoId = null,
                syncMessage = when (result) {
                    is SyncService.SyncResult.Success -> result.message
                    is SyncService.SyncResult.Error -> result.message
                    else -> null
                }
            )
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    fun deleteRepo(repo: RepoEntity) {
        viewModelScope.launch {
            val result = syncService.deleteRepo(repo)
            _uiState.value = _uiState.value.copy(
                syncMessage = when (result) {
                    is SyncService.SyncResult.Success -> result.message
                    is SyncService.SyncResult.Error -> result.message
                    else -> null
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(syncMessage = null, errorMessage = null)
    }

    class Factory(
        private val repoRepository: RepoRepository,
        private val syncService: SyncService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RepoListViewModel(repoRepository, syncService) as T
        }
    }
}
