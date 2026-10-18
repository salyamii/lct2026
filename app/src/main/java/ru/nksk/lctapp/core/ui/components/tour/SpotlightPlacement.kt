package ru.nksk.lctapp.core.ui.components.tour

import androidx.compose.ui.geometry.Rect

internal data class SpotlightCardRegion(val top: Float, val height: Float)

/** Prefer a free vertical interval shared by every highlight, then prioritize the arrow target. */
internal fun spotlightCardRegion(height: Float, highlights: List<Rect>, pointer: Rect?, gap: Float,
    minimumHeight: Float, belowTarget: Boolean = false): SpotlightCardRegion {
    if (belowTarget && pointer != null) {
        val top = maxOf(gap, pointer.bottom + gap)
        val available = height - gap - top
        // A short card scrolls as a whole instead of covering the character.
        if (available > 0f) return SpotlightCardRegion(top, available)
    }
    fun regions(rects: List<Rect>): List<SpotlightCardRegion> = buildList {
        var cursor = gap
        rects.sortedBy { it.top }.forEach { rect ->
            val end = (rect.top - gap).coerceIn(gap, (height - gap).coerceAtLeast(gap))
            if (end > cursor) add(SpotlightCardRegion(cursor, end - cursor))
            cursor = maxOf(cursor, rect.bottom + gap)
        }
        if (height - gap > cursor) add(SpotlightCardRegion(cursor, height - gap - cursor))
    }
    val free = regions(highlights).maxByOrNull { it.height }
    if (free != null && free.height >= minimumHeight) return free
    val fallback = regions(listOfNotNull(pointer)).maxByOrNull { it.height }
    if (fallback != null && fallback.height >= minimumHeight) return fallback
    if (belowTarget && fallback != null) return fallback
    return SpotlightCardRegion(gap, (height - 2 * gap).coerceAtLeast(1f))
}
