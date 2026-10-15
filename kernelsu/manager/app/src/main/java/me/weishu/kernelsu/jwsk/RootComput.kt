package me.weishu.kernelsu.jwsk

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ksuApp
import java.io.File

data class ComputResult(val code: Int, val output: String)

/** Manual terminal only. No dependency from the AI package and no external-manager fallback. */
object RootComput {
    suspend fun execute(command: String): ComputResult = withContext(Dispatchers.IO) {
        require(command.isNotBlank() && command.length <= 8192 && '\u0000' !in command)
        if (!Natives.isManager) {
            val result = me.weishu.kernelsu.jwsk.alpha.AlphaClient(ksuApp).root(command)
            return@withContext ComputResult(result.exitCode(), result.output())
        }
        check(Natives.kernelUAPIVersion == Natives.managerUAPIVersion)
        val daemon = File(ksuApp.applicationInfo.nativeLibraryDir, "libksud.so")
        Shell.Builder.create().setTimeout(10).build(daemon.absolutePath, "debug", "su").use { shell ->
            check(shell.isRoot)
            val out = BoundedOutput()
            val err = BoundedOutput()
            // Preserve Comput's child-shell isolation and 120-second runtime limit.
            val result = shell.newJob().add("/system/bin/toybox timeout -s KILL 120 /system/bin/sh -c ${quote(command)}")
                .to(out, err).exec()
            ComputResult(result.code, (out + err).joinToString("\n"))
        }
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private class BoundedOutput : ArrayList<String>() {
        private var remaining = 64 * 1024
        override fun add(element: String): Boolean {
            if (remaining <= 0 || size >= 2000) return true
            val line = element.take(remaining)
            remaining -= line.length + 1
            return super.add(line)
        }
    }
}
