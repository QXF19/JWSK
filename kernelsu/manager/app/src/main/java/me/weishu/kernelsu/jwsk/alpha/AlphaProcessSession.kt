package me.weishu.kernelsu.jwsk.alpha

import android.system.Os
import android.system.OsConstants
import java.io.File

/** Private shell handshake avoids unsupported Process.pid() on Android 14-16. */
internal class AlphaProcessSession(private val work: File) : BoundedProcessRunner.Lifecycle {
    private var group: Int? = null
    override fun started(process: Process) {
        val identity = File(work, "process.pid")
        repeat(100) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (!process.isAlive) error("Alpha patch process exited before its handshake")
            if (identity.isFile && identity.length() > 0) {
                check(identity.length() in 1..16)
                val pid = identity.readText().trim().toInt()
                check(pid > 1)
                check(Os.stat("/proc/$pid").st_uid == android.os.Process.myUid())
                val stat = File("/proc/$pid/stat").readText()
                check(stat.length <= 4096)
                val fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ', limit = 5)
                check(fields.size == 5 && fields[2].toInt() == pid && fields[3].toInt() == pid)
                group = pid
                check(File(work, "run.start").createNewFile())
                return
            }
            Thread.sleep(10)
        }
        error("Alpha patch process handshake timed out")
    }
    override fun terminating(process: Process) {
        if (process.isAlive) group?.let { pid ->
            // A negative PID addresses only this newly created session's process group.
            runCatching { Os.kill(-pid, OsConstants.SIGKILL) }
        }
    }
}
