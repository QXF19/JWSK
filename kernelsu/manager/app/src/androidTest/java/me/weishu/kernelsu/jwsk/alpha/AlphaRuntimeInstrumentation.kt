package me.weishu.kernelsu.jwsk.alpha

import android.app.Activity
import android.app.Instrumentation
import android.content.pm.ApplicationInfo
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import me.weishu.kernelsu.jwsk.artifacts.KernelArtifactInspector
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Explicit emulator-only runtime checks; not included in the distributable manager. */
class AlphaRuntimeInstrumentation : Instrumentation() {
    private var rootExpected = false
    private var coldBootFixture = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        rootExpected = arguments?.getString("alphaRoot") == "true"
        coldBootFixture = arguments?.getString("coldBootFixture") == "true"
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
                if (coldBootFixture) {
                    output.image.copyTo(File(app.filesDir, "jwsk-test-patched-init_boot.img"), overwrite = true)
                    result.putString("cold_boot_fixture", "app-private fixture prepared; device-protected runtime staged")
                }
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
            val policies = AlphaPolicyStore(app)
            val testUid = app.packageManager.getApplicationInfo(context.packageName, 0).uid
            val previous = policies.policies().firstOrNull { it.uid == testUid }
            try {
                policies.save(AlphaPolicy(testUid, 2, 0, true, true))
                check(policies.policies().any { it.uid == testUid && it.policy == 2 })
                policies.save(AlphaPolicy(testUid, 1, 0, false, false))
                check(policies.policies().any { it.uid == testUid && it.policy == 1 && !it.logging })
            } finally { if (previous != null) policies.save(previous) else policies.remove(testUid) }
            result.putString("policy_store", "passed: persistent grant, revoke and original state restored")

            val settingsModel = withContext(Dispatchers.Main) { AlphaManagement(app.applicationContext as android.app.Application) }
            suspend fun waitForSettings() = withTimeout(30000) { settingsModel.state.first { !it.loading } }
                .also { check(!it.failed) { it.text } }
            withContext(Dispatchers.Main) { settingsModel.load(AlphaSection.SETTINGS) }
            val originalSettings = waitForSettings()
            val originalZygisk = originalSettings.settings["zygisk"] ?: 0
            val ownTarget = context.packageName to context.packageName
            var addedTarget = false
            try {
                withContext(Dispatchers.Main) { settingsModel.setting("zygisk", 1 - originalZygisk) }
                check(waitForSettings().settings["zygisk"] == 1 - originalZygisk)
                if (ownTarget !in originalSettings.denyTargets) {
                    addedTarget = true
                    withContext(Dispatchers.Main) { settingsModel.denyTarget(ownTarget.first, ownTarget.second, true) }
                    check(ownTarget in waitForSettings().denyTargets)
                }
            } finally {
                withContext(Dispatchers.Main) { settingsModel.setting("zygisk", originalZygisk) }
                check(waitForSettings().settings["zygisk"] == originalZygisk)
                if (addedTarget) {
                    withContext(Dispatchers.Main) { settingsModel.denyTarget(ownTarget.first, ownTarget.second, false) }
                    check(ownTarget !in waitForSettings().denyTargets)
                }
            }
            result.putString("alpha_settings", "passed: settings persistence and own DenyList target; original state restored")

            withContext(Dispatchers.Main) { settingsModel.load(AlphaSection.POLICIES) }
            val appList = waitForSettings().apps
            check(appList.any { it.uid == testUid && context.packageName in it.packages && it.label.isNotBlank() && it.applicationInfo != null })
            result.putString("app_identity", "passed: actual PackageManager label, package, icon metadata and UID mapping")

            val fixture = File.createTempFile("jwsk-test-module-", ".zip", app.cacheDir)
            val model = withContext(Dispatchers.Main) { AlphaManagement(app.applicationContext as android.app.Application) }
            val moduleId = "jwsk_validation_inert"
            check(client.root("[ ! -e /data/adb/modules/$moduleId ] && [ ! -e /data/adb/modules_update/$moduleId ]", 10000).exitCode() == 0)
            try {
                java.util.zip.ZipOutputStream(fixture.outputStream()).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry("module.prop"))
                    zip.write("id=$moduleId\nname=JWSK inert validation module\nversion=1.0\nversionCode=1\nauthor=JWSK\ndescription=Dedicated emulator test fixture\n".toByteArray())
                    zip.closeEntry()
                }
                withContext(Dispatchers.Main) { model.load(AlphaSection.MODULES) }
                withTimeout(30000) { model.state.first { !it.loading } }.also { check(!it.failed) }
                withContext(Dispatchers.Main) { model.install(android.net.Uri.fromFile(fixture)) }
                withTimeout(125000) { model.state.first { !it.loading } }.also { check(!it.failed) { it.text } }
                check(model.state.value.modules.any { it.id == moduleId })
                withContext(Dispatchers.Main) { model.module(moduleId, "disable", true) }
                withTimeout(30000) { model.state.first { !it.loading } }.also { check(!it.failed && it.modules.any { m -> m.id == moduleId && !m.enabled }) }
                withContext(Dispatchers.Main) { model.module(moduleId, "disable", false) }
                withTimeout(30000) { model.state.first { !it.loading } }.also { check(!it.failed && it.modules.any { m -> m.id == moduleId && m.enabled }) }
                result.putString("module_lifecycle", "passed: install inert ZIP, list, disable and enable through manager repository")
            } finally {
                fixture.delete()
                // Dedicated emulator only, exact directories created by this fixture; refuse symlinks or foreign metadata.
                client.root("for d in /data/adb/modules/$moduleId /data/adb/modules_update/$moduleId; do " +
                    "[ -e \"\$d\" ] || continue; [ ! -L \"\$d\" ] && grep -q '^id=$moduleId\$' \"\$d/module.prop\" && rm -rf -- \"\$d\" || exit 1; done", 10000)
            }
        } else {
            check(!status.available && !status.trustedManager) { "Offline test unexpectedly found a live Alpha daemon" }
            result.putString("offline_detection", "passed: no daemon started or Root request made")
        }
    }
}
