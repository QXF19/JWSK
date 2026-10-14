package me.weishu.kernelsu.jwsk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.jwsk.RootComput
import me.weishu.kernelsu.jwsk.ai.AiViewModel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun JwskToolsScreen(bottomPadding: Dp) {
    val navigator = LocalNavigator.current
    val model = viewModel<AiViewModel>()
    val config by model.settings.collectAsStateWithLifecycle()
    val colors = MiuixTheme.colorScheme
    val pageColor = if (me.weishu.kernelsu.ui.theme.isInDarkTheme()) colors.background else Color(0xFFDFEFF8)
    LazyColumn(modifier = Modifier.fillMaxSize().background(pageColor),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box {
                JwskHomeArtwork()
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)
                    .background(colors.surface.copy(alpha = 0.90f), RoundedCornerShape(28.dp)).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.jwsk_tools), fontSize = 22.sp)
                    Box(Modifier.size(48.dp).clickable { navigator.push(Route.Settings) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings), modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                ArrowPreference(title = stringResource(R.string.jwsk_ai_assistant),
                    summary = if (config.showUnconfiguredBadge) stringResource(R.string.jwsk_unconfigured) else null,
                    onClick = { navigator.push(Route.AiChat) })
                ArrowPreference(title = "Comput", summary = stringResource(R.string.jwsk_comput_description),
                    onClick = { navigator.push(Route.Comput) })
                ArrowPreference(title = stringResource(R.string.jwsk_logs), onClick = { navigator.push(Route.Sulog) })
            }
        }
    }
}

@Composable
fun ComputScreen() {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("id") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var alphaManager by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        alphaManager = me.weishu.kernelsu.jwsk.alpha.AlphaClient(me.weishu.kernelsu.ksuApp).probe().trustedManager
    }
    val canRun = alphaManager || (Natives.isManager && Natives.kernelUAPIVersion == Natives.managerUAPIVersion)
    val errorText = stringResource(R.string.jwsk_comput_failed)
    val exitLabel = stringResource(R.string.jwsk_exit_code)
    val confirm = rememberConfirmDialog(onConfirm = {
        val selected = command
        running = true
        scope.launch {
            try {
                val result = RootComput.execute(selected)
                output = "$exitLabel: ${result.code}\n\n${result.output}"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                output = errorText
            } finally { running = false }
        }
    })
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(text = stringResource(R.string.jwsk_back), onClick = { navigator.pop() })
            Text("Comput", modifier = Modifier.padding(top = 10.dp))
        }
        if (!canRun) Text(stringResource(R.string.jwsk_root_unavailable))
        TextField(value = command, onValueChange = { command = it.take(8192) }, enabled = !running,
            label = stringResource(R.string.jwsk_command), modifier = Modifier.fillMaxWidth(), minLines = 3)
        TextButton(text = stringResource(if (running) R.string.jwsk_running else R.string.jwsk_execute),
            enabled = canRun && command.isNotBlank() && !running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = { confirm.showConfirm(title = "Comput", content = command) })
        LazyColumn(Modifier.weight(1f)) {
            item { SelectionContainer { Text(output, fontFamily = FontFamily.Monospace) } }
        }
        TextButton(text = stringResource(R.string.jwsk_clear_output), enabled = !running, onClick = { output = "" })
    }
}
