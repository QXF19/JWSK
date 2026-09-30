package moe.shizuku.manager.about

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.ui.compose.GroupDivider

class AboutActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"

        setContent {
            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.action_about),
                    onNavigateUp = { finish() }
                ) {
                    item {
                        AboutHeader(versionName)
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        AboutDescriptionCard()
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        AboutLinksGroup()
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "© 2026 JWSK Contributors · Open Source",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AboutHeader(versionName: String) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val appIcon = remember(context) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                val bitmap = drawable.toBitmap(192, 192)
                bitmap.asImageBitmap()
            }.getOrNull()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = 32.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Version $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutDescriptionCard() {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "关于 JWSK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "JWSK 是面向 Android 12–16 的 Magisk 与 KernelSU Root 管理器，提供框架检测、Root 模块、Magisk 授权策略、Comput 超级用户终端、日志与离线镜像修补。修补功能只导出新文件，不会自动写入设备分区。",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun AboutLinksGroup() {
        val context = this@AboutActivity
        SettingsGroup(title = "项目与上游") {
            SettingsRow(
                icon = R.drawable.ic_baseline_link_24,
                title = stringResource(R.string.about_source_code_button),
                summary = "github.com/QXF19/JWSK",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/QXF19/JWSK")))
                }
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_baseline_link_24,
                title = "Magisk",
                summary = "github.com/topjohnwu/Magisk",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/topjohnwu/Magisk")))
                }
            )
            GroupDivider()
            SettingsRow(
                icon = R.drawable.ic_outline_info_24,
                title = "KernelSU",
                summary = "github.com/tiann/KernelSU",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tiann/KernelSU")))
                }
            )
        }
    }
}
