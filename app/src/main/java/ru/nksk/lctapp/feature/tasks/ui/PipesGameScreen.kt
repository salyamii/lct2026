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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import ru.nksk.lctapp.domain.minigame.PipesState

private val PIPE_COLORS = listOf(
    Color(0xFFE0805A), // канатный терракот
    Color(0xFF6FA8DC), // морской синий
    Color(0xFF9BBF6B), // болотный зелёный
)

/** Shared connect-the-ends board for offered deeds. */
@Composable
fun PipesGameScreen(
    uiState: PipesGameUiState,
    onAction: (PipesGameAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    val activeColor = state.activeColor
    Column(
        modifier = Modifier.fillMaxSize().background(DeedColors.Scene),
    ) {
        Box {
            GameArtwork(deed?.sceneRes ?: R.drawable.location_workshop,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                Modifier.align(Alignment.BottomEnd).size(92.dp).padding(4.dp)) }
            DeedHeader(deed?.title ?: "Свяжи концы", onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                deed?.instructions ?: "Соедини концы одного цвета непрерывной линией, не пересекая чужие!",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip(if (activeColor == null) "Выбери конец" else "Цвет ${activeColor + 1}")
                if (deed?.storyAction != true) {
                    CoinChip(deed?.let { "Награда до ${it.maximumReward} монет" } ?: "Награда: 10")
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until PipesState.SIZE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until PipesState.SIZE) {
                            val cell = row * PipesState.SIZE + col
                            val endpoint = state.endpoints.firstOrNull { it.first == cell || it.second == cell }
                            val pathColor = state.endpoints.firstOrNull { endpoint ->
                                state.paths[endpoint.color]?.contains(cell) == true
                            }
                            val activeCell = cell in state.activePath
                            val color = when {
                                endpoint != null -> PIPE_COLORS[endpoint.color]
                                activeCell -> PIPE_COLORS[activeColor ?: 0]
                                pathColor != null -> PIPE_COLORS[pathColor.color].copy(alpha = 0.55f)
                                else -> DeedColors.CreamSoft
                            }
                            val shape = RoundedCornerShape(10.dp)
                            val description = when {
                                endpoint != null -> "Конец цвета ${endpoint.color + 1}, ячейка ${cell + 1}"
                                activeCell -> "Тропинка цвета ${(activeColor ?: 0) + 1}, ячейка ${cell + 1}"
                                pathColor != null -> "Линия цвета ${pathColor.color + 1}, ячейка ${cell + 1}"
                                else -> "Свободная ячейка ${cell + 1}"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .semantics { contentDescription = description }
                                    .clip(shape)
                                    .background(color)
                                    .then(
                                        if (endpoint != null) {
                                            Modifier.border(3.dp, DeedColors.Text.copy(alpha = 0.5f), CircleShape)
                                        } else Modifier
                                    )
                                    .clickable(enabled = !state.won && deed?.canPlay != false) {
                                        onAction(PipesGameAction.Press(cell))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (endpoint != null) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(DeedColors.White))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (state.activeColor != null && !state.won) {
                DeedButtonSoft("Сбросить тропинку", { onAction(PipesGameAction.Release) })
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (state.won && deed == null) {
        DeedResultSheet(
            emoji = "🪢",
            title = "Все концы связаны!",
            reward = 10,
            onAgain = { onAction(PipesGameAction.Restart) },
            onHub = onBack,
        )
    }
}
