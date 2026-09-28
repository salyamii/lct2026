package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import kotlin.math.roundToInt
import ru.nksk.lctapp.domain.minigame.StackingState

/** Shared crate-stacking board for offered deeds. */
@Composable
fun StackingGameScreen(
    uiState: StackingGameUiState,
    onAction: (StackingGameAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
    position: () -> Float = { 0.5f },
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
        DeedGameSheet(deed, DeedRewardPreview.fromStacking(state), modifier = Modifier.weight(1f)) {
            Text(
                deed?.instructions ?: "Опусти бегущий ящик на предыдущий.",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Text(
                text = "Останется только пересечение!",
                fontSize = 15.sp,
                fontFamily = Rubik,
                color = DeedColors.TextSoft,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Уложено: ${state.placed} из ${StackingState.ROUNDS}")
                if (deed == null) {
                    CoinChip("Награда: 8")
                }
            }
            Spacer(Modifier.height(12.dp))
            val boardDescription = "Штабель: уложено ${state.placed} из ${StackingState.ROUNDS}"
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = boardDescription }
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeedColors.Board)
                    .padding(12.dp),
            ) {
                val boardWidth = maxWidth
                // 8 клеток в ряду; клетка ограничена, чтобы доска не вытолкнула кнопку за экран.
                val cell = (maxWidth / 8).coerceAtMost(40.dp)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!state.finished && !uiState.missed) {
                        val headCells = cellsFor(state.currentWidth)
                        MovingCrateRow(position, headCells, cell, boardWidth)
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
                    state.locked.asReversed().forEach { block ->
                        val cells = cellsFor(block.width)
                        val left = (block.x * CELLS_ACROSS / 100f).roundToInt()
                            .coerceIn(0, CELLS_ACROSS - cells)
                        CrateRow(left, cells, cell, highlighted = false)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Причал",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Rubik,
                        color = DeedColors.TextSoft,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!state.finished && !uiState.missed) {
            DeedButton(
                "Опустить ящик",
                { if (deed?.canPlay != false) onAction(StackingGameAction.Drop) },
                Modifier.navigationBarsPadding(),
            )
        }
        Spacer(Modifier.height(10.dp))
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

/** Бегущий ящик: плавная дробная позиция над штабелем, рамка подсветки. */
@Composable
private fun MovingCrateRow(position: () -> Float, crateCells: Int, cell: Dp, boardWidth: Dp) {
    val headWidth = cell * crateCells
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width((boardWidth * position()).coerceIn(0.dp, boardWidth - headWidth)))
        Row(
            modifier = Modifier
                .border(2.dp, DeedColors.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(crateCells) {
                Image(
                    painter = painterResource(R.drawable.deed_game_crate),
                    contentDescription = null,
                    modifier = Modifier
                        .width(cell)
                        .height(cell)
                        .clip(RoundedCornerShape(6.dp)),
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** Клеток в ряду доски. */
private const val CELLS_ACROSS = 8

/** Число клеток под ящик шириной [width] из 100 долей полосы. */
private fun cellsFor(width: Int): Int = (width * CELLS_ACROSS / 100f).roundToInt().coerceIn(1, CELLS_ACROSS)

/** Ряд маленьких ящиков-клеток; голова тропинки подсвечивается рамкой. */
@Composable
private fun CrateRow(leftCells: Int, crateCells: Int, cell: Dp, highlighted: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(cell * leftCells))
        Row(
            modifier = Modifier
                .then(if (highlighted) Modifier.border(2.dp, DeedColors.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp)) else Modifier)
                .padding(if (highlighted) 3.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(crateCells) {
                Image(
                    painter = painterResource(R.drawable.deed_game_crate),
                    contentDescription = null,
                    modifier = Modifier
                        .width(cell)
                        .height(cell)
                        .clip(RoundedCornerShape(6.dp)),
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
