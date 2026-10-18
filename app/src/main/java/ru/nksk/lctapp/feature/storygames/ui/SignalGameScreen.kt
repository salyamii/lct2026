package ru.nksk.lctapp.feature.storygames.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import ru.nksk.lctapp.domain.minigame.chapterone.SignalGame
import ru.nksk.lctapp.domain.minigame.chapterone.SignalLight
import ru.nksk.lctapp.domain.minigame.chapterone.SignalState

data class SignalUiState(val game: SignalState = SignalState())
sealed interface SignalAction {
    data class Choose(val light: SignalLight) : SignalAction
    data object Undo : SignalAction
    data object Send : SignalAction
    data object Restart : SignalAction
}

@HiltViewModel
class SignalViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(SignalUiState(SignalGame.restore(
        savedState.get<IntArray>("signal.entered")?.toList().orEmpty(), savedState["signal.checked"] ?: false,
    )))
    val uiState = mutableState.asStateFlow()
    fun onAction(action: SignalAction) {
        val next = when (action) {
            is SignalAction.Choose -> SignalGame.choose(mutableState.value.game, action.light)
            SignalAction.Undo -> SignalGame.undo(mutableState.value.game)
            SignalAction.Send -> SignalGame.send(mutableState.value.game)
            SignalAction.Restart -> SignalState()
        }
        if (next == mutableState.value.game) return
        mutableState.value = SignalUiState(next)
        savedState["signal.entered"] = next.entered.map { it.ordinal }.toIntArray()
        savedState["signal.checked"] = next.checked
    }
}

@Composable
fun SignalGameEntry(
    onExit: () -> Unit,
    onCompleted: ((SignalState) -> Unit)? = null,
    interactionBlocked: Boolean = false,
) {
    val viewModel = hiltViewModel<SignalViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = interactionBlocked) {}
    SignalGameScreen(state,
        onAction = { if (!interactionBlocked) viewModel.onAction(it) },
        onExit = { if (!interactionBlocked) onExit() },
        onCompleted = onCompleted?.let { complete -> {
            if (!interactionBlocked && viewModel.uiState.value.game.complete) complete(viewModel.uiState.value.game)
        } }, interactionBlocked = interactionBlocked)
}

@Composable
fun SignalGameScreen(
    state: SignalUiState,
    onAction: (SignalAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: (() -> Unit)? = null,
    interactionBlocked: Boolean = false,
) {
    val game = state.game
    ChapterGameScaffold(
        title = "Ответ башни",
        instruction = when {
            game.complete -> "Башня ответила! Старый путь всё ещё под присмотром."
            game.needsCorrection -> "Башня молчит. Сравни порядок огней с записью."
            else -> "Набери три огня по записи и отправь сигнал башне."
        },
        onExit = onExit,
        footer = {
            if (game.complete && onCompleted != null) {
                GameActionButton("Продолжить", onCompleted, interactionBlocked = interactionBlocked)
            } else if (game.complete) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameActionButton("Ещё раз", { onAction(SignalAction.Restart) }, Modifier.weight(1f))
                GameActionButton("К играм", onExit, Modifier.weight(1f))
            }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameActionButton("Убрать", { onAction(SignalAction.Undo) }, Modifier.weight(1f),
                    enabled = game.entered.isNotEmpty())
                GameActionButton("Послать", { onAction(SignalAction.Send) }, Modifier.weight(1f),
                    enabled = game.entered.size == 3 && !game.needsCorrection)
            }
        },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Text("Запись", color = ClueCream, fontFamily = Nunito, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 12.dp))
                SignalGame.code.forEachIndexed { index, light ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = ClueCream, fontFamily = Nunito)
                        GameArtwork(light.artwork(true), light.label(), Modifier.size(43.dp))
                    }
                }
            }
            SignalTower(game.complete, Modifier.fillMaxWidth().weight(1f))
            if (!game.complete) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(3) { index ->
                        val chosen = game.entered.getOrNull(index)
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(ClueGold.copy(alpha = .16f))
                                drawCircle(ClueGold.copy(alpha = .7f), style = Stroke(1.dp.toPx()))
                            }
                            if (chosen == null) Text("${index + 1}", color = ClueCream, fontFamily = Nunito)
                            else GameArtwork(chosen.artwork(true), "Огонь ${index + 1}: ${chosen.label()}", Modifier.fillMaxSize())
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    // Their physical order differs from the note, so the child reads the clue.
                    listOf(SignalLight.BLUE, SignalLight.RED, SignalLight.AMBER).forEach { light ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(enabled = game.entered.size < 3,
                                role = Role.Button, onClickLabel = "Добавить ${light.label().lowercase()} огонь") {
                                onAction(SignalAction.Choose(light))
                            }.semantics(mergeDescendants = true) { contentDescription = light.label() }) {
                            GameArtwork(light.artwork(game.entered.lastOrNull() == light), null, Modifier.size(62.dp))
                            Text(light.label(), color = ClueCream, fontFamily = Nunito, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalTower(answered: Boolean, modifier: Modifier) {
    val lightAmount by animateFloatAsState(if (answered) 1f else 0f, tween(1200), label = "Ответ огней")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * (2f / 3f))
        Box(Modifier.size(width, width * 1.5f)) {
            GameArtwork(R.drawable.story_north_tower_signal, "Северная башня", Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                if (lightAmount > 0f) {
                    listOf(.327f, .414f, .502f).forEachIndexed { index, y ->
                        val color = SignalGame.code[index].color()
                        val origin = Offset(size.width * .45f, size.height * y)
                        val beam = Path().apply {
                            moveTo(origin.x, origin.y)
                            lineTo(size.width * .03f, origin.y - size.height * .05f)
                            lineTo(size.width * .03f, origin.y + size.height * .05f)
                            close()
                        }
                        drawPath(beam, Brush.horizontalGradient(listOf(Color.Transparent,
                            color.copy(alpha = lightAmount * .38f))))
                        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = lightAmount), Color.Transparent),
                            origin, size.width * .15f), size.width * .15f, origin)
                        drawCircle(color.copy(alpha = lightAmount * .85f), size.width * .036f, origin)
                    }
                }
            }
        }
    }
}

private fun SignalLight.label(): String = when (this) {
    SignalLight.RED -> "Красный"
    SignalLight.AMBER -> "Жёлтый"
    SignalLight.BLUE -> "Синий"
}
private fun SignalLight.color(): Color = when (this) {
    SignalLight.RED -> Color(0xFFFF735B)
    SignalLight.AMBER -> Color(0xFFFFD95A)
    SignalLight.BLUE -> Color(0xFF75C9FF)
}
private fun SignalLight.artwork(on: Boolean): Int = when (this) {
    SignalLight.RED -> if (on) R.drawable.deed_game_signal_red_on else R.drawable.deed_game_signal_red_off
    SignalLight.AMBER -> if (on) R.drawable.deed_game_signal_amber_on else R.drawable.deed_game_signal_amber_off
    SignalLight.BLUE -> if (on) R.drawable.deed_game_signal_blue_on else R.drawable.deed_game_signal_blue_off
}
