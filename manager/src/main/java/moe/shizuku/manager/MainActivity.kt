@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.about.AboutActivity
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.logs.ComputScreen
import moe.shizuku.manager.patch.PatchHubActivity
import moe.shizuku.manager.root.RootDashboardCard
import moe.shizuku.manager.root.RootEnvironment
import moe.shizuku.manager.root.RootLogsScreen
import moe.shizuku.manager.root.RootManager
import moe.shizuku.manager.root.RootModulesScreen
import moe.shizuku.manager.root.RootPolicyScreen
import moe.shizuku.manager.ui.compose.JwskRootTheme

class MainActivity : AppActivity() {
    private var refreshGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JwskRootTheme {
                JwskRootManager(
                    refreshGeneration = refreshGeneration,
                    onPatch = { startActivity(Intent(this, PatchHubActivity::class.java)) },
                    onAbout = { startActivity(Intent(this, AboutActivity::class.java)) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshGeneration++
    }
}

private enum class RootSection(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Rounded.Home),
    MODULES("模块", Icons.Rounded.Extension),
    POLICIES("授权", Icons.Rounded.Security),
    COMPUT("Comput", Icons.Rounded.Terminal),
    LOGS("日志", Icons.AutoMirrored.Rounded.ReceiptLong)
}

@Composable
private fun JwskRootManager(
    refreshGeneration: Int,
    onPatch: () -> Unit,
    onAbout: () -> Unit
) {
    val context = LocalContext.current
    var section by remember { mutableStateOf(RootSection.HOME) }
    var environment by remember { mutableStateOf(RootEnvironment()) }
    var detecting by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshGeneration) {
        detecting = true
        environment = RootManager.detect(context.applicationContext)
        detecting = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                RootSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (section) {
                RootSection.HOME -> RootHomeScreen(
                    environment = environment,
                    detecting = detecting,
                    onRefresh = {
                        detecting = true
                        scope.launch {
                            environment = RootManager.detect(context.applicationContext)
                            detecting = false
                        }
                    },
                    onModules = { section = RootSection.MODULES },
                    onPolicies = { section = RootSection.POLICIES },
                    onComput = { section = RootSection.COMPUT },
                    onLogs = { section = RootSection.LOGS },
                    onPatch = onPatch,
                    onAbout = onAbout
                )
                RootSection.MODULES -> RootModulesScreen(environment)
                RootSection.POLICIES -> RootPolicyScreen(environment) { section = RootSection.HOME }
                RootSection.COMPUT -> ComputScreen(environment)
                RootSection.LOGS -> RootLogsScreen(environment)
            }
        }
    }

    if (detecting && section != RootSection.HOME) {
        Surface(
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun RootHomeScreen(
    environment: RootEnvironment,
    detecting: Boolean,
    onRefresh: () -> Unit,
    onModules: () -> Unit,
    onPolicies: () -> Unit,
    onComput: () -> Unit,
    onLogs: () -> Unit,
    onPatch: () -> Unit,
    onAbout: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("JWSK", fontWeight = FontWeight.Bold)
                    Text("Magisk · KernelSU Root 管理器", style = MaterialTheme.typography.labelMedium)
                }
            },
            actions = {
                FilledTonalButton(onClick = onAbout, modifier = Modifier.padding(end = 12.dp)) {
                    Text("关于")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RootDashboardCard(
                    environment = environment,
                    onModules = onModules,
                    onPatch = onPatch,
                    onComput = onComput,
                    onLogs = onLogs,
                    onRootApps = onPolicies,
                    onSettings = onAbout
                )
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Root 状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(environment.statusDetail)
                        Button(enabled = !detecting, onClick = onRefresh) {
                            if (detecting) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            Text(if (detecting) "检测中" else "重新检测")
                        }
                    }
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("双修补入口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Boot 镜像交给 Magisk 修补；内核与 LKM 路线交给 KernelSU。JWSK 只生成补丁文件，不自动刷写分区。")
                        FilledTonalButton(onClick = onPatch) { Text("进入修补中心") }
                    }
                }
            }
        }
    }
}
