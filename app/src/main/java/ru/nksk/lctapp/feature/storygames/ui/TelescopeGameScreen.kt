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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopeAssembly
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopePart
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopeStage

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
            game.completed -> "Звёзды стали ближе!"
            game.assembled -> "Двигай ползунок, пока звёзды не станут чёткими."
            game.stage == TelescopeStage.MOUNT -> "Крути пальцем прямо на винтах, как отвёрткой. Затяни оба крепления."
            game.stage == TelescopeStage.THREAD -> "Обведи окуляр пальцем по часовой стрелке, чтобы вкрутить его."
            else -> "${game.nextPart!!.assemblyInstruction()} Перенеси деталь на её место."
        },
        onExit = onExit,
        footer = {
            if (game.completed) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (onCompleted == null) "На телескоп удалось накопить. Теперь можно рассмотреть далёкие звёзды."
                        else "Телескоп настроен. Теперь Смотритель сможет рассмотреть звёзды.",
                        fontFamily = Nunito, color = TelescopeCream, textAlign = TextAlign.Center,
                        fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
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
                    GameActionButton(if (game.inFocus) "Рассмотреть звёзды" else "Наведи резкость",
                        { onAction(TelescopeGameAction.Observe) }, enabled = game.inFocus)
                }
            } else if (game.stage == TelescopeStage.MOUNT || game.stage == TelescopeStage.THREAD) {
                Unit
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
        if (game.stage == TelescopeStage.MOUNT || game.stage == TelescopeStage.THREAD) {
            TelescopeConnection(game, onAction, Modifier.fillMaxSize())
        } else {
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
}

@Composable
private fun TelescopeConnection(
    game: TelescopeAssembly,
    onAction: (TelescopeGameAction) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(28.dp))) {
        if (game.stage == TelescopeStage.MOUNT) {
            // The interaction centres follow the actual bolt heads in the full sprite canvases.
            val unit = minOf(maxWidth * 1.65f, maxHeight * 1.5f)
            val originX = maxWidth * .5f - unit * .48f
            val originY = maxHeight * .5f - unit * .64f
            GameArtwork(R.drawable.story_game_telescope_tripod, null,
                Modifier.offset(originX + unit * .16f, originY + unit * .49f)
                    .wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(unit * .78f))
            GameArtwork(R.drawable.story_game_telescope_tube, null,
                Modifier.offset(originX + unit * .189f, originY + unit * .055f)
                    .wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(unit * .82f))
            listOf(Offset(.4924f, .5634f), Offset(.4642f, .724f)).forEachIndexed { index, point ->
                TelescopeTwistTarget(
                    label = "Крепление ${index + 1}",
                    degrees = if (index == 0) game.firstFastener else game.secondFastener,
                    limit = TelescopeAssembly.MOUNT_TURN,
                    selected = game.selectedFastener == index,
                    onSelect = { onAction(TelescopeGameAction.SelectFastener(index)) },
                    onTurn = { onAction(TelescopeGameAction.TurnFastener(index, it)) },
                    onRelease = { onAction(TelescopeGameAction.FinishTurning) },
                    modifier = Modifier.offset(originX + unit * point.x - 44.dp,
                        originY + unit * point.y - 44.dp).size(88.dp),
                )
            }
        } else {
            val eyepieceSize = minOf(maxWidth * .9f, maxHeight * .9f)
            val eyeX = (maxWidth - eyepieceSize) / 2
            val eyeY = maxHeight * .5f - eyepieceSize * .52f
            val tubeSize = maxWidth * 1.2f
            val threadX = eyeX + eyepieceSize * .85f
            val threadY = eyeY + eyepieceSize * .24f
            GameArtwork(R.drawable.story_game_telescope_tube, null,
                Modifier.offset(threadX - tubeSize * .055f, threadY - tubeSize * .77f)
                    .wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(tubeSize))
            GameArtwork(R.drawable.story_game_telescope_lens, null,
                Modifier.offset(eyeX, eyeY).size(eyepieceSize).graphicsLayer {
                    // A small movement along the thread shows the eyepiece seating into the tube.
                    translationX = 4.dp.toPx() * game.eyepieceTurn / TelescopeAssembly.EYEPIECE_TURN
                    translationY = -3.dp.toPx() * game.eyepieceTurn / TelescopeAssembly.EYEPIECE_TURN
                })
            TelescopeTwistTarget(
                label = "Окуляр", degrees = game.eyepieceTurn,
                limit = TelescopeAssembly.EYEPIECE_TURN, selected = true, onSelect = {},
                onTurn = { onAction(TelescopeGameAction.TurnEyepiece(it)) },
                onRelease = { onAction(TelescopeGameAction.FinishTurning) },
                modifier = Modifier.offset(eyeX + eyepieceSize * .25f - 68.dp,
                    eyeY + eyepieceSize * .68f - 68.dp).size(136.dp),
                drawHead = false,
            )
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

@Composable
private fun TelescopeTwistTarget(
    label: String,
    degrees: Float,
    limit: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onTurn: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    drawHead: Boolean = true,
) {
    val currentTurn by rememberUpdatedState(onTurn)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentRelease by rememberUpdatedState(onRelease)
    val ready = degrees >= limit - TelescopeAssembly.TURN_TOLERANCE
    Canvas(modifier
        .semantics {
            contentDescription = label
            stateDescription = if (ready) "Закреплено" else "Затянуто на ${(degrees / limit * 100).toInt()} процентов"
            customActions = listOf(
                CustomAccessibilityAction("Затянуть") { onTurn(45f); onRelease(); true },
                CustomAccessibilityAction("Ослабить") { onTurn(-45f); onRelease(); true },
            )
        }
        .pointerInput(drawHead) {
            var lastAngle = 0f
            var tracking = false
            fun angle(position: Offset): Float =
                (atan2(position.y - size.height / 2f, position.x - size.width / 2f) * 180f / PI).toFloat()
            detectDragGestures(
                onDragStart = { position ->
                    currentSelect()
                    lastAngle = angle(position)
                    tracking = (position - Offset(size.width / 2f, size.height / 2f)).getDistance() > size.width * .12f
                },
                onDragEnd = { currentRelease() },
            ) { change, _ ->
                change.consume()
                val position = change.position
                val nextAngle = angle(position)
                val outsideHub = (position - Offset(size.width / 2f, size.height / 2f)).getDistance() > size.width * .12f
                if (tracking && outsideHub) {
                    val delta = ((nextAngle - lastAngle + 540f) % 360f) - 180f
                    if (kotlin.math.abs(delta) < 100f) currentTurn(delta)
                }
                tracking = outsideHub
                lastAngle = nextAngle
            }
        }) {
        val radius = size.minDimension * .37f
        drawCircle(TelescopeCream.copy(alpha = if (selected) .5f else .3f), radius,
            style = Stroke(1.5.dp.toPx()))
        drawArc(TelescopeGold, -90f, 360f * (if (ready) 1f else degrees / limit), false,
            topLeft = center - Offset(radius, radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        if (drawHead) {
            val head = size.minDimension * .22f
            drawCircle(Color(0x55301B0C), head + 2.dp.toPx(), center + Offset(1.dp.toPx(), 2.dp.toPx()))
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFFDE8C), Color(0xFFB77D35), Color(0xFF6C411C))), head)
            drawCircle(Color(0xFF8E5A27), head, style = Stroke(1.dp.toPx()))
            rotate(degrees) {
                drawLine(Color(0xFF4B2F1B), center - Offset(head * .62f, 0f),
                    center + Offset(head * .62f, 0f), 4.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFFFFE4A2), center - Offset(head * .55f, 2.dp.toPx()),
                    center + Offset(head * .55f, -2.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun TelescopeStarLens(game: TelescopeAssembly, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics {
        contentDescription = "Звёздное небо в телескопе"
        stateDescription = if (game.inFocus) "Звёзды видны чётко" else "Звёзды размыты"
    }) {
        val radius = size.minDimension * .44f
        drawCircle(Color(0xFF39251A), radius + 12.dp.toPx(), center)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF20395C), Color(0xFF070F20)), center, radius), radius, center)
        drawCircle(Brush.linearGradient(listOf(Color(0xFF8C5527), TelescopeGold, Color(0xFF9F6A31))),
            radius + 5.dp.toPx(), center, style = Stroke(8.dp.toPx()))
        val stars = listOf(Offset(-.52f, -.3f), Offset(-.22f, -.48f), Offset(.02f, -.15f),
            Offset(.38f, -.22f), Offset(.5f, .22f), Offset(.15f, .48f), Offset(-.25f, .23f))
        val points = stars.map { center + it * radius }
        if (game.completed) points.zipWithNext().forEach { (from, to) ->
            drawLine(TelescopeGold.copy(alpha = .55f), from, to, 1.5.dp.toPx(), StrokeCap.Round)
        }
        points.forEachIndexed { index, point ->
            val halo = (5 + (1 - game.sharpness) * 28).dp.toPx()
            drawCircle(Brush.radialGradient(listOf(TelescopeCream.copy(alpha = .7f), Color.Transparent), point, halo), halo, point)
            val starSize = (1.7f + game.sharpness * 1.8f).dp.toPx()
            drawCircle(TelescopeCream.copy(alpha = .3f + game.sharpness * .7f), starSize, point)
            if (game.inFocus && index % 2 == 0) {
                drawLine(TelescopeCream, point - Offset(6.dp.toPx(), 0f), point + Offset(6.dp.toPx(), 0f), 1.dp.toPx())
                drawLine(TelescopeCream, point - Offset(0f, 6.dp.toPx()), point + Offset(0f, 6.dp.toPx()), 1.dp.toPx())
            }
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
    TelescopePart.TRIPOD -> "Сначала поставь штатив."
    TelescopePart.TUBE -> "Закрепи трубу на штативе."
    TelescopePart.LENS -> "Осталось вставить окуляр."
}
