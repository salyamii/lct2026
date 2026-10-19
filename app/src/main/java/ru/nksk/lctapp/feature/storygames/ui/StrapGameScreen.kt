package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.StrapPhase
import ru.nksk.lctapp.domain.minigame.chapterone.StrapPoint
import ru.nksk.lctapp.domain.minigame.chapterone.StrapRepair

private val StrapThread = Color(0xFFFFDB8B)
private val StrapThreadShade = Color(0xFF72441E)
private val StrapMetal = Color(0xFFD1DEE0)

@Composable
fun StrapGameEntry(
    onExit: () -> Unit,
    onCompleted: ((StrapRepair) -> Unit)? = null,
    interactionBlocked: Boolean = false,
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = !interactionBlocked,
) {
    val model = hiltViewModel<StrapGameViewModel>()
    val state by model.uiState.collectAsStateWithLifecycle()
    val blocked by rememberUpdatedState(interactionBlocked)
    val onAction: (StrapGameAction) -> Unit = remember(model) {
        { action -> if (!blocked) model.onAction(action) }
    }
    val exit = { if (!blocked) onExit() }
    BackHandler(onBack = exit)
    StrapGameScreen(state, onAction, exit, onCompleted, interactionBlocked,
        onHome = { if (homeEnabled) onHome() }, homeEnabled = homeEnabled)
}

@Composable
fun StrapGameScreen(
    state: StrapGameUiState,
    onAction: (StrapGameAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: ((StrapRepair) -> Unit)? = null,
    interactionBlocked: Boolean = false,
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = !interactionBlocked,
) {
    val game = state.game
    ChapterGameScaffold(
        title = "Почини лямку",
        instruction = when (game.phase) {
            StrapPhase.ALIGN -> "Подтяни конец лямки к основе рюкзака."
            StrapPhase.STITCH -> "Веди нить через светлые точки по порядку."
            StrapPhase.TIGHTEN -> "Потяни нить вниз, чтобы затянуть узел."
            StrapPhase.DONE -> "Лямка снова крепкая, новый рюкзак покупать не пришлось."
        },
        onExit = onExit,
        onHome = onHome,
        homeEnabled = homeEnabled,
        footer = {
            when (game.phase) {
                StrapPhase.ALIGN -> GameActionButton("Совместить края", { onAction(StrapGameAction.Align) })
                StrapPhase.STITCH -> GamePlaque(Modifier.fillMaxWidth().height(64.dp), light = true, fixedEdges = true) {
                    Text("Нить: ${game.stitchedPoints} из ${StrapRepair.STITCH_COUNT}", color = GameInk,
                        fontFamily = Nunito, fontSize = 16.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center))
                }
                StrapPhase.TIGHTEN -> GameActionButton("Затянуть узел", { onAction(StrapGameAction.Tighten) })
                StrapPhase.DONE -> if (onCompleted != null) GameActionButton("Продолжить",
                    { onCompleted(game) }, enabled = !interactionBlocked)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameActionButton("Ещё раз", { onAction(StrapGameAction.Restart) }, Modifier.weight(1f))
                    GameActionButton("К играм", onExit, Modifier.weight(1f))
                }
            }
        },
    ) {
        StrapWorkbench(game, onAction)
    }
}

@Composable
private fun StrapWorkbench(game: StrapRepair, onAction: (StrapGameAction) -> Unit) {
    val latestGame by rememberUpdatedState(game)
    var drag by remember(game.phase) { mutableStateOf(Offset.Zero) }
    var finger by remember(game.phase) { mutableStateOf<Offset?>(null) }
    var pull by remember(game.phase) { mutableFloatStateOf(0f) }
    val successGlow by animateFloatAsState(if (game.phase == StrapPhase.DONE) 1f else 0f,
        tween(280), label = "strapComplete")
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
        .border(2.dp, Color(0xFFA47A43), RoundedCornerShape(24.dp))) {
        GameArtwork(R.drawable.story_game_workbench_surface, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color(0x660F151A)))))
        val width = minOf(maxWidth - 12.dp, (maxHeight - 8.dp) * (2f / 3f)).coerceAtLeast(1.dp)
        Box(Modifier.align(Alignment.Center).size(width, width * 1.5f)
            .semantics(mergeDescendants = true) {
                contentDescription = when (game.phase) {
                    StrapPhase.ALIGN -> "Конец лямки и основа рюкзака"
                    StrapPhase.STITCH -> "Шов. Пройдено точек: ${game.stitchedPoints} из ${StrapRepair.STITCH_COUNT}"
                    StrapPhase.TIGHTEN -> "Нить готова, осталось затянуть узел"
                    StrapPhase.DONE -> "Лямка пришита к рюкзаку"
                }
                customActions = when (game.phase) {
                    StrapPhase.ALIGN -> listOf(CustomAccessibilityAction("Совместить края") {
                        onAction(StrapGameAction.Align); true
                    })
                    StrapPhase.STITCH -> listOf(CustomAccessibilityAction("Протянуть нить к следующей точке") {
                        onAction(StrapGameAction.Stitch(game.stitchedPoints)); true
                    })
                    StrapPhase.TIGHTEN -> listOf(CustomAccessibilityAction("Затянуть узел") {
                        onAction(StrapGameAction.Tighten); true
                    })
                    StrapPhase.DONE -> emptyList()
                }
            }
            .pointerInput(game.phase, onAction) {
                fun normalized(point: Offset) = StrapPoint(point.x / size.width.coerceAtLeast(1),
                    point.y / size.height.coerceAtLeast(1))
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown()
                        val phase = latestGame.phase
                        val initial = normalized(down.position)
                        if (phase == StrapPhase.DONE ||
                            (phase == StrapPhase.ALIGN && (initial.x !in 0.18f..0.82f || initial.y < .53f)) ||
                            (phase == StrapPhase.TIGHTEN && (initial.x !in 0.27f..0.73f || initial.y !in 0.49f..0.87f))
                        ) return@awaitEachGesture
                        var previous = down.position
                        finger = previous
                        if (phase == StrapPhase.STITCH) onAction(StrapGameAction.Trace(initial, initial))
                        do {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                            finger = pointer.position
                            when (phase) {
                                StrapPhase.ALIGN -> drag = pointer.position - down.position
                                StrapPhase.STITCH -> if ((pointer.position - previous).getDistance() > 1f) {
                                    onAction(StrapGameAction.Trace(normalized(previous), normalized(pointer.position)))
                                }
                                StrapPhase.TIGHTEN -> pull = ((pointer.position.y - down.position.y) / size.height.coerceAtLeast(1))
                                    .coerceIn(0f, .20f)
                                StrapPhase.DONE -> Unit
                            }
                            previous = pointer.position
                            pointer.consume()
                        } while (event.changes.any { it.pressed })
                        when (phase) {
                            StrapPhase.ALIGN -> onAction(StrapGameAction.AlignAt(drag.x / size.width.coerceAtLeast(1),
                                drag.y / size.height.coerceAtLeast(1)))
                            StrapPhase.TIGHTEN -> onAction(StrapGameAction.Pull(pull))
                            else -> Unit
                        }
                    } finally {
                        drag = Offset.Zero
                        finger = null
                        pull = 0f
                    }
                }
            }) {
            if (game.phase == StrapPhase.ALIGN) {
                GameArtwork(R.drawable.story_game_strap_patch, null, Modifier.fillMaxSize().graphicsLayer {
                    translationY = size.height * StrapRepair.ALIGNED_Y
                    clip = true
                    shape = StrapBottomShape
                    alpha = .22f
                })
            }
            GameArtwork(R.drawable.story_game_strap_patch, null, Modifier.fillMaxSize().clip(StrapTopShape))
            GameArtwork(R.drawable.story_game_strap_patch, null, Modifier.fillMaxSize().graphicsLayer {
                translationX = if (game.aligned) 0f else drag.x
                translationY = if (game.aligned) size.height * StrapRepair.ALIGNED_Y else drag.y
                clip = true
                shape = StrapBottomShape
            })
            Canvas(Modifier.fillMaxSize()) {
                if (game.phase == StrapPhase.ALIGN) drawAlignmentGuide(drag)
                else drawStrapStitches(game, finger, pull, successGlow)
            }
        }
        if (game.phase == StrapPhase.DONE) {
            GameArtwork(R.drawable.gear_explorer_backpack, "Починенный рюкзак",
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(72.dp))
        }
    }
}

private fun DrawScope.drawAlignmentGuide(drag: Offset) {
    val near = kotlin.math.abs(drag.x / size.width) <= .14f &&
        kotlin.math.abs(drag.y / size.height - StrapRepair.ALIGNED_Y) <= .10f
    drawRoundRect(
        if (near) Color(0xFFDAE8A2) else StrapThread.copy(alpha = .65f),
        topLeft = Offset(size.width * .32f, size.height * .35f),
        size = Size(size.width * .36f, size.height * .16f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
    )
}

private fun DrawScope.drawStrapStitches(game: StrapRepair, finger: Offset?, pull: Float, success: Float) {
    val points = StrapRepair.points.map { Offset(it.x * size.width, it.y * size.height) }
    if (success > 0f) drawCircle(Brush.radialGradient(listOf(Color(0x88F8DA8C), Color.Transparent),
        center = points.last(), radius = size.width * .40f), size.width * .40f, points.last(), alpha = success)
    points.forEachIndexed { index, point ->
        if (game.phase == StrapPhase.STITCH && index == game.stitchedPoints) {
            drawCircle(Brush.radialGradient(listOf(Color(0x88FFE7A7), Color.Transparent),
                point, 26.dp.toPx()), 26.dp.toPx(), point)
            drawCircle(StrapThread, 12.dp.toPx(), point, style = Stroke(2.dp.toPx()))
        }
        drawCircle(Color(0xFF5D391F), 4.5.dp.toPx(), point)
        drawCircle(Color(0xFF211914), 2.5.dp.toPx(), point)
    }
    // The two long stitches cross on the front; connecting stitches pass behind the leather.
    if (game.stitchedPoints >= 2) drawThread(points[0], points[1])
    if (game.stitchedPoints >= 4) drawThread(points[2], points[3])
    if (game.phase == StrapPhase.STITCH) {
        val previous = if (game.stitchedPoints > 0) points[game.stitchedPoints - 1]
            else points[0] + Offset(-size.width * .15f, -size.height * .06f)
        val end = finger ?: points[game.stitchedPoints] + Offset(19.dp.toPx(), -25.dp.toPx())
        val loose = Path().apply {
            moveTo(previous.x, previous.y)
            quadraticBezierTo((previous.x + end.x) / 2f - 14.dp.toPx(),
                (previous.y + end.y) / 2f + 16.dp.toPx(), end.x, end.y)
        }
        drawThreadPath(loose, .85f)
        drawNeedle(end, vertical = false)
    }
    if (game.phase == StrapPhase.TIGHTEN) {
        val center = points.last()
        val factor = (1f - pull / .16f).coerceIn(.12f, 1f)
        val loop = Path().apply {
            moveTo(center.x, center.y)
            cubicTo(center.x - size.width * .22f * factor, center.y + size.height * .14f * factor,
                center.x + size.width * .22f * factor, center.y + size.height * .14f * factor, center.x, center.y)
        }
        drawThreadPath(loop)
        val tail = Offset(size.width * .50f, size.height * (.65f + pull))
        drawThread(center, tail)
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFE5A0), Color.Transparent), tail, 25.dp.toPx()),
            25.dp.toPx(), tail)
        drawNeedle(tail, vertical = true)
    } else if (game.phase == StrapPhase.DONE) {
        val center = points.last()
        drawThread(center + Offset(-7.dp.toPx(), -3.dp.toPx()), center + Offset(7.dp.toPx(), 4.dp.toPx()))
        drawThread(center + Offset(-6.dp.toPx(), 4.dp.toPx()), center + Offset(6.dp.toPx(), -3.dp.toPx()))
        drawThread(center, center + Offset(-10.dp.toPx(), 15.dp.toPx()))
        drawThread(center, center + Offset(9.dp.toPx(), 12.dp.toPx()))
    }
}

private fun DrawScope.drawThread(start: Offset, end: Offset) {
    drawLine(StrapThreadShade, start + Offset(0f, 1.dp.toPx()), end + Offset(0f, 1.dp.toPx()),
        6.dp.toPx(), StrokeCap.Round)
    drawLine(StrapThread, start, end, 3.5.dp.toPx(), StrokeCap.Round)
    drawLine(Color(0xFFFFEAC0), start + Offset(0f, -1.dp.toPx()), end + Offset(0f, -1.dp.toPx()),
        .8.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawThreadPath(path: Path, alpha: Float = 1f) {
    drawPath(path, StrapThreadShade, alpha, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, StrapThread, alpha, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawNeedle(point: Offset, vertical: Boolean) {
    val end = if (vertical) point + Offset(0f, 22.dp.toPx()) else point + Offset(19.dp.toPx(), -22.dp.toPx())
    drawLine(Color(0x66202018), point + Offset(2.dp.toPx(), 2.dp.toPx()), end + Offset(2.dp.toPx(), 2.dp.toPx()),
        5.dp.toPx(), StrokeCap.Round)
    drawLine(StrapMetal, point, end, 3.dp.toPx(), StrokeCap.Round)
    drawLine(Color.White, point, end, .8.dp.toPx(), StrokeCap.Round)
    drawCircle(Color(0xFF5A655E), 1.2.dp.toPx(), end)
}

private object StrapTopShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(Rect(0f, 0f, size.width, size.height * .53f))
}

private object StrapBottomShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(Rect(0f, size.height * .53f, size.width, size.height))
}
