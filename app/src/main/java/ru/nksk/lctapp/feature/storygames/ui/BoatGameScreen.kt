package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.BoatJourney
import ru.nksk.lctapp.domain.minigame.chapterone.BoatPhase
import ru.nksk.lctapp.domain.minigame.chapterone.BoatPlan
import ru.nksk.lctapp.domain.minigame.chapterone.BoatRepair
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun BoatGameEntry(
    onExit: () -> Unit,
    repairCoins: Int? = null,
    onPaidRepair: (suspend (BoatRepairRequest) -> BoatRepairPaymentResult)? = null,
    onArrived: ((BoatJourney) -> Unit)? = null,
    startPrepared: Boolean = false,
    interactionBlocked: Boolean = false,
) {
    val model = hiltViewModel<BoatGameViewModel>()
    val state by model.uiState.collectAsStateWithLifecycle()
    val externalPayment = onPaidRepair != null
    val paymentHandler by rememberUpdatedState(onPaidRepair)
    val blocked by rememberUpdatedState(interactionBlocked)
    val onAction: (BoatGameAction) -> Unit = remember(model, externalPayment) {
        { action ->
            if (blocked) Unit
            else if (action == BoatGameAction.RequestPaidRepair) model.requestPaidRepair(externalPayment)
            else model.onAction(action)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, startPrepared) { model.setStartPrepared(startPrepared) }
    LaunchedEffect(model, lifecycle, interactionBlocked) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.setScreenActive(!interactionBlocked)
            try { awaitCancellation() } finally { model.setScreenActive(false) }
        }
    }
    LaunchedEffect(model, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.paymentRequest.collectLatest { request ->
                if (request != null) {
                    val result = try {
                        val handler = paymentHandler
                        when {
                            handler != null -> handler(request)
                            model.isPracticePayment(request) -> BoatRepairPaymentResult.Paid
                            else -> BoatRepairPaymentResult.Failed
                        }
                    } catch (cancelled: CancellationException) {
                        // Resume retries the same request; a host write may already have committed.
                        throw cancelled
                    } catch (_: Exception) {
                        BoatRepairPaymentResult.Failed
                    }
                    model.confirmPayment(request, result)
                }
            }
        }
    }
    val exit = { if (!blocked) onExit() }
    BackHandler(onBack = exit)
    BoatGameScreen(state, model.motion, onAction, exit,
        onArrived = onArrived?.let { completed -> { if (!blocked) completed(model.motion.value) } },
        repairCoins = if (externalPayment) repairCoins else state.reserve,
        externalPayment = externalPayment, interactionBlocked = interactionBlocked)
}

@Composable
private fun BoatGameScreen(
    state: BoatGameUiState,
    motion: StateFlow<BoatJourney>,
    onAction: (BoatGameAction) -> Unit,
    onExit: () -> Unit,
    onArrived: (() -> Unit)?,
    repairCoins: Int?,
    externalPayment: Boolean,
    interactionBlocked: Boolean,
) {
    val instruction = when (state.phase) {
        BoatPhase.PREPARATION -> "Билет и обед уже в плане. Реши, что сделать с двумя оставшимися монетами."
        BoatPhase.SAILING -> "Веди лодку пальцем и обходи камни. В конце реки нас ждёт причал."
        BoatPhase.LANDING -> "Причал близко, швартуемся!"
        BoatPhase.DAMAGED -> "Лодка задела камень и остановилась. Выбери, как продолжить."
        BoatPhase.TYING -> "Проведи верёвку через три кольца по порядку."
        BoatPhase.ARRIVED -> when (state.repair) {
            BoatRepair.PAID -> "Лодка починена, и мы добрались до причала."
            BoatRepair.MANUAL -> "Ты починил крепление и добрался до причала."
            BoatRepair.NONE -> "Добрались до причала!"
        }
    }
    ChapterGameScaffold(
        title = "Поездка к звёздам",
        instruction = instruction,
        onExit = onExit,
        footer = {
            when (state.phase) {
                BoatPhase.PREPARATION -> GameActionButton("Отправиться", { onAction(BoatGameAction.Launch) },
                    enabled = state.plan != null)
                BoatPhase.SAILING, BoatPhase.LANDING -> Unit
                BoatPhase.ARRIVED -> if (onArrived != null) GameActionButton("Продолжить", onArrived,
                    enabled = !interactionBlocked)
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameActionButton("Ещё раз", { onAction(BoatGameAction.Restart) }, Modifier.weight(1f))
                        GameActionButton("К играм", onExit, Modifier.weight(1f))
                    }
                else -> Unit
            }
        },
    ) {
        when (state.phase) {
            BoatPhase.PREPARATION -> BoatPreparation(state.plan, onAction)
            BoatPhase.SAILING, BoatPhase.LANDING -> BoatRiver(motion, onAction)
            BoatPhase.DAMAGED -> BoatRepairChoice(state, repairCoins, externalPayment, onAction)
            BoatPhase.TYING -> BoatKnot(state.knotPoints, onAction)
            BoatPhase.ARRIVED -> BoatArrival(state, externalPayment)
        }
    }
}

@Composable
private fun BoatPreparation(plan: BoatPlan?, onAction: (BoatGameAction) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GamePlaque(Modifier.fillMaxWidth(), light = true, fixedEdges = true) {
            Text("Монеты для этой поездки: ${BoatJourney.ALLOWANCE}", color = GameInk, fontFamily = Nunito,
                fontSize = 15.sp, modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
        }
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(22.dp))) {
            GameArtwork(R.drawable.location_pier_day, null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alignment = Alignment.Center)
            GameArtwork(R.drawable.story_game_boat, null, Modifier.align(Alignment.Center).size(105.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoatExpense("Билет", BoatJourney.TICKET_PRICE, R.drawable.collection_stars_ticket, Modifier.weight(1f))
            BoatExpense("Обед", BoatJourney.FOOD_PRICE, R.drawable.menu_satiety_fed_v2, Modifier.weight(1f))
        }
        Text("Осталось 2 монеты", color = Color(0xFFFFE9B3), fontFamily = Nunito, fontSize = 17.sp)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoatPlanChoice("Про запас", plan == BoatPlan.RESERVE, Modifier.weight(1f)) {
                onAction(BoatGameAction.ChoosePlan(BoatPlan.RESERVE))
            }
            BoatPlanChoice("Открытка", plan == BoatPlan.POSTCARD, Modifier.weight(1f)) {
                onAction(BoatGameAction.ChoosePlan(BoatPlan.POSTCARD))
            }
        }
    }
}

@Composable
private fun BoatExpense(label: String, coins: Int, artwork: Int, modifier: Modifier) {
    GamePlaque(modifier.heightIn(min = 88.dp), light = true, fixedEdges = true) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GameArtwork(artwork, null, Modifier.size(42.dp))
            Column {
                Text(label, color = GameInk, fontFamily = Nunito, fontSize = 14.sp)
                Text("$coins монет${if (coins == 2) "ы" else ""}", color = GameInk.copy(alpha = .75f),
                    fontFamily = Nunito, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BoatPlanChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GamePlaque(modifier.heightIn(min = 82.dp)
        .border(if (selected) 2.dp else 0.dp, if (selected) Color(0xFFFFD47C) else Color.Transparent,
            RoundedCornerShape(18.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick), light = true, fixedEdges = true) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = GameInk, fontFamily = Nunito, fontSize = 16.sp, textAlign = TextAlign.Center)
            Text(if (selected) "Выбрано · 2" else "2 монеты", color = GameInk.copy(alpha = .75f),
                fontFamily = Nunito, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BoatRepairChoice(
    state: BoatGameUiState,
    repairCoins: Int?,
    externalPayment: Boolean,
    onAction: (BoatGameAction) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameArtwork(R.drawable.story_game_boat, null, Modifier.height(106.dp).fillMaxWidth())
        GamePlaque(Modifier.fillMaxWidth(), light = true, fixedEdges = true) {
            Text(when {
                repairCoins == null -> "Ремонт: ${BoatJourney.REPAIR_PRICE} монеты"
                externalPayment -> "На ремонт: ${repairCoins.coerceAtLeast(0)}"
                else -> "В запасе: $repairCoins"
            }, color = GameInk, fontFamily = Nunito, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.Center))
        }
        state.paymentMessage?.let { Text(it, color = Color(0xFFFFE9B3), fontFamily = Nunito,
            fontSize = 14.sp, textAlign = TextAlign.Center) }
        GameActionButton(if (state.paymentRetryRequired) "Повторить оплату" else "Ремонт · ${BoatJourney.REPAIR_PRICE} монеты",
            { onAction(BoatGameAction.RequestPaidRepair) },
            enabled = state.paymentRetryRequired || repairCoins == null || repairCoins >= BoatJourney.REPAIR_PRICE,
            loading = state.paymentPending)
        val otherChoicesEnabled = !state.paymentPending && !state.paymentRetryRequired
        GameActionButton("Починить самому", { onAction(BoatGameAction.BeginKnot) }, enabled = otherChoicesEnabled)
        GameActionButton("Начать сначала", { onAction(BoatGameAction.Restart) }, enabled = otherChoicesEnabled)
    }
}

@Composable
private fun BoatArrival(state: BoatGameUiState, externalPayment: Boolean) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
        GameArtwork(R.drawable.location_pier_day, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xBB102B35)))))
        GameArtwork(R.drawable.story_game_boat, null, Modifier.align(Alignment.Center).size(190.dp))
        GamePlaque(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            light = true, fixedEdges = true) {
            Text(when {
                externalPayment -> "Поездка завершена"
                state.plan == BoatPlan.POSTCARD -> "Открытка осталась на память"
                else -> "В запасе: ${state.reserve}"
            },
                modifier = Modifier.align(Alignment.Center), color = GameInk, fontFamily = Nunito,
                fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BoatRiver(motion: StateFlow<BoatJourney>, onAction: (BoatGameAction) -> Unit) {
    val frame = motion.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
        .border(2.dp, Color(0xFFB88946), RoundedCornerShape(24.dp))
        .semantics {
            contentDescription = "Река. Веди лодку пальцем."
            customActions = listOf(
                CustomAccessibilityAction("Сместить лодку влево") {
                    onAction(BoatGameAction.Nudge(-1)); true
                },
                CustomAccessibilityAction("Сместить лодку вправо") {
                    onAction(BoatGameAction.Nudge(1)); true
                },
            )
        }
        .pointerInput(onAction) {
            awaitEachGesture {
                val down = awaitFirstDown()
                onAction(BoatGameAction.Steer(down.position.x / size.width.coerceAtLeast(1)))
                do {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    onAction(BoatGameAction.Steer(pointer.position.x / size.width.coerceAtLeast(1)))
                    pointer.consume()
                } while (event.changes.any { it.pressed })
            }
        }) {
        Canvas(Modifier.fillMaxSize()) { drawBoatRiver(frame.value) }
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val spriteWidth = (maxWidth * .26f).coerceIn(68.dp, 106.dp).coerceAtMost(maxHeight * .32f)
        GameArtwork(R.drawable.story_game_boat, null,
            Modifier.align(Alignment.Center).width(spriteWidth).height(spriteWidth * 1.35f)
                .graphicsLayer {
                    val current = frame.value
                    translationX = widthPx * (current.x - .5f)
                    translationY = heightPx * (.77f - .5f)
                    rotationZ = (current.targetX - current.x) * 28f
                })
        BoatRouteProgress(frame, Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun BoatRouteProgress(frame: State<BoatJourney>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("До причала", color = Color(0xFFFFE9B3), fontFamily = Nunito, fontSize = 12.sp)
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            drawLine(Color(0xAA14343D), Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                size.height, StrokeCap.Round)
            drawLine(Color(0xFFEBC26B), Offset(0f, size.height / 2),
                Offset(size.width * frame.value.distance, size.height / 2), size.height, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawBoatRiver(frame: BoatJourney) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF153F52), Color(0xFF326D7C), Color(0xFF194C61))))
    val shift = (frame.distance * 4f) % 1f
    for (side in 0..1) {
        val bank = Path().apply {
            moveTo(if (side == 0) 0f else size.width, 0f)
            for (step in 0..24) {
                val y = step / 24f
                val edge = .08f + sin((y + shift) * PI * 3).toFloat() * .023f
                lineTo(size.width * if (side == 0) edge else 1f - edge, y * size.height)
            }
            lineTo(if (side == 0) 0f else size.width, size.height)
            close()
        }
        drawPath(bank, Color(0xFF5C6948))
        drawPath(bank, Color(0xFFB6AD6E), style = Stroke(5.dp.toPx()))
    }
    repeat(18) { i ->
        val y = ((i / 18f + frame.distance * 3f) % 1f) * size.height
        val x = (.18f + ((i * 17) % 61) / 100f) * size.width
        drawArc(Color(0xFFAFD3CA).copy(alpha = .20f), 25f, 130f, useCenter = false,
            topLeft = Offset(x - 14.dp.toPx(), y - 3.dp.toPx()), size = Size(28.dp.toPx(), 6.dp.toPx()),
            style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
    }
    BoatJourney.rocks.forEach { rock ->
        val y = (.77f - (rock.distance - frame.distance) / .31f) * size.height
        if (y in -50f..size.height + 50f) {
            val x = rock.x * size.width
            val radius = size.width * .065f
            drawOval(Color(0x7720302E), Offset(x - radius * 1.25f, y - radius * .5f),
                Size(radius * 2.5f, radius * 1.6f))
            val path = Path().apply {
                moveTo(x - radius, y + radius * .25f)
                lineTo(x - radius * .8f, y - radius * .55f)
                lineTo(x - radius * .2f, y - radius * .85f)
                lineTo(x + radius * .7f, y - radius * .5f)
                lineTo(x + radius, y + radius * .3f)
                lineTo(x + radius * .25f, y + radius * .65f)
                close()
            }
            drawPath(path, Brush.linearGradient(listOf(Color(0xFFB6B297), Color(0xFF5F7066)),
                Offset(x, y - radius), Offset(x, y + radius)))
            drawPath(path, Color(0xFF4C5B55), style = Stroke(1.5.dp.toPx()))
            drawLine(Color(0xFFCCC7A4), Offset(x - radius * .6f, y - radius * .45f),
                Offset(x + radius * .35f, y - radius * .35f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
    val boatY = size.height * .77f
    val boatX = size.width * frame.x
    val wake = Path().apply {
        moveTo(boatX - 9.dp.toPx(), boatY + 35.dp.toPx())
        lineTo(boatX - 21.dp.toPx(), boatY + 68.dp.toPx())
        moveTo(boatX + 9.dp.toPx(), boatY + 35.dp.toPx())
        lineTo(boatX + 21.dp.toPx(), boatY + 68.dp.toPx())
    }
    drawPath(wake, Color(0xFFD4E2CE).copy(alpha = .45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    if (frame.distance > .83f) {
        val dockY = (.77f - (1f - frame.distance) / .31f) * size.height
        val left = size.width * .30f
        val right = size.width * .70f
        repeat(5) { index ->
            drawRoundRect(Color(0xFF7D522D), Offset(left, dockY + index * 10.dp.toPx()),
                Size(right - left, 9.dp.toPx()))
            drawLine(Color(0xFFB79058), Offset(left + 3.dp.toPx(), dockY + index * 10.dp.toPx()),
                Offset(right - 3.dp.toPx(), dockY + index * 10.dp.toPx()), 1.5.dp.toPx())
        }
    }
}

private val knotRings = listOf(Offset(.22f, .25f), Offset(.76f, .49f), Offset(.35f, .76f))

@Composable
private fun BoatKnot(completed: Int, onAction: (BoatGameAction) -> Unit) {
    val currentCompleted by rememberUpdatedState(completed)
    var finger by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF3B352B), Color(0xFF1C2B30))))
        .border(2.dp, Color(0xFFB88946), RoundedCornerShape(24.dp))
        .semantics(mergeDescendants = true) {
            contentDescription = "Узел. Пройдено колец: $completed из 3"
            if (completed < 3) customActions = listOf(CustomAccessibilityAction("Протянуть к кольцу ${completed + 1}") {
                onAction(BoatGameAction.ThreadRing(completed))
                true
            })
        }
        .pointerInput(onAction) {
            fun follow(point: Offset) {
                finger = point
                val index = currentCompleted
                if (index !in knotRings.indices) return
                val target = Offset(knotRings[index].x * size.width, knotRings[index].y * size.height)
                if ((point - target).getDistance() <= 40.dp.toPx()) onAction(BoatGameAction.ThreadRing(index))
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                follow(down.position)
                do {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    follow(pointer.position)
                    pointer.consume()
                } while (event.changes.any { it.pressed })
                finger = null
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val points = knotRings.map { Offset(it.x * size.width, it.y * size.height) }
            val line = Path().apply {
                moveTo(0f, size.height * .15f)
                points.take(completed).forEach { lineTo(it.x, it.y) }
                finger?.let { lineTo(it.x, it.y) }
            }
            drawPath(line, Color(0xFF54341B), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            drawPath(line, Color(0xFFD2AC6C), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                drawCircle(Color(0x55000000), 31.dp.toPx(), point + Offset(3.dp.toPx(), 4.dp.toPx()))
                drawCircle(if (index == completed) Color(0xFFFFD482) else Color(0xFFB19259),
                    28.dp.toPx(), point, style = Stroke(7.dp.toPx()))
                if (index < completed) drawCircle(Color(0xFFB9D38E), 14.dp.toPx(), point)
            }
        }
        knotRings.forEachIndexed { index, position ->
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
            Text("${index + 1}", color = Color(0xFFFFEAC4), fontFamily = Nunito, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    translationX = widthPx * (position.x - .5f)
                    translationY = heightPx * (position.y - .5f)
                }.semantics { contentDescription = "Кольцо ${index + 1}" })
        }
    }
}
