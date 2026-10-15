package me.weishu.kernelsu.jwsk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.jwsk.alpha.AlphaPatchViewModel
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AlphaPatchScreen() {
    val model = viewModel<AlphaPatchViewModel>()
    val state by model.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val select = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::select) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(model::export) }
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = stringResource(R.string.jwsk_back), onClick = { navigator.pop() })
            Text("Magisk Alpha", style = MiuixTheme.textStyles.title2, modifier = Modifier.padding(top = 10.dp))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.jwsk_alpha_patch_description), maxLines = Int.MAX_VALUE) }
            item {
                TextButton(text = stringResource(R.string.jwsk_alpha_select), enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { select.launch(arrayOf("*/*")) })
            }
            state.name?.let { name -> item { SelectionContainer { Text(name, maxLines = Int.MAX_VALUE) } } }
            item {
                val enabled = !state.busy && !state.ready
                Card {
                    SwitchPreference(title = stringResource(R.string.jwsk_alpha_verity), checked = state.options.keepVerity,
                        enabled = enabled, onCheckedChange = { model.options(state.options.copy(keepVerity = it)) })
                    SwitchPreference(title = stringResource(R.string.jwsk_alpha_encrypt), checked = state.options.keepEncryption,
                        enabled = enabled, onCheckedChange = { model.options(state.options.copy(keepEncryption = it)) })
                    SwitchPreference(title = stringResource(R.string.jwsk_alpha_vbmeta), checked = state.options.patchVbmeta,
                        enabled = enabled, onCheckedChange = { model.options(state.options.copy(patchVbmeta = it)) })
                    SwitchPreference(title = stringResource(R.string.jwsk_alpha_recovery), checked = state.options.recovery,
                        enabled = enabled, onCheckedChange = { model.options(state.options.copy(recovery = it)) })
                    SwitchPreference(title = stringResource(R.string.jwsk_alpha_sar), checked = state.options.legacySar,
                        enabled = enabled, onCheckedChange = { model.options(state.options.copy(legacySar = it)) })
                }
            }
            item {
                if (state.busy) {
                    Text(stringResource(R.string.jwsk_running))
                    TextButton(text = stringResource(R.string.jwsk_cancel), onClick = model::cancel)
                } else if (state.ready) {
                    TextButton(text = stringResource(R.string.jwsk_alpha_export), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = { save.launch("jwsk-alpha-patched.img") })
                } else {
                    TextButton(text = stringResource(R.string.jwsk_alpha_patch), enabled = state.name != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = model::patch)
                }
            }
            if (state.error) item { Text(stringResource(R.string.jwsk_alpha_failed), maxLines = Int.MAX_VALUE) }
            if (state.saved) item { Text(stringResource(R.string.jwsk_alpha_saved), maxLines = Int.MAX_VALUE) }
            state.sha256?.let { sha -> item { SelectionContainer { Text("SHA-256\n$sha", maxLines = Int.MAX_VALUE) } } }
            if (state.log.isNotBlank()) item { SelectionContainer { Text(state.log, maxLines = Int.MAX_VALUE) } }
        }
    }
}
