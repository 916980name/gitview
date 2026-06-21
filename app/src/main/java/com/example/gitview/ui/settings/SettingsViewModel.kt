package com.example.gitview.ui.settings

import androidx.lifecycle.ViewModel
import com.example.gitview.data.crypto.CryptoManager
import com.example.gitview.data.crypto.EncryptionInfo
import com.example.gitview.data.crypto.EncryptionLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val encryptionLevel: EncryptionLevel = EncryptionLevel.SOFTWARE,
    val encryptionAlgorithm: String = "AES/GCM/NoPadding",
    val keySize: Int = 256,
    val unlockedDeviceRequired: Boolean = true,
    val createdAt: String = ""
)

class SettingsViewModel(
    private val cryptoManager: CryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadEncryptionInfo()
    }

    private fun loadEncryptionInfo() {
        val info = cryptoManager.getEncryptionInfo()
        if (info != null) {
            _uiState.value = SettingsUiState(
                encryptionLevel = info.level,
                encryptionAlgorithm = info.algorithm,
                keySize = info.keySize,
                unlockedDeviceRequired = info.unlockedDeviceRequired,
                createdAt = info.createdAt
            )
        }
    }
}
