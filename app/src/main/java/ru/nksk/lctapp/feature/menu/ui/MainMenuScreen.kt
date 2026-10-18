package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.heightIn
import ru.nksk.lctapp.core.ui.components.tour.LocalSpotlightTargets

import ru.nksk.lctapp.core.ui.components.tour.spotlightTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.core.ui.components.rememberScenePainter
import ru.nksk.lctapp.core.ui.components.GameArtworkScene
import ru.nksk.lctapp.core.ui.components.SavingsGauge
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.theme.Rubik

private val MapButtonWidth = 176.dp
private val MapButtonOverflow = 56.dp
private val MapButtonHeight = MapButtonWidth * (2f / 3f)

@Composable
fun MainMenuScreen(
    state: MainMenuUiState,
    onAction: (MainMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    settingsButton: (@Composable () -> Unit)? = null,
    onRename: () -> Unit = {},
) {
    val tourTargets = LocalSpotlightTargets.current
    val interactionEnabled = !state.busy && tourTargets?.blocking != true
    val dispatch: (MainMenuAction) -> Unit = { if (interactionEnabled) onAction(it) }
    val onFeed: (() -> Unit)? = if (state.canFeed) ({ dispatch(MainMenuAction.Feed) }) else null
    var budgetExpanded by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    var screenTop by remember { mutableStateOf(0f) }
    var screenWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    var hudBottom by remember { mutableStateOf(0f) }
    var characterTop by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(tourTargets?.blocking) { if (tourTargets?.blocking == true) budgetExpanded = false }
    val characterPosition = Modifier.spotlightTarget("menu.pet").onGloballyPositioned { characterTop = it.positionInRoot().y }
    GameArtworkScene(state.backgroundRes, modifier.fillMaxSize().background(AdventureNight)) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds().background(AdventureNight)
        .onGloballyPositioned { screenTop = it.positionInRoot().y; screenWindowOrigin = it.boundsInWindow().topLeft }) {
        val sceneScale = maxOf(maxWidth / 390.dp, maxHeight / 844.dp)
        val scenePainter = rememberScenePainter(state.backgroundRes,
            DpSize((483f * sceneScale).dp, (858f * sceneScale).dp))
        VillageBackdrop(scenePainter)
        // A separate name dialog owns keyboard avoidance; the village must retain its framing.
        BoxWithConstraints(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(vertical = 12.dp)) {
            val useSideBySide = maxWidth > maxHeight || maxWidth >= 840.dp
            val sidePanelWidth = 360.dp.coerceAtMost(maxWidth * 0.52f)
            if (useSideBySide) {
                Row(
                    // Reserve the screen-edge map's visible width beside the scrollable controls.
                    Modifier.fillMaxSize().absolutePadding(left = 24.dp, right = MapButtonWidth - MapButtonOverflow + 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MenuCharacterScene(state.pet, state.savings, state.dayStatus, { dispatch(MainMenuAction.Goal) },
                        Modifier.weight(1f).fillMaxSize(), characterPosition, !state.busy && tourTargets?.blocking != true)
                    Column(
                        Modifier.width(sidePanelWidth)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        MenuHud(state.pet.name, state.coins, dispatch, indicators = state.indicators, budget = state.budget,
                            budgetExpanded = budgetExpanded, onBudgetExpandedChange = { budgetExpanded = it }, settingsButton = settingsButton,
                            onRename = { if (tourTargets?.blocking != true) onRename() }, renameEnabled = interactionEnabled,
                            onFeed = onFeed, interactionEnabled = interactionEnabled)
                        MenuActions(dispatch, state)
                    }
                }
            } else {
                // Keep normal compact phones fixed; scroll only when the larger layout needs it.
                val indicatorWidth = maxWidth.coerceAtMost(480.dp) - 32.dp - if (settingsButton != null) 56.dp else 0.dp
                val wrapIndicators = indicatorWidth / density.fontScale < 240.dp
                val scrollContent = maxHeight < 520.dp || density.fontScale > 1.3f ||
                    (wrapIndicators && maxHeight < 700.dp)
                val contentScroll = rememberScrollState()
                val attachPlaquesToEdge = maxWidth <= 480.dp
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxSize().align(Alignment.TopCenter)
                        .then(if (scrollContent) Modifier.verticalScroll(contentScroll) else Modifier),
                ) {
                    MenuHud(state.pet.name, state.coins, dispatch,
                        Modifier.onGloballyPositioned { hudBottom = it.positionInRoot().y + it.size.height },
                        indicators = state.indicators, budget = state.budget,
                        budgetExpanded = budgetExpanded, onBudgetExpandedChange = { budgetExpanded = it }, settingsButton = settingsButton,
                        onRename = { if (tourTargets?.blocking != true) onRename() }, renameEnabled = interactionEnabled,
                        onFeed = onFeed, interactionEnabled = interactionEnabled,
                        attachNameToEdge = attachPlaquesToEdge)
                    MenuCharacterScene(state.pet, state.savings, state.dayStatus, { dispatch(MainMenuAction.Goal) },
                        if (scrollContent) Modifier.fillMaxWidth().height(260.dp)
                        else Modifier.weight(1f).fillMaxWidth(), characterPosition, !state.busy && tourTargets?.blocking != true,
                        attachDayToEdge = attachPlaquesToEdge)
                    MenuActions(dispatch, state)
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
                    .size(MapButtonWidth, MapButtonHeight).spotlightTarget("menu.map"),
            )
        }
        if (tourTargets?.expandedTarget == "menu.budget.details") state.budget?.let { budget ->
            val anchor = tourTargets.bounds["menu.budget"]
            val panelTop = with(density) { ((anchor?.bottom ?: screenWindowOrigin.y) - screenWindowOrigin.y).toDp() } + 8.dp
            val panelWidth = (maxWidth - 32.dp).coerceAtMost(280.dp)
            val panelLeft = with(density) { ((anchor?.left ?: screenWindowOrigin.x) - screenWindowOrigin.x).toDp() }
                .coerceIn(16.dp, (maxWidth - panelWidth - 16.dp).coerceAtLeast(16.dp))
            // Reserve space for the tutorial card; the inert preview can be scrolled safely.
            val panelHeight = (maxHeight - panelTop - 300.dp).coerceIn(100.dp, (maxHeight * .38f).coerceAtLeast(100.dp))
            MenuBudgetTourPreview(budget,
                Modifier.align(Alignment.TopStart).absoluteOffset(x = panelLeft, y = panelTop)
                    .width(panelWidth)
                    .heightIn(max = panelHeight))
        }
    }
    }
}

@Composable
private fun MenuCharacterScene(
    pet: MainMenuPetUiState,
    savings: MainMenuSavingsUiState?,
    dayStatus: String?,
    onOpenGoal: () -> Unit,
    modifier: Modifier,
    artworkModifier: Modifier,
    enabled: Boolean,
    attachDayToEdge: Boolean = false,
) {
    BoxWithConstraints(modifier) {
        val dayAboveSavings = savings != null && dayStatus != null && maxWidth < 276.dp
        val daySpace = if (dayAboveSavings) 64.dp * LocalDensity.current.fontScale.coerceAtLeast(1f) else 0.dp
        CharacterScene(pet, Modifier.fillMaxSize()
            .then(if (savings != null) Modifier.padding(start = 56.dp) else Modifier), artworkModifier)
        savings?.let { target ->
            val availableHeight = (maxHeight - 20.dp - daySpace).coerceAtLeast(0.dp)
            SavingsGauge(target.saved, target.total,
                Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 10.dp)
                    .width(minOf(104.dp, (maxWidth - 8.dp).coerceAtLeast(0.dp)))
                    .heightIn(max = availableHeight)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Открыть цель", onClick = onOpenGoal),
                contentDescription = "${target.title}: накоплено ${minOf(target.saved, target.total)} из ${target.total} монет")
        }
        dayStatus?.let { status ->
            MenuRightEdgePlaque(
                Modifier.align(if (dayAboveSavings) Alignment.TopEnd else Alignment.BottomEnd)
                    .padding(end = if (attachDayToEdge) 0.dp else 12.dp,
                        top = if (dayAboveSavings) 10.dp else 0.dp, bottom = if (dayAboveSavings) 0.dp else 10.dp)
                    .widthIn(min = 112.dp, max = 152.dp).heightIn(min = 52.dp),
                attached = attachDayToEdge,
                light = true,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(status, Modifier.align(Alignment.Center), color = GameInk,
                    fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Preview(name = "Бюджет · 390 × 844", widthDp = 390, heightDp = 844)
@Preview(name = "Compact phone", widthDp = 360, heightDp = 640)
@Preview(name = "Landscape", widthDp = 844, heightDp = 390)
@Preview(name = "Large text", widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Preview(name = "Compact with large text", widthDp = 360, heightDp = 640, fontScale = 1.5f)
@Composable
private fun MainMenuPreview() {
    LCTAppTheme { MainMenuScreen(state = MainMenuPreviewState, onAction = {}) }
}
