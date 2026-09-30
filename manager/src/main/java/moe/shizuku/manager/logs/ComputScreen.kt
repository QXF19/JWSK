@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.root.RootEnvironment
import moe.shizuku.manager.root.RootManager
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold

@Composable
fun ComputScreen(environment: RootEnvironment) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("id") }
    var output by remember { mutableStateOf("JWSK Root Comput 已就绪。\n命令将直接以超级用户身份执行。") }
    var running by remember { mutableStateOf(false) }
    var confirmRun by remember { mutableStateOf(false) }

    fun runCommand() {
        scope.launch {
            running = true
            output = "正在执行…"
            output = runCatching { RootManager.runComput(context, command) }.fold(
                onSuccess = { result ->
                    buildString {
                        append("退出码: ${result.code} · ${result.elapsedMs} ms")
                        if (result.output.isNotBlank()) append("\n\n${result.output}")
                        else append("\n\n命令没有输出")
                    }
                },
                onFailure = { "执行失败：${it.message ?: it.javaClass.simpleName}" }
            )
            running = false
        }
    }

    ShizukuLazyScaffold(title = "Comput · Root 终端", onNavigateUp = null) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null)
                        Text("仅 Root 模式", fontWeight = FontWeight.Bold)
                        AssistChip(onClick = {}, label = { Text(environment.title) })
                    }
                    Text(
                        if (environment.rootGranted) {
                            "命令由 Magisk/KernelSU 授权的 su Shell 直接执行，不经过 ADB 或 Shizuku。"
                        } else {
                            "尚未取得 Root 权限。请先在 Magisk 或 KernelSU 中允许 JWSK。"
                        }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = command,
                onValueChange = { if (it.length <= 8192) command = it },
                label = { Text("Shell 命令") },
                placeholder = { Text("例如：id 或 getprop ro.build.version.release") },
                enabled = !running,
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { confirmRun = true },
                    enabled = environment.rootGranted && command.isNotBlank() && !running,
                    modifier = Modifier.weight(1f)
                ) {
                    if (running) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(if (running) "执行中" else " 执行")
                }
                OutlinedButton(
                    onClick = { output = "输出已清空" },
                    enabled = !running,
                    modifier = Modifier.weight(1f)
                ) { Text("清空输出") }
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("执行输出", fontWeight = FontWeight.SemiBold)
                        FilledTonalButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("JWSK Comput", output))
                            Toast.makeText(context, "已复制输出", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Text(" 复制")
                        }
                    }
                    SelectionContainer {
                        Text(
                            output,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(min = 160.dp, max = 520.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }

    if (confirmRun) {
        AlertDialog(
            onDismissRequest = { confirmRun = false },
            title = { Text("执行 Root 命令？") },
            text = { Text("Root 命令可以修改系统文件和数据。请确认你理解这条命令的作用：\n\n$command") },
            confirmButton = {
                Button(onClick = {
                    confirmRun = false
                    runCommand()
                }) { Text("确认执行") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("取消") } }
        )
    }
}
