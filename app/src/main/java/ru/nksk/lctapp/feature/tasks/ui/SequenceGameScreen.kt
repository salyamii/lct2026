package ru.nksk.lctapp.feature.tasks.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.SequenceState

private val SIGNAL_COLORS = listOf(
    Color(0xFFF6C445), // янтарный
    Color(0xFFA8E830), // лаймовый
    Color(0xFF7EB2FF), // лазурный
    Color(0xFFE58BD0), // розовый
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
                    uiState.phase == SequencePhase.SHOWING -> "Смотри внимательно…"
                    uiState.phase == SequencePhase.FEEDBACK && state.lastCorrect == true -> "Верно! Башня готовит следующий сигнал"
                    uiState.phase == SequencePhase.FEEDBACK -> "Сбой сигнала. Следующий раунд!"
                    else -> if (state.finished) "Смена окончена" else "Повтори последовательность"
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
                            val shape = CircleShape
                            val description = "Сигнал ${signal + 1}"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .semantics { contentDescription = description }
                                    .clip(shape)
                                    .background(if (flashing) SIGNAL_COLORS[signal] else SIGNAL_COLORS[signal].copy(alpha = 0.25f))
                                    .then(if (flashing) Modifier.border(4.dp, DeedColors.Text.copy(alpha = 0.3f), shape) else Modifier)
                                    .clickable(enabled = uiState.phase == SequencePhase.INPUT && deed?.canPlay != false) {
                                        onAction(SequenceGameAction.Tap(signal))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {}
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
