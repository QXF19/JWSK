package me.weishu.kernelsu.jwsk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import me.weishu.kernelsu.ui.component.liquid.lens
import me.weishu.kernelsu.ui.component.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Reuses the approved text-free artwork; all controls remain native and separate. */
@Composable
internal fun JwskArtworkPage(compact: Boolean = false, content: @Composable BoxScope.(Dp, LayerBackdrop?) -> Unit) {
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val dark = isInDarkTheme()
    val colors = MiuixTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val header = if (compact) (maxWidth * (295f / 851f)).coerceAtMost(180.dp)
            else (maxWidth * (554f / 851f)).coerceAtMost(300.dp)
        Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            Image(painterResource(R.drawable.jwsk_furry_field), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
            if (dark) Box(Modifier.fillMaxSize().background(Color(0xFF04172D).copy(alpha = 0.70f)))
            Image(painterResource(R.drawable.jwsk_furry_header), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(header).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                })
        }
        // System glyphs need a stable backing even when artwork is highly varied.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(if (dark) colors.surface else Color.White.copy(alpha = 0.94f)))
        content(header, backdrop)
    }
}

@Composable
internal fun JwskGlassPanel(modifier: Modifier = Modifier, backdrop: LayerBackdrop?, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val surface = MiuixTheme.colorScheme.surface
    val isDarkSurface = isInDarkTheme()
    Column(modifier.clip(shape).then(if (backdrop != null) Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(12.dp.toPx(), 12.dp.toPx())
            lens(refractionHeight = 8.dp.toPx(), refractionAmount = 8.dp.toPx())
        },
        onDrawSurface = { drawRect(surface.copy(alpha = if (isDarkSurface) 0.42f else 0.62f)) },
    ) else Modifier.background(surface)), content = content)
}
