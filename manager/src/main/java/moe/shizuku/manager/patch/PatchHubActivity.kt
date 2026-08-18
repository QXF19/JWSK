@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager.patch

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import java.io.File

class PatchHubActivity : AppActivity() {
    private var inputImage by mutableStateOf<SelectedImage?>(null)
    private var kernelImage by mutableStateOf<SelectedImage?>(null)
    private var mode by mutableStateOf(PatchMode.MAGISK)
    private var kmi by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("请选择原厂 boot.img 或 init_boot.img。")
    private var confirm by mutableStateOf(false)
    private var pendingOutput: File? = null

    private val inputPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        busy = true
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { BootImageInspector.import(this@PatchHubActivity, uri, "input") } }
                .onSuccess {
                    inputImage = it
                    status = "已载入 ${it.displayName}\n格式：${it.format}\nSHA-256：${it.sha256}"
                }
                .onFailure { status = "读取失败：${it.message}" }
            busy = false
        }
    }

    private val kernelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        busy = true
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { BootImageInspector.import(this@PatchHubActivity, uri, "kernel") } }
                .onSuccess {
                    kernelImage = it
                    status = "已载入自定义内核 ${it.displayName}\nSHA-256：${it.sha256}"
                }
                .onFailure { status = "读取内核失败：${it.message}" }
            busy = false
        }
    }

    private val outputPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val output = pendingOutput
        if (uri == null || output == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "w").use { stream ->
                        requireNotNull(stream) { "无法创建输出文件" }
                        output.inputStream().use { it.copyTo(stream) }
                    }
                }
            }.onSuccess {
                Toast.makeText(this@PatchHubActivity, "修补镜像已导出", Toast.LENGTH_LONG).show()
            }.onFailure {
                status = "导出失败：${it.message}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShizukuExpressiveTheme {
                PatchHubScreen(
                    input = inputImage,
                    kernel = kernelImage,
                    mode = mode,
                    kmi = kmi,
                    busy = busy,
                    status = status,
                    confirm = confirm,
                    onBack = { finish() },
                    onPickInput = { inputPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                    onPickKernel = { kernelPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                    onMode = { mode = it },
                    onKmi = { kmi = it },
                    onRequestPatch = { confirm = true },
                    onDismissConfirm = { confirm = false },
                    onConfirmPatch = {
                        confirm = false
                        runPatch()
                    }
                )
            }
        }
    }

    private fun runPatch() {
        val input = inputImage ?: return
        busy = true
        status = "正在使用 ${mode.title} 修补；不会写入设备分区……"
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    PatchExecutor.patch(this@PatchHubActivity, input, mode, kmi, kernelImage)
                }
            }.onSuccess { result ->
                pendingOutput = result.output
                status = "修补完成\n输出：${result.output.name}\nSHA-256：${result.sha256}\n\n${result.log.takeLast(6000)}"
                outputPicker.launch(result.output.name)
            }.onFailure {
                status = "修补失败：${it.message}"
            }
            busy = false
        }
    }
}

@Composable
private fun PatchHubScreen(
    input: SelectedImage?,
    kernel: SelectedImage?,
    mode: PatchMode,
    kmi: String,
    busy: Boolean,
    status: String,
    confirm: Boolean,
    onBack: () -> Unit,
    onPickInput: () -> Unit,
    onPickKernel: () -> Unit,
    onMode: (PatchMode) -> Unit,
    onKmi: (String) -> Unit,
    onRequestPatch: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmPatch: () -> Unit
) {
    if (confirm) {
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = { Text("确认修补镜像") },
            text = {
                Text("请确认输入来自当前设备的原厂固件，并已备份原镜像。JWSK 只生成新文件，不会自动刷写；刷入错误镜像仍可能导致设备无法启动。")
            },
            confirmButton = { TextButton(onClick = onConfirmPatch) { Text("确认并修补") } },
            dismissButton = { TextButton(onClick = onDismissConfirm) { Text("取消") } }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JWSK 镜像修补中心") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("安全边界", fontWeight = FontWeight.Bold)
                        Text("仅离线修补并导出镜像，不读取或写入 boot 分区，不包含 root 隐藏、完整性绕过或反检测功能。")
                    }
                }
            }
            item {
                Button(onClick = onPickInput, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (input == null) "选择原厂 boot / init_boot 镜像" else "更换输入：${input.displayName}")
                }
            }
            item {
                Text("修补引擎", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PatchMode.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { onMode(item) },
                            label = { Text("${item.title} · ${item.detail}") }
                        )
                    }
                }
            }
            if (mode != PatchMode.MAGISK) {
                item {
                    OutlinedTextField(
                        value = kmi,
                        onValueChange = onKmi,
                        label = { Text("KMI（可留空自动识别，如 android13-5.10）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            if (mode == PatchMode.KERNELSU_KERNEL) {
                item {
                    OutlinedButton(onClick = onPickKernel, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(kernel?.let { "更换内核：${it.displayName}" } ?: "选择匹配 KMI 的自定义内核")
                    }
                }
            }
            item {
                Button(
                    onClick = onRequestPatch,
                    enabled = !busy && input != null && (mode != PatchMode.KERNELSU_KERNEL || kernel != null),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) CircularProgressIndicator() else Text("生成修补镜像")
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("状态与日志", fontWeight = FontWeight.Bold)
                        Text(status, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
