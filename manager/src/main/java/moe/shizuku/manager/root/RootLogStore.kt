package moe.shizuku.manager.root

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RootLogStore {
    private const val MAX_BYTES = 1024 * 1024L
    private const val KEEP_BYTES = 768 * 1024
    private const val LOG_NAME = "jwsk-root.log"
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, "logs/$LOG_NAME")

    fun append(context: Context, category: String, message: String) {
        val cleanCategory = category.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24)
        val cleanMessage = message
            .replace('\u0000', ' ')
            .replace(Regex("(?i)(token|password|secret|authorization)\\s*[:=]\\s*\\S+"), "$1=<已隐藏>")
            .take(4096)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val stamp = JwskNativeCore.integrityStamp("$timestamp|$cleanCategory|$cleanMessage")
        synchronized(lock) {
            val target = file(context)
            target.parentFile?.mkdirs()
            if (target.length() > MAX_BYTES) {
                val tail = target.inputStream().use { input ->
                    val skip = (target.length() - KEEP_BYTES).coerceAtLeast(0)
                    input.skip(skip)
                    input.bufferedReader().readText().substringAfter('\n', "")
                }
                target.writeText("$timestamp [SYSTEM] 日志已轮转\n$tail", Charsets.UTF_8)
            }
            target.appendText("$timestamp [$cleanCategory] {$stamp} $cleanMessage\n", Charsets.UTF_8)
        }
    }

    fun read(context: Context): String = synchronized(lock) {
        file(context).takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty()
    }

    fun clear(context: Context) = synchronized(lock) {
        val target = file(context)
        if (target.exists()) target.writeText("", Charsets.UTF_8)
    }

    fun exportFile(context: Context): File = synchronized(lock) {
        val export = File(context.cacheDir, "exports/JWSK-root-log.txt")
        export.parentFile?.mkdirs()
        export.writeText(read(context), Charsets.UTF_8)
        export
    }
}
