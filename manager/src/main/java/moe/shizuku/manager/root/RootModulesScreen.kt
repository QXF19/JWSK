@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager.root

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold

private val ROOT_MODULE_MIME_TYPES = arrayOf(
    "application/zip",
    "application/octet-stream",
    "application/x-zip-compressed"
)

@Composable
fun RootModulesScreen(environment: RootEnvironment) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modules by remember { mutableStateOf<List<RootModule>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf<Pair<String, String>?>(null) }
    var removeTarget by remember { mutableStateOf<RootModule?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            modules = RootManager.listModules(context, environment)
            loading = false
        }
    }

    fun execute(module: RootModule, title: String, block: suspend () -> RootCommandResult) {
        scope.launch {
            busyId = module.id
            val result = runCatching { block() }
            output = result.fold(
                onSuccess = { title to "退出码 ${it.code}\n${it.output}".trim() },
                onFailure = { title to (it.message ?: it.javaClass.simpleName) }
            )
            busyId = null
            reload()
        }
    }

    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            loading = true
            runCatching { RootManager.installModule(context, uri, environment) }
                .onSuccess {
                    output = "模块安装" to "退出码 ${it.code}\n${it.output}".trim()
                    Toast.makeText(context, if (it.code == 0) "模块安装完成，重启后生效" else "模块安装失败", Toast.LENGTH_LONG).show()
                }
                .onFailure {
                    output = "模块安装失败" to (it.message ?: it.javaClass.simpleName)
                }
            loading = false
            reload()
        }
    }

    LaunchedEffect(environment) { reload() }

    ShizukuLazyScaffold(
        title = when (environment.backend) {
            RootBackend.KERNEL_SU -> "KernelSU 模块"
            RootBackend.MAGISK -> "Kitsune / Magisk 模块"
            else -> "Root 模块"
        },
        onNavigateUp = null,
        actions = {
            FilledTonalButton(
                onClick = { installLauncher.launch(ROOT_MODULE_MIME_TYPES) },
                enabled = !loading && environment.canManageRootModules,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                ShizukuIcon(R.drawable.ic_outline_arrow_upward_24, modifier = Modifier.size(18.dp))
                Text(" 安装 ZIP")
            }
        }
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text(environment.title) })
                        AssistChip(onClick = {}, label = { Text(environment.modeLabel) })
                    }
                    Text(
                        if (environment.backend == RootBackend.KERNEL_SU) {
                            "使用 KernelSU 官方 ksud 模块生命周期命令。安装、开关、卸载和执行操作均写入 JWSK 日志。"
                        } else {
                            "使用 Magisk 官方模块安装入口，并通过标准 disable/remove 标记管理状态。"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    environment.warning?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (loading) {
            item { CircularProgressIndicator(Modifier.padding(24.dp)) }
        } else if (modules.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("没有已安装模块", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("请选择兼容 Magisk/KernelSU 的 ZIP。安装前请确认来源和设备兼容性。")
                        Button(onClick = { installLauncher.launch(ROOT_MODULE_MIME_TYPES) }) { Text("选择模块 ZIP") }
                    }
                }
            }
        }

        items(modules, key = { it.id }) { module ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(module.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${module.id} · ${module.version} · ${module.author}", style = MaterialTheme.typography.bodySmall)
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (module.pendingRemoval) "待卸载" else if (module.enabled) "已启用" else "已停用") }
                        )
                    }
                    if (module.description.isNotBlank()) Text(module.description, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            enabled = busyId == null && !module.pendingRemoval,
                            onClick = {
                                execute(module, if (module.enabled) "停用模块" else "启用模块") {
                                    RootManager.setEnabled(context, module, !module.enabled)
                                }
                            }
                        ) { Text(if (module.enabled) "停用" else "启用") }
                        if (module.hasAction) {
                            OutlinedButton(
                                enabled = busyId == null,
                                onClick = { execute(module, "执行 ${module.name}") { RootManager.runAction(context, module) } }
                            ) { Text("执行") }
                        }
                        TextButton(enabled = busyId == null, onClick = { removeTarget = module }) { Text("卸载") }
                    }
                    if (busyId == module.id) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
    }

    removeTarget?.let { module ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("确认卸载模块") },
            text = { Text("${module.name} 将在下次重启时移除。此操作可能改变系统启动环境。") },
            confirmButton = {
                Button(onClick = {
                    removeTarget = null
                    execute(module, "卸载 ${module.name}") { RootManager.uninstall(context, module) }
                }) { Text("确认卸载") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("取消") } }
        )
    }

    output?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { output = null },
            title = { Text(title) },
            text = { MonospaceLog(body.ifBlank { "没有输出" }) },
            confirmButton = { TextButton(onClick = { output = null }) { Text("关闭") } }
        )
    }
}
