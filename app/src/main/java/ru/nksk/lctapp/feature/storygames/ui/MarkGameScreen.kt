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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.MarkGame
import ru.nksk.lctapp.domain.minigame.chapterone.MarkState
import kotlin.math.ceil

data class MarkUiState(val game: MarkState = MarkState())
sealed interface MarkAction {
    data class Touch(val x: Float, val y: Float) : MarkAction
    data object Restart : MarkAction
}

@HiltViewModel
class MarkViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(MarkUiState(MarkGame.restore(
        savedState.get<IntArray>("mark.cleaned")?.toList().orEmpty(),
        savedState["mark.stroke"] ?: 0, savedState["mark.point"] ?: 0,
    )))
    val uiState = mutableState.asStateFlow()
    fun onAction(action: MarkAction) {
        val next = when (action) {
            is MarkAction.Touch -> MarkGame.touch(mutableState.value.game, action.x, action.y)
            MarkAction.Restart -> MarkState()
        }
        if (next == mutableState.value.game) return
        mutableState.value = MarkUiState(next)
        savedState["mark.cleaned"] = next.cleaned.toIntArray()
        savedState["mark.stroke"] = next.stroke
        savedState["mark.point"] = next.point
    }
}

@Composable
fun MarkGameEntry(
    onExit: () -> Unit,
    onCompleted: ((MarkState) -> Unit)? = null,
    interactionBlocked: Boolean = false,
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = !interactionBlocked,
) {
    val viewModel = hiltViewModel<MarkViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = interactionBlocked) {}
    MarkGameScreen(state,
        onAction = { if (!interactionBlocked) viewModel.onAction(it) },
        onExit = { if (!interactionBlocked) onExit() },
        onHome = { if (homeEnabled) onHome() },
        homeEnabled = homeEnabled,
        onCompleted = onCompleted?.let { complete -> {
            if (!interactionBlocked && viewModel.uiState.value.game.complete) complete(viewModel.uiState.value.game)
        } }, interactionBlocked = interactionBlocked)
}

@Composable
fun MarkGameScreen(
    state: MarkUiState,
    onAction: (MarkAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: (() -> Unit)? = null,
    interactionBlocked: Boolean = false,
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = !interactionBlocked,
) {
    val game = state.game
    val instruction = when {
        game.complete -> "Знак зарисован. Его можно сравнить с метками у башни."
        game.readyToTrace -> "Обведи знак на листе. Светлая точка покажет, куда вести палец."
        else -> "Сотри пыль пальцем, чтобы рассмотреть знак на ящике."
    }
    ChapterGameScaffold("Знак на причале", instruction, onExit,
        onHome = onHome, homeEnabled = homeEnabled,
        footer = {
            if (game.complete && onCompleted != null) {
                GameActionButton("Продолжить", onCompleted, interactionBlocked = interactionBlocked)
            } else if (game.complete) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameActionButton("Ещё раз", { onAction(MarkAction.Restart) }, Modifier.weight(1f))
                GameActionButton("К играм", onExit, Modifier.weight(1f))
            }
        },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (game.readyToTrace) {
                Row(Modifier.fillMaxWidth().height(98.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    GameArtwork(R.drawable.story_marked_crate, "Знак на ящике", Modifier.size(100.dp))
                    Text(if (game.complete) "Зарисовка готова" else "${game.stroke} из 4 частей",
                        color = ClueCream, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                val currentAction by rememberUpdatedState(onAction)
                Box(Modifier.size(side)) {
                    if (!game.readyToTrace) GameArtwork(R.drawable.story_marked_crate, null, Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize().pointerInput(interactionBlocked) {
                        if (interactionBlocked) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var previousPosition = down.position
                            fun touch(position: Offset) {
                                if (size.width > 0 && size.height > 0) currentAction(
                                    MarkAction.Touch(position.x / size.width, position.y / size.height))
                            }
                            touch(down.position)
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.id == down.id && change.pressed) {
                                        val delta = change.position - previousPosition
                                        val steps = ceil(delta.getDistance() / maxOf(1f, size.width * .025f)).toInt()
                                            .coerceIn(1, 64)
                                        for (step in 1..steps) touch(previousPosition + delta * (step.toFloat() / steps))
                                        previousPosition = change.position
                                    }
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }.semantics {
                        contentDescription = if (game.readyToTrace) "Зарисовка знака" else "Пыльный знак на ящике"
                        customActions = if (interactionBlocked) emptyList() else if (!game.readyToTrace) MarkGame.dust.mapIndexedNotNull { index, point ->
                            if (index in game.cleaned) null else CustomAccessibilityAction("Очистить участок ${index + 1}") {
                                onAction(MarkAction.Touch(point.x, point.y)); true
                            }
                        } else if (!game.complete) listOf(CustomAccessibilityAction("Обвести следующий участок") {
                            val point = MarkGame.strokes[game.stroke][game.point]
                            onAction(MarkAction.Touch(point.x, point.y)); true
                        }) else emptyList()
                    }) {
                        if (game.readyToTrace) drawMark(game) else {
                            MarkGame.dust.forEachIndexed { index, point ->
                                if (index !in game.cleaned) {
                                    val center = Offset(point.x * size.width, point.y * size.height)
                                    val radius = size.minDimension * .23f
                                    drawCircle(Brush.radialGradient(listOf(Color(0xFFD5BE93),
                                        Color(0xFFAD9063).copy(alpha = .95f), Color.Transparent), center, radius),
                                        radius, center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawMark(state: MarkState) {
    drawClueBoard(parchment = true)
    val unit = size.minDimension
    // The ring is preprinted; only the four meaningful parts need tracing.
    drawCircle(Color(0xFF9C753F).copy(alpha = .35f), unit * .34f,
        Offset(size.width * .5f, size.height * .47f), style = Stroke(unit * .012f))
    MarkGame.strokes.forEachIndexed { index, points ->
        fun path(count: Int): Path = Path().apply {
            points.take(count).forEachIndexed { i, point ->
                if (i == 0) moveTo(point.x * size.width, point.y * size.height)
                else lineTo(point.x * size.width, point.y * size.height)
            }
        }
        drawPath(path(points.size), Color(0xFF79562D).copy(alpha = .20f),
            style = Stroke(unit * .014f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val count = when {
            index < state.stroke -> points.size
            index == state.stroke -> state.point
            else -> 0
        }
        if (count > 1) drawPath(path(count), Color(0xFF5C3A22),
            style = Stroke(unit * .015f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    if (!state.complete) {
        val p = MarkGame.strokes[state.stroke][state.point]
        val center = Offset(p.x * size.width, p.y * size.height)
        drawCircle(Color(0xFF84602D).copy(alpha = .20f), unit * .055f, center)
        drawCircle(Color(0xFFFFF9E0), unit * .025f, center)
        drawCircle(Color(0xFF76542B), unit * .030f, center, style = Stroke(unit * .005f))
    }
}
