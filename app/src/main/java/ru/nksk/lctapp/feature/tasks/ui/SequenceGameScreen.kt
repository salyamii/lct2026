package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Rubik
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
    Column(
        modifier = Modifier.fillMaxSize().background(DeedColors.Scene),
    ) {
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
        DeedSheet(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                deed?.instructions ?: "Смотри на вспышки башни и повтори их по памяти. Пять раундов!",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Раунд ${uiState.roundNumber} из ${SequenceState.ROUNDS}")
                if (deed?.storyAction != true) {
                    CoinChip(deed?.let { "Награда до ${it.maximumReward} монет" } ?: "Сигналов: ${state.correct}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    state.finished -> "Смена окончена"
                    uiState.phase == SequencePhase.SHOWING -> "Смотри внимательно…"
                    uiState.phase == SequencePhase.FEEDBACK && state.lastCorrect == true -> "Верно! Башня готовит следующий сигнал"
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
                            val flashing = uiState.phase == SequencePhase.SHOWING &&
                                state.sequence.getOrNull(uiState.showingIndex ?: -1) == signal
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
                                val lamp = if (flashing) SIGNAL_LIT[signal] else SIGNAL_DARK[signal]
                                Image(
                                    painter = painterResource(lamp),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    alignment = Alignment.Center,
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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
