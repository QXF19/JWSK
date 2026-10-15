package me.weishu.kernelsu.jwsk

import android.app.Activity
import android.app.Instrumentation
import android.content.pm.ApplicationInfo
import android.os.Bundle
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ui.util.getRootShell
import me.weishu.kernelsu.ui.util.listModules
import me.weishu.kernelsu.ui.util.toggleModule
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Explicit dedicated-emulator checks only; excluded from the distributable app. */
class KsuRuntimeInstrumentation : Instrumentation() {
    private var mode = "verify"
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        mode = arguments?.getString("mode") ?: "verify"
        start()
    }
    override fun onStart() {
        Thread({
            val result = Bundle()
            try {
                val app = targetContext
                check(app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
                val emulator = ProcessBuilder("/system/bin/getprop", "ro.kernel.qemu").start()
                check(emulator.inputStream.bufferedReader().readText().trim() == "1" && emulator.waitFor() == 0)
                check(Natives.isManager) { "Original signing identity not recognized by the kernel" }
                check(Natives.version == 32680 && Natives.kernelUAPIVersion == Natives.managerUAPIVersion)
                check(Natives.isLkmMode && Natives.isFullFeatured())
                val shell = getRootShell()
                val root = shell.newJob().add("id -u; id -Z; getenforce").exec()
                check(root.isSuccess && root.out.firstOrNull() == "0")
                check(root.out.any { it == "Enforcing" })
                result.putString("identity", "passed: kernel certificate-backed manager, uid 0, matching UAPI, SELinux Enforcing")
                val uid = app.packageManager.getApplicationInfo(context.packageName, 0).uid
                val previous = Natives.getAppProfile(context.packageName, uid)
                if (mode == "grant" || mode == "revoke") {
                    check(Natives.setAppProfile(previous.copy(allowSu = mode == "grant")))
                    check(Natives.getAppProfile(context.packageName, uid).allowSu == (mode == "grant"))
                    result.putString("profile", "$mode persisted for isolated test UID $uid")
                } else {
                    try {
                        check(Natives.setAppProfile(previous.copy(allowSu = true)))
                        check(Natives.getAppProfile(context.packageName, uid).allowSu)
                        check(Natives.setAppProfile(previous.copy(allowSu = false)))
                        check(!Natives.getAppProfile(context.packageName, uid).allowSu)
                    } finally { check(Natives.setAppProfile(previous)) }
                    result.putString("profile", "passed: native grant/revoke/readback, original profile restored")
                    val su = Natives.isSuEnabled()
                    val umount = Natives.isKernelUmountEnabled()
                    try {
                        check(Natives.setSuEnabled(!su)); check(Natives.isSuEnabled() == !su)
                        check(Natives.setKernelUmountEnabled(!umount)); check(Natives.isKernelUmountEnabled() == !umount)
                    } finally {
                        check(Natives.setSuEnabled(su)); check(Natives.setKernelUmountEnabled(umount))
                    }
                    result.putString("settings", "passed: native su/umount feature roundtrip, originals restored")
                    val id = "jwsk_validation_inert"
                    val fixture = File.createTempFile("jwsk-ksu-inert-", ".zip", app.cacheDir)
                    fun command(value: String) = shell.newJob().add(value).exec().also { check(it.isSuccess) { it.err.joinToString("\n") } }
                    check(shell.newJob().add("test ! -e /data/adb/modules/$id && test ! -e /data/adb/modules_update/$id").exec().isSuccess)
                    try {
                        ZipOutputStream(fixture.outputStream()).use { zip ->
                            zip.putNextEntry(ZipEntry("module.prop"))
                            zip.write("id=$id\nname=JWSK inert validation\nversion=1\nversionCode=1\nauthor=JWSK\ndescription=No scripts or mounts\n".toByteArray())
                            zip.closeEntry()
                        }
                        val ksud = app.applicationInfo.nativeLibraryDir + "/libksud.so"
                        command("$ksud module install '${fixture.absolutePath}'")
                        check((0 until JSONArray(listModules()).length()).any { JSONArray(listModules()).getJSONObject(it).optString("id") == id })
                        check(toggleModule(id, false)); command("test -e /data/adb/modules/$id/disable")
                        check(toggleModule(id, true)); command("test ! -e /data/adb/modules/$id/disable")
                        result.putString("modules", "passed: actual ksud install/list/disable/enable, inert fixture only")
                    } finally {
                        fixture.delete()
                        command("rm -rf /data/adb/modules/jwsk_validation_inert /data/adb/modules_update/jwsk_validation_inert")
                    }
                }
                finish(Activity.RESULT_OK, result)
            } catch (failure: Throwable) {
                result.putString("failure", failure.stackTraceToString().take(4096))
                finish(Activity.RESULT_CANCELED, result)
            }
        }, "jwsk-ksu-runtime-test").start()
    }
}
