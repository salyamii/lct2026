package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.chapterone.RecordStudy
import ru.nksk.lctapp.domain.minigame.chapterone.RecordStudyKind
import ru.nksk.lctapp.domain.minigame.chapterone.SignalLight
import kotlin.math.roundToInt

@Composable
fun RecordGameEntry(
    kind: RecordStudyKind,
    onExit: () -> Unit,
    onCompleted: ((RecordStudy) -> Unit)? = null,
    interactionBlocked: Boolean = false,
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = !interactionBlocked,
) {
    val viewModel = hiltViewModel<RecordGameViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(kind) { viewModel.prepare(kind) }
    BackHandler(enabled = interactionBlocked) {}
    if (state.game.kind == kind) RecordGameScreen(
        state = state,
        onAction = { if (!interactionBlocked) viewModel.onAction(it) },
        onExit = { if (!interactionBlocked) onExit() },
        onHome = { if (homeEnabled) onHome() },
        homeEnabled = homeEnabled,
        onCompleted = onCompleted?.let { complete -> {
            val game = viewModel.uiState.value.game
            if (!interactionBlocked && game.kind == kind && game.complete) complete(game)
        } },
        interactionBlocked = interactionBlocked,
    )
}

@Composable
fun RecordGameScreen(
    state: RecordGameUiState,
    onAction: (RecordGameAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: (() -> Unit)? = null,
    interactionBlocked: Boolean = false,
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = !interactionBlocked,
) {
    val game = state.game
    val title = when (game.kind) {
        RecordStudyKind.CARGO_JOURNAL -> "Журнал грузов"
        RecordStudyKind.THREE_LENSES -> "Три линзы"
        RecordStudyKind.LIGHT_ORDER -> "Запись огней"
    }
    val instruction = when (game.kind) {
        RecordStudyKind.CARGO_JOURNAL -> when {
            game.complete -> "Грузы шли к башне через дорожные посты."
            game.recorded.isEmpty() -> "Найди ящик на странице журнала."
            game.needsCorrection -> "Следуй по дороге от причала к башне."
            else -> "Проведи пальцем по пути груза. Можно нажимать на точки."
        }
        RecordStudyKind.THREE_LENSES -> when {
            game.complete -> "Три линзы найдены. Порядок ищи на дорожном посту."
            game.needsCorrection -> "Подбери отверстие такого же цвета."
            else -> "Выбери линзу и поставь на её место в схеме."
        }
        RecordStudyKind.LIGHT_ORDER -> when {
            game.complete -> "Порядок огней записан."
            game.needsCorrection -> "Сверь следующий цвет с записью внизу страницы."
            else -> "Перенеси три цвета из журнала в свою запись."
        }
    }
    ChapterGameScaffold(title, instruction, onExit, onHome = onHome,
        homeEnabled = homeEnabled, footer = {
        if (game.complete) GameActionButton(
            "Продолжить", onCompleted ?: onExit, interactionBlocked = interactionBlocked,
        )
    }) {
        when (game.kind) {
            RecordStudyKind.CARGO_JOURNAL -> CargoRecord(game, onAction, interactionBlocked)
            RecordStudyKind.THREE_LENSES -> LensRecord(game, onAction, interactionBlocked)
            RecordStudyKind.LIGHT_ORDER -> LightRecord(game, onAction, interactionBlocked)
        }
    }
}

private data class RecordPoint(val x: Float, val y: Float, val label: String)
private val CargoPoints = listOf(
    RecordPoint(.172f, .195f, "Ящик в журнале"),
    RecordPoint(.693f, .608f, "Причал"),
    RecordPoint(.845f, .433f, "Поворот дороги"),
    RecordPoint(.652f, .352f, "Старый мост и дорожный пост"),
    RecordPoint(.843f, .230f, "Башня"),
)
private val LensPoints = mapOf(
    SignalLight.RED to RecordPoint(.511f, .281f, "Красная линза"),
    SignalLight.AMBER to RecordPoint(.336f, .578f, "Жёлтая линза"),
    SignalLight.BLUE to RecordPoint(.628f, .609f, "Синяя линза"),
)

@Composable
private fun CargoRecord(game: RecordStudy, onAction: (RecordGameAction) -> Unit, blocked: Boolean) {
    val rightPage = game.recorded.isNotEmpty()
    val currentAction by rememberUpdatedState(onAction)
    val currentGame by rememberUpdatedState(game)
    FocusedRecordPage(R.drawable.story_cargo_journal, rightPage, Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().pointerInput(rightPage, blocked) {
            if (blocked) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun visit(position: Offset) {
                    val expected = currentGame.recorded.size
                    val indices = if (rightPage) 1..4 else 0..0
                    val nearest = indices.minByOrNull { index ->
                        val p = CargoPoints[index].pagePosition(size.width.toFloat(), size.height.toFloat(), rightPage)
                        (p - position).getDistance()
                    } ?: return
                    val point = CargoPoints[nearest].pagePosition(size.width.toFloat(), size.height.toFloat(), rightPage)
                    val radius = maxOf(24.dp.toPx(), size.width * .075f)
                    if ((point - position).getDistance() <= radius && nearest >= expected) {
                        currentAction(RecordGameAction.Visit(nearest))
                    }
                }
                visit(down.position)
                down.consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.filter { it.id == down.id && it.pressed }.forEach {
                        visit(it.position)
                        it.consume()
                    }
                } while (event.changes.any { it.pressed })
            }
        }.semantics {
            contentDescription = if (rightPage) "Путь груза на странице журнала" else "Страница с грузами"
            customActions = if (blocked || game.complete) emptyList() else {
                val next = game.recorded.size
                listOf(CustomAccessibilityAction(CargoPoints[next].label) {
                    onAction(RecordGameAction.Visit(next)); true
                })
            }
        }) {
            val indices = if (rightPage) 1..4 else 0..0
            indices.forEach { index ->
                val p = CargoPoints[index].pagePosition(size.width, size.height, rightPage)
                val done = index in game.recorded
                val active = index == game.recorded.size
                if (active || done) {
                    drawCircle(if (done) Color(0xFF3C744E).copy(alpha = .30f) else ClueCream.copy(alpha = .42f),
                        18.dp.toPx(), p)
                    drawCircle(if (done) Color(0xFF315C3F) else ClueCream, 13.dp.toPx(), p,
                        style = Stroke(3.dp.toPx()))
                    if (done) drawCircle(Color(0xFF315C3F), 5.dp.toPx(), p)
                }
            }
        }
    }
}

/** A viewport into an unchanged full-canvas book makes its printed clues readable on a phone. */
@Composable
private fun FocusedRecordPage(
    artwork: Int,
    rightPage: Boolean,
    modifier: Modifier,
    overlay: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight / (1.9f / 1.5f))
        val height = width * (1.9f / 1.5f)
        Box(Modifier.size(width, height).clip(RoundedCornerShape(12.dp))) {
            // Only the camera changes: original image dimensions and page coordinates are retained.
            GameArtwork(artwork, null,
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(width * 1.9f, height).offset(x = if (rightPage) width * -.9f else 0.dp),
                contentScale = ContentScale.FillBounds)
            overlay()
        }
    }
}

private fun RecordPoint.pagePosition(width: Float, height: Float, rightPage: Boolean): Offset =
    Offset((x * 1.9f - if (rightPage) .9f else 0f) * width, y * height)

@Composable
private fun LensRecord(game: RecordStudy, onAction: (RecordGameAction) -> Unit, blocked: Boolean) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * (1464f / 1084f))
            val height = width * (1084f / 1464f)
            Box(Modifier.size(width, height)) {
                GameArtwork(R.drawable.story_story_plate, "Схема трёх линз", Modifier.fillMaxSize())
                LensPoints.forEach { (light, point) ->
                    val placed = light.ordinal in game.recorded
                    Box(Modifier.size(54.dp).offset {
                        IntOffset((width.toPx() * point.x - 27.dp.toPx()).roundToInt(),
                            (height.toPx() * point.y - 27.dp.toPx()).roundToInt())
                    }.clickable(enabled = !blocked && !placed && game.selectedLens != null,
                        role = Role.Button, onClickLabel = "Поставить линзу") {
                        onAction(RecordGameAction.PlaceLens(light))
                    }.semantics { contentDescription = if (placed) "${point.label} на месте" else "Место: ${point.label.lowercase()}" },
                        contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(light.recordColor().copy(alpha = if (placed) .18f else .30f), size.minDimension * .38f)
                            drawCircle(if (placed) ClueCream else light.recordColor(), size.minDimension * .39f,
                                style = Stroke(2.dp.toPx()))
                        }
                        if (placed) GameArtwork(light.recordArtwork(), null, Modifier.size(48.dp))
                    }
                }
            }
        }
        if (!game.complete) RecordPalette(
            enabled = { it.ordinal !in game.recorded && !blocked },
            selected = game.selectedLens,
            onChoose = { onAction(RecordGameAction.SelectLens(it)) },
        )
    }
}

@Composable
private fun LightRecord(game: RecordStudy, onAction: (RecordGameAction) -> Unit, blocked: Boolean) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusedRecordPage(R.drawable.story_observation_journal, true,
            Modifier.fillMaxWidth().weight(1f))
        GamePlaque(Modifier.fillMaxWidth(), light = true, fixedEdges = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Моя запись", fontFamily = Nunito, color = ClueInk, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp)
                repeat(3) { index ->
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color(0xFF8F6B36).copy(alpha = .14f), size.minDimension * .40f)
                            drawCircle(Color(0xFF8F6B36), size.minDimension * .40f, style = Stroke(1.dp.toPx()))
                        }
                        val chosen = game.recorded.getOrNull(index)?.let { SignalLight.entries[it] }
                        if (chosen != null) GameArtwork(chosen.recordArtwork(),
                            "${index + 1}: ${chosen.recordLabel()}", Modifier.fillMaxSize())
                        else Text("${index + 1}", fontFamily = Nunito, color = ClueInk)
                    }
                }
            }
        }
        if (!game.complete) RecordPalette(enabled = { !blocked }, selected = null,
            onChoose = { onAction(RecordGameAction.Stamp(it)) })
    }
}

@Composable
private fun RecordPalette(enabled: (SignalLight) -> Boolean, selected: Int?, onChoose: (SignalLight) -> Unit) {
    Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        listOf(SignalLight.BLUE, SignalLight.RED, SignalLight.AMBER).forEach { light ->
            Column(Modifier.clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled(light), role = Role.Button, onClickLabel = "Выбрать цвет") { onChoose(light) }
                .semantics(mergeDescendants = true) { contentDescription = light.recordLabel() }
                .padding(horizontal = 7.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    if (selected == light.ordinal) Canvas(Modifier.fillMaxSize()) {
                        drawCircle(ClueGold, size.minDimension * .48f, style = Stroke(2.dp.toPx()))
                    }
                    GameArtwork(light.recordArtwork(), null,
                        Modifier.size(52.dp).alpha(if (enabled(light)) 1f else .35f))
                }
                Text(light.recordLabel(), color = ClueCream, fontFamily = Nunito,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun SignalLight.recordLabel(): String = when (this) {
    SignalLight.RED -> "Красный"
    SignalLight.AMBER -> "Жёлтый"
    SignalLight.BLUE -> "Синий"
}
private fun SignalLight.recordColor(): Color = when (this) {
    SignalLight.RED -> Color(0xFFE86B52)
    SignalLight.AMBER -> Color(0xFFF3C553)
    SignalLight.BLUE -> Color(0xFF65B5ED)
}
private fun SignalLight.recordArtwork(): Int = when (this) {
    SignalLight.RED -> R.drawable.deed_game_signal_red_on
    SignalLight.AMBER -> R.drawable.deed_game_signal_amber_on
    SignalLight.BLUE -> R.drawable.deed_game_signal_blue_on
}
