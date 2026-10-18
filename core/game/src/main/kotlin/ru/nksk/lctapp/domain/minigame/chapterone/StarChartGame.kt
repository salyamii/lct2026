package ru.nksk.lctapp.domain.minigame.chapterone

data class StarChartPoint(val x: Float, val y: Float)

data class StarChartState(val joined: Set<Int> = emptySet(), val selected: Int? = null) {
    val complete: Boolean get() = joined == StarChartGame.edges.indices.toSet()
}

/** Drawing rules shared by the practice constellation and the story's route map. */
object StarChartGame {
    val stars = listOf(
        StarChartPoint(.16f, .32f), StarChartPoint(.35f, .19f),
        StarChartPoint(.58f, .30f), StarChartPoint(.81f, .20f),
        StarChartPoint(.73f, .58f), StarChartPoint(.46f, .72f),
        StarChartPoint(.21f, .62f),
    )
    val edges = listOf(0 to 1, 1 to 2, 2 to 3, 2 to 4, 4 to 5, 5 to 6, 6 to 0)

    fun select(state: StarChartState, star: Int): StarChartState = selectChartPoint(state, star, stars.size, edges)

    fun restore(joined: List<Int>, selected: Int?): StarChartState = restoreChart(joined, selected, stars.size, edges.size)
}

/** Seven missing stretches of one road, from the pier across the bridge to the tower. */
object RouteMapGame {
    val points = listOf(
        StarChartPoint(.17f, .82f), StarChartPoint(.31f, .70f),
        StarChartPoint(.25f, .52f), StarChartPoint(.43f, .46f),
        StarChartPoint(.60f, .46f), StarChartPoint(.71f, .34f),
        StarChartPoint(.62f, .19f), StarChartPoint(.82f, .14f),
    )
    val edges = (0 until points.lastIndex).map { it to it + 1 }
    fun select(state: StarChartState, point: Int): StarChartState = selectChartPoint(state, point, points.size, edges)
    fun restore(joined: List<Int>, selected: Int?): StarChartState = restoreChart(joined, selected, points.size, edges.size)
}

private fun selectChartPoint(state: StarChartState, star: Int, pointCount: Int, edges: List<Pair<Int, Int>>): StarChartState {
        if (state.complete || star !in 0 until pointCount) return state
        val previous = state.selected ?: return state.copy(selected = star)
        if (star == previous) return state
        val edge = edges.indexOfFirst { (a, b) -> (a == previous && b == star) || (a == star && b == previous) }
        return if (edge < 0) state.copy(selected = star) else {
            val joined = state.joined + edge
            state.copy(joined = joined, selected = if (joined.size == edges.size) null else star)
        }
}

private fun restoreChart(joined: List<Int>, selected: Int?, pointCount: Int, edgeCount: Int): StarChartState = StarChartState(
    joined.filter { it in 0 until edgeCount }.toSet(), selected?.takeIf { it in 0 until pointCount },
).let { if (it.complete) it.copy(selected = null) else it }
