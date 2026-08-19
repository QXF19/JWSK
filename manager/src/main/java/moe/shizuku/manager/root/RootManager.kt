package moe.shizuku.manager.root

import android.content.Context
import android.net.Uri
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object RootManager {
    private val moduleId = Regex("^[A-Za-z][A-Za-z0-9._-]{0,63}$")

    suspend fun detect(context: Context, adbActive: Boolean): RootEnvironment = withContext(Dispatchers.IO) {
        val bundledKsud = quote(File(context.applicationInfo.nativeLibraryDir, "libksud.so").absolutePath)
        val probe = run("""
            echo JWSK_UID=$(id -u 2>/dev/null)
            if command -v magisk >/dev/null 2>&1; then echo JWSK_MAGISK=$(magisk -v 2>/dev/null | head -n 1); fi
            if command -v ksud >/dev/null 2>&1; then
              echo JWSK_KSU=$(ksud debug version 2>/dev/null | head -n 1)
            elif [ -x $bundledKsud ]; then
              echo JWSK_KSU=$($bundledKsud debug version 2>/dev/null | head -n 1)
            fi
            if [ -d /data/adb/ksu ]; then echo JWSK_KSU_PRESENT=1; fi
            if [ -f /sys/module/kernelsu/parameters/version ]; then echo JWSK_KSU_KERNEL=$(cat /sys/module/kernelsu/parameters/version 2>/dev/null); fi
            if [ -d /data/adb/ksu ]; then
              if [ -f /data/adb/ksu/.lkm ]; then echo JWSK_KSU_MODE=LKM; else echo JWSK_KSU_MODE=GKI; fi
            fi
        """.trimIndent(), category = "DETECT", logOutput = false)
        val lines = probe.stdout + probe.stderr
        val uid = value(lines, "JWSK_UID")
        val magisk = value(lines, "JWSK_MAGISK")?.takeIf { it.isNotBlank() }
        val ksu = value(lines, "JWSK_KSU")?.takeIf { it.isNotBlank() }
        val ksuPresent = value(lines, "JWSK_KSU_PRESENT") == "1" || value(lines, "JWSK_KSU_KERNEL") != null
        val root = uid == "0"
        val hasMagisk = root && magisk != null
        val hasKsu = root && (ksu != null || ksuPresent)
        val backend = when {
            hasMagisk && hasKsu -> RootBackend.HYBRID
            hasKsu -> RootBackend.KERNEL_SU
            hasMagisk -> RootBackend.MAGISK
            adbActive -> RootBackend.ADB
            else -> RootBackend.NONE
        }
        val warning = when (backend) {
            RootBackend.HYBRID -> "检测到两个 Root 框架。为防止模块与授权数据库冲突，JWSK 不会自动修改它们。"
            RootBackend.KERNEL_SU -> "KernelSU 的应用授权界面仅对内核认可的管理器开放；JWSK 可稳定管理补丁、模块、日志与命令。"
            else -> null
        }
        RootEnvironment(
            backend = backend,
            rootGranted = root,
            adbActive = adbActive,
            magiskVersion = magisk,
            kernelSuVersion = ksu ?: value(lines, "JWSK_KSU_KERNEL"),
            kernelMode = value(lines, "JWSK_KSU_MODE"),
            warning = warning
        ).also {
            RootLogStore.append(context, "DETECT", "backend=${it.backend}, root=${it.rootGranted}, adb=${it.adbActive}")
        }
    }

    suspend fun listModules(context: Context, environment: RootEnvironment): List<RootModule> = withContext(Dispatchers.IO) {
        if (!environment.canManageRootModules) return@withContext emptyList()
        val result = run("""
            for d in /data/adb/modules/*; do
              [ -d "${'$'}d" ] || continue
              id=${'$'}(basename "${'$'}d")
              echo "@@JWSK_MODULE:${'$'}id"
              [ -f "${'$'}d/module.prop" ] && cat "${'$'}d/module.prop"
              [ -f "${'$'}d/disable" ] && echo "jwsk_enabled=false" || echo "jwsk_enabled=true"
              [ -f "${'$'}d/remove" ] && echo "jwsk_remove=true" || echo "jwsk_remove=false"
              [ -f "${'$'}d/action.sh" ] && echo "jwsk_action=true" || echo "jwsk_action=false"
              echo "@@JWSK_END"
            done
        """.trimIndent(), "MODULE_LIST", logOutput = false)
        if (result.code != 0) {
            RootLogStore.append(context, "MODULE_LIST", "failed code=${result.code}")
            return@withContext emptyList()
        }
        parseModules(result.stdout, environment.backend)
    }

    suspend fun installModule(context: Context, uri: Uri, environment: RootEnvironment): RootCommandResult = withContext(Dispatchers.IO) {
        require(environment.canManageRootModules) { "当前没有可用的 Root 模块执行器" }
        val stagingDir = File(context.cacheDir, "root_modules").apply { mkdirs() }
        val zip = File(stagingDir, "module-${System.currentTimeMillis()}.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            zip.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取模块 ZIP")
        require(zip.length() in 1..(256L * 1024 * 1024)) { "模块 ZIP 大小无效" }
        val digest = JwskNativeCore.sha256(zip)
        RootLogStore.append(context, "MODULE_INSTALL", "backend=${environment.backend}, size=${zip.length()}, sha256=$digest")
        val command = when (environment.backend) {
            RootBackend.MAGISK -> "magisk --install-module ${quote(zip.absolutePath)}"
            RootBackend.KERNEL_SU -> "${ksud(context)} module install ${quote(zip.absolutePath)}"
            else -> error("混合框架下禁止自动安装，请先停用其中一个 Root 框架")
        }
        run(command, "MODULE_INSTALL")
            .also { RootLogStore.append(context, "MODULE_INSTALL", "code=${it.code}, elapsed=${it.elapsedMs}ms") }
            .also { zip.delete() }
    }

    suspend fun listMagiskPolicies(context: Context): List<RootPolicy> = withContext(Dispatchers.IO) {
        val result = run(
            "magisk --sqlite 'SELECT uid,policy,until,logging,notification FROM policies'",
            "POLICY_LIST",
            logOutput = false
        )
        if (result.code != 0) return@withContext emptyList()
        result.stdout.mapNotNull { line ->
            val values = line.split('|')
                .mapNotNull { field -> field.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { it[0] to it[1] }
            val uid = values["uid"]?.toIntOrNull() ?: return@mapNotNull null
            val packageName = context.packageManager.getPackagesForUid(uid)?.firstOrNull().orEmpty()
            val appName = runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(packageName.ifBlank { "UID $uid" })
            RootPolicy(
                uid = uid,
                packageName = packageName,
                appName = appName,
                policy = values["policy"]?.toIntOrNull() ?: 0,
                logging = values["logging"] != "0",
                notification = values["notification"] != "0",
                until = values["until"]?.toLongOrNull() ?: -1L
            )
        }.sortedBy { it.appName.lowercase() }
    }

    suspend fun setMagiskPolicy(context: Context, policy: RootPolicy, allow: Boolean): RootCommandResult {
        require(policy.uid >= 0) { "UID 无效" }
        val value = if (allow) 2 else 1
        val command = "magisk --sqlite 'REPLACE INTO policies (uid,policy,until,logging,notification) VALUES(${policy.uid},$value,-1,1,1)'"
        return run(command, "POLICY_UPDATE").also {
            RootLogStore.append(context, "POLICY_UPDATE", "uid=${policy.uid}, allow=$allow, code=${it.code}")
        }
    }

    suspend fun deleteMagiskPolicy(context: Context, uid: Int): RootCommandResult {
        require(uid >= 0) { "UID 无效" }
        return run("magisk --sqlite 'DELETE FROM policies WHERE uid=$uid'", "POLICY_DELETE").also {
            RootLogStore.append(context, "POLICY_DELETE", "uid=$uid, code=${it.code}")
        }
    }

    suspend fun setEnabled(context: Context, module: RootModule, enabled: Boolean): RootCommandResult {
        validate(module.id)
        val command = if (module.backend == RootBackend.KERNEL_SU) {
            "${ksud(context)} module ${if (enabled) "enable" else "disable"} ${quote(module.id)}"
        } else {
            val path = "/data/adb/modules/${module.id}/disable"
            if (enabled) "rm -f ${quote(path)}" else "touch ${quote(path)}"
        }
        return run(command, "MODULE_TOGGLE").also {
            RootLogStore.append(context, "MODULE_TOGGLE", "id=${module.id}, enabled=$enabled, code=${it.code}")
        }
    }

    suspend fun uninstall(context: Context, module: RootModule): RootCommandResult {
        validate(module.id)
        val command = if (module.backend == RootBackend.KERNEL_SU) {
            "${ksud(context)} module uninstall ${quote(module.id)}"
        } else {
            "touch ${quote("/data/adb/modules/${module.id}/remove")}"
        }
        return run(command, "MODULE_REMOVE").also {
            RootLogStore.append(context, "MODULE_REMOVE", "id=${module.id}, code=${it.code}")
        }
    }

    suspend fun runAction(context: Context, module: RootModule): RootCommandResult {
        validate(module.id)
        require(module.hasAction) { "模块没有 action.sh" }
        val command = if (module.backend == RootBackend.KERNEL_SU) {
            "${ksud(context)} module action ${quote(module.id)}"
        } else {
            "cd ${quote("/data/adb/modules/${module.id}")} && sh ./action.sh"
        }
        return run(command, "MODULE_ACTION").also {
            RootLogStore.append(context, "MODULE_ACTION", "id=${module.id}, code=${it.code}, elapsed=${it.elapsedMs}ms")
        }
    }

    suspend fun run(command: String, category: String, logOutput: Boolean = true): RootCommandResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val result = Shell.cmd(command).exec()
        RootCommandResult(
            code = result.code,
            stdout = result.out.take(2000),
            stderr = result.err.take(1000),
            elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        )
    }

    private fun parseModules(lines: List<String>, backend: RootBackend): List<RootModule> {
        val result = mutableListOf<RootModule>()
        var id: String? = null
        val props = linkedMapOf<String, String>()
        fun flush() {
            val safeId = id?.takeIf { moduleId.matches(it) } ?: return
            result += RootModule(
                id = safeId,
                name = props["name"].orEmpty().ifBlank { safeId },
                version = props["version"].orEmpty().ifBlank { "未知" },
                versionCode = props["versionCode"]?.toLongOrNull() ?: 0L,
                author = props["author"].orEmpty().ifBlank { "未知" },
                description = props["description"].orEmpty(),
                enabled = props["jwsk_enabled"] != "false",
                pendingRemoval = props["jwsk_remove"] == "true",
                hasAction = props["jwsk_action"] == "true",
                backend = backend
            )
        }
        for (line in lines) {
            when {
                line.startsWith("@@JWSK_MODULE:") -> {
                    id = line.substringAfter(':').trim()
                    props.clear()
                }
                line == "@@JWSK_END" -> {
                    flush()
                    id = null
                    props.clear()
                }
                id != null && '=' in line -> {
                    val key = line.substringBefore('=').trim()
                    val value = line.substringAfter('=').trim().take(2048)
                    if (key in setOf("name", "version", "versionCode", "author", "description", "jwsk_enabled", "jwsk_remove", "jwsk_action")) {
                        props[key] = value
                    }
                }
            }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    private fun validate(id: String) = require(moduleId.matches(id)) { "模块 ID 不安全" }
    private fun ksud(context: Context) = quote(File(context.applicationInfo.nativeLibraryDir, "libksud.so").absolutePath)
    private fun quote(value: String) = "'${value.replace("'", "'\\''")}'"
    private fun value(lines: List<String>, key: String): String? = lines
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
        ?.trim()
}
