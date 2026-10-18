package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.StarChartGame
import ru.nksk.lctapp.domain.minigame.chapterone.StarChartState
import ru.nksk.lctapp.domain.minigame.chapterone.RouteMapGame
import kotlin.math.ceil

data class StarChartUiState(val game: StarChartState = StarChartState())
sealed interface StarChartAction {
    data class Select(val star: Int, val routeMap: Boolean = false) : StarChartAction
    data object Restart : StarChartAction
}

@HiltViewModel
class StarChartViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(StarChartUiState(
        if (savedState.get<Boolean>("chart.route") == true) RouteMapGame.restore(
            savedState.get<IntArray>("chart.lines")?.toList().orEmpty(), savedState["chart.selected"])
        else StarChartGame.restore(savedState.get<IntArray>("chart.lines")?.toList().orEmpty(), savedState["chart.selected"]),
    ))
    val uiState = mutableState.asStateFlow()
    fun onAction(action: StarChartAction) {
        val next = when (action) {
            is StarChartAction.Select -> {
                savedState["chart.route"] = action.routeMap
                if (action.routeMap) RouteMapGame.select(mutableState.value.game, action.star)
                else StarChartGame.select(mutableState.value.game, action.star)
            }
            StarChartAction.Restart -> StarChartState()
        }
        if (next == mutableState.value.game) return
        mutableState.value = StarChartUiState(next)
        savedState["chart.lines"] = next.joined.toIntArray()
        savedState["chart.selected"] = next.selected
    }
}

@Composable
fun StarChartGameEntry(
    onExit: () -> Unit,
    onCompleted: ((StarChartState) -> Unit)? = null,
    routeMap: Boolean = false,
    interactionBlocked: Boolean = false,
) {
    val viewModel = hiltViewModel<StarChartViewModel>(key = if (routeMap) "route_map" else "star_chart")
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = interactionBlocked) {}
    StarChartGameScreen(state,
        onAction = { if (!interactionBlocked) viewModel.onAction(it) },
        onExit = { if (!interactionBlocked) onExit() },
        onCompleted = onCompleted?.let { complete -> {
            if (!interactionBlocked && viewModel.uiState.value.game.complete) complete(viewModel.uiState.value.game)
        } }, routeMap = routeMap, interactionBlocked = interactionBlocked)
}

@Composable
fun StarChartGameScreen(
    state: StarChartUiState,
    onAction: (StarChartAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: (() -> Unit)? = null,
    routeMap: Boolean = false,
    interactionBlocked: Boolean = false,
) {
    val game = state.game
    val points = if (routeMap) RouteMapGame.points else StarChartGame.stars
    ChapterGameScaffold(
        title = if (routeMap) "Путь к башне" else "Карта звёзд",
        instruction = if (routeMap) {
            if (game.complete) "Путь отмечен: от причала через мост к башне."
            else "Соедини точки дороги от причала через мост к башне."
        } else if (game.complete) "Карта готова. Теперь созвездие легче найти в небе."
        else "Соедини звёзды, как на образце: веди пальцем или касайся по очереди.",
        onExit = onExit,
        footer = {
            if (game.complete && onCompleted != null) {
                GameActionButton("Продолжить", onCompleted, interactionBlocked = interactionBlocked)
            } else if (game.complete) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameActionButton("Ещё раз", { onAction(StarChartAction.Restart) }, Modifier.weight(1f))
                GameActionButton("К играм", onExit, Modifier.weight(1f))
            } else GameActionButton("Стереть линии", { onAction(StarChartAction.Restart) })
        },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Canvas(Modifier.size(100.dp)) {
                    if (routeMap) drawRouteMap(RouteMapGame.edges.indices.toSet(), null)
                    else drawChart(StarChartGame.edges.indices.toSet(), null, true)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(if (routeMap) "Старая карта" else "Образец", color = ClueCream, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
                    Text("${game.joined.size} из ${StarChartGame.edges.size} линий", color = ClueGold,
                        fontFamily = Nunito, fontSize = 14.sp)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                val currentAction by rememberUpdatedState(onAction)
                Canvas(Modifier.size(side).pointerInput(routeMap, interactionBlocked) {
                    if (interactionBlocked) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var previous = -1
                        var previousPosition = down.position
                        fun select(position: Offset) {
                            val radius = maxOf(25.dp.toPx(), size.width * .075f)
                            val hit = points.indexOfFirst {
                                (Offset(it.x * size.width, it.y * size.height) - position).getDistance() <= radius
                            }
                            if (hit >= 0 && hit != previous) {
                                previous = hit
                                currentAction(StarChartAction.Select(hit, routeMap))
                            }
                        }
                        select(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                val delta = change.position - previousPosition
                                val steps = ceil(delta.getDistance() / maxOf(1f, size.width * .025f)).toInt()
                                    .coerceIn(1, 64)
                                for (step in 1..steps) select(previousPosition + delta * (step.toFloat() / steps))
                                previousPosition = change.position
                            }
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                }.semantics {
                    contentDescription = (if (routeMap) "Дорога к башне" else "Карта созвездия") + ". Соединено ${game.joined.size} из 7 линий"
                    customActions = if (interactionBlocked) emptyList() else points.indices.map { index ->
                        CustomAccessibilityAction(if (routeMap) "Точка дороги ${index + 1}" else "Звезда ${index + 1}") {
                            onAction(StarChartAction.Select(index, routeMap)); true
                        }
                    }
                }) {
                    if (routeMap) drawRouteMap(game.joined, game.selected)
                    else drawChart(game.joined, game.selected, false)
                }
            }
        }
    }
}

internal val ClueGold = Color(0xFFE7BD68)
internal val ClueCream = Color(0xFFFFF0C8)
internal val ClueInk = Color(0xFF30233C)

private fun DrawScope.drawRouteMap(joined: Set<Int>, selected: Int?) {
    drawClueBoard(parchment = true)
    val u = size.minDimension
    fun point(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    val river = Path().apply {
        moveTo(size.width * .48f, size.height * .06f)
        cubicTo(size.width * .63f, size.height * .22f, size.width * .40f, size.height * .43f,
            size.width * .53f, size.height * .61f)
        cubicTo(size.width * .64f, size.height * .80f, size.width * .39f, size.height * .85f,
            size.width * .32f, size.height * .94f)
    }
    drawPath(river, Color(0xFF7FA9AB), style = Stroke(u * .055f, cap = StrokeCap.Round))
    drawPath(river, Color(0xFFBCD2C5), style = Stroke(u * .027f, cap = StrokeCap.Round))
    // A few terrain marks frame the road without obscuring its generous touch targets.
    listOf(.16f to .17f, .28f to .22f, .80f to .69f).forEach { (x, y) ->
        val mountain = Path().apply {
            moveTo((x - .065f) * size.width, (y + .055f) * size.height)
            lineTo(x * size.width, (y - .045f) * size.height)
            lineTo((x + .065f) * size.width, (y + .055f) * size.height)
        }
        drawPath(mountain, Color(0xFF9B8957), style = Stroke(u * .004f, cap = StrokeCap.Round))
    }
    val points = RouteMapGame.points.map { point(it.x, it.y) }
    RouteMapGame.edges.forEachIndexed { index, (a, b) ->
        drawLine(Color(0xFF927951).copy(alpha = .35f), points[a], points[b], u * .007f,
            cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * .012f, u * .015f)))
        if (index in joined) {
            drawLine(Color(0xFFB9792F).copy(alpha = .2f), points[a], points[b], u * .035f, StrokeCap.Round)
            drawLine(Color(0xFF68452F), points[a], points[b], u * .011f, StrokeCap.Round)
        }
    }
    // A timber bridge spans the river exactly where the road crosses it.
    val bridgeLeft = .47f * size.width
    val bridgeTop = .41f * size.height
    drawRoundRect(Color(0xFFB58D53), Offset(bridgeLeft, bridgeTop), Size(u * .10f, u * .10f), CornerRadius(u * .009f))
    repeat(5) { i ->
        val x = bridgeLeft + u * (.01f + i * .02f)
        drawLine(Color(0xFF6B4B2F), Offset(x, bridgeTop), Offset(x, bridgeTop + u * .10f), u * .003f)
    }
    drawLine(Color(0xFF6B4B2F), Offset(bridgeLeft, bridgeTop), Offset(bridgeLeft + u * .10f, bridgeTop), u * .007f)
    drawLine(Color(0xFF6B4B2F), Offset(bridgeLeft, bridgeTop + u * .10f),
        Offset(bridgeLeft + u * .10f, bridgeTop + u * .10f), u * .007f)
    // Pier and tower are drawn as landmarks, not additional controls.
    val pier = points.first()
    drawRoundRect(Color(0xFF997044), pier + Offset(-u * .08f, u * .045f), Size(u * .16f, u * .045f), CornerRadius(u * .008f))
    repeat(4) { i -> drawLine(Color(0xFF5F492F), pier + Offset(u * (-.06f + .04f * i), u * .03f),
        pier + Offset(u * (-.06f + .04f * i), u * .11f), u * .009f) }
    val tower = points.last()
    drawRoundRect(Color(0xFF8F8A77), tower + Offset(-u * .035f, -u * .072f), Size(u * .07f, u * .08f), CornerRadius(u * .006f))
    repeat(3) { i -> drawRect(Color(0xFF797764), tower + Offset(u * (-.04f + .03f * i), -u * .09f), Size(u * .02f, u * .025f)) }
    drawRect(Color(0xFF473D34), tower + Offset(-u * .01f, -u * .052f), Size(u * .02f, u * .027f))
    points.forEachIndexed { index, p ->
        if (index == selected) drawCircle(Color(0xFFC28B34).copy(alpha = .23f), u * .055f, p)
        drawCircle(ClueCream, u * .022f, p)
        drawCircle(Color(0xFF735335), u * .022f, p, style = Stroke(u * .005f))
        drawCircle(Color(0xFF735335), u * .008f, p)
    }
}

private fun DrawScope.drawChart(joined: Set<Int>, selected: Int?, miniature: Boolean) {
    drawClueBoard()
    val unit = size.minDimension
    // Fixed ornament positions avoid random work during pointer frames.
    repeat(33) { i ->
        val x = (.08f + ((i * 37) % 84) / 100f) * size.width
        val y = (.08f + ((i * 23) % 84) / 100f) * size.height
        drawCircle(ClueCream.copy(alpha = .24f), unit * .0025f, Offset(x, y))
    }
    val points = StarChartGame.stars.map { Offset(it.x * size.width, it.y * size.height) }
    joined.forEach { edge ->
        val (a, b) = StarChartGame.edges[edge]
        drawLine(ClueGold.copy(alpha = .15f), points[a], points[b], unit * .025f, StrokeCap.Round)
        drawLine(ClueGold, points[a], points[b], unit * .007f, StrokeCap.Round)
    }
    points.forEachIndexed { index, point ->
        if (index == selected) {
            drawCircle(ClueGold.copy(alpha = .17f), unit * .065f, point)
            drawCircle(ClueGold, unit * .045f, point, style = Stroke(unit * .003f))
        }
        drawCircle(Brush.radialGradient(listOf(ClueGold.copy(alpha = .50f), Color.Transparent), point,
            unit * .045f), unit * .045f, point)
        val r = unit * if (miniature) .022f else .020f
        drawLine(ClueCream, point - Offset(r, 0f), point + Offset(r, 0f), unit * .006f, StrokeCap.Round)
        drawLine(ClueCream, point - Offset(0f, r), point + Offset(0f, r), unit * .006f, StrokeCap.Round)
        drawCircle(Color.White, unit * .009f, point)
    }
}

internal fun DrawScope.drawClueBoard(parchment: Boolean = false) {
    val inset = size.minDimension * .018f
    val radius = CornerRadius(size.minDimension * .055f)
    drawRoundRect(Brush.verticalGradient(if (parchment) listOf(Color(0xFFF8E7BA), Color(0xFFDDBE80))
        else listOf(Color(0xFF14233E), Color(0xFF091426))), cornerRadius = radius)
    drawRoundRect(ClueGold.copy(alpha = .8f), Offset(inset, inset),
        Size(size.width - inset * 2, size.height - inset * 2), radius, style = Stroke(inset * .3f))
    listOf(Offset(.06f, .06f), Offset(.94f, .06f), Offset(.06f, .94f), Offset(.94f, .94f)).forEach {
        val c = Offset(it.x * size.width, it.y * size.height)
        drawCircle(Color(0xFF84612C), inset * .7f, c)
        drawCircle(ClueGold, inset * .4f, c - Offset(inset * .12f, inset * .12f))
    }
}
