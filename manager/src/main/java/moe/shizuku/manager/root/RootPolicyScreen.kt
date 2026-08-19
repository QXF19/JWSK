@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold

@Composable
fun RootPolicyScreen(environment: RootEnvironment, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var policies by remember { mutableStateOf<List<RootPolicy>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        if (environment.backend != RootBackend.MAGISK) return
        scope.launch {
            loading = true
            policies = RootManager.listMagiskPolicies(context)
            loading = false
        }
    }

    LaunchedEffect(environment) { reload() }

    ShizukuLazyScaffold(title = "Root 应用授权", onNavigateUp = onBack) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(environment.title) })
                    Text(
                        when (environment.backend) {
                            RootBackend.MAGISK -> "管理 Magisk 已记录的超级用户策略。新应用首次请求仍由 Magisk 守护进程弹窗确认。"
                            RootBackend.KERNEL_SU -> "当前 KernelSU 内核只允许它认可签名的管理器修改应用授权。JWSK 不伪造管理器身份，以免造成授权失控。模块、修补、日志和 Comput 不受影响。"
                            RootBackend.HYBRID -> "双 Root 框架可能同时拦截请求。请只保留一个活动框架后再管理应用授权。"
                            RootBackend.ADB -> "ADB 模式没有系统 Root 授权数据库；这里只管理 JWSK/Shizuku 可访问的应用。"
                            RootBackend.NONE -> "尚未检测到可管理的 Root 框架。"
                        }
                    )
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        if (loading) item { CircularProgressIndicator(Modifier.padding(24.dp)) }

        if (environment.backend == RootBackend.MAGISK && !loading && policies.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("暂无授权记录", fontWeight = FontWeight.SemiBold)
                        Text("当应用首次请求 su 后，它会出现在这里。")
                    }
                }
            }
        }

        items(policies, key = { it.uid }) { policy ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(policy.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${policy.packageName.ifBlank { "未知包名" }} · UID ${policy.uid}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            scope.launch {
                                val result = RootManager.setMagiskPolicy(context, policy, !policy.allowed)
                                message = if (result.code == 0) "授权策略已更新" else "更新失败：${result.output}"
                                reload()
                            }
                        }) { Text(if (policy.allowed) "改为拒绝" else "允许") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val result = RootManager.deleteMagiskPolicy(context, policy.uid)
                                message = if (result.code == 0) "已恢复为首次询问" else "删除失败：${result.output}"
                                reload()
                            }
                        }) { Text("恢复询问") }
                    }
                }
            }
        }
    }
}
