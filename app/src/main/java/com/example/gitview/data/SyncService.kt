package com.example.gitview.data

import com.example.gitview.data.crypto.CryptoManager
import com.example.gitview.data.db.RepoEntity
import com.example.gitview.data.git.GitManager
import com.example.gitview.data.repository.RepoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SyncService(
    private val cryptoManager: CryptoManager,
    private val gitManager: GitManager,
    private val repoRepository: RepoRepository
) {

    sealed class SyncResult {
        data class Success(val message: String) : SyncResult()
        data class Error(val message: String) : SyncResult()
        data class Progress(val message: String) : SyncResult()
    }

    suspend fun addRepo(url: String, name: String): SyncResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val uuid = UUID.randomUUID().toString()
            val tempDir = cryptoManager.getRepoTempDir(uuid)
            val encryptedDir = cryptoManager.getRepoEncryptedDir(uuid)

            if (tempDir.exists()) tempDir.deleteRecursively()
            if (encryptedDir.exists()) encryptedDir.deleteRecursively()

            try {
                gitManager.cloneRepo(url, tempDir)

                val commitInfo = gitManager.getLastCommitInfo(tempDir)
                val entity = RepoEntity(
                    uuid = uuid,
                    name = name,
                    remoteUrl = url,
                    lastSyncTime = System.currentTimeMillis(),
                    lastCommitMessage = commitInfo.message
                )
                repoRepository.insert(entity)

                cryptoManager.encryptRepoFiles(tempDir, encryptedDir)
                SyncResult.Success("Repository cloned successfully")
            } finally {
                cryptoManager.cleanTempDir(uuid)
            }
        } catch (e: Exception) {
            SyncResult.Error("Failed to clone: ${e.message}")
        }
    }

    suspend fun syncRepo(repoEntity: RepoEntity): SyncResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val tempDir = cryptoManager.getRepoTempDir(repoEntity.uuid)
            val encryptedDir = cryptoManager.getRepoEncryptedDir(repoEntity.uuid)

            if (!encryptedDir.exists()) {
                return@withContext SyncResult.Error("Repository data not found")
            }

            if (tempDir.exists()) tempDir.deleteRecursively()

            try {
                cryptoManager.decryptRepoFiles(encryptedDir, tempDir)

                val changedFiles = gitManager.pullRepo(tempDir)
                val commitInfo = gitManager.getLastCommitInfo(tempDir)

                repoRepository.updateLastSync(
                    repoEntity.id,
                    System.currentTimeMillis(),
                    commitInfo.message
                )

                cryptoManager.encryptRepoFiles(tempDir, encryptedDir)

                if (changedFiles.isEmpty()) {
                    SyncResult.Success("Already up to date")
                } else {
                    SyncResult.Success("Updated ${changedFiles.size} file(s)")
                }
            } finally {
                cryptoManager.cleanTempDir(repoEntity.uuid)
            }
        } catch (e: Exception) {
            SyncResult.Error("Failed to sync: ${e.message}")
        }
    }

    suspend fun deleteRepo(repoEntity: RepoEntity): SyncResult = withContext(Dispatchers.IO) {
        return@withContext try {
            repoRepository.delete(repoEntity)
            cryptoManager.deleteRepoData(repoEntity.uuid)
            SyncResult.Success("Repository deleted")
        } catch (e: Exception) {
            SyncResult.Error("Failed to delete: ${e.message}")
        }
    }

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

    suspend fun openRepoForBrowsing(repoEntity: RepoEntity): SyncResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val tempDir = cryptoManager.getRepoTempDir(repoEntity.uuid)
            val encryptedDir = cryptoManager.getRepoEncryptedDir(repoEntity.uuid)

            if (!encryptedDir.exists()) {
                return@withContext SyncResult.Error("Repository data not found")
            }

            if (tempDir.exists()) {
                return@withContext SyncResult.Success("Repository already open")
            }

            cryptoManager.decryptRepoFiles(encryptedDir, tempDir)
            SyncResult.Success("Repository ready")
        } catch (e: Exception) {
            SyncResult.Error("Failed to open repository: ${e.message}")
        }
    }

    fun closeRepo(repoEntity: RepoEntity) {
        cryptoManager.cleanTempDir(repoEntity.uuid)
    }

    fun getRepoWorkingDir(repoEntity: RepoEntity): File {
        return cryptoManager.getRepoTempDir(repoEntity.uuid)
    }

    fun getFileTree(repoEntity: RepoEntity): List<GitManager.FileNode> {
        val tempDir = cryptoManager.getRepoTempDir(repoEntity.uuid)
        return if (tempDir.exists()) {
            gitManager.getFileTree(tempDir)
        } else {
            emptyList()
        }
    }

    fun readFile(repoEntity: RepoEntity, relativePath: String): String {
        val tempDir = cryptoManager.getRepoTempDir(repoEntity.uuid)
        val file = File(tempDir, relativePath)
        return gitManager.readFileContent(file)
    }
}
