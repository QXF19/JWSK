@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager.root

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold

@Composable
fun RootLogsScreen(environment: RootEnvironment) {
    val context = LocalContext.current
    var content by remember { mutableStateOf(RootLogStore.read(context)) }
    var confirmClear by remember { mutableStateOf(false) }

    fun refresh() { content = RootLogStore.read(context) }

    ShizukuLazyScaffold(
        title = "日志管理",
        onNavigateUp = null,
        actions = {
            TextButton(onClick = { refresh() }) { Text("刷新") }
        }
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row {
                        AssistChip(onClick = {}, label = { Text(environment.title) })
                    }
                    Text("仅保存 JWSK 自身的检测、补丁与模块操作摘要。日志达到 1 MB 后自动轮转，敏感字段会被隐藏。")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("JWSK 日志", content))
                    },
                    modifier = Modifier.weight(1f).height(44.dp)
                ) { Text("复制") }
                OutlinedButton(
                    onClick = {
                        val file = RootLogStore.exportFile(context)
                        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "导出 JWSK 日志"))
                    },
                    modifier = Modifier.padding(start = 8.dp).weight(1f).height(44.dp)
                ) { Text("导出") }
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(start = 4.dp).height(44.dp)) { Text("清空") }
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                MonospaceLog(content.ifBlank { "暂无 JWSK 操作日志" }, modifier = Modifier.padding(14.dp))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空日志？") },
            text = { Text("此操作只清除 JWSK 的本地操作日志，不会修改 Magisk、KernelSU 或系统日志。") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    RootLogStore.clear(context)
                    confirmClear = false
                    refresh()
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
}
