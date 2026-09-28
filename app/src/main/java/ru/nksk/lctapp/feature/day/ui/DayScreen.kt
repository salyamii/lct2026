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
    onLoadingContinue: () -> Unit = onBack) {
    // Saving overlays the existing card. It must not insert a row, shift the scroll
    // position or recolour every choice immediately before the route disappears.
    BackHandler(enabled = state.busy) {}
    Box(Modifier.fillMaxSize()) {
        DayContent(state.copy(busy = false),
            onAction = { if (!state.busy) onAction(it) },
            onBack = { if (!state.busy) onBack() },
            onLoadingContinue = { if (!state.busy) onLoadingContinue() })
        if (state.busy) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }) {
            GameLoadingIndicator(Modifier.align(Alignment.Center).safeDrawingPadding())
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
    onLoadingContinue: () -> Unit) {
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
                Button({ onAction(DayAction.Retry) }) { Text("Повторить") }
                TextButton(onBack) { Text("Назад") }
            }
        }
        return
    }
    if (state.summary != null) {
        DaySummaryScreen(state, onAction, onBack)
    } else if (state.layout == EventLayout.INTRODUCTION) {
        StoryIntroduction(state, onAction, onBack)
    } else if (state.layout == EventLayout.PURCHASE) {
        PurchaseCard(state, onAction, onBack)
    } else AdventureScreen(
        title = state.category, onBack = onBack, backgroundRes = state.eventBackgroundRes,
        blurBackground = state.focusesItem,
        artworkSceneKey = state.audioOccurrenceId ?: state.title,
        sceneAspectRatio = 1.12f, pinFooter = false,
        scene = { DayEventScene(state, Modifier.fillMaxSize()) },
    ) {
        GameTitle(state.title)
        if (state.body.isNotBlank()) GameBody(state.body)
        if (state.impact.isNotBlank()) GameBody(state.impact)
        if (state.effort.isNotBlank()) GameBody(state.effort)
        state.deedDeadline?.let { GameBody(it) }
        state.message?.let { GameBody(it) }
        state.actionNotice?.let { GameBody(it) }
        if (state.practiceRequired) GameButton("К практике", !state.busy) { onAction(DayAction.OpenLearning) }
        if (state.options.any { it.needsFood }) {
            GameButton("Покормить", !state.busy) { onAction(DayAction.ShowMeals) }
        }
        state.options.filterNot { it.needsFood }.forEach { option ->
            option.spending?.let { GameBody(it) }
            GameButton(if (option.needsFood) "Покормить" else option.label, !state.busy && option.enabled) {
                onAction(if (option.needsFood) DayAction.ShowMeals else DayAction.Choose(option.id))
            }
        }
        state.primary?.let { text ->
            state.primarySpending?.let { GameBody(it) }
            GameButton(text, !state.busy) { onAction(if (state.primaryNeedsFood) DayAction.ShowMeals else DayAction.Primary) }
        }
        state.later?.let { text ->
            OutlinedButton(
                { onAction(DayAction.Later) }, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = GameInk,
                    disabledContainerColor = GameDisabledButtonContainer,
                    disabledContentColor = GameDisabledButtonContent,
                ),
            ) { Text(text) }
        }
    }
    if (state.showMeals) AlertDialog(
        onDismissRequest = { if (!state.busy) onAction(DayAction.CloseMeals) },
        containerColor = GamePaper, titleContentColor = GameInk, textContentColor = GameInk,
        title = { Text("${state.petName} проголодался") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Еда утолит голод, а сон вернёт силы.")
                state.message?.let { Text(it) }
                state.meals.forEach { meal ->
                    meal.spending?.let { Text(it) }
                    meal.consequence?.let { Text(it) }
                    GameButton(meal.label, meal.enabled && !state.busy) { onAction(DayAction.Feed(meal.id)) }
                }
            }
        },
        confirmButton = {
            TextButton({ onAction(DayAction.CloseMeals) }, enabled = !state.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = GameInk)) { Text("Вернуться") }
        },
    )
}

@Composable
private fun PurchaseCard(state: DayUiState, onAction: (DayAction) -> Unit, onBack: () -> Unit) {
    AdventureScreen(
        title = state.locationTitle, onBack = onBack,
        backgroundRes = state.eventBackgroundRes,
        blurBackground = state.focusesItem,
        artworkSceneKey = state.audioOccurrenceId ?: state.title,
        sceneAspectRatio = 1.12f, pinFooter = false,
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
        if (state.effort.isNotBlank()) AdventureBody(state.effort)
        state.message?.let { AdventureBody(it) }
        state.actionNotice?.let { AdventureBody(it) }
        if (state.practiceRequired) {
            AdventurePrimaryButton("К практике", { onAction(DayAction.OpenLearning) }, enabled = !state.busy)
        }
        if (state.options.any { it.needsFood }) {
            AdventurePrimaryButton("Покормить", { onAction(DayAction.ShowMeals) }, enabled = !state.busy)
        }
        state.options.filterNot { it.needsFood }.forEach { option ->
            option.spending?.let { AdventureBody(it) }
            AdventurePrimaryButton(option.label, { onAction(DayAction.Choose(option.id)) },
                enabled = option.enabled && !state.busy)
        }
        state.primary?.let { label ->
            state.primarySpending?.let { AdventureBody(it) }
            AdventurePrimaryButton(label, { onAction(if (state.primaryNeedsFood) DayAction.ShowMeals else DayAction.Primary) },
                enabled = !state.busy)
        }
        state.later?.let { label ->
            AdventureQuietButton(label, { onAction(DayAction.Later) }, enabled = !state.busy)
        }
    }
}

@Composable
private fun StoryIntroduction(state: DayUiState, onAction: (DayAction) -> Unit, onBack: () -> Unit) {
    AdventureScreen(
        title = state.locationTitle, onBack = onBack,
        backgroundRes = state.eventBackgroundRes,
        artworkSceneKey = state.audioOccurrenceId ?: state.title,
        sceneAspectRatio = 1.5f, scene = {}, pinFooter = false,
        footer = {
            if (state.practiceRequired) AdventurePrimaryButton("К практике", { onAction(DayAction.OpenLearning) }, enabled = !state.busy)
            if (state.options.any { it.needsFood }) {
                AdventurePrimaryButton("Покормить", { onAction(DayAction.ShowMeals) }, enabled = !state.busy)
            }
            state.options.filterNot { it.needsFood }.forEach { option ->
                AdventurePrimaryButton(option.label, { onAction(DayAction.Choose(option.id)) }, enabled = option.enabled && !state.busy)
            }
            state.primary?.let { label ->
                AdventurePrimaryButton(label, { onAction(if (state.primaryNeedsFood) DayAction.ShowMeals else DayAction.Primary) }, enabled = !state.busy)
            }
            state.later?.let { label ->
                AdventureQuietButton(label, { onAction(DayAction.Later) }, enabled = !state.busy)
            }
        },
    ) {
        AdventureHeading(state.title)
        AdventureBody(state.body)
        state.message?.let { AdventureBody(it) }
        state.actionNotice?.let { AdventureBody(it) }
    }
}
