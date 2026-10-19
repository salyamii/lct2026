package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min
import kotlin.math.roundToInt
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopeAssembly
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopePart

private val TelescopeGold = Color(0xFFE8BC68)
private val TelescopeCream = Color(0xFFFFEDC9)

@Composable
fun TelescopeGameEntry(
    onExit: () -> Unit,
    onCompleted: ((TelescopeAssembly) -> Unit)? = null,
    interactionBlocked: Boolean = false,
) {
    val viewModel = hiltViewModel<TelescopeGameViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = interactionBlocked) {}
    TelescopeGameScreen(state,
        onAction = { if (!interactionBlocked) viewModel.onAction(it) },
        onExit = { if (!interactionBlocked) onExit() },
        onCompleted = onCompleted?.let { complete -> { game -> if (!interactionBlocked) complete(game) } },
        interactionBlocked = interactionBlocked)
}

@Composable
fun TelescopeGameScreen(
    uiState: TelescopeGameUiState,
    onAction: (TelescopeGameAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: ((TelescopeAssembly) -> Unit)? = null,
    interactionBlocked: Boolean = false,
) {
    val game = uiState.game
    ChapterGameScaffold(
        title = "Собери телескоп",
        instruction = when {
            game.completed -> "Телескоп настроен."
            game.assembled -> "Двигай ползунок, пока звёзды не станут чёткими."
            else -> game.nextPart!!.assemblyInstruction()
        },
        onExit = onExit,
        footer = {
            if (game.completed) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onCompleted != null) {
                        GameActionButton("Продолжить", { onCompleted(game) }, interactionBlocked = interactionBlocked)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameActionButton("Ещё раз", { onAction(TelescopeGameAction.Restart) }, Modifier.weight(1f))
                        GameActionButton("К играм", onExit, Modifier.weight(1f))
                    }
                }
            } else if (game.assembled) {
                Column {
                    Slider(game.focus, { onAction(TelescopeGameAction.Focus(it)) },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Настройка резкости" },
                        colors = SliderDefaults.colors(thumbColor = TelescopeGold,
                            activeTrackColor = TelescopeGold, inactiveTrackColor = Color(0xFF594332)))
                    GameActionButton(if (game.inFocus) "Рассмотреть созвездие" else "Наведи резкость",
                        { onAction(TelescopeGameAction.Observe) }, enabled = game.inFocus)
                }
            } else {
                val next = game.nextPart!!
                GameActionButton("Установить: ${next.partName().lowercase()}",
                    { onAction(TelescopeGameAction.Install(next)) }, enabled = game.selectedPart == next)
            }
        },
    ) {
        if (game.assembled) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TelescopeStarLens(game, Modifier.size(minOf(maxWidth, maxHeight)))
            }
        } else {
            TelescopeWorkbench(game, onAction, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TelescopeWorkbench(
    game: TelescopeAssembly,
    onAction: (TelescopeGameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boardBounds by remember { mutableStateOf(Rect.Zero) }
    Box(modifier.clip(RoundedCornerShape(28.dp))
        .background(Color(0xFF523723))
        .border(1.dp, TelescopeGold.copy(alpha = .5f), RoundedCornerShape(28.dp))) {
        GameArtwork(R.drawable.story_game_workbench_surface, null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0x2223140B), Color(0x6623140B)))))

        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val canvasWidth = minOf(maxWidth, maxHeight / 1.32f)
                val canvasHeight = canvasWidth * 1.32f
                Box(Modifier.size(canvasWidth, canvasHeight)
                    .onGloballyPositioned { boardBounds = it.boundsInRoot() }) {
                    TelescopePart.entries.forEach { part ->
                        val geometry = telescopeGeometry(part)
                        val installed = part.ordinal < game.installedCount
                        if (installed || part == game.nextPart) GameArtwork(part.artwork(), null,
                            Modifier.offset(canvasWidth * geometry.x, canvasWidth * geometry.y)
                                .size(canvasWidth * geometry.size)
                                .alpha(if (installed) 1f else .12f))
                    }
                    game.nextPart?.let { next ->
                        val target = telescopeTarget(next)
                        Box(Modifier.offset(canvasWidth * target.x - 36.dp, canvasHeight * target.y - 36.dp)
                            .size(72.dp)
                            .clickable(role = Role.Button, onClickLabel = "Установить ${next.partName().lowercase()}") {
                                if (game.selectedPart == next) onAction(TelescopeGameAction.Install(next))
                            }
                            .semantics {
                                contentDescription = "Место для детали: ${next.partName()}"
                                stateDescription = if (game.selectedPart == next) "Деталь выбрана" else "Сначала выбери деталь"
                            }, contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(TelescopeGold.copy(alpha = .14f))
                                drawCircle(TelescopeGold, radius = size.minDimension / 2 - 3.dp.toPx(),
                                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 8f))))
                            }
                            Text("${game.installedCount + 1}", fontFamily = Nunito, color = TelescopeCream,
                                fontWeight = FontWeight.Black, fontSize = 24.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TelescopePart.entries.forEach { part ->
                    TelescopePartTray(part, game, boardBounds, onAction, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TelescopePartTray(
    part: TelescopePart,
    game: TelescopeAssembly,
    boardBounds: Rect,
    onAction: (TelescopeGameAction) -> Unit,
    modifier: Modifier,
) {
    var trayBounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember(game.installedCount) { mutableStateOf(Offset.Zero) }
    val installed = part.ordinal < game.installedCount
    val selected = game.selectedPart == part
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.onGloballyPositioned { trayBounds = it.boundsInRoot() }
        .zIndex(if (drag != Offset.Zero) 2f else 0f)) {
        Column(Modifier.fillMaxSize().background(Color(0x55352317), shape)
            .border(if (selected) 2.dp else 1.dp,
                TelescopeGold.copy(alpha = if (selected) 1f else .45f), shape)
            .clickable(enabled = !installed, role = Role.Button) { onAction(TelescopeGameAction.Select(part)) }
            .semantics { stateDescription = if (installed) "Установлено" else if (selected) "Выбрано" else "Не установлено" },
            horizontalAlignment = Alignment.CenterHorizontally) {
            GameArtwork(part.artwork(), part.partName(),
                Modifier.weight(1f).fillMaxWidth()
                    .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                    .alpha(if (installed) .3f else 1f)
                    .pointerInput(part, game.installedCount, boardBounds) {
                        if (!installed) detectDragGestures(
                            onDragStart = { onAction(TelescopeGameAction.Select(part)) },
                            onDragCancel = { drag = Offset.Zero },
                            onDragEnd = {
                                val drop = trayBounds.center + drag
                                val target = telescopeTarget(part)
                                val destination = Offset(boardBounds.left + boardBounds.width * target.x,
                                    boardBounds.top + boardBounds.height * target.y)
                                if ((drop - destination).getDistance() < min(boardBounds.width, boardBounds.height) * .3f) {
                                    onAction(TelescopeGameAction.Install(part))
                                }
                                drag = Offset.Zero
                            },
                        ) { change, amount -> change.consume(); drag += amount }
                    })
            Text(if (installed) "Готово" else part.partName(), fontFamily = Nunito,
                fontWeight = FontWeight.Bold, color = TelescopeCream, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

private data class TelescopePartGeometry(val x: Float, val y: Float, val size: Float)
private fun telescopeGeometry(part: TelescopePart): TelescopePartGeometry = when (part) {
    TelescopePart.TRIPOD -> TelescopePartGeometry(.16f, .49f, .78f)
    TelescopePart.TUBE -> TelescopePartGeometry(.189f, .055f, .82f)
    TelescopePart.LENS -> TelescopePartGeometry(.064f, .636f, .20f)
}
private fun telescopeTarget(part: TelescopePart): Offset = when (part) {
    TelescopePart.TRIPOD -> Offset(.56f, .75f)
    TelescopePart.TUBE -> Offset(.62f, .32f)
    TelescopePart.LENS -> Offset(.16f, .55f)
}
private fun TelescopePart.artwork(): Int = when (this) {
    TelescopePart.TRIPOD -> R.drawable.story_game_telescope_tripod
    TelescopePart.TUBE -> R.drawable.story_game_telescope_tube
    TelescopePart.LENS -> R.drawable.story_game_telescope_lens
}
private fun TelescopePart.partName(): String = when (this) {
    TelescopePart.TRIPOD -> "Штатив"
    TelescopePart.TUBE -> "Труба"
    TelescopePart.LENS -> "Окуляр"
}
private fun TelescopePart.assemblyInstruction(): String = when (this) {
    TelescopePart.TRIPOD -> "Перенеси штатив на отмеченное место."
    TelescopePart.TUBE -> "Поставь трубу на штатив."
    TelescopePart.LENS -> "Вставь окуляр в трубу."
}
