package me.weishu.kernelsu.jwsk.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The complete provider profile, including credentials, lives in Keystore-encrypted no-backup storage. */
interface AiProfileStore {
    suspend fun load(): AiSettings
    suspend fun save(settings: AiSettings)
}

class SecureAiStore(context: Context) : AiProfileStore {
    private val app = context.applicationContext
    private val directory get() = app.noBackupFilesDir.resolve("jwsk-ai")
    private val file get() = directory.resolve("profiles.aesgcm")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): AiSettings = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext AiSettings()
        require(file.length() <= MAX_FILE_BYTES) { "AI settings too large" }
        val encoded = Files.readAllBytes(file.toPath())
        require(encoded.size in (MAGIC.size + IV_BYTES + 16)..MAX_FILE_BYTES) { "Invalid AI settings file" }
        require(encoded.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Unknown AI settings format" }
        val iv = encoded.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val plain = cipher.doFinal(encoded, MAGIC.size + IV_BYTES, encoded.size - MAGIC.size - IV_BYTES)
        try {
            json.decodeFromString<AiSettings>(plain.toString(Charsets.UTF_8))
        } finally {
            plain.fill(0)
        }
    }

    override suspend fun save(settings: AiSettings): Unit = withContext(Dispatchers.IO) {
        require(settings.profiles.map { it.configuration.provider }.distinct().size == settings.profiles.size)
        val plain = json.encodeToString(settings).toByteArray(Charsets.UTF_8)
        try {
            require(plain.size <= MAX_FILE_BYTES - 64) { "AI settings too large" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(MAGIC)
            val sealed = cipher.doFinal(plain)
            val output = ByteBuffer.allocate(MAGIC.size + IV_BYTES + sealed.size)
                .put(MAGIC).put(cipher.iv).put(sealed).array()
            directory.mkdirs()
            val temp = Files.createTempFile(directory.toPath(), "profile-", ".tmp")
            try {
                Files.write(temp, output)
                try {
                    Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temp)
            }
        } finally {
            plain.fill(0)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    private companion object {
        const val ALIAS = "jwsk_ai_profiles_v1"
        const val IV_BYTES = 12
        const val MAX_FILE_BYTES = 1024 * 1024
        val MAGIC = "JWSKAI01".toByteArray(Charsets.US_ASCII)
    }
}
