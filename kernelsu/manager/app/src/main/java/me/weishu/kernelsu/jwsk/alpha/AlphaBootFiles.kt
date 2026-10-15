package me.weishu.kernelsu.jwsk.alpha

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Populate the upstream daemon's device-protected first-boot import directory. */
internal object AlphaBootFiles {
    suspend fun prepare(context: Context, payload: AlphaPayload) = withContext(Dispatchers.IO) {
        val data = context.createDeviceProtectedStorageContext().dataDir.canonicalFile
        val install = File(data, "install")
        check(!Files.isSymbolicLink(install.toPath()))
        val staging = Files.createTempDirectory(data.toPath(), "jwsk-alpha-boot-").toFile()
        var previous: File? = null
        try {
            for ((name, binary) in payload.binaries) {
                val destination = File(staging, if(name == "init_ld") "init-ld" else name)
                binary.inputStream().use { source -> destination.outputStream().use { source.copyTo(it) } }
                check(runInterruptible { AlphaAssets.sha256(binary.inputStream()) } == runInterruptible { AlphaAssets.sha256(destination.inputStream()) })
                check(destination.setExecutable(true, false))
            }
            payload.scripts.forEach { (name, bytes) ->
                File(staging, name).writeText(bytes.toString(Charsets.UTF_8).replace("#MAGISK_VERSION_STUB",
                    "MAGISK_VER='96221b69-alpha-jwsk'\nMAGISK_VER_CODE=31000"))
            }
            File(staging, "stub.apk").writeBytes(payload.stub)
            File(staging, ".jwsk-alpha-runtime").writeText(AlphaAssets.SOURCE)
            if (install.exists()) {
                check(install.isDirectory && File(install, ".jwsk-alpha-runtime").isFile) { "Unrecognized pending installation" }
                val backup = File(data, "jwsk-alpha-previous-${java.util.UUID.randomUUID()}")
                Files.move(install.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
                previous = backup
            }
            try { Files.move(staging.toPath(), install.toPath(), StandardCopyOption.ATOMIC_MOVE) }
            catch (failure: Throwable) {
                previous?.let { Files.move(it.toPath(), install.toPath(), StandardCopyOption.ATOMIC_MOVE); previous = null }
                throw failure
            }
        } finally {
            // Exact application-owned staging directories, never following executable symlinks.
            for (directory in listOfNotNull(staging, previous)) {
                check(directory.parentFile?.canonicalFile == data && directory.name.startsWith("jwsk-alpha-"))
                if (directory.isDirectory) Files.walkFileTree(directory.toPath(), object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                    override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                        Files.delete(file); return java.nio.file.FileVisitResult.CONTINUE
                    }
                    override fun postVisitDirectory(dir: java.nio.file.Path, failure: java.io.IOException?): java.nio.file.FileVisitResult {
                        if (failure != null) throw failure
                        Files.delete(dir); return java.nio.file.FileVisitResult.CONTINUE
                    }
                })
            }
        }
    }
}
