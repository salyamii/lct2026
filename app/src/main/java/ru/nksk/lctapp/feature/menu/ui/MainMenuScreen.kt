package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.core.ui.components.rememberScenePainter

private val MapButtonWidth = 176.dp
private val MapButtonOverflow = 56.dp
private val MapButtonHeight = MapButtonWidth * (2f / 3f)

@Composable
fun MainMenuScreen(
    state: MainMenuUiState,
    onAction: (MainMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatch: (MainMenuAction) -> Unit = { if (!state.busy) onAction(it) }
    var budgetExpanded by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    var screenTop by remember { mutableStateOf(0f) }
    var hudBottom by remember { mutableStateOf(0f) }
    var characterTop by remember { mutableStateOf<Float?>(null) }
    val characterPosition = Modifier.onGloballyPositioned { characterTop = it.positionInRoot().y }
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds().background(AdventureNight)
        .onGloballyPositioned { screenTop = it.positionInRoot().y }) {
        val viewport = DpSize(maxWidth, maxHeight)
        val sceneScale = maxOf(maxWidth / 390.dp, maxHeight / 844.dp)
        val scenePainter = rememberScenePainter(state.backgroundRes,
            DpSize((483f * sceneScale).dp, (858f * sceneScale).dp))
        VillageBackdrop(scenePainter)
        // Only the foreground observes insets: the village extends behind native system bars.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 12.dp)) {
            val useSideBySide = maxWidth > maxHeight || maxWidth >= 840.dp
            val sidePanelWidth = 360.dp.coerceAtMost(maxWidth * 0.52f)
            if (useSideBySide) {
                Row(
                    // Reserve the screen-edge map's visible width beside the scrollable controls.
                    Modifier.fillMaxSize().absolutePadding(left = 24.dp, right = MapButtonWidth - MapButtonOverflow + 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CharacterScene(state.pet, Modifier.weight(1f).fillMaxSize(), characterPosition)
                    Column(
                        Modifier.width(sidePanelWidth)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        MenuHud(state.pet.name, state.coins, state.completedGoals, state.totalGoals, dispatch, state.goalTitle, budget = state.budget,
                            budgetExpanded = budgetExpanded, onBudgetExpandedChange = { budgetExpanded = it })
                        MenuActions(dispatch, viewport, state, scenePainter)
                    }
                }
            } else {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxSize().align(Alignment.TopCenter),
                ) {
                    MenuHud(state.pet.name, state.coins, state.completedGoals, state.totalGoals, dispatch, state.goalTitle,
                        Modifier.onGloballyPositioned { hudBottom = it.positionInRoot().y + it.size.height }, budget = state.budget,
                            budgetExpanded = budgetExpanded, onBudgetExpandedChange = { budgetExpanded = it })
                    CharacterScene(state.pet, Modifier.weight(1f).fillMaxWidth(), characterPosition)
                    MenuActions(dispatch, viewport, state, scenePainter)
                }
            }
        }
        characterTop?.let { top ->
            // Prefer above the character, but keep the portrait HUD clear at larger font scales.
            val portrait = maxWidth <= maxHeight && maxWidth < 840.dp
            val minimumTop = if (portrait) with(density) { (hudBottom - screenTop).toDp() } + 8.dp else 0.dp
            val mapTop = (with(density) { (top - screenTop).toDp() } - MapButtonHeight - 8.dp).coerceAtLeast(minimumTop)
            VillageMapButton(
                onClick = { dispatch(MainMenuAction.Village) },
                modifier = Modifier.align(AbsoluteAlignment.TopRight)
                    .absoluteOffset(x = MapButtonOverflow, y = mapTop)
                    .size(MapButtonWidth, MapButtonHeight),
            )
        }
    }
}

@Preview(name = "Бюджет · 390 × 844", widthDp = 390, heightDp = 844)
@Preview(name = "Compact phone", widthDp = 360, heightDp = 640)
@Preview(name = "Landscape", widthDp = 844, heightDp = 390)
@Preview(name = "Large text", widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Composable
private fun MainMenuPreview() {
    LCTAppTheme { MainMenuScreen(state = MainMenuPreviewState, onAction = {}) }
}
