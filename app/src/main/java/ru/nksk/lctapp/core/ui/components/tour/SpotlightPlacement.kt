package ru.nksk.lctapp.core.ui.components.tour

import androidx.compose.ui.geometry.Rect

internal data class SpotlightCardRegion(val top: Float, val height: Float)

/** Prefer a free vertical interval shared by every highlight, then prioritize the arrow target. */
internal fun spotlightCardRegion(height: Float, highlights: List<Rect>, pointer: Rect?, gap: Float,
    minimumHeight: Float): SpotlightCardRegion {
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
    return SpotlightCardRegion(gap, (height - 2 * gap).coerceAtLeast(1f))
}
