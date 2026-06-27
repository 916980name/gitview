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
