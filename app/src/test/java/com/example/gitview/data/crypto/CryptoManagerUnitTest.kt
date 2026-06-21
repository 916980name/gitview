package com.example.gitview.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManagerUnitTest {

    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12

    @Test
    fun aesGcm_encryptDecrypt_roundtrip() {
        val key = generateAESKey()
        val plaintext = "Hello, this is a test message for encryption!".toByteArray()

        val encrypted = encrypt(plaintext, key)
        val decrypted = decrypt(encrypted, key)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun aesGcm_samePlaintext_producesDifferentCiphertext() {
        val key = generateAESKey()
        val plaintext = "same message".toByteArray()

        val encrypted1 = encrypt(plaintext, key)
        val encrypted2 = encrypt(plaintext, key)

        assertTrue(!encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun aesGcm_tamperedCiphertext_failsDecryption() {
        val key = generateAESKey()
        val plaintext = "sensitive data".toByteArray()

        val encrypted = encrypt(plaintext, key)
        encrypted[encrypted.size / 2] = (encrypted[encrypted.size / 2] + 1).toByte()

        try {
            decrypt(encrypted, key)
            assertTrue("Should have thrown exception", false)
        } catch (e: Exception) {
            assertTrue(true)
        }
    }

    @Test
    fun aesGcm_wrongKey_failsDecryption() {
        val key1 = generateAESKey()
        val key2 = generateAESKey()
        val plaintext = "secret".toByteArray()

        val encrypted = encrypt(plaintext, key1)
        try {
            decrypt(encrypted, key2)
            assertTrue("Should have thrown exception", false)
        } catch (e: Exception) {
            assertTrue(true)
        }
    }

    @Test
    fun encryptedFileFormat_parsesCorrectly() {
        val key = generateAESKey()
        val plaintext = "File format test data".toByteArray()

        val encrypted = encrypt(plaintext, key)

        val buffer = ByteBuffer.wrap(encrypted)
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val ciphertextLen = buffer.int
        val ciphertext = ByteArray(ciphertextLen)
        buffer.get(ciphertext)

        assertEquals(ivLength, iv.size)
        assertTrue(ciphertextLen > 0)
        assertEquals(ciphertextLen, ciphertext.size)

        val decrypted = decrypt(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun encryptionLevel_enumValues() {
        assertEquals(3, EncryptionLevel.entries.size)
        assertEquals(EncryptionLevel.STRONGBOX, EncryptionLevel.valueOf("STRONGBOX"))
        assertEquals(EncryptionLevel.TEE, EncryptionLevel.valueOf("TEE"))
        assertEquals(EncryptionLevel.SOFTWARE, EncryptionLevel.valueOf("SOFTWARE"))
    }

    @Test
    fun encryptionInfo_serialization() {
        val info = EncryptionInfo(
            level = EncryptionLevel.TEE,
            userAuthenticationRequired = true,
            keySize = 256,
            algorithm = "AES/GCM/NoPadding",
            createdAt = "2026-06-21T10:00:00Z"
        )

        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.serializer(), info
        )
        assertTrue(json.contains("TEE"))
        assertTrue(json.contains("AES/GCM/NoPadding"))

        val decoded = kotlinx.serialization.json.Json.decodeFromString<EncryptionInfo>(json)
        assertEquals(EncryptionLevel.TEE, decoded.level)
        assertEquals(256, decoded.keySize)
    }

    private fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256, SecureRandom())
        return keyGenerator.generateKey()
    }

    private fun encrypt(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)

        val output = java.io.ByteArrayOutputStream()
        output.write(iv)
        output.write(ByteBuffer.allocate(4).putInt(ciphertext.size).array())
        output.write(ciphertext)
        return output.toByteArray()
    }

    private fun decrypt(encryptedData: ByteArray, key: SecretKey): ByteArray {
        val buffer = ByteBuffer.wrap(encryptedData)
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val ciphertextLen = buffer.int
        val ciphertext = ByteArray(ciphertextLen)
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        return cipher.doFinal(ciphertext)
    }
}
