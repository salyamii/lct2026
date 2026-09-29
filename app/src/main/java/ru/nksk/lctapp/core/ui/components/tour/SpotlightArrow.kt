package ru.nksk.lctapp.core.ui.components.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** Orthogonal connector: prefer a straight gap, otherwise use a clear corridor beside highlights. */
internal fun spotlightArrowRoute(card: Rect, target: Rect, obstacles: List<Rect>, viewport: Rect,
    clearance: Float, cornerInset: Float): List<Offset> {
    val above = card.bottom <= target.top
    if (!above && card.top < target.bottom) return emptyList()
    val edgeY = if (above) card.bottom else card.top
    val left = card.left + minOf(cornerInset, card.width / 2)
    val right = card.right - minOf(cornerInset, card.width / 2)
    val blocked = obstacles.map { it.inflate(clearance) }
    fun clear(points: List<Offset>): Boolean = points.all {
        it.x >= viewport.left && it.x <= viewport.right && it.y >= viewport.top && it.y <= viewport.bottom
    } && points.zipWithNext().all { (a, b) -> blocked.none { segmentCrossesRect(a, b, it) } }
    val end = Offset(target.center.x, if (above) target.top else target.bottom)
    val start = Offset(end.x.coerceIn(left, right), edgeY)
    if (start.x == end.x && clear(listOf(start, end))) return listOf(start, end)

    val lanes = (blocked.flatMap { listOf(it.left, it.right) } +
        listOf(target.left - clearance, target.right + clearance, viewport.left + clearance, viewport.right - clearance)).distinct()
    return lanes.mapNotNull { x ->
        if (x > target.left - clearance && x < target.right + clearance) return@mapNotNull null
        val tip = Offset(if (x < target.left) target.left else target.right, target.center.y)
        val entry = Offset(x.coerceIn(left, right), edgeY)
        val points = if (entry.x == x) listOf(entry, Offset(x, tip.y), tip) else {
            val departureY = edgeY + if (above) clearance else -clearance
            listOf(entry, Offset(entry.x, departureY), Offset(x, departureY), Offset(x, tip.y), tip)
        }
        points.takeIf(::clear)
    }.minByOrNull { points ->
        // Prefer a clean side corridor over a slightly shorter route with extra elbows.
        points.zipWithNext().sumOf { (a, b) -> (abs(a.x - b.x) + abs(a.y - b.y)).toDouble() } +
            (points.size - 2) * clearance * 2
    }
        ?: emptyList()
}

/** Routes run horizontally or vertically; touching a clearance boundary is allowed. */
internal fun segmentCrossesRect(a: Offset, b: Offset, rect: Rect): Boolean = when {
    a.x == b.x -> a.x > rect.left && a.x < rect.right && maxOf(a.y, b.y) > rect.top && minOf(a.y, b.y) < rect.bottom
    a.y == b.y -> a.y > rect.top && a.y < rect.bottom && maxOf(a.x, b.x) > rect.left && minOf(a.x, b.x) < rect.right
    else -> true
}
