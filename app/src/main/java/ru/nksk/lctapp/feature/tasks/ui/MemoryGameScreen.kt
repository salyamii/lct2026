package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.components.GameArtwork
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.MemoryState

/** Shared pair-finding board for training and offered deeds. */
@Composable
fun MemoryGameScreen(
    uiState: MemoryGameUiState,
    onAction: (MemoryGameAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        Box {
            GameArtwork(deed?.sceneRes ?: R.drawable.location_hill,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                Modifier.align(Alignment.BottomEnd).size(92.dp).padding(4.dp)) }
            DeedHeader(deed?.title ?: stringResource(R.string.deeds_star_title), onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                deed?.instructions ?: if (deed != null) "Найди одинаковые пары. Чем меньше ошибок, тем больше награда." else stringResource(R.string.deeds_memory_prompt),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeedChip(stringResource(R.string.deeds_moves, state.moves))
                if (deed?.storyAction != true) CoinChip(deed?.let { "Награда до ${it.maximumReward} монет" }
                    ?: stringResource(R.string.deeds_demo_reward, MemoryState.REWARD))
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.faces.chunked(4).forEachIndexed { rowIndex, rowFaces ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowFaces.forEachIndexed { columnIndex, face ->
                            val index = rowIndex * 4 + columnIndex
                            val artwork = deed?.pairArtwork?.takeIf { it.size == MemoryState.PAIRS }?.get(face)
                                ?: defaultMemoryPairArtwork[face]
                            val label = memoryPairNameResource(artwork)?.let { stringResource(it) }
                                ?: stringResource(R.string.deeds_pair_unknown, face + 1)
                            StarPlateView(
                                index = index,
                                face = artwork,
                                label = label,
                                revealed = index in state.faceUp || index in state.matched,
                                matched = index in state.matched,
                                enabled = state.pending == null && !state.won && deed?.canPlay != false,
                                onClick = {
                                    onAction(MemoryGameAction.Tap(index))
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (state.won && deed == null) {
        DeedResultSheet(
            emoji = "🌌",
            title = stringResource(R.string.deeds_memory_complete),
            reward = MemoryState.REWARD,
            onAgain = { onAction(MemoryGameAction.Restart) },
            onHub = onBack,
        )
    }
}

@Composable
private fun StarPlateView(
    index: Int,
    face: Int,
    label: String,
    revealed: Boolean,
    matched: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = when {
        matched -> stringResource(R.string.deeds_card_matched, index + 1, label)
        revealed -> stringResource(R.string.deeds_card_open, index + 1, label)
        else -> stringResource(R.string.deeds_card_hidden, index + 1)
    }
    val shape = RoundedCornerShape(14.dp)
    val background = when {
        matched -> DeedColors.Lime.copy(alpha = 0.30f)
        revealed -> DeedColors.CreamCard
        else -> DeedColors.Board
    }
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .semantics { contentDescription = description }
            .clip(shape)
            .background(background)
            .then(if (revealed && !matched) Modifier.border(1.5.dp, DeedColors.Border, shape) else Modifier)
            .then(if (enabled && !revealed) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (revealed) {
            GameArtwork(face,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        } else {
            GameArtwork(R.drawable.deed_star_plate_back,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
