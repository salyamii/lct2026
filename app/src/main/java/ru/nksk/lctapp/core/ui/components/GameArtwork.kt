package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale

/** Local raster art, decoded off the UI thread at its displayed size and cached across screens. */
@Composable
internal fun GameArtwork(
    @DrawableRes resource: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (LocalInspectionMode.current) {
        Image(painterResource(resource), contentDescription, modifier, alignment, contentScale)
    } else {
        AsyncImage(resource, contentDescription, modifier = modifier,
            alignment = alignment, contentScale = contentScale)
    }
}

/** One painter serves both the menu backdrop and its frosted panel, at the full scene size. */
@Composable
internal fun rememberScenePainter(@DrawableRes resource: Int, size: DpSize): Painter {
    if (LocalInspectionMode.current) return painterResource(resource)
    val context = LocalContext.current
    val width = with(LocalDensity.current) { size.width.roundToPx().coerceAtLeast(1) }
    val height = with(LocalDensity.current) { size.height.roundToPx().coerceAtLeast(1) }
    val request = remember(context, resource, width, height) {
        ImageRequest.Builder(context).data(resource).size(width, height).scale(Scale.FILL).build()
    }
    return rememberAsyncImagePainter(request, contentScale = ContentScale.FillBounds)
}
