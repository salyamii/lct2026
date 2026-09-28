package ru.nksk.lctapp.feature.gear.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingIndicator
import ru.nksk.lctapp.core.ui.components.GameArtwork
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.AdventureMuted
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

@Composable
internal fun GearScreen(state: GearLoadState, onBack: () -> Unit, onRetry: () -> Unit,
    onEquip: (String) -> Unit = {}, onOpenItem: (String) -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState()) {
    Box(Modifier.fillMaxSize().background(AdventureNight).safeDrawingPadding()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 840.dp).fillMaxSize()) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.navigation_back), color = Color.White, fontFamily = Nunito)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.gear_title), color = Color.White, fontFamily = Rubik,
                        fontWeight = FontWeight.ExtraBold, fontSize = 28.sp,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.gear_subtitle),
                        color = AdventureMuted, fontFamily = Nunito, fontSize = 14.sp,
                    )
                }
                GameArtwork(R.drawable.menu_gear, contentDescription = null,
                    modifier = Modifier.size(58.dp), contentScale = ContentScale.Fit,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = GearColors.Cream,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                // Loading/error content scrolls too, including in landscape and at large font sizes.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(152.dp),
                    state = gridState,
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state) {
                        GearLoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                GameLoadingIndicator()
                                Text(stringResource(R.string.gear_loading), color = GearColors.Ink, fontFamily = Nunito)
                            }
                        }
                        GearLoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.gear_error), color = GearColors.Ink, fontFamily = Nunito)
                                TextButton(onClick = onRetry) {
                                    Text(stringResource(R.string.game_retry), color = GearColors.Ink)
                                }
                            }
                        }
                        is GearLoadState.Ready -> {
                            state.actionMessage?.let { message ->
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(stringResource(message), color = GearColors.Ink, fontFamily = Nunito)
                                }
                            }
                            gearSection(
                                key = "story", title = R.string.gear_story_title,
                                emptyTitle = R.string.gear_story_empty_title,
                                emptyDescription = R.string.gear_story_empty_description,
                                items = state.inventory.storyItems,
                                onOpenItem = onOpenItem,
                            )
                            gearSection(
                                key = "accessories", title = R.string.gear_accessories_title,
                                emptyTitle = R.string.gear_accessories_empty_title,
                                emptyDescription = R.string.gear_accessories_empty_description,
                                items = state.inventory.accessories,
                                enabled = !state.busy,
                                onEquip = onEquip,
                                onOpenItem = onOpenItem,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.gearSection(
    key: String,
    title: Int,
    emptyTitle: Int,
    emptyDescription: Int,
    items: List<GearItemUiState>,
    enabled: Boolean = true,
    onEquip: (String) -> Unit = {},
    onOpenItem: (String) -> Unit = {},
) {
    item(key = "$key-header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
        GearSectionHeading(stringResource(title), items.size)
    }
    if (items.isEmpty()) {
        item(key = "$key-empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
            GearEmptySection(stringResource(emptyTitle), stringResource(emptyDescription))
        }
    } else {
        val reserveEquipSlot = items.any { it.lookId != null }
        items(items, key = { "owned-${it.occurrenceId}" }, contentType = { "item" }) {
            GearItemCard(it, enabled, onEquip, onOpenItem, reserveEquipSlot = reserveEquipSlot)
        }
    }
}

@Preview(name = "Empty inventory", widthDp = 360, heightDp = 780)
@Composable
private fun EmptyGearPreview() {
    LCTAppTheme { GearScreen(GearLoadState.Ready(GearUiState(emptyList(), emptyList())), {}, {}) }
}

/** Figma item examples for preview only. Never installed or awarded to a saved game. */
@Preview(name = "Owned items", widthDp = 360, heightDp = 800)
@Composable
private fun OwnedGearPreview() {
    LCTAppTheme {
        GearScreen(
            GearLoadState.Ready(GearUiState(
                storyItems = listOf(
                    GearItemUiState("map", "Карта", "Старинная карта на потёртом пергаменте", 50),
                    GearItemUiState("compass", "Компас", "Латунный компас с откидной крышкой", 80),
                ),
                accessories = listOf(GearItemUiState("bandana", "Бандана", "Красная бандана с узлом", 40)),
            )), {}, {},
        )
    }
}
