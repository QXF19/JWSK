package moe.shizuku.manager.root

import java.io.File
import java.security.MessageDigest

object JwskNativeCore {
    private val loaded = runCatching {
        System.loadLibrary("jwsk_core")
        true
    }.getOrDefault(false)

    private external fun nativeMix64(input: Long): Long

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun moduleProperty(content: String, key: String): String? {
        return content.lineSequence()
            .map { it.trim() }
            .firstOrNull { !it.startsWith('#') && it.substringBefore('=', "") == key }
            ?.substringAfter('=', "")
            ?.trim()
            ?.take(1024)
    }

    fun integrityStamp(content: String): String {
        var folded = 0x6a09e667f3bcc909L
        content.forEach { folded = (folded xor it.code.toLong()) * 0x100000001b3L }
        val mixed = if (loaded) {
            runCatching { nativeMix64(folded) }.getOrElse { mix64(folded) }
        } else {
            mix64(folded)
        }
        return mixed.toULong().toString(16).padStart(16, '0').takeLast(16)
    }

    private fun mix64(input: Long): Long {
        var value = input.toULong()
        value = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9UL
        value = (value xor (value shr 27)) * 0x94d049bb133111ebUL
        return (value xor (value shr 31)).toLong()
    }
}
