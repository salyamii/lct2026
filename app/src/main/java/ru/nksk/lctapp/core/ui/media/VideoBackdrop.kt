package ru.nksk.lctapp.core.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One small poster from the bundled video fills its unused margins. Decode and blur run
 * off main, independently of the playback worker, with no second player or frame polling.
 * Software blur also covers API 24–30, where Compose's RenderEffect blur is unavailable.
 */
internal suspend fun loadVideoBackdrop(context: Context, assetPath: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            context.assets.openFd(assetPath).use { source ->
                retriever.setDataSource(source.fileDescriptor, source.startOffset, source.length)
            }
            val frame = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(3_000_000, MediaMetadataRetriever.OPTION_CLOSEST, 90, 160)
            } else retriever.getFrameAtTime(3_000_000, MediaMetadataRetriever.OPTION_CLOSEST)
            frame ?: return@withContext null
            val small = Bitmap.createScaledBitmap(frame, 90, 160, true)
            if (small !== frame) frame.recycle()
            try {
                val blurred = blurredPixels(small)
                Bitmap.createBitmap(blurred, small.width, small.height, Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            } finally {
                small.recycle()
            }
        } catch (error: Exception) {
            // A decorative poster failure must not fail or delay actual video playback.
            Log.w("BundledMedia", "Could not load intro backdrop: $assetPath", error)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

/** Three separable box passes approximate a soft Gaussian; edge pixels extend to the margins. */
private fun blurredPixels(bitmap: Bitmap): IntArray {
    val width = bitmap.width
    val height = bitmap.height
    var source = IntArray(width * height)
    var target = IntArray(source.size)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    val radius = 5
    val samples = radius * 2 + 1
    repeat(3) {
        for (horizontal in listOf(true, false)) {
            for (y in 0 until height) for (x in 0 until width) {
                var red = 0
                var green = 0
                var blue = 0
                for (offset in -radius..radius) {
                    val sx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    val pixel = source[sy * width + sx]
                    red += (pixel ushr 16) and 255
                    green += (pixel ushr 8) and 255
                    blue += pixel and 255
                }
                target[y * width + x] = (255 shl 24) or ((red / samples) shl 16) or
                    ((green / samples) shl 8) or (blue / samples)
            }
            val previous = source
            source = target
            target = previous
        }
    }
    return source
}
