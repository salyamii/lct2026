package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

@Composable
fun MainMenuScreen(
    state: MainMenuUiState,
    onAction: (MainMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(AdventureNight)) {
        val viewport = DpSize(maxWidth, maxHeight)
        VillageBackdrop()
        // Only the foreground observes insets: the village extends behind native system bars.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 12.dp)) {
            val useSideBySide = maxWidth > maxHeight || maxWidth >= 840.dp
            val sidePanelWidth = 360.dp.coerceAtMost(maxWidth * 0.52f)
            if (useSideBySide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CharacterScene(state.pet, Modifier.weight(1f).fillMaxSize())
                    Column(
                        Modifier.width(sidePanelWidth)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        MenuHud(state, onAction)
                        MenuActions(onAction, viewport)
                    }
                }
            } else {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxSize().align(Alignment.TopCenter),
                ) {
                    MenuHud(state, onAction)
                    CharacterScene(state.pet, Modifier.weight(1f).fillMaxWidth())
                    MenuActions(onAction, viewport)
                }
            }
        }
    }
}

@Preview(name = "Figma · 390 × 844", widthDp = 390, heightDp = 844)
@Preview(name = "Compact phone", widthDp = 360, heightDp = 640)
@Preview(name = "Landscape", widthDp = 844, heightDp = 390)
@Preview(name = "Large text", widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Composable
private fun MainMenuPreview() {
    LCTAppTheme { MainMenuScreen(state = MainMenuPreviewState, onAction = {}) }
}
