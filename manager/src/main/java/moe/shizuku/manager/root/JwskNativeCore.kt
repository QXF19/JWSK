package moe.shizuku.manager.root

import java.io.File

object JwskNativeCore {
    private val loaded = runCatching {
        System.loadLibrary("jwsk_core")
        check(nativeApiVersion() == 120)
        listOf(0L, 1L, -1L, Long.MIN_VALUE).forEach { check(nativeMix64(it) == mix64(it)) }
        true
    }.getOrDefault(false)

    private external fun nativeMix64(input: Long): Long
    private external fun nativeApiVersion(): Int

    val backendLabel: String get() = if (loaded) "Rust JNI 1.2.0" else "Kotlin 回退"

    fun sha256(file: File): String = FileDigest.sha256(file)

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
