package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.delay

/** A ready painter can enter an animation without another asynchronous load inside its frame. */
internal data class GameArtworkLoad(val painter: Painter?, val settled: Boolean)

@Composable
internal fun rememberGameArtworkLoad(
    @DrawableRes resource: Int,
    size: DpSize,
    participateInScene: Boolean = true,
    imageLoader: ImageLoader? = null,
): GameArtworkLoad {
    if (LocalInspectionMode.current) return GameArtworkLoad(painterResource(resource), true)
    val context = LocalContext.current
    val loader = imageLoader ?: context.imageLoader
    val width = with(LocalDensity.current) { size.width.roundToPx().coerceAtLeast(1) }
    val height = with(LocalDensity.current) { size.height.roundToPx().coerceAtLeast(1) }
    // A panel animation can change the character's bounds every frame. Keep its decode
    // budget while shrinking; upgrade only after growth settles, preserving the ready frame.
    var decodeSize by remember(context, resource) { mutableStateOf(IntSize(width, height)) }
    LaunchedEffect(context, resource, width, height) {
        if (width > decodeSize.width || height > decodeSize.height) {
            delay(220)
            decodeSize = IntSize(maxOf(width, decodeSize.width), maxOf(height, decodeSize.height))
        }
    }
    val request = remember(context, resource, decodeSize) {
        ImageRequest.Builder(context).data(resource).size(decodeSize.width, decodeSize.height).scale(Scale.FIT).build()
    }
    // Explicit measured bounds avoid waiting for this painter to be drawn before starting decode.
    // Resource changes need a fresh painter, but resizing the same image must retain it.
    val painter = key(context, resource, loader) {
        rememberAsyncImagePainter(request, loader, contentScale = ContentScale.Fit)
    }
    val loaded = painter.state
    var previous by remember(context, resource, loader) { mutableStateOf<Painter?>(null) }
    val readyPainter = (loaded as? AsyncImagePainter.State.Success)?.painter
    SideEffect { if (readyPainter != null) previous = readyPainter }
    val displayed = readyPainter ?: previous
    val settled = displayed != null || loaded is AsyncImagePainter.State.Error
    ReportGameArtworkLoad(request, settled, participateInScene)
    return GameArtworkLoad(displayed, settled)
}

/** Local raster art, decoded off the UI thread at its displayed size and cached across screens. */
@Composable
internal fun GameArtwork(
    @DrawableRes resource: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    revealWithScene: Boolean = true,
    colorFilter: ColorFilter? = null,
) {
    if (LocalInspectionMode.current) {
        Image(painterResource(resource), contentDescription, modifier, alignment, contentScale,
            colorFilter = colorFilter)
    } else {
        var settled by remember(resource) { mutableStateOf(false) }
        BundledArtwork(resource, contentDescription,
            modifier = modifier.then(if (revealWithScene) gameArtworkVisibility(retainKey = resource) else Modifier),
            fadeIn = !revealWithScene || !hasGameArtworkScene(),
            onLoadSettled = { settled = it },
            alignment = alignment, contentScale = contentScale, colorFilter = colorFilter)
        ReportGameArtworkLoad(resource, settled)
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
        ImageRequest.Builder(context).data(resource).size(width, height).scale(Scale.FILL)
            .crossfade(180).build()
    }
    var settled by remember(request) { mutableStateOf(false) }
    var previous by remember { mutableStateOf<Painter?>(null) }
    val painter = rememberAsyncImagePainter(request, placeholder = previous, error = previous,
        onLoading = { settled = false },
        onSuccess = { previous = it.painter; settled = true },
        onError = { settled = true },
        contentScale = ContentScale.FillBounds)
    ReportGameArtworkLoad(request, settled)
    return painter
}
