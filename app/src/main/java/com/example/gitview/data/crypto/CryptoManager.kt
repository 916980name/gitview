package com.example.gitview.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "gitview_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTION_INFO_FILE = "encryption_info.json"
        private const val AES_TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val DEK_SIZE = 256
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private var dek: SecretKey? = null

    private val filesDir: File
        get() = context.filesDir

    private val reposDir: File
        get() = File(filesDir, "repos")

    private val tempDir: File
        get() = File(filesDir, "temp")

    private val encryptionInfoFile: File
        get() = File(filesDir, ENCRYPTION_INFO_FILE)

    suspend fun initialize(): EncryptionLevel {
        cleanAllTempDirs()

        val (kek, level) = getOrCreateKEK()
        getOrCreateDEK(kek)

        val info = loadOrCreateEncryptionInfo(level)
        return info.level
    }

    fun isInitialized(): Boolean = dek != null

    private fun getOrCreateKEK(): Pair<SecretKey, EncryptionLevel> {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val level = loadEncryptionLevel() ?: detectExistingKeyLevel(key)
            return Pair(key, level)
        }
        return createKEK()
    }

    private fun createKEK(): Pair<SecretKey, EncryptionLevel> {
        val level = tryCreateStrongBoxKey() ?: tryCreateTEEKey() ?: tryCreateSoftwareKey()
        if (level == null) {
            throw IllegalStateException("Failed to create encryption key")
        }
        val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        return Pair(key, level)
    }

    private fun tryCreateStrongBoxKey(): EncryptionLevel? {
        return try {
            generateKeyInKeystore(isStrongBox = true)
            EncryptionLevel.STRONGBOX
        } catch (e: Exception) {
            null
        }
    }

    private fun tryCreateTEEKey(): EncryptionLevel? {
        return try {
            generateKeyInKeystore(isStrongBox = false)
            val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
            val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (keyInfo.isInsideSecureHardware) EncryptionLevel.TEE else EncryptionLevel.SOFTWARE
        } catch (e: Exception) {
            null
        }
    }

    private fun tryCreateSoftwareKey(): EncryptionLevel? {
        return try {
            generateKeyInKeystore(isStrongBox = false)
            EncryptionLevel.SOFTWARE
        } catch (e: Exception) {
            null
        }
    }

    private fun generateKeyInKeystore(isStrongBox: Boolean) {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(DEK_SIZE)
            .setUnlockedDeviceRequired(true)

        if (isStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }

    private fun detectExistingKeyLevel(key: SecretKey): EncryptionLevel {
        return try {
            val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            when {
                keyInfo.isInsideSecureHardware -> {
                    if (keyInfo.isUserAuthenticationRequired && keyInfo.userAuthenticationValidityDurationSeconds > 0) {
                        try {
                            val strongBoxKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                            if (strongBoxKey != null) {
                                EncryptionLevel.TEE
                            } else {
                                EncryptionLevel.TEE
                            }
                        } catch (e: Exception) {
                            EncryptionLevel.TEE
                        }
                    }
                    EncryptionLevel.TEE
                }
                else -> EncryptionLevel.SOFTWARE
            }
        } catch (e: Exception) {
            EncryptionLevel.SOFTWARE
        }
    }

    private fun getOrCreateDEK(kek: SecretKey) {
        dek = try {
            val encryptedDekData = loadEncryptedDEK()
            if (encryptedDekData != null) {
                decryptDEK(kek, encryptedDekData)
            } else {
                val newDek = generateDEK()
                encryptAndSaveDEK(kek, newDek)
                newDek
            }
        } catch (e: Exception) {
            null
        }

        if (dek == null) {
            val newDek = generateDEK()
            encryptAndSaveDEK(kek, newDek)
            dek = newDek
        }
    }

    private fun generateDEK(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(DEK_SIZE, SecureRandom())
        return keyGenerator.generateKey()
    }

    private fun encryptAndSaveDEK(kek: SecretKey, dek: SecretKey) {
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(dek.encoded)

        val output = ByteArrayOutputStream()
        output.write(iv)
        output.write(ByteBuffer.allocate(4).putInt(ciphertext.size).array())
        output.write(ciphertext)

        context.getSharedPreferences("gitview_crypto", Context.MODE_PRIVATE)
            .edit()
            .putString("encrypted_dek", android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP))
            .apply()
    }

    private fun loadEncryptedDEK(): ByteArray? {
        val base64 = context.getSharedPreferences("gitview_crypto", Context.MODE_PRIVATE)
            .getString("encrypted_dek", null) ?: return null
        return android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
    }

    private fun decryptDEK(kek: SecretKey, encryptedData: ByteArray): SecretKey {
        val buffer = ByteBuffer.wrap(encryptedData)
        val iv = ByteArray(IV_LENGTH)
        buffer.get(iv)
        val ciphertextLen = buffer.int
        val ciphertext = ByteArray(ciphertextLen)
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val dekBytes = cipher.doFinal(ciphertext)
        return SecretKeySpec(dekBytes, "AES")
    }

    fun encryptFile(inputFile: File): ByteArray {
        val dek = checkNotNull(dek) { "CryptoManager not initialized" }
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, dek)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(inputFile.readBytes())

        val output = ByteArrayOutputStream()
        output.write(iv)
        output.write(ByteBuffer.allocate(4).putInt(ciphertext.size).array())
        output.write(ciphertext)
        return output.toByteArray()
    }

    fun encryptData(data: ByteArray): ByteArray {
        val dek = checkNotNull(dek) { "CryptoManager not initialized" }
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, dek)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)

        val output = ByteArrayOutputStream()
        output.write(iv)
        output.write(ByteBuffer.allocate(4).putInt(ciphertext.size).array())
        output.write(ciphertext)
        return output.toByteArray()
    }

    fun decryptData(encryptedData: ByteArray): ByteArray {
        val dek = checkNotNull(dek) { "CryptoManager not initialized" }
        val buffer = ByteBuffer.wrap(encryptedData)
        val iv = ByteArray(IV_LENGTH)
        buffer.get(iv)
        val ciphertextLen = buffer.int
        val ciphertext = ByteArray(ciphertextLen)
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    fun decryptFileTo(encryptedData: ByteArray, outputFile: File) {
        val plaintext = decryptData(encryptedData)
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(plaintext)
    }

    fun encryptRepoFiles(sourceDir: File, targetDir: File) {
        targetDir.mkdirs()
        sourceDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(sourceDir).path
                val encryptedFile = File(targetDir, "$relativePath.enc")
                encryptedFile.parentFile?.mkdirs()
                encryptedFile.writeBytes(encryptFile(file))
            }
        }
    }

    fun decryptRepoFiles(sourceDir: File, targetDir: File) {
        targetDir.mkdirs()
        sourceDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "enc") {
                val relativeEncPath = file.relativeTo(sourceDir).path
                val relativePath = relativeEncPath.removeSuffix(".enc")
                val plainFile = File(targetDir, relativePath)
                decryptFileTo(file.readBytes(), plainFile)
            }
        }
    }

    fun getRepoEncryptedDir(repoUuid: String): File = File(reposDir, repoUuid)
    fun getRepoTempDir(repoUuid: String): File = File(tempDir, repoUuid)

    fun cleanTempDir(repoUuid: String) {
        val dir = getRepoTempDir(repoUuid)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun cleanAllTempDirs() {
        if (tempDir.exists()) {
            tempDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    fun deleteRepoData(repoUuid: String) {
        val encryptedDir = getRepoEncryptedDir(repoUuid)
        if (encryptedDir.exists()) {
            encryptedDir.deleteRecursively()
        }
        cleanTempDir(repoUuid)
    }

    private fun loadOrCreateEncryptionInfo(level: EncryptionLevel): EncryptionInfo {
        return try {
            val json = encryptionInfoFile.readText()
            Json.decodeFromString<EncryptionInfo>(json)
        } catch (e: Exception) {
            val info = EncryptionInfo(
                level = level,
                createdAt = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    java.util.Locale.US
                ).format(java.util.Date())
            )
            encryptionInfoFile.writeText(Json.encodeToString(info))
            info
        }
    }

    private fun loadEncryptionLevel(): EncryptionLevel? {
        return try {
            val json = encryptionInfoFile.readText()
            Json.decodeFromString<EncryptionInfo>(json).level
        } catch (e: Exception) {
            null
        }
    }

    fun getEncryptionInfo(): EncryptionInfo? {
        return try {
            val json = encryptionInfoFile.readText()
            Json.decodeFromString<EncryptionInfo>(json)
        } catch (e: Exception) {
            null
        }
    }
}
