package me.weishu.kernelsu.jwsk.alpha

import android.app.Application
import android.net.Uri
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile

internal enum class AlphaSection { POLICIES, MODULES, SETTINGS, LOGS }
internal data class AlphaApp(val uid: Int, val label: String, val packages: List<String>,
    val applicationInfo: android.content.pm.ApplicationInfo?, val system: Boolean,
    val installedAt: Long = 0, val updatedAt: Long = 0)
internal data class AlphaModule(val id: String, val name: String, val version: String,
    val author: String, val description: String, val enabled: Boolean, val remove: Boolean, val updated: Boolean, val action: Boolean)
internal data class AlphaManagementState(val loading: Boolean = true, val available: Boolean = false,
    val failed: Boolean = false, val policies: List<AlphaPolicy> = emptyList(), val modules: List<AlphaModule> = emptyList(),
    val apps: List<AlphaApp> = emptyList(),
    val settings: Map<String, Int> = emptyMap(), val denyEnforced: Boolean = false,
    val denyTargets: List<Pair<String, String>> = emptyList(), val text: String = "")

internal class AlphaManagement(app: Application) : AndroidViewModel(app) {
    private val client = AlphaClient(app)
    private val database = AlphaPolicyStore(app)
    private val mutable = MutableStateFlow(AlphaManagementState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var current = AlphaSection.POLICIES
    private var reloadPending = false

    fun load(section: AlphaSection = current) {
        current = section
        if (job?.isActive == true) { reloadPending = true; return }
        run { reload() }
    }
    private suspend fun reload() {
        val available = client.probe().trustedManager
        if (!available) { mutable.value = AlphaManagementState(loading = false); return }
        var next = mutable.value.copy(available = true, failed = false)
        when (current) {
            AlphaSection.POLICIES -> {
                val policies = database.policies()
                val apps = withContext(Dispatchers.IO) {
                    val pm = getApplication<Application>().packageManager
                    val installed = pm.getInstalledApplications(0).filter { it.uid != Process.myUid() && it.uid / 100000 == Process.myUid() / 100000 }
                        .groupBy { it.uid }.map { (uid, applications) ->
                            val sorted = applications.sortedBy { it.packageName }
                            val metadata = sorted.mapNotNull { runCatching { pm.getPackageInfo(it.packageName, 0) }.getOrNull() }
                            AlphaApp(uid, sorted.joinToString(" / ") { it.loadLabel(pm).toString() }, sorted.map { it.packageName },
                                sorted.first(), sorted.all { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 },
                                metadata.minOfOrNull { it.firstInstallTime } ?: 0,
                                metadata.maxOfOrNull { it.lastUpdateTime } ?: 0)
                        }
                    installed + policies.filter { policy -> installed.none { it.uid == policy.uid } }
                        .map { AlphaApp(it.uid, "", emptyList(), null, false) }
                }
                next = next.copy(policies = policies, apps = apps)
            }
            AlphaSection.MODULES -> next = next.copy(modules = readModules())
            AlphaSection.SETTINGS -> {
                val status = client.root("magisk --denylist status", 10000)
                check(status.exitCode() in 0..1 && status.output().contains("Denylist is"))
                val targets = execute("magisk --denylist ls").lineSequence().mapNotNull { line ->
                    val fields = line.split('|', limit = 2)
                    if(fields.size == 2) fields[0] to fields[1] else null
                }.toList()
                next = next.copy(settings = database.sql("SELECT key,value FROM settings")
                    .associate { it.getValue("key") to it.getValue("value").toInt() }, denyEnforced = status.exitCode() == 0, denyTargets = targets)
            }
            AlphaSection.LOGS -> {
                val requests = withContext(Dispatchers.IO) { AlphaLogs(getApplication()).use { it.text() } }
                val daemon = execute("if [ -f /cache/magisk.log ]; then tail -c 49152 /cache/magisk.log; elif [ -f /data/adb/magisk.log ]; then tail -c 49152 /data/adb/magisk.log; fi")
                next = next.copy(text = "$requests\n$daemon")
            }
        }
        mutable.value = next
    }
    fun policy(value: AlphaPolicy) = run { database.save(value); reload() }
    fun removePolicy(uid: Int) = run { database.remove(uid); reload() }
    fun setting(key: String, value: Int) = run {
        val bounds = mapOf("root_access" to 0..3, "multiuser_mode" to 0..2, "mnt_ns" to 0..2,
            "zygisk" to 0..1, "bootloop" to 0..1)
        require(value in bounds.getValue(key))
        database.sql("REPLACE INTO settings(key,value) VALUES('$key',$value)")
        reload()
    }
    fun module(id: String, marker: String, present: Boolean) = run {
        require(ID.matches(id) && marker in setOf("disable", "remove"))
        val directory = AlphaClient.quote("/data/adb/modules/$id")
        val target = AlphaClient.quote("/data/adb/modules/$id/$marker")
        execute("[ -d $directory ] && [ ! -L $directory ] && ${if(present) "touch $target" else "rm -f -- $target"} && " +
            "(export BOOTMODE=true; export MAGISKTMP=\"\$(magisk --path)\"; . /data/adb/magisk/util_functions.sh; copy_preinit_files)")
        reload()
    }
    fun action(id: String) = run {
        require(ID.matches(id))
        val directory = AlphaClient.quote("/data/adb/modules/$id")
        mutable.value = mutable.value.copy(text = execute("[ -d $directory ] && [ ! -L $directory ] && cd $directory && exec sh ./action.sh", 120000))
    }
    fun install(uri: Uri) = run {
        var staging: File? = null
        try {
            withContext(Dispatchers.IO) {
                val file = File.createTempFile("jwsk-alpha-module-", ".zip", getApplication<Application>().cacheDir)
                staging = file
                val coroutine = currentCoroutineContext()
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        total += count; check(total <= 256L * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                } } ?: error("Cannot read module")
                ZipFile(file).use { zip ->
                    var total = 0L
                    zip.entries().asSequence().forEach { entry ->
                        check(!entry.name.startsWith('/') && !entry.name.contains('\\') && entry.name.split('/').none { it == ".." })
                        check(entry.size in 0..(128L * 1024 * 1024)); total += entry.size
                        check(total <= 512L * 1024 * 1024)
                    }
                    val props = zip.getEntry("module.prop") ?: error("Missing module metadata")
                    check(props.size in 1..65536)
                    val metadata = Properties().apply { zip.getInputStream(props).reader(Charsets.UTF_8).use { load(it) } }
                    check(ID.matches(metadata.getProperty("id", "")))
                }
                client.prepareRuntime()
                mutable.value = mutable.value.copy(text = execute("magisk --install-module ${AlphaClient.quote(file.path)}", 120000))
            }
            reload()
        } finally { withContext(NonCancellable + Dispatchers.IO) { staging?.delete() } }
    }
    fun clearLog() = run {
        withContext(Dispatchers.IO) { AlphaLogs(getApplication()).use { it.clear() } }
        execute("if [ -f /cache/magisk.log ]; then : > /cache/magisk.log; fi; if [ -f /data/adb/magisk.log ]; then : > /data/adb/magisk.log; fi")
        reload()
    }
    fun denyEnforce(enabled: Boolean) = run { execute("magisk --denylist ${if(enabled) "enable" else "disable"}"); reload() }
    fun denyTarget(packageName: String, processName: String, add: Boolean) = run {
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
        require(processName.isEmpty() || processName.matches(Regex("[A-Za-z0-9_.:]{1,256}")))
        execute("magisk --denylist ${if(add) "add" else "rm"} ${AlphaClient.quote(packageName)} ${AlphaClient.quote(processName)}")
        reload()
    }
    fun exportLog(uri: Uri) = run {
        val snapshot = mutable.value.text
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(snapshot) }
                ?: error("Cannot save log")
        }
    }
    private suspend fun readModules(): List<AlphaModule> {
        val script = """
            for d in /data/adb/modules/*; do
              [ -d "${'$'}d" ] && [ ! -L "${'$'}d" ] || continue
              id=${'$'}{d##*/}
              printf '%s|' "${'$'}id"
              for flag in disable remove update; do [ -e "${'$'}d/${'$'}flag" ] && printf '1|' || printf '0|'; done
              [ -f "${'$'}d/action.sh" ] && printf '1|' || printf '0|'
              [ -f "${'$'}d/module.prop" ] && head -c 65536 "${'$'}d/module.prop" | base64 -w 0
              printf '\n'
            done
        """.trimIndent()
        return execute(script).lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val fields = line.split('|', limit = 6)
            if (fields.size != 6 || !ID.matches(fields[0])) return@mapNotNull null
            val props = Properties().apply { load(Base64.getDecoder().decode(fields[5]).inputStream().reader(Charsets.UTF_8)) }
            AlphaModule(fields[0], props.getProperty("name", fields[0]).take(256), props.getProperty("version", "").take(128),
                props.getProperty("author", "").take(256), props.getProperty("description", "").take(2048),
                fields[1] == "0", fields[2] == "1", fields[3] == "1", fields[4] == "1")
        }.sortedBy { it.name }.toList()
    }
    private suspend fun execute(command: String, timeout: Long = 15000): String {
        val result = client.root(command, timeout)
        if (result.exitCode() != 0 || result.timedOut() || result.truncated()) throw AlphaPatchFailure(result.output())
        return result.output()
    }
    private fun run(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        mutable.value = mutable.value.copy(loading = true, failed = false)
        job = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(failed = true,
                text = (failure as? AlphaPatchFailure)?.diagnosticLog ?: mutable.value.text) }
            finally {
                mutable.value = mutable.value.copy(loading = false)
                job = null
                if (reloadPending && currentCoroutineContext().isActive) { reloadPending = false; load(current) }
            }
        }
    }
    companion object { val ID = Regex("[A-Za-z][A-Za-z0-9._-]{0,127}") }
}
