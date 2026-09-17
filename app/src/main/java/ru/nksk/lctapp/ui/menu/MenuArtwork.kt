package ru.nksk.lctapp.ui.menu

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp

/** Keep the original exports, but decode small icons close to their actual display resolution. */
@Composable
internal fun MenuArtwork(@DrawableRes resource: Int, size: Dp) {
    val resources = LocalResources.current
    val targetPixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val bitmap = remember(resources, resource, targetPixels) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, resource, bounds)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetPixels &&
            bounds.outHeight / (sampleSize * 2) >= targetPixels
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false
        }
        checkNotNull(BitmapFactory.decodeResource(resources, resource, options)).asImageBitmap()
    }
    Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
}
