@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package moe.shizuku.manager.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.compose.ShizukuIcon

@Composable
fun RootDashboardCard(
    environment: RootEnvironment,
    onModules: () -> Unit,
    onPatch: () -> Unit,
    onComput: () -> Unit,
    onLogs: () -> Unit,
    onRootApps: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = when (environment.backend) {
            RootBackend.KERNEL_SU -> MaterialTheme.colorScheme.primaryContainer
            RootBackend.MAGISK -> MaterialTheme.colorScheme.tertiaryContainer
            RootBackend.HYBRID -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = CircleShape,
                    modifier = Modifier.size(54.dp)
                ) {
                    ShizukuIcon(R.drawable.ic_root_24dp, modifier = Modifier.padding(14.dp))
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(environment.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when (environment.backend) {
                            RootBackend.MAGISK -> "Boot 镜像 Root 管理模式"
                            RootBackend.KERNEL_SU -> "内核级 Root 管理模式"
                            RootBackend.HYBRID -> "检测到双 Root 框架"
                            RootBackend.ADB -> "精简 ADB 管理模式"
                            RootBackend.NONE -> "选择内核修补、Boot 修补或 ADB 激活"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                AssistChip(onClick = {}, label = { Text(environment.modeLabel) })
            }

            environment.warning?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPatch) {
                    ShizukuIcon(R.drawable.ic_outline_arrow_upward_24, modifier = Modifier.size(18.dp))
                    Text(" 安装 / 修补")
                }
                FilledTonalButton(onClick = onModules) {
                    ShizukuIcon(R.drawable.ic_system_icon, modifier = Modifier.size(18.dp))
                    Text(if (environment.backend == RootBackend.KERNEL_SU) " 内核模块" else " Kitsune 模块")
                }
                OutlinedButton(onClick = onRootApps, enabled = environment.rootGranted || environment.adbActive) {
                    Text("应用授权")
                }
                OutlinedButton(onClick = onComput) { Text("Comput") }
                OutlinedButton(onClick = onLogs) { Text("日志") }
                OutlinedButton(onClick = onSettings) { Text("设置") }
            }
        }
    }
}
