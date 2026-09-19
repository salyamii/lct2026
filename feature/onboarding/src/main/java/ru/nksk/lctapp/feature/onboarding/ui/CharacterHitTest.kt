package ru.nksk.lctapp.feature.onboarding.ui

/** Alpha only: transparent canvas and selection glow never become touch targets. */
internal class AlphaMask(val width: Int, val height: Int, private val alpha: ByteArray) {
    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height &&
        (alpha[y * width + x].toInt() and 255) >= 32
}

internal data class HitLayer(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val mask: AlphaMask,
) {
    fun contains(x: Float, y: Float): Boolean {
        if (width <= 0 || height <= 0 || x < left || y < top || x >= left + width || y >= top + height) return false
        return mask.contains(((x - left) / width * mask.width).toInt(), ((y - top) / height * mask.height).toInt())
    }
}

/** Layers use painting order; the last opaque pixel receives the tap. */
internal fun hitCharacter(layers: List<HitLayer>, x: Float, y: Float): Int? =
    layers.indices.reversed().firstOrNull { layers[it].contains(x, y) }
