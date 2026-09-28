package ru.nksk.lctapp.feature.map.ui

internal data class MapCanvasSize(val width: Float, val height: Float, val scrollable: Boolean)

/** Dimensions in dp; image and landmark coordinates always share this same canvas. */
internal fun mapCanvasSize(width: Float, height: Float, fontScale: Float): MapCanvasSize {
    val aspectRatio = 853f / 1844f
    val fullHeight = width / aspectRatio
    // Below this height, fitting the map crowds its nine labels and touch targets.
    val minimumReadableHeight = 560f * fontScale.coerceAtLeast(1f)
    val scrollable = fullHeight > height && height < minimumReadableHeight
    val canvasWidth = if (scrollable) width else minOf(width, height * aspectRatio)
    return MapCanvasSize(canvasWidth, canvasWidth / aspectRatio, scrollable)
}
