package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.background
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
import ru.nksk.lctapp.domain.minigame.StackingState

private val CRATE_COLORS = listOf(
    Color(0xFFC89B6C),
    Color(0xFFB08A5E),
    Color(0xFFD8B284),
    Color(0xFFA67B4F),
    Color(0xFFC4A47C),
    Color(0xFF9A7B55),
)

/** Shared crate-stacking board for offered deeds. */
@Composable
fun StackingGameScreen(
    uiState: StackingGameUiState,
    onAction: (StackingGameAction) -> Unit,
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
            DeedHeader(deed?.title ?: "Ящики на причале", onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                deed?.instructions ?: "Опусти бегущий ящик на предыдущий. Останется только пересечение!",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Уложено: ${state.placed} из ${StackingState.ROUNDS}")
                if (deed?.storyAction != true) {
                    CoinChip(deed?.let { "Награда до ${it.maximumReward} монет" } ?: "Награда: 12")
                }
            }
            Spacer(Modifier.height(12.dp))
            val boardDescription = "Штабель: уложено ${state.placed} из ${StackingState.ROUNDS}"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = boardDescription }
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeedColors.Board)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = state.currentWidth.toFloat() / StackingState.SPACE
                // Переносимый ящик над штабелем.
                if (!state.finished && !uiState.missed) {
                    MovingCrate(uiState.blockPosition, fraction)
                }
                if (uiState.missed) {
                    Text(
                        text = "Ящик ушёл в воду!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Rubik,
                        color = DeedColors.Chip,
                    )
                }
                state.locked.asReversed().forEachIndexed { reverseIndex, block ->
                    val level = state.locked.size - reverseIndex
                    CrateBar(
                        x = block.x.toFloat() / StackingState.SPACE,
                        width = block.width.toFloat() / StackingState.SPACE,
                        color = CRATE_COLORS[(level - 1) % CRATE_COLORS.size],
                    )
                }
                Text(
                    text = "Причал",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Rubik,
                    color = DeedColors.TextSoft,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (!state.finished && !uiState.missed) {
                DeedButton("Опустить ящик", { if (deed?.canPlay != false) onAction(StackingGameAction.Drop) })
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (state.finished && deed == null) {
        DeedResultSheet(
            emoji = "📦",
            title = if (state.won) "Штабель готов к отплытию!" else "Штабель рассыпался",
            reward = state.placed * 2,
            onAgain = { onAction(StackingGameAction.Restart) },
            onHub = onBack,
        )
    }
}

@Composable
private fun MovingCrate(position: Float, width: Float) {
    val left = position.coerceIn(0.01f, 0.98f)
    val crateWidth = width.coerceIn(0.05f, 1f)
    Row(Modifier.fillMaxWidth().height(30.dp)) {
        Spacer(Modifier.weight(left))
        Box(
            modifier = Modifier
                .weight(crateWidth)
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(DeedColors.Chip),
        ) {}
        Spacer(Modifier.weight((1f - left - crateWidth).coerceAtLeast(0.01f)))
    }
}

@Composable
private fun CrateBar(x: Float, width: Float, color: Color) {
    val left = x.coerceIn(0.01f, 0.98f)
    val crateWidth = width.coerceIn(0.05f, 1f)
    Row(Modifier.fillMaxWidth().height(30.dp)) {
        Spacer(Modifier.weight(left))
        Box(
            modifier = Modifier
                .weight(crateWidth)
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(color),
        ) {}
        Spacer(Modifier.weight((1f - left - crateWidth).coerceAtLeast(0.01f)))
    }
}
