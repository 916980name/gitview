package com.example.gitview.ui.markdown

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gitview.data.SyncService
import com.example.gitview.data.db.RepoEntity
import com.example.gitview.data.repository.RepoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MarkdownViewerUiState(
    val fileName: String = "",
    val content: String = "",
    val isLoading: Boolean = false,
    val isMarkdown: Boolean = true,
    val errorMessage: String? = null
)

class MarkdownViewerViewModel(
    private val repoId: Long,
    private val filePath: String,
    private val repoRepository: RepoRepository,
    private val syncService: SyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarkdownViewerUiState(isLoading = true))
    val uiState: StateFlow<MarkdownViewerUiState> = _uiState.asStateFlow()

    init {
        loadFile()
    }

    private fun loadFile() {
        viewModelScope.launch {
            try {
                val repo = repoRepository.getById(repoId)
                if (repo == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Repository not found"
                    )
                    return@launch
                }

                val content = syncService.readFile(repo, filePath)
                val isMarkdown = filePath.endsWith(".md", ignoreCase = true) ||
                    filePath.endsWith(".markdown", ignoreCase = true)

                _uiState.value = MarkdownViewerUiState(
                    fileName = filePath.substringAfterLast("/"),
                    content = content,
                    isLoading = false,
                    isMarkdown = isMarkdown
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to read file: ${e.message}"
                )
            }
        }
    }

    class Factory(
        private val repoId: Long,
        private val filePath: String,
        private val repoRepository: RepoRepository,
        private val syncService: SyncService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MarkdownViewerViewModel(repoId, filePath, repoRepository, syncService) as T
        }
    }
}
