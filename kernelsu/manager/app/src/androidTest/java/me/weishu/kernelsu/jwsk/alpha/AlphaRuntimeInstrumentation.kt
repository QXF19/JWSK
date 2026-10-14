package me.weishu.kernelsu.jwsk.alpha

import android.app.Activity
import android.app.Instrumentation
import android.content.pm.ApplicationInfo
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import me.weishu.kernelsu.jwsk.artifacts.KernelArtifactInspector
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Explicit emulator-only runtime checks; not included in the distributable manager. */
class AlphaRuntimeInstrumentation : Instrumentation() {
    private var rootExpected = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        rootExpected = arguments?.getString("alphaRoot") == "true"
        start()
    }

    override fun onStart() {
        Thread({
            val result = Bundle()
            try {
                runBlocking { verifyRuntime(result) }
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("failure", failure.stackTraceToString().take(4096))
                finish(Activity.RESULT_CANCELED, result)
            }
        }, "jwsk-alpha-runtime-test").start()
    }

    private suspend fun verifyRuntime(result: Bundle) {
        val app = targetContext
        check(app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val runner = BoundedProcessRunner()
        check(runner.run(listOf("/system/bin/getprop", "ro.kernel.qemu"), null, emptyMap(), 3000)
            .output().trim() == "1") { "Runtime tests require an Android emulator" }
        val payload = AlphaAssets(app).load()
        result.putString("assets", "verified: ${payload.binaries.size} native files, scripts and original certificate")

        val input = File.createTempFile("jwsk-test-init-boot-", ".img", app.cacheDir)
        try {
            // A real, unmodified SDK ramdisk is copied into ignored androidTest assets locally.
            // This fixture is not shipped in the app and no SDK image is ever edited.
            val ramdisk = context.assets.open("avd-ramdisk.img").use { it.readBytes() }
            check(ramdisk.size in 1024..(32 * 1024 * 1024))
            val header = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            header.put("ANDROID!".toByteArray(Charsets.US_ASCII))
            header.putInt(8, 0)
            header.putInt(12, ramdisk.size)
            header.putInt(20, 1584)
            header.putInt(40, 4)
            input.outputStream().use { stream ->
                stream.write(header.array())
                stream.write(ramdisk)
                stream.write(ByteArray((4096 - ramdisk.size % 4096) % 4096))
            }
            val before = KernelArtifactInspector.inspectBootImage(input)
            val patcher = AlphaBootPatcher(app)
            val output = patcher.patch(input, AlphaPatchOptions())
            try {
                check(output.originalSha256 == before.sha256())
                check(KernelArtifactInspector.inspectBootImage(input).sha256() == before.sha256())
                check(output.inspection.sha256() != before.sha256())
                check(output.inspection.header().bootHeaderVersion() == 4)
                result.putString("offline_patch", "passed: v4 ramdisk-only image changed; original unchanged")
                result.putString("patch_log", output.log.take(8192))
            } finally { patcher.release(output) }
            check(app.cacheDir.listFiles().orEmpty().none { it.name.startsWith("jwsk-alpha-") })
            result.putString("cleanup", "passed: private patch workspace removed")
        } finally { input.delete() }

        val client = AlphaClient(app)
        val status = client.probe()
        if (rootExpected) {
            check(status.available && status.trustedManager) { "Original certificate manager identity was not accepted" }
            val root = client.root("id -u", 10000)
            check(root.exitCode() == 0 && root.output().trim() == "0") { "Verified manager Root command failed" }
            result.putString("alpha_root", "passed: certificate-backed manager identity and uid 0")
        } else {
            check(!status.available && !status.trustedManager) { "Offline test unexpectedly found a live Alpha daemon" }
            result.putString("offline_detection", "passed: no daemon started or Root request made")
        }
    }
}
