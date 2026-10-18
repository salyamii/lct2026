package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.WheelRepair

private val WheelGold = Color(0xFFE8BC68)
private val WheelCream = Color(0xFFFFEDC9)

@Composable
fun WheelGameEntry(
    onExit: () -> Unit,
    onCompleted: ((WheelRepair) -> Unit)? = null,
    interactionBlocked: Boolean = false,
) {
    val viewModel = hiltViewModel<WheelGameViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = interactionBlocked) {}
    WheelGameScreen(state,
        onAction = { if (!interactionBlocked) viewModel.onAction(it) },
        onExit = { if (!interactionBlocked) onExit() },
        onCompleted = onCompleted?.let { complete -> { game -> if (!interactionBlocked) complete(game) } },
        interactionBlocked = interactionBlocked)
}

@Composable
fun WheelGameScreen(
    uiState: WheelGameUiState,
    onAction: (WheelGameAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: ((WheelRepair) -> Unit)? = null,
    interactionBlocked: Boolean = false,
) {
    val game = uiState.game
    ChapterGameScaffold(
        title = "Почини колесо",
        instruction = if (game.completed) "Колесо снова в деле!"
            else "Поверни часть колеса, чтобы края совпали. Затем закрепи её.",
        onExit = onExit,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (game.completed) {
                    Text("Колесо починили — покупать новое не пришлось.",
                        fontFamily = Nunito, fontSize = 15.sp, color = WheelCream,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    if (onCompleted != null) {
                        GameActionButton("Продолжить", { onCompleted(game) }, interactionBlocked = interactionBlocked)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameActionButton("Ещё раз", { onAction(WheelGameAction.Restart) }, Modifier.weight(1f))
                        GameActionButton("К играм", onExit, Modifier.weight(1f))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameActionButton("Влево", { onAction(WheelGameAction.Rotate(-10f)) }, Modifier.weight(1f))
                        GameActionButton("Вправо", { onAction(WheelGameAction.Rotate(10f)) }, Modifier.weight(1f))
                    }
                    GameActionButton(if (game.aligned) "Закрепить" else "Совмести края",
                        { onAction(WheelGameAction.Secure) }, enabled = game.aligned)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly) {
            WheelRepairBoard(game, onAction, Modifier.weight(1f).fillMaxWidth())
            if (!game.completed) {
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(WheelRepair.SECTION_COUNT) { section ->
                        val selected = section == game.selectedSection
                        Box(Modifier.size(52.dp)
                            .background(if (selected) WheelGold else Color(0xD233241B), CircleShape)
                            .border(1.dp, WheelGold, CircleShape)
                            .clickable(enabled = !game.isFixed(section), role = Role.Button,
                                onClickLabel = "Часть ${section + 1}") { onAction(WheelGameAction.Select(section)) }
                            .semantics {
                                contentDescription = "Часть колеса ${section + 1}"
                                stateDescription = if (game.isFixed(section)) "Закреплена" else if (selected) "Выбрана" else "Нужно закрепить"
                            }, contentAlignment = Alignment.Center) {
                            Text(if (game.isFixed(section)) "✓" else "${section + 1}", fontFamily = Nunito,
                                fontWeight = FontWeight.Black, fontSize = 21.sp,
                                color = if (selected) Color(0xFF38251A) else WheelCream)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelRepairBoard(game: WheelRepair, onAction: (WheelGameAction) -> Unit, modifier: Modifier) {
    var lastFingerAngle by remember(game.selectedSection) { mutableFloatStateOf(0f) }
    val completionTurn by animateFloatAsState(if (game.completed) 360f else 0f,
        tween(if (game.completed) 1400 else 0), label = "repairedWheel")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val edge = minOf(maxWidth, maxHeight)
        Box(Modifier.size(edge).clip(RoundedCornerShape(32.dp))
            .background(Brush.radialGradient(listOf(Color(0xE6503522), Color(0xDF1E1A16))))
            .border(1.dp, WheelGold.copy(alpha = .45f), RoundedCornerShape(32.dp))
            .padding(12.dp)
            .semantics {
                contentDescription = "Колесо на верстаке"
                stateDescription = if (game.completed) "Отремонтировано" else "Закреплено ${game.fixedCount} из 3 частей"
            }
            .pointerInput(game.completed, game.selectedSection) {
                if (!game.completed) detectDragGestures(
                    onDragStart = { position -> lastFingerAngle = wheelFingerAngle(position, size.width / 2f, size.height / 2f) },
                ) { change, _ ->
                    change.consume()
                    val point = change.position
                    // Ignore the tiny hub: angle is unstable immediately around the centre.
                    if ((point - Offset(size.width / 2f, size.height / 2f)).getDistance() > size.width * .13f) {
                        val next = wheelFingerAngle(point, size.width / 2f, size.height / 2f)
                        onAction(WheelGameAction.Rotate(WheelRepair.normalizeAngle(next - lastFingerAngle)))
                        lastFingerAngle = next
                    }
                }
            }) {
            if (game.completed) {
                GameArtwork(R.drawable.story_game_wheel, "Починенное колесо",
                    Modifier.fillMaxSize().graphicsLayer { rotationZ = completionTurn })
            } else {
                GameArtwork(R.drawable.story_game_wheel, null, Modifier.fillMaxSize().alpha(.14f))
                repeat(WheelRepair.SECTION_COUNT) { section ->
                    if (game.isFixed(section)) {
                        GameArtwork(R.drawable.story_game_wheel, null,
                            Modifier.fillMaxSize().clip(WheelSectionShape(section)))
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension * .46f
                    val start = (-90 + game.selectedSection * 120).toFloat()
                    drawArc(WheelGold.copy(alpha = if (game.aligned) .85f else .35f), start, 120f, false,
                        topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2),
                        style = Stroke(2.dp.toPx()))
                }
                GameArtwork(R.drawable.story_game_wheel, null,
                    Modifier.fillMaxSize().graphicsLayer { rotationZ = game.angle }
                        .clip(WheelSectionShape(game.selectedSection)))
            }
        }
    }
}

/** All pieces retain the exact full artwork canvas; this clips only at draw time. */
private data class WheelSectionShape(val section: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.maxDimension
        val start = -90f + section * 120f
        val radians = start * PI / 180
        return Outline.Generic(Path().apply {
            moveTo(center.x, center.y)
            lineTo(center.x + cos(radians).toFloat() * radius, center.y + sin(radians).toFloat() * radius)
            arcTo(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius), start, 120f, false)
            close()
        })
    }
}

private fun wheelFingerAngle(position: Offset, centerX: Float, centerY: Float): Float =
    (atan2(position.y - centerY, position.x - centerX) * 180f / PI).toFloat()
