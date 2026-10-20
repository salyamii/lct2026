package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.rememberGameArtworkLoad
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.MemoryState
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview

/** Shared pair-finding board for training and offered deeds. */
@Composable
fun MemoryGameScreen(
    uiState: MemoryGameUiState,
    onAction: (MemoryGameAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
    onHome: () -> Unit = onBack,
) {
    val state = uiState.game

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        Box {
            DeedBackdrop(deed?.sceneRes ?: R.drawable.location_hill,
                modifier = Modifier.fillMaxWidth().height(150.dp), blurred = deed?.storyAction != true)
            deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                Modifier.align(Alignment.BottomEnd).size(92.dp).padding(4.dp)) }
            DeedHeader(deed?.title ?: stringResource(R.string.deeds_star_title), onBack = onBack, onHome = onHome, homeEnabled = deed?.canReturnHome != false)
        }
        DeedGameSheet(deed, DeedRewardPreview.fromMemory(state), modifier = Modifier.weight(1f)) {
            Text(
                deed?.instructions ?: stringResource(R.string.deeds_memory_prompt),
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
                if (deed == null) CoinChip(stringResource(R.string.deeds_demo_reward, MemoryState.REWARD))
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
                            // The same distinct original finds are used in every story or deed board.
                            val artwork = defaultMemoryPairArtwork[face]
                            val label = memoryPairNameResource(artwork)?.let { stringResource(it) }
                                ?: stringResource(R.string.deeds_pair_unknown, face + 1)
                            StarPlateView(
                                index = index,
                                face = artwork,
                                label = label,
                                revealed = index in state.faceUp || index in state.matched,
                                matched = index in state.matched,
                                enabled = !state.won && deed?.canPlay != false,
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
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(0.75f)
            .semantics { contentDescription = description }
            .clip(shape)
            .background(background)
            .then(if (revealed && !matched) Modifier.border(1.5.dp, DeedColors.Border, shape) else Modifier)
            .then(if (enabled && !revealed) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Both painters stay mounted while the card is closed. Opening never starts a new
        // request, and retaining the painters also protects this board from cache eviction.
        val front = rememberGameArtworkLoad(face,
            DpSize((maxWidth - 12.dp).coerceAtLeast(1.dp), (maxHeight - 12.dp).coerceAtLeast(1.dp)),
            participateInScene = false)
        val back = rememberGameArtworkLoad(R.drawable.deed_star_plate_back,
            DpSize(maxWidth, maxHeight), participateInScene = false)
        val opacity = animateFloatAsState(if (back.painter != null || front.painter != null) 1f else 0f,
            tween(180), label = "memory-artwork-reveal")
        Crossfade(targetState = revealed && front.painter != null,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value },
            animationSpec = tween(140), label = "memory-card-face") { showFace ->
            val painter = if (showFace) front.painter else back.painter
            if (painter != null) Image(painter, null,
                modifier = Modifier.fillMaxSize().then(if (showFace) Modifier.padding(6.dp) else Modifier),
                contentScale = if (showFace) ContentScale.Fit else ContentScale.Crop)
        }
    }
}
