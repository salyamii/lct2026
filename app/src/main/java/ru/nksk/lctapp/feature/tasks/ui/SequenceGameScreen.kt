package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.rememberGameArtworkLoad
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.domain.minigame.SequenceState

// Иллюминаторы башни: тёмное и светящееся состояния каждого сигнала.
private val SIGNAL_DARK = listOf(
    R.drawable.deed_game_signal_amber_off,
    R.drawable.deed_game_signal_green_off,
    R.drawable.deed_game_signal_blue_off,
    R.drawable.deed_game_signal_red_off,
)

private val SIGNAL_LIT = listOf(
    R.drawable.deed_game_signal_amber_on,
    R.drawable.deed_game_signal_green_on,
    R.drawable.deed_game_signal_blue_on,
    R.drawable.deed_game_signal_red_on,
)

/** Shared signal-repeating board for offered deeds. */
@Composable
fun SequenceGameScreen(
    uiState: SequenceGameUiState,
    onAction: (SequenceGameAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    BoxWithConstraints(Modifier.fillMaxSize().background(DeedColors.Scene)) {
        val lampSize = DpSize(maxWidth / 2, maxWidth / 2)
        val darkLamps = SIGNAL_DARK.map { rememberGameArtworkLoad(it, lampSize) }
        val litLamps = SIGNAL_LIT.map { rememberGameArtworkLoad(it, lampSize) }
        val lampsReady = darkLamps.all { it.settled } && litLamps.all { it.settled }
        LaunchedEffect(lampsReady) {
            if (lampsReady) onAction(SequenceGameAction.ArtworkReady)
        }
        Column(Modifier.fillMaxSize()) {
            Box {
                GameArtwork(deed?.sceneRes ?: R.drawable.location_observatory,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentScale = ContentScale.Crop,
                )
                deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                    Modifier.align(Alignment.BottomEnd).size(92.dp).padding(4.dp)) }
                DeedHeader(deed?.title ?: "Сигналы башни", onBack = onBack)
            }
            DeedGameSheet(deed, DeedRewardPreview.fromSequence(state), modifier = Modifier.weight(1f)) {
                Text(
                    deed?.instructions ?: "Смотри на вспышки и повтори их по памяти.",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Rubik,
                    color = DeedColors.Text,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeedChip("Раунд ${uiState.roundNumber} из ${state.roundLimit}")
                    if (deed == null) {
                        CoinChip("Сигналов: ${state.correct}")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        state.finished -> "Смена окончена"
                        uiState.phase == SequencePhase.SHOWING -> "Смотри внимательно…"
                        uiState.phase == SequencePhase.FEEDBACK && state.lastCorrect == true -> "Верно! Готовим следующую комбинацию"
                        uiState.phase == SequencePhase.FEEDBACK -> "Сбой сигнала. Следующий раунд!"
                        else -> "Повтори последовательность"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Rubik,
                    color = DeedColors.TextSoft,
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SequenceState.SIGNALS.chunked(2).forEach { rowSignals ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowSignals.forEach { signal ->
                                val flashing = signal in uiState.inputSignals ||
                                    (uiState.phase == SequencePhase.SHOWING &&
                                        state.sequence.getOrNull(uiState.showingIndex ?: -1) == signal)
                                val shape = RoundedCornerShape(20.dp)
                                val description = "Сигнал ${signal + 1}"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .semantics { contentDescription = description }
                                        .clip(shape)
                                        .clickable(enabled = uiState.phase == SequencePhase.INPUT && deed?.canPlay != false) {
                                            onAction(SequenceGameAction.Tap(signal))
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Keep both variants loaded: a tap changes drawing, never the image request.
                                    darkLamps[signal].painter?.let { painter -> Image(
                                        painter = painter,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        alignment = Alignment.Center,
                                        contentScale = ContentScale.Crop,
                                    ) }
                                    litLamps[signal].painter?.let { painter -> Image(
                                        painter = painter,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().drawWithContent {
                                            if (flashing) drawContent()
                                        },
                                        contentScale = ContentScale.Crop,
                                    ) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
    if (state.finished && deed == null) {
        DeedResultSheet(
            emoji = "🗼",
            title = "Смена сигналов окончена!",
            reward = state.correct * 2,
            onAgain = { onAction(SequenceGameAction.Restart) },
            onHub = onBack,
        )
    }
}
