package me.weishu.kernelsu.jwsk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Browsing is always available; Root-only controls require a recognized backend. */
@Composable
fun RootUnavailableScreen(title: String) {
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)
        .statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, fontSize = 24.sp)
        Text(stringResource(R.string.jwsk_root_unavailable), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
