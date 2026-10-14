package me.weishu.kernelsu.jwsk.alpha

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties

internal data class AlphaPayload(
    val binaries: Map<String, File>,
    val scripts: Map<String, ByteArray>,
    val stub: ByteArray,
    val magiskSha256: String,
)

/** Reads only APK-owned assets and read-only native libraries, never downloaded executables. */
internal class AlphaAssets(context: Context) {
    private val app = context.applicationContext
    suspend fun load(): AlphaPayload = withContext(Dispatchers.IO) {
        val receipt = Properties().apply {
            app.assets.open("alpha/backend.properties").use { load(it.bounded(65536).inputStream()) }
        }
        check(receipt.getProperty("ALPHA_SOURCE_COMMIT") == SOURCE)
        check(receipt.getProperty("ALPHA_VERSION_CODE") == "31000")
        check(receipt.getProperty("ALPHA_MANAGER_PROTOCOL") == "1")
        val abi = Build.SUPPORTED_ABIS.firstOrNull { receipt.containsKey("ALPHA_SHA256_${it}_magisk") }
            ?: error("Unsupported Alpha architecture")
        val nativeDirectory = File(app.applicationInfo.nativeLibraryDir).canonicalFile
        val binaries = listOf("magisk", "magiskinit", "magiskboot", "magiskpolicy", "init_ld").associateWith { name ->
            val file = File(nativeDirectory, "libalpha_$name.so").canonicalFile
            check(file.parentFile == nativeDirectory && file.isFile)
            check(file.length() in 1..(32L * 1024 * 1024))
            val expected = receipt.getProperty("ALPHA_SHA256_${abi}_$name")
            check(expected?.matches(Regex("[a-f0-9]{64}")) == true)
            check(runInterruptible { file.inputStream().use(::sha256) } == expected)
            file
        }
        val scripts = listOf("boot_patch.sh", "util_functions.sh").associateWith { name ->
            val bytes = app.assets.open("alpha/$name").use { it.bounded(256 * 1024) }
            check(sha256(bytes.inputStream()) == receipt.getProperty("ALPHA_ASSET_SHA256_$name"))
            bytes
        }
        val stub = app.assets.open("alpha/stub.apk").use { it.bounded(256 * 1024) }
        check(sha256(stub.inputStream()) == receipt.getProperty("ALPHA_ASSET_SHA256_stub.apk"))
        val certificateCopy = File.createTempFile("jwsk-alpha-anchor-", ".apk", app.cacheDir)
        try {
            certificateCopy.writeBytes(stub)
            val info = app.packageManager.getPackageArchiveInfo(certificateCopy.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: error("Invalid Alpha anchor APK")
            check(info.packageName == app.packageName)
            val signatures = info.signingInfo?.apkContentsSigners ?: error("Missing Alpha anchor certificate")
            check(signatures.size == 1 && sha256(signatures.single().toByteArray().inputStream()) == CERTIFICATE)
        } finally { certificateCopy.delete() }
        AlphaPayload(binaries, scripts, stub, receipt.getProperty("ALPHA_SHA256_${abi}_magisk"))
    }

    companion object {
        const val SOURCE = "96221b69fae9910b1c0c75c2f92a4ebb2c2dc698"
        const val CERTIFICATE = "1f934155f4ffc1aeefb44faa15bdbef277ca4472435ed45e09fa583b9d67a76f"
        internal fun sha256(input: InputStream): String = input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                val count = stream.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        private fun InputStream.bounded(limit: Int): ByteArray {
            val bytes = readNBytes(limit + 1)
            check(bytes.size <= limit) { "Oversized Alpha asset" }
            return bytes
        }
    }
}
