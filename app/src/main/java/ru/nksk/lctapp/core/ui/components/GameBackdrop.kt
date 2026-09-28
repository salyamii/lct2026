package ru.nksk.lctapp.core.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

/** Only the decorative background is softened; foreground artwork and controls are separate. */
@Composable
internal fun GameBackdrop(@DrawableRes resource: Int, blurred: Boolean, modifier: Modifier = Modifier) {
    if (!blurred || LocalInspectionMode.current) {
        GameArtwork(resource, null, if (blurred) modifier.blur(12.dp) else modifier,
            contentScale = ContentScale.Crop)
        return
    }
    val context = LocalContext.current
    val request = remember(context, resource) {
        ImageRequest.Builder(context).data(resource)
            // Keep the entire image's aspect ratio. Crop happens only when it is displayed.
            .size(256, 256).scale(Scale.FIT).precision(Precision.EXACT)
            .allowHardware(false)
            .transformations(SoftBackdropTransformation)
            .transformationDispatcher(Dispatchers.Default)
            .build()
    }
    key(request) {
        var settled by remember { mutableStateOf(false) }
        AsyncImage(request, null, modifier, contentScale = ContentScale.Crop,
            onSuccess = { settled = true }, onError = { settled = true })
        ReportGameArtworkLoad(request, settled)
    }
}

/** A bounded software transform works on API 24+ and shares the existing Coil memory cache. */
private object SoftBackdropTransformation : Transformation {
    override val cacheKey = "soft-backdrop-v1:256:radius6:passes3"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // Some decoders sample in powers of two. Bound the transform's working set too.
        val scale = minOf(1f, 256f / maxOf(input.width, input.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(input,
            (input.width * scale).roundToInt().coerceAtLeast(1),
            (input.height * scale).roundToInt().coerceAtLeast(1), true) else input
        try {
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            val blurred = blurBackdropPixels(pixels, small.width, small.height, radius = 6)
            return Bitmap.createBitmap(blurred, small.width, small.height, Bitmap.Config.ARGB_8888)
        } finally {
            // Never mutate or recycle the input: another request can still own it.
            if (small !== input) small.recycle()
        }
    }
}
