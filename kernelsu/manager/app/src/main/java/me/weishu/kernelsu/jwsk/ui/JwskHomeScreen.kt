package me.weishu.kernelsu.jwsk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.screen.home.HomeActions
import me.weishu.kernelsu.ui.screen.home.HomeUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun JwskHomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    bottomPadding: Dp,
    onSettings: () -> Unit,
    onDeviceDetails: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val pageColor = if (me.weishu.kernelsu.ui.theme.isInDarkTheme()) colors.background else Color(0xFFDFEFF8)
    val working = !state.detectionFailed && (state.alphaManager || (state.isManager && state.isRootAvailable &&
        state.kernelUAPIVersion == state.managerUAPIVersion && !state.requiresNewKernel && !state.requiresNewManager))
    val reason = when {
        state.detectionFailed -> R.string.jwsk_detection_failed
        state.alphaManager -> R.string.jwsk_root_ready
        state.alphaVersion != null -> R.string.jwsk_manager_unrecognized
        state.ksuVersion == null -> R.string.jwsk_kernel_unavailable
        !state.isManager -> R.string.jwsk_manager_unrecognized
        state.requiresNewKernel || state.requiresNewManager || state.kernelUAPIVersion != state.managerUAPIVersion -> R.string.jwsk_api_mismatch
        !state.isRootAvailable -> R.string.jwsk_root_unavailable
        state.isSafeMode -> R.string.jwsk_safe_mode
        else -> R.string.jwsk_root_ready
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageColor),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box {
                JwskHomeArtwork()
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)
                        .background(colors.surface.copy(alpha = 0.90f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("JWSK", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Box(Modifier.size(48.dp).background(colors.surface.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
                        .clickable(onClickLabel = stringResource(R.string.settings), onClick = onSettings), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings), tint = colors.onSurface, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Icon(if (working) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                            contentDescription = null, modifier = Modifier.size(64.dp), tint = colors.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(if (state.isLoading) R.string.jwsk_loading else if (working) R.string.jwsk_working else R.string.jwsk_not_working),
                                fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            if (!state.isLoading) Text(stringResource(reason), fontSize = 15.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                    if (!working && !state.isLoading && !state.detectionFailed) Text(stringResource(R.string.jwsk_matching_kernel), fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                    TextButton(text = stringResource(R.string.jwsk_install_patch), onClick = actions.onInstallClick,
                        enabled = !state.isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onDeviceDetails).padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeveloperBoard, null, modifier = Modifier.size(28.dp), tint = colors.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.jwsk_kernel_version), fontWeight = FontWeight.SemiBold)
                            Text(state.kernelVersion.toString(), color = colors.onSurfaceVariantSummary, fontSize = 14.sp)
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("KMI", fontWeight = FontWeight.SemiBold)
                            val kmi = Regex("(\\d+\\.\\d+).*?(android\\d+)-(\\d+)").find(state.systemInfo.kernelVersion)
                                ?.let { "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
                            Text(kmi ?: stringResource(R.string.jwsk_unknown), fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Magisk Alpha", fontWeight = FontWeight.SemiBold)
                            Text(state.alphaVersion ?: stringResource(R.string.jwsk_unknown), fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }
}
