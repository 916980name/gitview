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
