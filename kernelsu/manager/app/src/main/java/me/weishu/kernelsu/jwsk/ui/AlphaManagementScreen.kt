package me.weishu.kernelsu.jwsk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.jwsk.alpha.*
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AlphaManagementScreen(section: AlphaSection, bottomPadding: Dp = 0.dp, showBack: Boolean = false) {
    if (section == AlphaSection.POLICIES) {
        AlphaSuperUserScreen(bottomPadding)
        return
    }
    val model = viewModel<AlphaManagement>(key = "alpha-${section.name}")
    val state by model.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var selectedModule by remember { mutableStateOf<android.net.Uri?>(null) }
    var denyPackage by remember { mutableStateOf("") }
    var denyProcess by remember { mutableStateOf("") }
    val installConfirm = rememberConfirmDialog(onConfirm = { selectedModule?.let(model::install); selectedModule = null })
    val installTitle = stringResource(R.string.jwsk_alpha_install_module)
    val installDescription = stringResource(R.string.jwsk_alpha_module_warning)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) { selectedModule = it; installConfirm.showConfirm(title = installTitle, content = installDescription) }
    }
    val logExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { it?.let(model::exportLog) }
    LaunchedEffect(section) { model.load(section) }
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (showBack) TextButton(text = stringResource(R.string.jwsk_back), onClick = { navigator.pop() })
            Text(stringResource(when(section) {
                AlphaSection.POLICIES -> R.string.superuser
                AlphaSection.MODULES -> R.string.module
                AlphaSection.SETTINGS -> R.string.jwsk_alpha_settings
                AlphaSection.LOGS -> R.string.jwsk_logs
            }), style = MiuixTheme.textStyles.title2, modifier = Modifier.padding(12.dp))
            TextButton(text = stringResource(R.string.jwsk_retry), enabled = !state.loading, onClick = { model.load(section) })
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading) item { Text(stringResource(R.string.jwsk_loading)) }
            if (state.failed) item { Text(stringResource(R.string.jwsk_alpha_manage_failed)) }
            if (!state.loading && !state.available) item { Text(stringResource(R.string.jwsk_root_unavailable)) }
            if (state.available) when (section) {
                AlphaSection.POLICIES -> Unit
                AlphaSection.MODULES -> {
                    item { TextButton(text = installTitle, enabled = !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) }) }
                    if (state.modules.isEmpty()) item { Text(stringResource(R.string.jwsk_alpha_no_modules)) }
                    items(state.modules, key = { it.id }) { module ->
                        Card {
                            SwitchPreference(title = module.name, summary = "${module.version} · ${module.author}", checked = module.enabled,
                                enabled = !state.loading && !module.remove,
                                onCheckedChange = { model.module(module.id, "disable", !it) })
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(module.description, maxLines = Int.MAX_VALUE)
                                if(module.updated) Text(stringResource(R.string.jwsk_alpha_reboot_required))
                                TextButton(text = stringResource(if(module.remove) R.string.jwsk_alpha_undo_remove else R.string.jwsk_alpha_remove),
                                    enabled = !state.loading && !module.updated,
                                    onClick = { model.module(module.id, "remove", !module.remove) })
                                if (module.action) TextButton(text = stringResource(R.string.jwsk_alpha_action), enabled = !state.loading,
                                    onClick = { model.action(module.id) })
                            }
                        }
                    }
                }
                AlphaSection.SETTINGS -> {
                    item { Text(stringResource(R.string.jwsk_alpha_reboot_required)) }
                    item { Card {
                        SwitchPreference(title = "Zygisk", checked = state.settings["zygisk"] == 1, enabled = !state.loading,
                            onCheckedChange = { model.setting("zygisk", if(it) 1 else 0) })
                        SwitchPreference(title = stringResource(R.string.jwsk_alpha_bootloop), checked = state.settings["bootloop"] == 1,
                            enabled = !state.loading, onCheckedChange = { model.setting("bootloop", if(it) 1 else 0) })
                        OverlayDropdownPreference(title = stringResource(R.string.jwsk_alpha_root_mode),
                            items = listOf(stringResource(R.string.jwsk_alpha_disabled), stringResource(R.string.jwsk_alpha_apps),
                                stringResource(R.string.jwsk_alpha_shell), stringResource(R.string.jwsk_alpha_apps_shell)),
                            selectedIndex = state.settings["root_access"] ?: 3, enabled = !state.loading,
                            onSelectedIndexChange = { model.setting("root_access", it) })
                        OverlayDropdownPreference(title = stringResource(R.string.jwsk_alpha_multiuser),
                            items = listOf(stringResource(R.string.jwsk_alpha_owner), stringResource(R.string.jwsk_alpha_owner_managed), stringResource(R.string.jwsk_alpha_user_managed)),
                            selectedIndex = state.settings["multiuser_mode"] ?: 0, enabled = !state.loading,
                            onSelectedIndexChange = { model.setting("multiuser_mode", it) })
                        OverlayDropdownPreference(title = stringResource(R.string.jwsk_alpha_namespace),
                            items = listOf(stringResource(R.string.jwsk_alpha_global), stringResource(R.string.jwsk_alpha_requester), stringResource(R.string.jwsk_alpha_isolated)),
                            selectedIndex = state.settings["mnt_ns"] ?: 1, enabled = !state.loading,
                            onSelectedIndexChange = { model.setting("mnt_ns", it) })
                    } }
                    item { Text(stringResource(R.string.jwsk_alpha_denylist), style = MiuixTheme.textStyles.title3) }
                    item { SwitchPreference(title = stringResource(R.string.jwsk_alpha_deny_enforce), checked = state.denyEnforced,
                        enabled = !state.loading, onCheckedChange = model::denyEnforce) }
                    item { TextField(value = denyPackage, onValueChange = { denyPackage = it.take(256) }, singleLine = true,
                        label = stringResource(R.string.jwsk_alpha_package), modifier = Modifier.fillMaxWidth(), enabled = !state.loading) }
                    item { TextField(value = denyProcess, onValueChange = { denyProcess = it.take(256) }, singleLine = true,
                        label = stringResource(R.string.jwsk_alpha_process), modifier = Modifier.fillMaxWidth(), enabled = !state.loading) }
                    item { TextButton(text = stringResource(R.string.jwsk_alpha_add_target), enabled = !state.loading && denyPackage.isNotBlank(),
                        onClick = { model.denyTarget(denyPackage.trim(), denyProcess.trim(), true) }) }
                    items(state.denyTargets, key = { "${it.first}|${it.second}" }) { target ->
                        Column { Text("${target.first}\n${target.second}", maxLines = Int.MAX_VALUE)
                            TextButton(text = stringResource(R.string.jwsk_alpha_remove_target), enabled = !state.loading,
                                onClick = { model.denyTarget(target.first, target.second, false) }) }
                    }
                }
                AlphaSection.LOGS -> {
                    item { TextButton(text = stringResource(R.string.jwsk_alpha_export_log), enabled = !state.loading && state.text.isNotBlank(),
                        onClick = { logExport.launch("jwsk-alpha-log.txt") }) }
                    item { TextButton(text = stringResource(R.string.jwsk_clear_output), enabled = !state.loading, onClick = model::clearLog) }
                    if (state.text.isBlank()) item { Text(stringResource(R.string.jwsk_alpha_no_logs)) }
                }
            }
            if (state.text.isNotBlank()) item { SelectionContainer { Text(state.text, maxLines = Int.MAX_VALUE) } }
        }
    }
}
