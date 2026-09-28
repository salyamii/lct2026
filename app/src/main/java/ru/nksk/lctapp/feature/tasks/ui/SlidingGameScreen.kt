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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.SlidingState

/** Shared sliding board for offered deeds. */
@Composable
fun SlidingGameScreen(
    uiState: SlidingGameUiState,
    onAction: (SlidingGameAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    Column(
        modifier = Modifier.fillMaxSize().background(DeedColors.Scene),
    ) {
        Box {
            GameArtwork(deed?.sceneRes ?: R.drawable.location_trail,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                Modifier.align(Alignment.BottomEnd).size(92.dp).padding(4.dp)) }
            DeedHeader(deed?.title ?: "Карта маршрута", onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                deed?.instructions ?: "Двигай плитки и собери карту маршрута по порядку номеров!",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Ходов: ${state.moves}")
                if (deed?.storyAction != true) {
                    CoinChip(deed?.let { "Награда до ${it.maximumReward} монет" } ?: "Награда: 10")
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until SlidingState.SIZE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until SlidingState.SIZE) {
                            val index = row * SlidingState.SIZE + col
                            val tile = state.tiles[index]
                            val shape = RoundedCornerShape(12.dp)
                            val description = if (tile == SlidingState.BLANK) {
                                "Пустая ячейка ${index + 1}"
                            } else {
                                "Плитка $tile, ячейка ${index + 1}"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .semantics { contentDescription = description }
                                    .clip(shape)
                                    .background(if (tile == SlidingState.BLANK) DeedColors.CreamSoft else DeedColors.Board)
                                    .then(
                                        if (tile == SlidingState.BLANK || state.won) Modifier
                                        else Modifier.border(1.5.dp, DeedColors.TextSoft.copy(alpha = 0.4f), shape)
                                    )
                                    .clickable(enabled = tile != SlidingState.BLANK && !state.won && deed?.canPlay != false) {
                                        onAction(SlidingGameAction.Tap(index))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (tile != SlidingState.BLANK) {
                                    Text(
                                        text = tile.toString(),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = Rubik,
                                        color = DeedColors.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (state.won && deed == null) {
        DeedResultSheet(
            emoji = "🗺️",
            title = "Карта собрана!",
            reward = 10,
            onAgain = { onAction(SlidingGameAction.Restart) },
            onHub = onBack,
        )
    }
}
