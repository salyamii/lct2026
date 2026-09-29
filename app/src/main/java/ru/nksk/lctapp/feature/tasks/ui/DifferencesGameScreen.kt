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
import ru.nksk.lctapp.domain.minigame.DifferencesState

// Полки собираются из находок Смотрителя; отличия - другой предмет в ячейке.
private val SHELF_ART = listOf(
    R.drawable.deed_pair_key,
    R.drawable.deed_pair_armillary,
    R.drawable.deed_pair_star_plate,
    R.drawable.deed_pair_astrolabe,
    R.drawable.deed_pair_tag,
    R.drawable.deed_pair_telescope,
    R.drawable.deed_pair_loupe,
    R.drawable.deed_pair_backpack,
)

/** Shared spot-the-difference board for offered deeds. */
@Composable
fun DifferencesGameScreen(
    uiState: DifferencesGameUiState,
    onAction: (DifferencesGameAction) -> Unit,
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
            DeedHeader(deed?.title ?: "Сверка находок", onBack = onBack)
        }
        DeedGameSheet(deed, DeedRewardPreview.fromDifferences(state), modifier = Modifier.weight(1f)) {
            Text(
                deed?.instructions ?: "Сравни полки и найди ${DifferencesState.DIFF_COUNT} отличий на нижней!",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Найдено: ${state.found.size} из ${DifferencesState.DIFF_COUNT}")
                if (deed == null) {
                    CoinChip("Награда: 10")
                }
            }
            Spacer(Modifier.height(12.dp))
            Board("Эталонная полка", state.top, found = emptySet(), enabled = false, onCell = {})
            Spacer(Modifier.height(12.dp))
            Board("Полка для сверки", state.bottom, found = state.found, enabled = !state.won && deed?.canPlay != false) { cell ->
                onAction(DifferencesGameAction.Tap(cell))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (state.won && deed == null) {
        DeedResultSheet(
            emoji = "🔍",
            title = "Все отличия найдены!",
            reward = 10,
            onAgain = { onAction(DifferencesGameAction.Restart) },
            onHub = onBack,
        )
    }
}

@Composable
private fun Board(
    label: String,
    items: List<Int>,
    found: Set<Int>,
    enabled: Boolean,
    onCell: (Int) -> Unit,
) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = Rubik,
        color = DeedColors.TextSoft,
    )
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(4).forEachIndexed { rowIndex, rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEachIndexed { columnIndex, art ->
                    val cell = rowIndex * 4 + columnIndex
                    val marked = cell in found
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.9f)
                            .semantics {
                                contentDescription = if (marked) "Найденное отличие, ячейка ${cell + 1}" else "Предмет, ячейка ${cell + 1}"
                            }
                            .clip(shape)
                            .background(if (marked) DeedColors.Lime.copy(alpha = 0.30f) else DeedColors.CreamCard)
                            .then(if (marked) Modifier.border(2.dp, DeedColors.Lime, shape) else Modifier)
                            .then(if (enabled) Modifier.clickable { onCell(cell) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        GameArtwork(
                            SHELF_ART[art % SHELF_ART.size],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}
