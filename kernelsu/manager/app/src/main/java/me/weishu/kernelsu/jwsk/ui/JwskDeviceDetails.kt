package me.weishu.kernelsu.jwsk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.HomeViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun JwskDeviceDetails() {
    val navigator = LocalNavigator.current
    val model = viewModel<HomeViewModel>()
    val state by model.uiState.collectAsStateWithLifecycle()
    val info = state.systemInfo
    val selinux = stringResource(when (info.selinuxStatus) {
        "Enforcing" -> R.string.selinux_status_enforcing
        "Permissive" -> R.string.selinux_status_permissive
        "Disabled" -> R.string.selinux_status_disabled
        else -> R.string.selinux_status_unknown
    })
    val seccomp = stringResource(when (info.seccompStatus) {
        -1 -> R.string.seccomp_status_not_supported
        0 -> R.string.seccomp_status_disabled
        1 -> R.string.seccomp_status_strict
        2 -> R.string.seccomp_status_filter
        else -> R.string.seccomp_status_unknown
    })
    val fields = listOf(
        stringResource(R.string.home_manager_version) to info.managerVersion,
        stringResource(R.string.home_kernel) to info.kernelVersion,
        stringResource(R.string.home_device_model) to info.deviceModel,
        stringResource(R.string.home_fingerprint) to info.fingerprint,
        stringResource(R.string.home_selinux_status) to selinux,
        stringResource(R.string.home_seccomp_status) to seccomp,
    )
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        TextButton(text = stringResource(R.string.jwsk_back), onClick = { navigator.pop() },
            modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                            fields.forEach { (label, value) ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(label, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                    Text(value, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
