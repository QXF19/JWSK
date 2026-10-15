package me.weishu.kernelsu.jwsk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import me.weishu.kernelsu.R

/**
 * Approved text-free plate; the app title and all controls are separate native elements.
 * Source plate: assets/plates/furry-art.png, copied byte-for-byte to drawable-nodpi/jwsk_furry_header.png.
 * Android references the packaged image through R.drawable rather than a filesystem URL.
 */
@Composable
fun JwskHomeArtwork(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.jwsk_furry_header),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().aspectRatio(851f / 554f),
    )
}
