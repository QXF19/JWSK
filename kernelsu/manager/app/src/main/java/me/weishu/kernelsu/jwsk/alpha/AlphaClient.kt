package me.weishu.kernelsu.jwsk.alpha

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

internal data class AlphaStatus(val available: Boolean = false, val trustedManager: Boolean = false, val version: String? = null)

/** Uses the running daemon's own inode and its read-only, certificate-backed identity query. */
internal class AlphaClient(context: Context) {
    private val assets = AlphaAssets(context)
    private val runner = BoundedProcessRunner()

    suspend fun probe(): AlphaStatus = try { probeVerified() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { AlphaStatus() }

    private suspend fun probeVerified(): AlphaStatus = withContext(Dispatchers.IO) {
        val payload = try { assets.load() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { return@withContext AlphaStatus() }
        val active = findActive(payload) ?: return@withContext AlphaStatus()
        val version = runInterruptible { runner.run(listOf(active.path, "-v"), null, emptyMap(), 3000) }
        if (version.exitCode() != 0 || !version.output().trim().startsWith("96221b69-alpha-jwsk:")) {
            return@withContext AlphaStatus()
        }
        val identity = runInterruptible { runner.run(listOf(active.path, "--jwsk-manager"), null, emptyMap(), 5000) }
        AlphaStatus(true, identity.exitCode() == 0 && identity.output().trim() == "1", version.output().trim())
    }

    /** Internal management/manual terminal only; there is no AI reference or caller. */
    suspend fun root(command: String, timeoutMillis: Long = 120000): BoundedProcessRunner.Result = withContext(Dispatchers.IO) {
        require(command.isNotBlank() && command.length <= 32768 && '\u0000' !in command)
        val payload = assets.load()
        val active = findActive(payload) ?: error("Matching Alpha daemon is unavailable")
        val identity = runInterruptible { runner.run(listOf(active.path, "--jwsk-manager"), null, emptyMap(), 5000) }
        check(identity.exitCode() == 0 && identity.output().trim() == "1") { "JWSK is not the verified Alpha manager" }
        val seconds = ((timeoutMillis + 999) / 1000).coerceIn(1, 120)
        val bounded = "exec /system/bin/toybox timeout -s KILL $seconds /system/bin/sh -c ${quote(command)}"
        runInterruptible { runner.run(listOf(active.path, "su", "-c", bounded), null, emptyMap(), timeoutMillis.coerceIn(1, 120000)) }
    }

    private suspend fun findActive(payload: AlphaPayload): File? = runInterruptible {
        // No PATH search and no arbitrary external manager invocation.
        listOf("/debug_ramdisk/magisk", "/sbin/magisk").map(::File).firstOrNull { file ->
            file.isFile && file.canExecute() && file.length() <= 32L * 1024 * 1024 &&
                runCatching { AlphaAssets.sha256(file.inputStream()) == payload.magiskSha256 }.getOrDefault(false)
        }
    }

    companion object {
        internal fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    }
}
