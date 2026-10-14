package me.weishu.kernelsu.jwsk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.jwsk.artifacts.KernelImportViewModel
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun InstallHubScreen() {
    val navigator = LocalNavigator.current
    val model = viewModel<KernelImportViewModel>()
    val intake by model.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::accept)
    }
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = stringResource(R.string.jwsk_back), onClick = { navigator.pop() })
            Text(stringResource(R.string.jwsk_install_patch), fontSize = 22.sp, modifier = Modifier.padding(top = 10.dp))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card {
                    ArrowPreference(title = "KernelSU LKM", summary = stringResource(R.string.jwsk_lkm_patch),
                        onClick = { navigator.push(Route.KernelSuInstall) })
                    ArrowPreference(title = "Magisk Alpha", summary = stringResource(R.string.jwsk_alpha_select),
                        onClick = { navigator.push(Route.AlphaPatch) })
                }
            }
            item {
                Text(stringResource(R.string.jwsk_gki_import), fontSize = 20.sp)
                Text(stringResource(R.string.jwsk_gki_import_description), modifier = Modifier.padding(top = 8.dp))
                TextButton(text = stringResource(if (intake.busy) R.string.jwsk_loading else R.string.jwsk_select_artifact),
                    enabled = !intake.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = { picker.launch(arrayOf("*/*")) })
            }
            if (intake.failed) item { Text(stringResource(R.string.jwsk_artifact_failed)) }
            if (intake.name != null) item {
                Card(Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(intake.name.orEmpty())
                            Text("${intake.format} · ${intake.bytes} bytes")
                            Text("SHA-256\n${intake.sha256}")
                            Text(stringResource(R.string.jwsk_artifact_accepted))
                        }
                    }
                }
            }
        }
    }
}
