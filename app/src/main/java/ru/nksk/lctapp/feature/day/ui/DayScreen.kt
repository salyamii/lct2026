package ru.nksk.lctapp.feature.day.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.domain.engine.EventLayout
import ru.nksk.lctapp.core.ui.components.*

@Composable
internal fun DayScreen(state: DayUiState, onAction: (DayAction) -> Unit, onBack: () -> Unit,
    onLoadingContinue: () -> Unit = onBack, summaryExtra: (@Composable () -> Unit)? = null) {
    // Saving overlays the existing card. It must not insert a row, shift the scroll
    // position or recolour every choice immediately before the route disappears.
    BackHandler(enabled = state.busy) {}
    Box(Modifier.fillMaxSize()) {
        DayContent(state.copy(busy = false),
            onAction = { if (!state.busy) onAction(it) },
            onBack = { if (!state.busy) onBack() },
            onLoadingContinue = { if (!state.busy) onLoadingContinue() }, summaryExtra = summaryExtra)
        if (state.busy) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }) {
            GameLoadingIndicator(Modifier.align(Alignment.Center).safeDrawingPadding(), delayMillis = 300)
        }
        if (state.retryRequired && !state.busy) AlertDialog(
            onDismissRequest = onBack,
            containerColor = GamePaper,
            title = { AdventureHeading("Сохранение не подтверждено") },
            text = { AdventureBody("Повторим то же действие. Второй раз монеты не спишутся и награда не начислится.") },
            confirmButton = { AdventurePrimaryButton("Повторить", { onAction(DayAction.Retry) }) },
            dismissButton = { AdventureQuietButton("Вернуться", onBack) },
        )
    }
}

@Composable
private fun DayContent(state: DayUiState, onAction: (DayAction) -> Unit, onBack: () -> Unit,
    onLoadingContinue: () -> Unit, summaryExtra: (@Composable () -> Unit)?) {
    if (state.loading) {
        Box(Modifier.fillMaxSize()) {
            GameLoadingScreen()
            // Keep the shared coin centered while the existing exit remains reachable.
            Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp)) {
                AdventurePrimaryButton("Вперёд", onLoadingContinue)
            }
        }
        return
    }
    if (state.failed) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Не удалось загрузить игру. Сохранение не изменено.")
                GameActionButton("Повторить", { onAction(DayAction.Retry) })
                TextButton(onBack) { Text("Назад") }
            }
        }
        return
    }
    if (state.summary != null) {
        DaySummaryScreen(state, onAction, onBack, summaryExtra)
    } else if (state.layout == EventLayout.INTRODUCTION) {
        StoryIntroduction(state, onAction, onBack)
    } else if (state.layout == EventLayout.PURCHASE) {
        PurchaseCard(state, onAction, onBack)
    } else AdventureScreen(
        title = "", onBack = onBack, backgroundRes = state.eventBackgroundRes,
        blurBackground = state.focusesItem,
        artworkSceneKey = state.audioOccurrenceId ?: state.title,
        indicators = { state.indicators?.let { PetIndicators(it) } },
        sceneAspectRatio = 1.12f, pinFooter = false,
        contentSpacing = 8.dp,
        fitContent = true,
        scene = { DayEventScene(state, Modifier.fillMaxSize()) },
    ) {
        GameTitle(state.title)
        if (state.body.isNotBlank()) GameBody(state.body)
        if (state.impact.isNotBlank()) GameBody(state.impact)
        state.deedDeadline?.let { GameBody(it) }
        state.message?.let { GameBody(it) }
        state.actionNotice?.let { GameBody(it) }
        DayActions(state, onAction)
    }
    if (state.showMeals) MealSelectionDialog(state.meals, state.busy,
        onChoose = { onAction(DayAction.Feed(it)) }, onDismiss = { onAction(DayAction.CloseMeals) },
        message = state.message)
}

@Composable
private fun PurchaseCard(state: DayUiState, onAction: (DayAction) -> Unit, onBack: () -> Unit) {
    AdventureScreen(
        title = "", onBack = onBack,
        backgroundRes = state.eventBackgroundRes,
        blurBackground = state.focusesItem,
        artworkSceneKey = state.audioOccurrenceId ?: state.title,
        indicators = { state.indicators?.let { PetIndicators(it) } },
        sceneAspectRatio = 1.12f, pinFooter = false,
        contentSpacing = 8.dp,
        fitContent = true,
        scene = {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 12.dp)
                    .widthIn(max = 292.dp).fillMaxWidth().height(218.dp),
                contentAlignment = Alignment.Center,
            ) {
                state.purchaseArtworkRes?.let { artwork ->
                    GameArtwork(artwork, state.title,
                        Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
            }
        },
    ) {
        AdventureHeading(state.title)
        AdventureBody(state.body)
        if (state.impact.isNotBlank()) AdventureBody(state.impact)
        state.message?.let { AdventureBody(it) }
        state.actionNotice?.let { AdventureBody(it) }
        DayActions(state, onAction)
    }
}

@Composable
private fun StoryIntroduction(state: DayUiState, onAction: (DayAction) -> Unit, onBack: () -> Unit) {
    AdventureScreen(
        title = "", onBack = onBack,
        backgroundRes = state.eventBackgroundRes,
        artworkSceneKey = state.audioOccurrenceId ?: state.title,
        indicators = { state.indicators?.let { PetIndicators(it) } },
        sceneAspectRatio = 1.5f, scene = {}, pinFooter = false,
        contentSpacing = 8.dp,
        fitContent = true,
        footer = { DayActions(state, onAction) },
    ) {
        AdventureHeading(state.title)
        AdventureBody(state.body)
        state.message?.let { AdventureBody(it) }
        state.actionNotice?.let { AdventureBody(it) }
    }
}

@Composable
private fun DayActions(state: DayUiState, onAction: (DayAction) -> Unit) {
    if (!state.practiceRequired && state.options.isEmpty() && state.primary == null && state.later == null) return
    Column(verticalArrangement = Arrangement.spacedBy(GameActionButtonDefaults.Spacing)) {
        state.financialAdvice?.let { advice ->
            FinancialAdviceText(advice.text, Modifier.padding(bottom = 8.dp), exposureKey = advice.id,
                onShown = { onAction(DayAction.AdvicePresented(advice.id)) })
        }
        if (state.practiceRequired) {
            AdventurePrimaryButton("К практике", { onAction(DayAction.OpenLearning) }, enabled = !state.busy)
        }
        if (state.options.any { it.needsFood }) {
            AdventurePrimaryButton("Покормить", { onAction(DayAction.ShowMeals) }, enabled = !state.busy)
        }
        DayChoices(state.options, state.busy, onAction)
        state.primary?.let { label ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.primarySpending?.let { AdventureBody(it) }
                AdventurePrimaryButton(label,
                    { onAction(if (state.primaryNeedsFood) DayAction.ShowMeals else DayAction.Primary) },
                    enabled = !state.busy)
            }
        }
        state.later?.let { label ->
            AdventureQuietButton(label, { onAction(DayAction.Later) }, enabled = !state.busy)
        }
    }
}

@Composable
private fun DayChoices(options: List<DayOption>, busy: Boolean, onAction: (DayAction) -> Unit) {
    val presentation = dayChoicesPresentation(options)
    if (presentation.options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (presentation.commonEffects.isNotEmpty()) {
            Text("После выбора", color = GameInk.copy(alpha = .72f),
                style = MaterialTheme.typography.labelMedium)
            PetChoiceEffects(presentation.commonEffects)
        }
        Column(verticalArrangement = Arrangement.spacedBy(GameActionButtonDefaults.Spacing)) {
            presentation.options.forEach { option -> DayChoice(option, busy, onAction) }
        }
    }
}

@Composable
private fun DayChoice(option: DayOption, busy: Boolean, onAction: (DayAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        option.spending?.let { AdventureBody(it) }
        if (option.petEffects.isNotEmpty()) {
            Text("После выполнения", color = GameInk.copy(alpha = .72f),
                style = MaterialTheme.typography.labelMedium)
            PetChoiceEffects(option.petEffects)
        }
        AdventurePrimaryButton(option.label, { onAction(DayAction.Choose(option.id)) },
            enabled = option.enabled && !busy)
    }
}
