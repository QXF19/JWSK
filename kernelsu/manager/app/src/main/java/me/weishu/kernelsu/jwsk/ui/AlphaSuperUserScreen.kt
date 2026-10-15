package me.weishu.kernelsu.jwsk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.jwsk.alpha.*
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.ListPopupDefaults
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AlphaSuperUserScreen(bottomPadding: Dp) {
    val model = viewModel<AlphaManagement>(key = "alpha-policies")
    val state by model.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var search by rememberSaveable { mutableStateOf("") }
    var sortType by rememberSaveable { mutableIntStateOf(0) }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val policyRevision by AlphaPolicyStore.changes.collectAsStateWithLifecycle()
    LaunchedEffect(policyRevision) { model.load(AlphaSection.POLICIES) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.load(AlphaSection.POLICIES) }
    val policies = remember(state.policies) { state.policies.associateBy { it.uid } }
    val filtered = remember(state.apps, policies, search, sortType, reversed, showSystem) {
        val collator = java.text.Collator.getInstance()
        val comparator = Comparator<AlphaApp> { left, right ->
            val grant = (if(policies[right.uid]?.granted == true) 1 else 0) - (if(policies[left.uid]?.granted == true) 1 else 0)
            if(grant != 0) grant else {
                val order = when(sortType) {
                    1 -> collator.compare(left.packages.firstOrNull().orEmpty(), right.packages.firstOrNull().orEmpty())
                    2 -> right.installedAt.compareTo(left.installedAt)
                    3 -> right.updatedAt.compareTo(left.updatedAt)
                    else -> collator.compare(left.label, right.label)
                }
                if(reversed) -order else order
            }
        }
        state.apps.filter { app ->
            (showSystem || !app.system || policies[app.uid]?.granted == true) &&
                (search.isBlank() || app.label.contains(search, ignoreCase = true) || app.packages.any { it.contains(search, ignoreCase = true) })
        }.sortedWith(comparator)
    }
    LazyColumn(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { navigator.push(Route.Sulog) }, modifier = Modifier.size(48.dp)) {
                    Icon(MiuixIcons.Notes, stringResource(R.string.jwsk_logs))
                }
                Row {
                    Box {
                        OverlayListPopup(show = sortMenu, popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd, onDismissRequest = { sortMenu = false }, content = {
                                ListPopupColumn {
                                    listOf(R.string.sort_by_name, R.string.sort_by_package_name, R.string.sort_by_install_time,
                                        R.string.sort_by_update_time, R.string.sort_reverse).forEachIndexed { index, label ->
                                        DropdownImpl(text = stringResource(label), optionSize = 5, index = index,
                                            isSelected = if(index == 4) reversed else sortType == index,
                                            onSelectedIndexChange = { if(index == 4) reversed = !reversed else sortType = index; sortMenu = false })
                                    }
                                }
                            })
                        IconButton(onClick = { sortMenu = true }, modifier = Modifier.size(48.dp)) { Icon(MiuixIcons.Sort, stringResource(R.string.menu_sort)) }
                    }
                    Box {
                        OverlayListPopup(show = moreMenu, popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd, onDismissRequest = { moreMenu = false }, content = {
                                ListPopupColumn {
                                    DropdownImpl(text = stringResource(R.string.show_system_apps), optionSize = 2, index = 0,
                                        isSelected = showSystem, onSelectedIndexChange = { showSystem = !showSystem; moreMenu = false })
                                    DropdownImpl(text = stringResource(R.string.jwsk_retry), optionSize = 2, index = 1,
                                        isSelected = false, onSelectedIndexChange = { model.load(); moreMenu = false })
                                }
                            })
                        IconButton(onClick = { moreMenu = true }, modifier = Modifier.size(48.dp)) { Icon(MiuixIcons.MoreCircle, stringResource(R.string.jwsk_alpha_more)) }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.superuser), style = MiuixTheme.textStyles.title1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        item { InputField(query = search, onQueryChange = { search = it.take(256) },
            label = stringResource(R.string.jwsk_alpha_search), modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(MiuixIcons.Basic.Search, null, modifier = Modifier.size(44.dp).padding(start = 16.dp, end = 8.dp),
                tint = MiuixTheme.colorScheme.onSurfaceContainerHigh) },
            onSearch = {}, expanded = false, onExpandedChange = {}) }
        if(state.loading) item { Text(stringResource(R.string.jwsk_loading), modifier = Modifier.padding(12.dp)) }
        if(state.failed) item { Text(stringResource(R.string.jwsk_alpha_manage_failed), modifier = Modifier.padding(12.dp)) }
        if(!state.loading && !state.available) item { Text(stringResource(R.string.jwsk_root_unavailable), modifier = Modifier.padding(12.dp)) }
        items(filtered, key = { it.uid }) { app ->
            val label = app.label.ifBlank { stringResource(R.string.jwsk_alpha_uninstalled) }
            Card(modifier = Modifier.fillMaxWidth(), onClick = { navigator.push(Route.AlphaPolicy(app.uid)) },
                showIndication = true, insideMargin = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    app.applicationInfo?.let { AppIconImage(applicationInfo = it, label = label, modifier = Modifier.size(48.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(app.packages.joinToString("\n"), maxLines = 2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
                    }
                    if(policies[app.uid]?.granted == true) StatusTag("ROOT",
                        backgroundColor = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        contentColor = MiuixTheme.colorScheme.onTertiaryContainer)
                    Icon(MiuixIcons.Basic.ArrowRight, null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun AlphaPolicyScreen(uid: Int) {
    val model = viewModel<AlphaManagement>(key = "alpha-policy-$uid")
    val state by model.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    LaunchedEffect(uid) { model.load(AlphaSection.POLICIES) }
    val app = state.apps.firstOrNull { it.uid == uid }
    val policy = state.policies.firstOrNull { it.uid == uid } ?: AlphaPolicy(uid, 0, 0, true, true)
    LazyColumn(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).systemBarsPadding(),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(text = stringResource(R.string.jwsk_back), onClick = { navigator.pop() }) }
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            app?.applicationInfo?.let { AppIconImage(applicationInfo = it, label = app.label, modifier = Modifier.size(56.dp)) }
            Text(app?.label?.ifBlank { stringResource(R.string.jwsk_alpha_uninstalled) }.orEmpty(), maxLines = 3,
                modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.title2)
        } }
        item { SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(app?.packages?.joinToString("\n").orEmpty(), maxLines = Int.MAX_VALUE)
            Text("UID $uid")
        } } }
        if((app?.packages?.size ?: 0) > 1) item { Text(stringResource(R.string.jwsk_alpha_shared_uid)) }
        if(state.failed) item { Text(stringResource(R.string.jwsk_alpha_manage_failed)) }
        item { Card {
            SwitchPreference(title = stringResource(R.string.jwsk_alpha_allow), checked = policy.granted,
                enabled = state.available && !state.loading, onCheckedChange = { model.policy(policy.copy(policy = if(it) 2 else 1, until = 0)) })
            SwitchPreference(title = stringResource(R.string.jwsk_logs), checked = policy.logging,
                enabled = state.available && !state.loading, onCheckedChange = { model.policy(policy.copy(logging = it)) })
            SwitchPreference(title = stringResource(R.string.jwsk_alpha_notification), checked = policy.notification,
                enabled = state.available && !state.loading, onCheckedChange = { model.policy(policy.copy(notification = it)) })
        } }
        item { TextButton(text = stringResource(R.string.jwsk_alpha_forget), enabled = state.available && !state.loading,
            onClick = { model.removePolicy(uid) }) }
    }
}
