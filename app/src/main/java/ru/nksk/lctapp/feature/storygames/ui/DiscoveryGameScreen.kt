package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun DiscoveryGameEntry(scene: DiscoveryScene, onExit: () -> Unit,
    onCompleted: (ChapterDiscovery) -> Unit, interactionBlocked: Boolean = false) {
    val model = hiltViewModel<DiscoveryGameViewModel>(key = "discovery:${scene.name}")
    val game by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model, scene) { model.prepare(scene) }
    BackHandler(enabled = interactionBlocked) {}
    if (game.scene != scene) return
    DiscoveryGameScreen(game, { if (!interactionBlocked) model.onAction(it) },
        { if (!interactionBlocked) onExit() }, {
            val current = model.uiState.value
            if (!interactionBlocked && current.scene == scene && current.complete) onCompleted(current)
        }, interactionBlocked)
}

@Composable
internal fun DiscoveryGameScreen(game: ChapterDiscovery, onAction: (DiscoveryAction) -> Unit,
    onExit: () -> Unit, onCompleted: () -> Unit, interactionBlocked: Boolean = false) {
    val title = when (game.scene) {
        DiscoveryScene.OBSERVATORY -> "Обсерватория"
        DiscoveryScene.PLATE_HAND, DiscoveryScene.PLATE_SOLUTION -> "Знакомый знак"
        DiscoveryScene.INSCRIPTION -> "Надпись на пластине"
        DiscoveryScene.EMBLEM -> "Старая эмблема"
        DiscoveryScene.SIGNAL_POST -> "Каменный пост"
        DiscoveryScene.FINALE -> "Ночь наблюдений"
    }
    ChapterGameScaffold(title, discoveryInstruction(game), onExit, footer = {
        if (game.complete) GameActionButton("Продолжить", onCompleted, interactionBlocked = interactionBlocked)
        else if (game.scene == DiscoveryScene.SIGNAL_POST) GameActionButton("Запомнить отражение",
            { onAction(DiscoveryAction.CaptureReflection) }, enabled = game.reflectionReady,
            interactionBlocked = interactionBlocked)
    }) {
        when (game.scene) {
            DiscoveryScene.OBSERVATORY -> ObservatoryDiscovery(game, onAction, interactionBlocked)
            DiscoveryScene.PLATE_HAND, DiscoveryScene.PLATE_SOLUTION -> PlateDiscovery(game, onAction, interactionBlocked)
            DiscoveryScene.INSCRIPTION -> InscriptionDiscovery(game, onAction, interactionBlocked)
            DiscoveryScene.EMBLEM -> EmblemDiscovery(game, onAction, interactionBlocked)
            DiscoveryScene.SIGNAL_POST -> PostDiscovery(game, onAction, interactionBlocked)
            DiscoveryScene.FINALE -> FinaleDiscovery(game, onAction, interactionBlocked)
        }
    }
}

private fun discoveryInstruction(game: ChapterDiscovery): String = when (game.scene) {
    DiscoveryScene.OBSERVATORY -> when (game.stage) {
        0 -> "Раздвинь створки купола. Их можно и просто коснуться."
        1 -> "Сними чехол с телескопа Смотрителя."
        else -> "Теперь можно смотреть на звёзды вместе со Смотрителем."
    }
    DiscoveryScene.PLATE_HAND, DiscoveryScene.PLATE_SOLUTION -> if (game.complete) "На пластине тот же знак, что на ящике."
        else if (game.scene == DiscoveryScene.PLATE_SOLUTION) "Проведи губкой по пластине. Состав снимает налёт."
        else "Потри налёт пальцем, чтобы рассмотреть знак."
    DiscoveryScene.INSCRIPTION -> if (game.complete) "Надпись стала видна целиком."
        else "Проведи светом по трём строкам надписи."
    DiscoveryScene.EMBLEM -> when (game.stage) {
        0 -> "Переверни эмблему и посмотри на оборот."
        1 -> "Совмести зарисовку со знаком на эмблеме."
        else -> "Знак совпал. На обороте написано: «Служба путей»."
    }
    DiscoveryScene.SIGNAL_POST -> if (game.complete) "Три отражения появились в этом порядке."
        else "Направь луч в светлый круг на куполе."
    DiscoveryScene.FINALE -> when {
        game.complete -> "Башня — часть сети Смотрителей путей. Теперь знаем, куда идти."
        game.placed.size < 3 -> "Разложи находки на столе: знак, карту и запись огней."
        else -> "Соедини совпавшие отметки. На карте проявятся старые пути."
    }
}

/** The actual illustrated room is revealed behind two brass-bound wooden shutters. */
@Composable
private fun ObservatoryDiscovery(game: ChapterDiscovery, onAction: (DiscoveryAction) -> Unit, blocked: Boolean) {
    val opening by animateFloatAsState(if (game.stage > 0) 1f else 0f, tween(650), label = "observatory_shutters")
    val cover by animateFloatAsState(if (game.stage > 1) 0f else 1f, tween(450), label = "telescope_cover")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * .76f)
        Box(Modifier.size(width, width / .76f).clip(RoundedCornerShape(topStart = 90.dp, topEnd = 90.dp, bottomStart = 18.dp, bottomEnd = 18.dp))) {
            GameArtwork(R.drawable.location_observatory, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xBB11182B)))))
            GameArtwork(R.drawable.story_telescope_parts, "Телескоп Смотрителя",
                Modifier.fillMaxWidth(.85f).fillMaxHeight(.78f).align(Alignment.BottomCenter))
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width
                if (cover > 0f) {
                    val cloth = Path().apply {
                        moveTo(u * .25f, size.height * .30f); quadraticTo(u * .55f, size.height * .10f, u * .86f, size.height * .28f)
                        lineTo(u * .91f, size.height * .85f); quadraticTo(u * .65f, size.height * .77f, u * .40f, size.height * .90f)
                        lineTo(u * .15f, size.height * .82f); close()
                    }
                    drawPath(cloth, Brush.linearGradient(listOf(Color(0xFF23334F), Color(0xFF486580), Color(0xFF18233B))), alpha = cover)
                    drawPath(cloth, ClueGold.copy(alpha = cover * .7f), style = Stroke(2.dp.toPx()))
                    listOf(.34f, .52f, .71f).forEach { x -> drawLine(Color(0xFF10213A).copy(alpha = cover * .45f),
                        Offset(u * x, size.height * .31f), Offset(u * (x - .05f), size.height * .80f), u * .017f, StrokeCap.Round) }
                }
                if (opening < 1f) {
                    val panelWidth = u * .5f * (1f - opening)
                    listOf(0f, u - panelWidth).forEach { left ->
                        drawRect(Brush.horizontalGradient(listOf(Color(0xFF3D281E), Color(0xFF795337), Color(0xFF36241D))),
                            Offset(left, 0f), Size(panelWidth, size.height))
                        repeat(5) { i -> val x = left + panelWidth * i / 5f
                            drawLine(Color(0xFFBD8E50).copy(alpha = .28f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx()) }
                        listOf(.16f, .78f).forEach { y -> drawLine(ClueGold.copy(alpha = .65f), Offset(left, size.height * y),
                            Offset(left + panelWidth, size.height * y), u * .018f) }
                    }
                    listOf(panelWidth - u * .08f, u - panelWidth + u * .08f).forEach { x ->
                        drawRoundRect(Color(0xFFBB9455), Offset(x - u * .014f, size.height * .43f),
                            Size(u * .028f, size.height * .13f), CornerRadius(u * .014f))
                    }
                }
            }
            if (!game.complete) {
                val action = if (game.stage == 0) DiscoveryAction.OpenDome else DiscoveryAction.RemoveCover
                val label = if (game.stage == 0) "Открыть створки" else "Снять чехол"
                val current by rememberUpdatedState(onAction)
                Box(Modifier.fillMaxSize().discoverySwipe(!blocked, game.stage) { current(action) }
                    .semantics { contentDescription = label; role = Role.Button
                        if (!blocked) onClick(label) { onAction(action); true } })
                DiscoveryCaption(label, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }
        }
    }
}

@Composable
private fun PlateDiscovery(game: ChapterDiscovery, onAction: (DiscoveryAction) -> Unit, blocked: Boolean) {
    DiscoveryTable {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * (1448f / 1086f))
            val current by rememberUpdatedState(onAction)
            Box(Modifier.size(width, width * (1086f / 1448f))) {
                GameArtwork(R.drawable.story_story_plate, null, Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().discoveryTouch(!blocked && !game.complete) { x, y -> current(DiscoveryAction.Clean(x, y)) }
                    .semantics {
                        contentDescription = "Пластина со знаком. Очищено ${game.revealed.size} из 9 участков"
                        customActions = if (blocked) emptyList() else ChapterDiscoveryGame.dust.mapIndexedNotNull { index, p ->
                            if (index in game.revealed) null else CustomAccessibilityAction("Очистить участок ${index + 1}") {
                                onAction(DiscoveryAction.Clean(p.x, p.y)); true
                            }
                        }
                    }) {
                    ChapterDiscoveryGame.dust.forEachIndexed { index, p ->
                        if (index !in game.revealed) {
                            val center = Offset(p.x * size.width, p.y * size.height)
                            val radius = size.width * .18f
                            drawCircle(Brush.radialGradient(listOf(Color(0xFF625D48).copy(alpha = .96f),
                                Color(0xFFACA17B).copy(alpha = .90f), Color.Transparent), center, radius), radius, center)
                            repeat(8) { i -> val a = i * 2.399f + index
                                drawCircle(Color(0xFFCABE91).copy(alpha = .6f), size.width * .008f,
                                    center + Offset(cos(a), sin(a)) * radius * .60f) }
                        }
                    }
                    if (game.complete) {
                        val center = Offset(size.width * .80f, size.height * .28f)
                        drawCircle(ClueGold.copy(alpha = .20f), size.width * .12f, center)
                        drawCircle(ClueGold, size.width * .12f, center, style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun InscriptionDiscovery(game: ChapterDiscovery, onAction: (DiscoveryAction) -> Unit, blocked: Boolean) {
    var light by remember { mutableStateOf(Offset(.50f, .26f)) }
    val lines = listOf("Когда северная башня погаснет,", "ищи три ответа", "в одном свете")
    DiscoveryTable {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth * .94f, maxHeight * .90f)
            Box(Modifier.size(width, width / .90f)) {
                Canvas(Modifier.fillMaxSize()) { drawInscriptionTablet() }
                Column(Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    lines.forEachIndexed { index, line ->
                        Text(line, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                            lineHeight = 25.sp, textAlign = TextAlign.Center,
                            color = if (index in game.revealed) ClueCream else Color(0xFF3F3D35),
                            modifier = Modifier.fillMaxWidth().weight(1f).wrapContentHeight()
                                .clickable(enabled = !blocked && !game.complete, role = Role.Button,
                                    onClickLabel = "Осветить строку ${index + 1}") { onAction(DiscoveryAction.Illuminate(index)) })
                    }
                }
                val current by rememberUpdatedState(onAction)
                Canvas(Modifier.fillMaxSize().discoveryTouch(!blocked && !game.complete) { x, y ->
                    light = Offset(x, y)
                    if (x in .12f.. .88f && y in .10f.. .90f) current(DiscoveryAction.Illuminate(((y - .10f) / .80f * 3).toInt().coerceIn(0, 2)))
                }.semantics {
                    contentDescription = "Свет на надписи. Видно ${game.revealed.size} из 3 строк"
                    customActions = if (blocked) emptyList() else (0..2).filterNot { it in game.revealed }.map { i ->
                        CustomAccessibilityAction("Осветить строку ${i + 1}") { onAction(DiscoveryAction.Illuminate(i)); true }
                    }
                }) {
                    if (!game.complete) {
                        val center = Offset(light.x * size.width, light.y * size.height)
                        val radius = size.width * .40f
                        drawCircle(Brush.radialGradient(listOf(Color(0x55FFE7A5), Color.Transparent), center, radius), radius, center)
                        drawCircle(Color(0x55FFE7A5), size.width * .025f, center)
                    }
                }
                GameArtwork(R.drawable.deed_goods_lantern, null,
                    Modifier.size(62.dp).align(Alignment.BottomEnd).offset(x = 8.dp, y = 18.dp))
            }
        }
    }
}

@Composable
private fun EmblemDiscovery(game: ChapterDiscovery, onAction: (DiscoveryAction) -> Unit, blocked: Boolean) {
    val turn by animateFloatAsState(if (game.stage > 0) 180f else 0f, tween(600), label = "emblem_turn")
    var dragging by remember { mutableStateOf<Offset?>(null) }
    DiscoveryTable {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            Box(Modifier.size(side)) {
                if (turn < 90f) GameArtwork(R.drawable.story_service_emblem, "Эмблема службы",
                    Modifier.fillMaxSize().graphicsLayer { rotationY = turn; cameraDistance = 14 * density })
                else Canvas(Modifier.fillMaxSize().graphicsLayer { rotationY = turn - 180f; cameraDistance = 14 * density }) {
                    val center = Offset(size.width * .5f, size.height * .46f)
                    val radius = size.minDimension * .36f
                    drawCircle(Color(0xFF1F1712), radius + 7.dp.toPx(), center + Offset(0f, 5.dp.toPx()))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFCBA15D), Color(0xFF87602F), Color(0xFF4E341E)),
                        center - Offset(radius * .30f, radius * .30f), radius * 1.9f), radius, center)
                    drawCircle(ClueGold, radius * .92f, center, style = Stroke(2.dp.toPx()))
                    drawCircle(Color(0xFF5B3D25), radius * .82f, center, style = Stroke(1.dp.toPx()))
                    drawKeeperMark(center + Offset(0f, -radius * .05f), radius * .65f,
                        if (game.complete) ClueCream else Color(0xFF4A331E))
                }
                if (game.stage == 0) {
                    val current by rememberUpdatedState(onAction)
                    Box(Modifier.fillMaxSize().discoverySwipe(!blocked) { current(DiscoveryAction.FlipEmblem) }
                        .semantics { contentDescription = "Перевернуть эмблему"; role = Role.Button
                            if (!blocked) onClick("Перевернуть") { onAction(DiscoveryAction.FlipEmblem); true } })
                    DiscoveryCaption("Перевернуть", Modifier.align(Alignment.BottomCenter).padding(12.dp))
                } else {
                    if (game.complete) Text("СЛУЖБА ПУТЕЙ", fontFamily = Nunito, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold, color = ClueCream, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(top = side * .39f))
                    else {
                        val current by rememberUpdatedState(onAction)
                        Canvas(Modifier.fillMaxSize().discoveryTouch(!blocked) { x, y ->
                            dragging = Offset(x, y)
                            if (abs(x - .5f) < .15f && abs(y - .46f) < .15f) current(DiscoveryAction.MatchEmblem)
                        }.semantics { contentDescription = "Зарисовка знакомого знака. Совмести с эмблемой"
                            if (!blocked) onClick("Совместить знак") { onAction(DiscoveryAction.MatchEmblem); true } }) {
                            val at = dragging ?: Offset(.20f, .83f)
                            val c = Offset(at.x * size.width, at.y * size.height)
                            val r = size.width * .14f
                            drawRoundRect(Color(0xFFFAE9BE).copy(alpha = .88f), c - Offset(r, r), Size(r * 2, r * 2), CornerRadius(r * .1f))
                            drawKeeperMark(c, r * .82f, Color(0xFF644524))
                            val target = Offset(size.width * .5f, size.height * .46f)
                            drawCircle(ClueCream.copy(alpha = .65f), size.width * .21f, target,
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 7.dp.toPx()))))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostDiscovery(game: ChapterDiscovery, onAction: (DiscoveryAction) -> Unit, blocked: Boolean) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(ClueGold.copy(alpha = .22f), size.minDimension * .40f)
                        drawCircle(ClueGold, size.minDimension * .40f, style = Stroke(1.dp.toPx())) }
                    if (i in game.reflected) GameArtwork(discoveryLightArtwork(i), "Отражение ${i + 1}", Modifier.size(43.dp))
                    else Text("${i + 1}", color = ClueCream, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * .8f)
            val height = width / .8f
            val current by rememberUpdatedState(onAction)
            Box(Modifier.size(width, height)) {
                GameArtwork(R.drawable.story_signal_post, null,
                    Modifier.fillMaxWidth().fillMaxHeight(.80f).align(Alignment.BottomCenter))
                Canvas(Modifier.fillMaxSize()) {
                    val active = game.reflected.size.coerceAtMost(2)
                    val y = .20f + (.24f + active * .18f) * .80f
                    val origin = Offset(size.width * .49f, size.height * y)
                    val end = Offset(size.width * (.10f + game.focus * .80f), size.height * .09f)
                    val color = discoveryLightColor(active)
                    val dome = Path().apply { moveTo(size.width * .08f, size.height * .17f)
                        quadraticTo(size.width * .50f, -size.height * .10f, size.width * .92f, size.height * .17f) }
                    drawPath(dome, Color(0xFF71849E).copy(alpha = .95f), style = Stroke(size.width * .11f, cap = StrokeCap.Round))
                    drawPath(dome, ClueGold.copy(alpha = .5f), style = Stroke(1.dp.toPx()))
                    if (!game.complete) {
                        val beam = Path().apply { moveTo(origin.x, origin.y); lineTo(end.x - size.width * .07f, end.y)
                            lineTo(end.x + size.width * .07f, end.y); close() }
                        drawPath(beam, Brush.linearGradient(listOf(color.copy(alpha = .45f), color.copy(alpha = .06f)), origin, end))
                        drawLine(color.copy(alpha = .70f), origin, end, 2.dp.toPx(), StrokeCap.Round)
                        val target = Offset(size.width * (.10f + ChapterDiscoveryGame.reflectionTargets[active] * .80f), size.height * .09f)
                        drawCircle(ClueCream.copy(alpha = .15f), size.width * .09f, target)
                        drawCircle(if (game.reflectionReady) color else ClueCream, size.width * .06f, target, style = Stroke(2.dp.toPx()))
                        drawCircle(color, size.width * .025f, end)
                    }
                }
                if (!game.complete) Box(Modifier.fillMaxSize().discoveryTouch(!blocked) { x, _ ->
                    current(DiscoveryAction.Aim((x - .10f) / .80f))
                }.semantics { contentDescription = "Линза ${game.reflected.size + 1}. Направь отражение на светлый круг"
                    customActions = if (blocked) emptyList() else listOf(
                        CustomAccessibilityAction("Направить луч на круг") {
                            onAction(DiscoveryAction.Aim(ChapterDiscoveryGame.reflectionTargets[game.reflected.size])); true
                        })
                })
            }
        }
    }
}

@Composable
private fun FinaleDiscovery(game: ChapterDiscovery, onAction: (DiscoveryAction) -> Unit, blocked: Boolean) {
    DiscoveryTable {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(3) { i -> FindingSlot(i, i in game.placed, game.selected == i, !blocked && game.placed.size < 3,
                    { onAction(DiscoveryAction.PlaceFinding(i)) }, Modifier.weight(1f)) }
            }
            if (game.placed.size < 3) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GameArtwork(R.drawable.story_route_map, null, Modifier.fillMaxSize().alpha(.38f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(3) { i ->
                        val enabled = !blocked && i !in game.placed
                        Column(Modifier.weight(1f).alpha(if (i in game.placed) .28f else 1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (game.selected == i) ClueGold.copy(alpha = .22f) else Color.Transparent)
                            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Выбрать находку") { onAction(DiscoveryAction.SelectFinding(i)) }
                            .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            FindingArtwork(i, Modifier.fillMaxWidth().height(70.dp))
                            Text(findingLabel(i), fontFamily = Nunito, fontSize = 12.sp, color = ClueCream, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight)
                    val targetRadius = maxOf(.085f, 24f / side.value.coerceAtLeast(1f))
                    val currentTargetRadius by rememberUpdatedState(targetRadius)
                    val current by rememberUpdatedState(onAction)
                    val currentGame by rememberUpdatedState(game)
                    Box(Modifier.size(side)) {
                        Canvas(Modifier.fillMaxSize()) { drawDiscoveryNetwork(game) }
                        Box(Modifier.fillMaxSize().discoveryTouch(!blocked && !game.complete) { x, y ->
                            val point = ChapterDiscoveryGame.networkPoints.indices.minByOrNull { i ->
                                val p = ChapterDiscoveryGame.networkPoints[i]
                                (x - p.x) * (x - p.x) + (y - p.y) * (y - p.y)
                            }?.takeIf { i ->
                                val p = ChapterDiscoveryGame.networkPoints[i]
                                (x - p.x) * (x - p.x) + (y - p.y) * (y - p.y) <= currentTargetRadius * currentTargetRadius
                            }
                            if (point != null && point != currentGame.selected) current(DiscoveryAction.SelectPoint(point))
                        }.semantics { contentDescription = "Сеть старых путей. Соединено ${game.joined.size} из 4 участков"
                            customActions = if (blocked || game.complete) emptyList() else ChapterDiscoveryGame.networkPoints.indices.map { i ->
                                CustomAccessibilityAction("Отметка ${i + 1}") { onAction(DiscoveryAction.SelectPoint(i)); true }
                            }
                        })
                    }
                }
                if (game.complete) Text("Смотрители путей", color = ClueCream, fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FindingSlot(index: Int, placed: Boolean, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(4.dp).clip(RoundedCornerShape(13.dp))
        .background(Color(0x661C150F)).clickable(enabled = enabled, role = Role.Button,
            onClickLabel = "Положить находку") { onClick() }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
            FindingArtwork(index, Modifier.fillMaxSize().alpha(if (placed) 1f else .25f))
            if (selected && !placed) Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(ClueGold, cornerRadius = CornerRadius(9.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
        }
        Text(findingLabel(index), color = ClueCream, fontFamily = Nunito, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FindingArtwork(index: Int, modifier: Modifier) {
    when (index) {
        0 -> Canvas(modifier) {
            drawClueBoard(parchment = true)
            drawKeeperMark(center, size.minDimension * .33f, Color(0xFF74502E))
        }
        1 -> GameArtwork(R.drawable.story_route_map, "Карта", modifier)
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawClueBoard(parchment = true) }
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i -> GameArtwork(discoveryLightArtwork(i), null, Modifier.weight(1f).height(34.dp)) }
            }
        }
    }
}

private fun findingLabel(index: Int) = when (index) { 0 -> "Знак"; 1 -> "Карта"; else -> "Огни" }

@Composable
private fun DiscoveryTable(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        GameArtwork(R.drawable.story_game_workbench_surface, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x44251B14)))
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
private fun DiscoveryCaption(label: String, modifier: Modifier = Modifier) {
    GamePlaque(modifier, light = true, fixedEdges = true) {
        Text(label, color = ClueInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

private fun discoveryLightColor(index: Int) = when (index) { 0 -> Color(0xFFEA7155); 1 -> Color(0xFFF3C653); else -> Color(0xFF65B5ED) }
private fun discoveryLightArtwork(index: Int) = when (index) {
    0 -> R.drawable.deed_game_signal_red_on
    1 -> R.drawable.deed_game_signal_amber_on
    else -> R.drawable.deed_game_signal_blue_on
}

private fun DrawScope.drawInscriptionTablet() {
    val margin = size.width * .04f
    drawRoundRect(Color(0xFF211B16), Offset(margin, margin + 6.dp.toPx()),
        Size(size.width - 2 * margin, size.height - 2 * margin), CornerRadius(16.dp.toPx()))
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFF8B744E), Color(0xFF49493E), Color(0xFF232C2A))),
        Offset(margin, margin), Size(size.width - 2 * margin, size.height - 2 * margin), CornerRadius(16.dp.toPx()))
    drawRoundRect(ClueGold.copy(alpha = .7f), Offset(margin, margin), Size(size.width - 2 * margin, size.height - 2 * margin),
        CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx()))
    listOf(.11f to .10f, .89f to .10f, .11f to .90f, .89f to .90f).forEach { (x, y) ->
        drawCircle(Color(0xFFC6A365), size.width * .017f, Offset(size.width * x, size.height * y))
    }
}

private fun DrawScope.drawKeeperMark(center: Offset, radius: Float, color: Color) {
    val star = Path().apply {
        repeat(10) { i -> val angle = -Math.PI / 2 + i * Math.PI / 5
            val r = radius * if (i % 2 == 0) .35f else .15f
            val x = center.x + cos(angle).toFloat() * r
            val y = center.y - radius * .42f + sin(angle).toFloat() * r
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }; close()
    }
    drawPath(star, color)
    listOf(-.43f, 0f, .43f).forEach { x -> drawLine(color,
        center + Offset(x * radius * .20f, -radius * .03f), center + Offset(x * radius, radius * .68f),
        radius * .09f, StrokeCap.Round) }
}

private fun DrawScope.drawDiscoveryNetwork(game: ChapterDiscovery) {
    drawClueBoard(parchment = true)
    val points = ChapterDiscoveryGame.networkPoints.map { Offset(it.x * size.width, it.y * size.height) }
    val u = size.minDimension
    val river = Path().apply { moveTo(u * .08f, u * .15f); cubicTo(u * .65f, u * .21f, u * .13f, u * .61f, u * .88f, u * .87f) }
    drawPath(river, Color(0xFF97BAC1), style = Stroke(u * .055f, cap = StrokeCap.Round))
    drawPath(river, Color(0xFFCADAD3), style = Stroke(u * .020f, cap = StrokeCap.Round))
    ChapterDiscoveryGame.networkEdges.forEachIndexed { index, (a, b) ->
        if (index in game.joined) {
            drawLine(Color(0xFFFFD570).copy(alpha = .40f), points[a], points[b], u * .05f, StrokeCap.Round)
            drawLine(Color(0xFF795025), points[a], points[b], u * .012f, StrokeCap.Round)
        } else drawLine(Color(0xFF9A784A).copy(alpha = .42f), points[a], points[b], u * .009f,
            StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * .02f, u * .025f)))
    }
    points.forEachIndexed { index, p ->
        val lit = game.complete || ChapterDiscoveryGame.networkEdges.withIndex().any { (i, edge) -> i in game.joined && (edge.first == index || edge.second == index) }
        if (lit) drawCircle(Brush.radialGradient(listOf(Color(0xCCFFE194), Color.Transparent), p, u * .12f), u * .12f, p)
        if (game.selected == index) drawCircle(Color(0xFF765128), u * .064f, p, style = Stroke(u * .006f))
        drawCircle(Color(0xFF825C33), u * .042f, p)
        drawCircle(if (lit) ClueGold else ClueCream, u * .032f, p)
        drawKeeperMark(p, u * .026f, Color(0xFF5B3C22))
    }
}

/** Broad tap targets also accept continuous strokes; interpolate so a quick swipe cannot skip a patch. */
private fun Modifier.discoveryTouch(enabled: Boolean, onTouch: (Float, Float) -> Unit): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var previous = down.position
        fun touch(p: Offset) { if (size.width > 0 && size.height > 0) onTouch(p.x / size.width, p.y / size.height) }
        touch(previous); down.consume()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.pressed) {
                val delta = change.position - previous
                val steps = ceil(delta.getDistance() / maxOf(1f, size.width * .025f)).toInt().coerceIn(1, 64)
                for (i in 1..steps) touch(previous + delta * (i.toFloat() / steps))
                previous = change.position
            }
            change.consume()
            if (!change.pressed) break
        }
    }
}

private fun Modifier.discoverySwipe(enabled: Boolean, stage: Int = 0, onComplete: () -> Unit): Modifier = pointerInput(enabled, stage) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        var done = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!done && ((change.position - down.position).getDistance() >= 36.dp.toPx() || !change.pressed)) {
                onComplete(); done = true
            }
            change.consume()
            if (!change.pressed) break
        }
    }
}
