package ru.nksk.lctapp.feature.onboarding.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.cos
import kotlin.math.sin

/** The app supplies its existing resources; this library has no dependency on app or other features. */
@Immutable
data class OnboardingArtwork(
    val background: Int,
    val fox: Int,
    val owl: Int,
    val axolotl: Int,
    val groundShadow: Int,
    val titleFont: FontFamily,
    val bodyFont: FontFamily,
)

internal data class CharacterBitmap(
    val image: ImageBitmap,
    val hit: HitLayer,
    val opaqueLeft: Float,
    val opaqueTop: Float,
    val opaqueRight: Float,
    val opaqueBottom: Float,
)

internal data class CharacterArtwork(val layers: List<CharacterBitmap>, val foxGlow: ImageBitmap)

internal fun loadCharacters(resources: Resources, artwork: OnboardingArtwork): CharacterArtwork {
    fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources, id,
        BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }))
    fun character(bitmap: Bitmap, left: Float, top: Float, width: Float, height: Float): CharacterBitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val alpha = ByteArray(pixels.size) { (pixels[it] ushr 24).toByte() }
        var minX = bitmap.width; var minY = bitmap.height; var maxX = 0; var maxY = 0
        pixels.forEachIndexed { index, pixel ->
            if (pixel ushr 24 >= 32) {
                val x = index % bitmap.width; val y = index / bitmap.width
                minX = minOf(minX, x); maxX = maxOf(maxX, x + 1)
                minY = minOf(minY, y); maxY = maxOf(maxY, y + 1)
            }
        }
        return CharacterBitmap(bitmap.asImageBitmap(),
            HitLayer(left, top, width, height, AlphaMask(bitmap.width, bitmap.height, alpha)),
            left + width * minX / bitmap.width, top + height * minY / bitmap.height,
            left + width * maxX / bitmap.width, top + height * maxY / bitmap.height)
    }
    val owl = decode(artwork.owl)
    val fox = decode(artwork.fox)
    val axolotl = decode(artwork.axolotl)
    // Match Figma's full canvases: owl/fox fit in their frames, axolotl fills its frame.
    return CharacterArtwork(listOf(
        character(owl, -58f, 182.5f, 285f, 285f),
        character(fox, 12f, 87f, 390f, 390f),
        character(axolotl, 154f, 118f, 300f, 404f),
    ), createFoxGlow(fox).asImageBitmap())
}

/** A cached alpha-derived rim/halo, generated off the UI thread; original artwork stays untouched. */
private fun createFoxGlow(fox: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(fox.width, fox.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val alpha = fox.extractAlpha()
    val offset = IntArray(2)
    val blur = alpha.extractAlpha(Paint().apply {
        maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
    }, offset)
    canvas.drawBitmap(blur, offset[0].toFloat(), offset[1].toFloat(), Paint().apply {
        color = 0xffd8ff80.toInt(); this.alpha = 220
    })
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfffff6c8.toInt() }
    repeat(24) {
        val angle = it * Math.PI * 2 / 24
        canvas.drawBitmap(alpha, (cos(angle) * 7).toFloat(), (sin(angle) * 7).toFloat(), rim)
    }
    alpha.recycle()
    blur.recycle()
    return result
}
