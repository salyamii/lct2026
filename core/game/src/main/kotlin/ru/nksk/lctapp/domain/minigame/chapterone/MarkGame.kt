package ru.nksk.lctapp.domain.minigame.chapterone

import kotlin.math.cos
import kotlin.math.sin

data class MarkPoint(val x: Float, val y: Float)
data class MarkState(val cleaned: Set<Int> = emptySet(), val stroke: Int = 0, val point: Int = 0) {
    val readyToTrace: Boolean get() = cleaned.size == MarkGame.dust.size
    val complete: Boolean get() = stroke == MarkGame.strokes.size
}

/** Generous checkpoints, no timer, penalty, money or handwriting grade. */
object MarkGame {
    val dust = listOf(
        MarkPoint(.25f, .29f), MarkPoint(.49f, .26f), MarkPoint(.74f, .30f),
        MarkPoint(.30f, .50f), MarkPoint(.56f, .48f), MarkPoint(.74f, .55f),
        MarkPoint(.25f, .70f), MarkPoint(.49f, .73f), MarkPoint(.73f, .74f),
    )
    val strokes: List<List<MarkPoint>> = listOf(
        (0..10).map { index ->
            val angle = -Math.PI / 2 + index * Math.PI / 5
            val radius = if (index % 2 == 0) .15 else .065
            MarkPoint(.5f + (cos(angle) * radius).toFloat(), .34f + (sin(angle) * radius).toFloat())
        },
        listOf(MarkPoint(.43f, .48f), MarkPoint(.38f, .62f), MarkPoint(.32f, .76f)),
        listOf(MarkPoint(.50f, .48f), MarkPoint(.50f, .63f), MarkPoint(.50f, .79f)),
        listOf(MarkPoint(.57f, .48f), MarkPoint(.62f, .62f), MarkPoint(.68f, .76f)),
    )

    fun touch(state: MarkState, x: Float, y: Float): MarkState {
        if (!x.isFinite() || !y.isFinite() || state.complete) return state
        if (!state.readyToTrace) {
            val cleared = dust.indices.filter { near(dust[it], x, y, .20f) }
            return state.copy(cleaned = state.cleaned + cleared)
        }
        val points = strokes[state.stroke]
        if (!near(points[state.point], x, y, .095f)) return state
        return if (state.point + 1 == points.size) state.copy(stroke = state.stroke + 1, point = 0)
        else state.copy(point = state.point + 1)
    }

    fun restore(cleaned: List<Int>, stroke: Int, point: Int): MarkState {
        val safeCleaned = cleaned.filter { it in dust.indices }.toSet()
        if (safeCleaned.size != dust.size) return MarkState(safeCleaned)
        val safeStroke = stroke.coerceIn(0, strokes.size)
        return MarkState(safeCleaned, safeStroke,
            if (safeStroke == strokes.size) 0 else point.coerceIn(0, strokes[safeStroke].lastIndex))
    }

    private fun near(p: MarkPoint, x: Float, y: Float, radius: Float): Boolean =
        (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y) <= radius * radius
}
