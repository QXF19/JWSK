package me.weishu.kernelsu.jwsk.alpha

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.jwsk.artifacts.KernelArtifactInspector
import java.io.File
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal data class AlphaPatchOptions(
    val keepVerity: Boolean = true,
    val keepEncryption: Boolean = true,
    val patchVbmeta: Boolean = false,
    val recovery: Boolean = false,
    val legacySar: Boolean = false,
)
internal data class AlphaPatchOutput(
    val image: File,
    val inspection: KernelArtifactInspector.Inspection,
    val originalSha256: String,
    val log: String,
)
internal class AlphaPatchFailure(val diagnosticLog: String) : Exception("Alpha image patch failed")

/** Offline, app-UID patching of a private input copy. No partition or Root access. */
internal class AlphaBootPatcher(context: Context) {
    private val app = context.applicationContext
    private val assets = AlphaAssets(app)
    private val cache = app.cacheDir.canonicalFile

    suspend fun patch(input: File, options: AlphaPatchOptions): AlphaPatchOutput {
        var leasedWork: File? = null
        try {
            return withContext(Dispatchers.IO) {
        val original = runInterruptible { KernelArtifactInspector.inspectBootImage(input) }
        val payload = assets.load()
        check(cache.usableSpace > original.bytes() * 6 + 128L * 1024 * 1024) { "Insufficient temporary storage" }
        val work = Files.createTempDirectory(cache.toPath(), "jwsk-alpha-").toFile()
        leasedWork = work
        var accepted = false
        try {
            val coroutine = currentCoroutineContext()
            val privateInput = File(work, "input.img")
            input.inputStream().use { source -> privateInput.outputStream().use { destination ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    coroutine.ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= KernelArtifactInspector.MAX_BYTES)
                    destination.write(buffer, 0, count)
                }
            } }
            check(runInterruptible { AlphaAssets.sha256(privateInput.inputStream()) } == original.sha256())
            payload.scripts.forEach { (name, bytes) -> File(work, name).writeBytes(bytes) }
            File(work, "stub.apk").writeBytes(payload.stub)
            mapOf("magisk" to "magisk", "magiskinit" to "magiskinit", "magiskboot" to "magiskboot", "init-ld" to "init_ld")
                .forEach { (name, binary) -> Os.symlink(payload.binaries.getValue(binary).absolutePath, File(work, name).path) }
            File(work, "tmp").mkdir()
            File(work, "jwsk-patch.sh").writeText(WRAPPER, Charsets.UTF_8)
            val environment = mapOf(
                "KEEPVERITY" to options.keepVerity.toString(),
                "KEEPFORCEENCRYPT" to options.keepEncryption.toString(),
                "PATCHVBMETAFLAG" to options.patchVbmeta.toString(),
                "RECOVERYMODE" to options.recovery.toString(),
                "LEGACYSAR" to options.legacySar.toString(),
                "PATH" to "/system/bin:/system/xbin",
            )
            val result = runInterruptible {
                BoundedProcessRunner(AlphaProcessSession(work)).run(listOf("/system/bin/toybox", "setsid",
                    "/system/bin/sh", "jwsk-patch.sh"), work, environment, 180000)
            }
            if (result.exitCode() != 0 || result.timedOut()) throw AlphaPatchFailure(result.output())
            val image = File(work, "new-boot.img").canonicalFile
            check(image.parentFile == work.canonicalFile && image.isFile)
            val inspection = runInterruptible { KernelArtifactInspector.inspectBootImage(image) }
            check(runInterruptible { AlphaAssets.sha256(input.inputStream()) } == original.sha256())
            AlphaBootFiles.prepare(app, payload)
            accepted = true
            AlphaPatchOutput(image, inspection, original.sha256(), result.output())
        } finally {
            if (!accepted) withContext(NonCancellable) { releaseWork(work) }
        }
            }
        } catch (failure: Throwable) {
            // Also cover prompt cancellation while dispatching a successful result back to UI.
            withContext(NonCancellable + Dispatchers.IO) { runCatching { leasedWork?.let(::releaseWork) } }
            throw failure
        }
    }

    suspend fun release(output: AlphaPatchOutput) = withContext(Dispatchers.IO) {
        releaseWork(output.image.parentFile ?: return@withContext)
    }

    private fun releaseWork(work: File) {
        check(work.parentFile?.canonicalFile == cache && work.name.startsWith("jwsk-alpha-"))
        if (!work.exists()) return
        // Never follow executable symlinks into nativeLibraryDir or delete the input URI.
        Files.walkFileTree(work.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(directory: Path, failure: java.io.IOException?): FileVisitResult {
                if (failure != null) throw failure
                Files.deleteIfExists(directory)
                return FileVisitResult.CONTINUE
            }
        })
    }

    companion object {
        // Source the unchanged upstream patcher, overriding only recovery/device-side helpers.
        // The preinit-device CLI reads mount metadata locally; it does not start a daemon.
        private val WRAPPER = """
            #!/system/bin/sh
            printf '%s\n' "${'$'}${'$'}" > process.pid
            while [ ! -f run.start ]; do sleep 0.05; done
            export BOOTMODE=true
            . ./util_functions.sh
            ui_print() { printf '%s\n' "${'$'}1"; }
            abort() { ui_print "${'$'}1"; exit 1; }
            sign_chromeos() { abort 'ChromeOS images are not supported by this offline flow'; }
            export TMPDIR="${'$'}PWD/tmp"
            export SOURCEDMODE=true
            export BOOTMODE=false
            unset MODPATH BOOTNAND
            PREINITDEVICE=${'$'}(./magisk --preinit-device 2>/dev/null || true)
            case "${'$'}PREINITDEVICE" in ''|*[!A-Za-z0-9._-]*) unset PREINITDEVICE;; esac
            ulimit -f 1048576 || exit 1
            . ./boot_patch.sh ./input.img
            [ -s new-boot.img ] || exit 1
            ./magiskboot cpio "${'$'}RAMDISK" test
            [ "${'$'}?" -eq 1 ] || exit 1
            exit 0
        """.trimIndent() + "\n"
    }
}
