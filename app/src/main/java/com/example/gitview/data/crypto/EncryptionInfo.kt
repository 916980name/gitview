package com.example.gitview.data.crypto

import kotlinx.serialization.Serializable

enum class EncryptionLevel {
    STRONGBOX,
    TEE,
    SOFTWARE
}

@Serializable
data class EncryptionInfo(
    val level: EncryptionLevel,
    val unlockedDeviceRequired: Boolean = true,
    val keySize: Int = 256,
    val algorithm: String = "AES/GCM/NoPadding",
    val createdAt: String = ""
)
