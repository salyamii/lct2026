package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.sin
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.domain.minigame.chapterone.RingPoint
import ru.nksk.lctapp.domain.minigame.chapterone.RingToss

private val RopeColors = listOf(Color(0xFFFFCE79), Color(0xFFA9CED0), Color(0xFFE7A276),
    Color(0xFFC2CF90), Color(0xFFF3D99B))

@Composable
internal fun RingTossGameEntry(
    onExit: () -> Unit,
    onCompleted: (RingToss) -> Unit,
    interactionBlocked: Boolean = false,
) {
    val model = hiltViewModel<RingTossGameViewModel>()
    val game by model.uiState.collectAsStateWithLifecycle()
    val blocked by rememberUpdatedState(interactionBlocked)
    val onAction: (RingTossAction) -> Unit = remember(model) { { if (!blocked) model.onAction(it) } }
    val exit = { if (!blocked) onExit() }
    BackHandler(onBack = exit)
    RingTossGameScreen(game, onAction, exit, onCompleted, interactionBlocked)
}

@Composable
internal fun RingTossGameScreen(
    game: RingToss,
    onAction: (RingTossAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: (RingToss) -> Unit,
    interactionBlocked: Boolean = false,
) {
    var settledCount by remember { mutableIntStateOf(game.throws.size) }
    val flight = remember { Animatable(1f) }
    val inFlight = game.throws.size > settledCount
    LaunchedEffect(game.throws.size) {
        if (game.throws.size > settledCount) {
            flight.snapTo(0f)
            flight.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
        }
        settledCount = game.throws.size
    }
    val lastHit = game.throws.lastOrNull()?.pegIndex != null
    ChapterGameScaffold(
        title = "Кольцеброс",
        instruction = if (game.complete && !inFlight) "Все кольца брошены. Попаданий: ${game.hits} из ${RingToss.THROW_COUNT}."
            else "Потяни кольцо вниз и отпусти.",
        onExit = onExit,
        footer = {
            if (game.complete && !inFlight) {
                GameActionButton("Продолжить", { if (!interactionBlocked) onCompleted(game) },
                    interactionBlocked = interactionBlocked)
            } else GamePlaque(Modifier.fillMaxWidth().height(55.dp), light = true, fixedEdges = true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    FinanceCaption("Кольца: ${RingToss.THROW_COUNT - game.throws.size}", fontSize = 14.sp)
                    FinanceCaption(when {
                        inFlight -> "Летит…"
                        game.throws.isEmpty() -> "Целься в колышки"
                        lastHit -> "Попали!"
                        else -> "Чуть мимо"
                    }, fontSize = 14.sp)
                }
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
            .border(2.dp, Color(0xFFB58B4C), RoundedCornerShape(24.dp))) {
            GameArtwork(R.drawable.story_game_workbench_surface, null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color(0xAA292737), Color(0x55442E20), Color(0x99634426)))))
            val width = minOf(maxWidth, maxHeight * .80f)
            val enabled = !game.complete && !inFlight && !interactionBlocked
            RingTossBoard(game, enabled, inFlight, { flight.value }, onAction,
                Modifier.align(Alignment.Center).size(width, width / .80f))
            if (!game.complete) FinanceCaption("Можно нажать на колышек", Modifier.align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp), color = Color(0xFFFFE9BE), fontSize = 12.sp)
        }
    }
}

@Composable
private fun RingTossBoard(
    game: RingToss,
    enabled: Boolean,
    inFlight: Boolean,
    flight: () -> Float,
    onAction: (RingTossAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pull by remember { mutableStateOf<Offset?>(null) }
    val latestAction by rememberUpdatedState(onAction)
    val latestEnabled by rememberUpdatedState(enabled)
    Canvas(modifier.semantics {
        contentDescription = "Деревянная стойка с тремя колышками"
        stateDescription = "Брошено ${game.throws.size} из ${RingToss.THROW_COUNT}. Попаданий ${game.hits}"
        customActions = if (enabled) listOf("левый колышек", "дальний колышек", "правый колышек")
            .mapIndexed { index, label -> CustomAccessibilityAction("Бросить на $label") {
                if (latestEnabled) latestAction(RingTossAction.AimAtPeg(index))
                latestEnabled
            } } else emptyList()
    }.pointerInput(enabled) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!latestEnabled) return@awaitEachGesture
            fun normalized(point: Offset) = Offset(point.x / size.width.coerceAtLeast(1),
                point.y / size.height.coerceAtLeast(1))
            val first = normalized(down.position)
            val start = Offset(RingToss.start.x, RingToss.start.y)
            val takingRing = (first - start).getDistance() < .15f
            val tappedPeg = RingToss.pegs.indexOfFirst { peg ->
                // The visible stem extends above the ring's landing point.
                kotlin.math.abs(first.x - peg.x) <= .105f && first.y in (peg.y - .14f)..(peg.y + .07f)
            }
            if (!takingRing && tappedPeg < 0) return@awaitEachGesture
            down.consume()
            var displacement = Offset.Zero
            try {
                do {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    displacement = normalized(pointer.position) - first
                    if (takingRing) pull = Offset(displacement.x.coerceIn(-.20f, .20f),
                        displacement.y.coerceIn(0f, .24f))
                    pointer.consume()
                } while (event.changes.any { it.pressed })
                if (latestEnabled) {
                    if (takingRing) latestAction(RingTossAction.Release(displacement.x, displacement.y))
                    else if (displacement.getDistance() < .045f) latestAction(RingTossAction.AimAtPeg(tappedPeg))
                }
            } finally { pull = null }
        }
    }) {
        drawFairBoard()
        val visible = if (inFlight) game.throws.dropLast(1) else game.throws
        visible.forEachIndexed { index, ring ->
            if (ring.pegIndex == null) drawRopeRing(ring.point.offset(size), size.width * .065f,
                .42f, RopeColors[index], .8f)
        }
        // Back row first, so the front pair occludes the far peg in the expected order.
        listOf(1, 0, 2).forEach { pegIndex ->
            val peg = RingToss.pegs[pegIndex].offset(size)
            val hits = visible.withIndex().filter { it.value.pegIndex == pegIndex }
            hits.forEachIndexed { level, hit ->
                drawRopeRing(peg + Offset(0f, -level * size.height * .008f), size.width * .067f,
                    .43f, RopeColors[hit.index])
            }
            drawPeg(peg, pegIndex)
            // Front half wraps in front of the peg; the back half stays behind it.
            hits.forEachIndexed { level, hit ->
                val center = peg + Offset(0f, -level * size.height * .008f)
                val radius = size.width * .067f
                drawArc(RopeColors[hit.index], 0f, 180f, false,
                    center - Offset(radius, radius * .43f), Size(radius * 2, radius * .86f),
                    style = Stroke(size.width * .014f, cap = StrokeCap.Round))
            }
        }
        if (inFlight) {
            val t = flight()
            val destination = game.throws.last().point.offset(size)
            val origin = RingToss.start.offset(size)
            val center = origin + (destination - origin) * t - Offset(0f, sin(t * PI).toFloat() * size.height * .23f)
            drawOval(Color(0x350C0A08), Offset(center.x - size.width * .06f, origin.y + (destination.y - origin.y) * t),
                Size(size.width * .12f, size.width * .027f))
            drawRopeRing(center, size.width * (.105f - .038f * t), .60f - .17f * t,
                RopeColors[game.throws.lastIndex])
        } else if (!game.complete) {
            val drag = pull ?: Offset.Zero
            if (drag.y >= RingToss.MINIMUM_PULL) drawAim(drag)
            val center = RingToss.start.offset(size) + Offset(drag.x * size.width, drag.y * size.height)
            drawOval(Color(0x550D0A06), center + Offset(-size.width * .1f, size.width * .05f),
                Size(size.width * .2f, size.width * .04f))
            drawRopeRing(center, size.width * .10f, .62f, RopeColors[game.throws.size])
        }
        drawRemainingRings(game.throws.size)
    }
}

private fun DrawScope.drawFairBoard() {
    fun point(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    val face = Path().apply {
        moveTo(size.width * .10f, size.height * .48f)
        lineTo(size.width * .90f, size.height * .48f)
        lineTo(size.width * .90f, size.height * .53f)
        lineTo(size.width * .10f, size.height * .53f)
        close()
    }
    drawOval(Color(0x66080705), point(.07f, .49f), Size(size.width * .86f, size.height * .065f))
    drawPath(face, Brush.verticalGradient(listOf(Color(0xFFC5904C), Color(0xFF59351C)),
        size.height * .48f, size.height * .54f))
    val top = Path().apply {
        moveTo(size.width * .24f, size.height * .18f)
        lineTo(size.width * .76f, size.height * .18f)
        lineTo(size.width * .90f, size.height * .48f)
        lineTo(size.width * .10f, size.height * .48f)
        close()
    }
    drawPath(top, Brush.linearGradient(listOf(Color(0xFFD7AC6E), Color(0xFFAE783D), Color(0xFFCE9A54)),
        point(.10f, .18f), point(.90f, .48f)))
    drawPath(top, Color(0xFFEDD095), style = Stroke(size.width * .009f))
    repeat(4) { i ->
        val backX = .24f + (i + 1) * .104f
        val frontX = .10f + (i + 1) * .16f
        drawLine(Color(0x775D391E), point(backX, .18f), point(frontX, .48f), size.width * .006f)
        drawLine(Color(0x55F6D59F), point(backX + .006f, .18f), point(frontX + .006f, .48f), size.width * .003f)
    }
    listOf(.145f, .855f).forEach { x ->
        drawCircle(Color(0xFF58351C), size.width * .012f, point(x, .505f))
        drawCircle(Color(0xFFD7B16F), size.width * .007f, point(x - .002f, .502f))
    }
    drawLine(Color(0x66F0C887), point(.16f, .67f), point(.84f, .67f), size.width * .006f, StrokeCap.Round)
}

private fun DrawScope.drawPeg(base: Offset, index: Int) {
    val w = size.width * .044f
    val h = size.height * .115f
    val top = base - Offset(w / 2, h)
    drawOval(Color(0x5A3E2615), base - Offset(w * .6f, w * .23f), Size(w * 1.5f, w * .60f))
    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF744625), Color(0xFFE0B77B), Color(0xFF9B6333)),
        top.x, top.x + w), top, Size(w, h), CornerRadius(w * .45f, w * .45f))
    drawRoundRect(listOf(Color(0xFFA15342), Color(0xFF5A7D79), Color(0xFF69754B))[index],
        top + Offset(0f, h * .19f), Size(w, h * .23f), CornerRadius(w * .1f, w * .1f))
    drawOval(Color(0xFFF1D49D), top - Offset(0f, w * .10f), Size(w, w * .48f))
}

private fun DrawScope.drawRopeRing(center: Offset, radius: Float, flatten: Float, color: Color, alpha: Float = 1f) {
    val width = size.width * .016f
    val top = center - Offset(radius, radius * flatten)
    val oval = Size(radius * 2, radius * flatten * 2)
    drawOval(Color(0xFF654022).copy(alpha = alpha), top + Offset(0f, width * .3f), oval,
        style = Stroke(width * 1.38f))
    drawOval(color.copy(alpha = alpha), top, oval, style = Stroke(width))
    drawArc(Color(0xFFFFF0CB).copy(alpha = alpha * .65f), 195f, 145f, false, top, oval,
        style = Stroke(width * .25f, cap = StrokeCap.Round))
    repeat(18) { segment ->
        val angle = segment * (2 * PI / 18)
        val x = kotlin.math.cos(angle).toFloat()
        val y = sin(angle).toFloat()
        val p = center + Offset(x * radius, y * radius * flatten)
        drawLine(Color(0xFF744C2D).copy(alpha = alpha * .28f), p - Offset(width * .18f, width * .20f),
            p + Offset(width * .18f, width * .20f), width * .14f, StrokeCap.Round)
    }
}

private fun DrawScope.drawAim(pull: Offset) {
    val end = RingToss.landingForPull(pull.x, pull.y).offset(size)
    val start = RingToss.start.offset(size)
    repeat(9) { index ->
        val t = (index + 1) / 10f
        val point = start + (end - start) * t - Offset(0f, sin(t * PI).toFloat() * size.height * .23f)
        drawCircle(Color(0xAAFFE5A7), size.width * .006f, point)
    }
    drawOval(Color(0xAAFFE5A7), end - Offset(size.width * .060f, size.width * .025f),
        Size(size.width * .12f, size.width * .05f), style = Stroke(size.width * .004f))
}

private fun DrawScope.drawRemainingRings(thrown: Int) {
    repeat((RingToss.THROW_COUNT - thrown - 1).coerceAtLeast(0)) { index ->
        drawRopeRing(Offset(size.width * .14f, size.height * (.84f - index * .022f)),
            size.width * .055f, .45f, RopeColors[thrown + index + 1], .9f)
    }
}

private fun RingPoint.offset(size: Size) = Offset(x * size.width, y * size.height)
