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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.domain.minigame.LightsState

/** Shared lights-out board for offered deeds. */
@Composable
fun LightsGameScreen(
    uiState: LightsGameUiState,
    onAction: (LightsGameAction) -> Unit,
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
            DeedHeader(deed?.title ?: "Фонари обсерватории", onBack = onBack)
        }
        DeedGameSheet(deed, DeedRewardPreview.fromLights(state), modifier = Modifier.weight(1f)) {
            Text(
                deed?.instructions ?: "Нажимай на фонари: гаснут сам фонарь и соседи. Погаси все к ночным наблюдениям!",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Ходов: ${state.moves}")
                if (deed == null) {
                    CoinChip("Награда: ${LightsState.REWARD}")
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until LightsState.SIZE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until LightsState.SIZE) {
                            val index = row * LightsState.SIZE + col
                            val lit = state.grid[index]
                            val shape = RoundedCornerShape(12.dp)
                            val description = if (lit) "Фонарь ${index + 1} горит" else "Фонарь ${index + 1} погас"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .semantics { contentDescription = description }
                                    .clip(shape)
                                    .background(if (lit) DeedColors.Chip else DeedColors.CreamSoft)
                                    .then(if (lit) Modifier.border(2.dp, DeedColors.Text.copy(alpha = 0.25f), shape) else Modifier)
                                    .clickable(enabled = !state.won && deed?.canPlay != false) {
                                        onAction(LightsGameAction.Tap(index))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                GameArtwork(
                                    if (lit) R.drawable.deed_game_lights_on else R.drawable.deed_game_lights_off,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().padding(6.dp),
                                    contentScale = ContentScale.Fit,
                                )
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
            emoji = "🏮",
            title = "Все фонари погашены!",
            reward = LightsState.REWARD,
            onAgain = { onAction(LightsGameAction.Restart) },
            onHub = onBack,
        )
    }
}
