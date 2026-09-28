package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import ru.nksk.lctapp.domain.minigame.SortingState

private val SUPPLY_COLORS = listOf(
    Color(0xFFE0805A),
    Color(0xFF6FA8DC),
    Color(0xFF9BBF6B),
    Color(0xFFF6C445),
)

/** Shared supply-sorting board for offered deeds. */
@Composable
fun SortingGameScreen(
    uiState: SortingGameUiState,
    onAction: (SortingGameAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    Column(
        modifier = Modifier.fillMaxSize().background(DeedColors.Scene),
    ) {
        Box {
            GameArtwork(deed?.sceneRes ?: R.drawable.location_pier,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                Modifier.align(Alignment.BottomEnd).size(92.dp).padding(4.dp)) }
            DeedHeader(deed?.title ?: "Разложи припасы", onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                deed?.instructions ?: "Перекладывай верхний припас так, чтобы в каждой банке был один вид!",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Перекладываний: ${state.moves}")
                if (deed?.storyAction != true) {
                    CoinChip(deed?.let { "Награда до ${it.maximumReward} монет" } ?: "Награда: 10")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                state.tubes.forEachIndexed { tubeIndex, tube ->
                    val selected = state.selected == tubeIndex
                    val shape = RoundedCornerShape(16.dp)
                    val description = "Банка ${tubeIndex + 1}, припасов ${tube.size}" +
                        if (selected) ", выбрана" else ""
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = description }
                            .clip(shape)
                            .background(DeedColors.CreamSoft)
                            .then(
                                if (selected) Modifier.border(3.dp, DeedColors.Lime, shape)
                                else Modifier.border(1.dp, DeedColors.Border, shape)
                            )
                            .clickable(enabled = !state.won && deed?.canPlay != false) {
                                onAction(SortingGameAction.Tap(tubeIndex))
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // Сверху вниз показываем от верхнего припаса к дну банки.
                        repeat(SortingState.CAPACITY) { levelFromTop ->
                            val level = SortingState.CAPACITY - 1 - levelFromTop
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (level < tube.size) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                            .clip(CircleShape)
                                            .background(SUPPLY_COLORS[tube[level]]),
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
            emoji = "🫙",
            title = "Порядок на складе!",
            reward = 10,
            onAgain = { onAction(SortingGameAction.Restart) },
            onHub = onBack,
        )
    }
}
