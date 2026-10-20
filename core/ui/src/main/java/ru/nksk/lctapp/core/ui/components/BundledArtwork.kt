package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Bundled raster art decoded at measured bounds through the application's shared Coil loader. */
@Composable
fun BundledArtwork(
    @DrawableRes resource: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
    fadeIn: Boolean = true,
    onLoadSettled: (Boolean) -> Unit = {},
) {
    if (LocalInspectionMode.current) {
        Image(painterResource(resource), contentDescription, modifier, alignment, contentScale,
            colorFilter = colorFilter)
        return
    }
    val context = LocalContext.current
    val request = remember(context, resource, fadeIn) {
        ImageRequest.Builder(context).data(resource).crossfade(if (fadeIn) 180 else 0).build()
    }
    // Keep the old image during a local replacement, including when a decode fails.
    var previous by remember { mutableStateOf<Painter?>(null) }
    AsyncImage(request, contentDescription, modifier,
        placeholder = previous, error = previous,
        onLoading = { onLoadSettled(false) },
        onSuccess = { previous = it.painter; onLoadSettled(true) },
        onError = { onLoadSettled(true) },
        alignment = alignment, contentScale = contentScale, colorFilter = colorFilter)
}
