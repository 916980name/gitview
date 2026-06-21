package com.example.gitview.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repos")
data class RepoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val name: String,
    val remoteUrl: String,
    val lastSyncTime: Long = 0,
    val lastCommitMessage: String = ""
)
