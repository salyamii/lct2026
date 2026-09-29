package ru.nksk.lctapp.core.ui.components.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import kotlin.math.roundToInt

/** Input-blocking sibling above a screen. Coordinates are measured, never tied to a device size. */
@Composable
fun SpotlightOverlay(
    title: String,
    body: String,
    progress: String,
    highlights: List<SpotlightHighlight>,
    pointer: Rect?,
    busy: Boolean,
    hasPrevious: Boolean,
    last: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onScroll: (Offset, Float) -> Unit = { _, _ -> },
) {
    BackHandler { if (!busy) { if (hasPrevious) onPrevious() else onSkip() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var origin by remember { mutableStateOf(Offset.Zero) }
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val insets = WindowInsets.safeDrawing
        val direction = LocalLayoutDirection.current
        val safeBounds = Rect(insets.getLeft(density, direction).toFloat(), insets.getTop(density).toFloat(),
            width - insets.getRight(density, direction), height - insets.getBottom(density))
        val localBounds = Rect(0f, 0f, width, height)
        // Keep the original silhouette at screen edges; the Canvas clips it without re-rounding.
        val localHighlights = highlights.map { it.copy(bounds = it.bounds.translate(-origin)) }
            .filter { it.bounds.overlaps(localBounds) }
        val rects = localHighlights.map { it.bounds.intersect(localBounds) }
        val target = pointer?.translate(-origin)?.intersect(localBounds)
        // The scrim covers the entire window. Insets apply only to the explanatory card.
        val safeOffset = Offset(0f, -safeBounds.top)
        val region = spotlightCardRegion(safeBounds.height, rects.map { it.translate(safeOffset) },
            target?.translate(safeOffset), gap, with(density) { 210.dp.toPx() })
        var cardWindowBounds by remember(title) { mutableStateOf<Rect?>(null) }
        val cardTop = safeBounds.top + region.top
        val focus = remember { FocusRequester() }
        LaunchedEffect(title) { focus.requestFocus() }
        Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .pointerInput(onScroll, origin) {
                detectVerticalDragGestures { change, delta ->
                    change.consume()
                    onScroll(change.position + origin, delta)
                }
            }) {
            val shade = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                localHighlights.forEach { addRoundRect(RoundRect(it.bounds, it.cornerRadius, it.cornerRadius)) }
            }
            drawPath(shade, Color.Black.copy(alpha = .76f))
            localHighlights.forEach { drawRoundRect(AdventureLime, it.bounds.topLeft, it.bounds.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(it.cornerRadius), style = Stroke(2.dp.toPx())) }
            val card = cardWindowBounds?.translate(-origin)
            if (target != null && card != null) {
                val route = spotlightArrowRoute(card, target, rects.filter { it != target }, safeBounds,
                    clearance = 8.dp.toPx(), cornerInset = 24.dp.toPx())
                if (route.size >= 2) {
                    val line = Path().apply {
                        moveTo(route.first().x, route.first().y)
                        route.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(line, AdventureLime, style = Stroke(2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    val end = route.last()
                    val delta = end - route[route.lastIndex - 1]
                    val length = delta.getDistance()
                    if (length > 0f) {
                        val direction = delta / length
                        val normal = Offset(-direction.y, direction.x)
                        val back = end - direction * 8.dp.toPx()
                        drawLine(AdventureLime, end, back + normal * 5.dp.toPx(), 2.dp.toPx())
                        drawLine(AdventureLime, end, back - normal * 5.dp.toPx(), 2.dp.toPx())
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.offset { IntOffset(0, cardTop.roundToInt()) }
                .absolutePadding(left = with(density) { safeBounds.left.toDp() } + 16.dp,
                    right = with(density) { (width - safeBounds.right).toDp() } + 16.dp).fillMaxWidth()
                .heightIn(max = with(density) { region.height.toDp() })
                .onGloballyPositioned { cardWindowBounds = it.boundsInWindow() }
                .focusRequester(focus).focusProperties { onExit = { cancelFocusChange() } }.focusGroup().focusable()
                .semantics { paneTitle = "Обучение: $title"; isTraversalGroup = true },
            shape = RoundedCornerShape(24.dp), color = GamePaper, shadowElevation = 10.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(progress, fontFamily = Nunito, fontSize = 13.sp, color = GameInk.copy(alpha = .7f))
                    Text(title, fontFamily = Rubik, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = GameInk, modifier = Modifier.semantics { heading() })
                    Text(body, fontFamily = Nunito, fontSize = 17.sp, lineHeight = 23.sp, color = GameInk)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (hasPrevious) TextButton(onPrevious, enabled = !busy) { Text("Назад", color = GameInk) }
                    else TextButton(onSkip, enabled = !busy) { Text("Пропустить", color = GameInk) }
                    Button(onNext, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = AdventureLime, contentColor = GameInk)) {
                        Text(if (last) "Понятно!" else "Дальше")
                    }
                }
                if (hasPrevious) TextButton(onSkip, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Пропустить обучение", color = GameInk.copy(alpha = .7f))
                }
            }
        }
    }
}
