package ru.nksk.lctapp.feature.menutour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.nksk.lctapp.core.ui.components.tour.LocalSpotlightTargets
import ru.nksk.lctapp.core.ui.components.tour.SpotlightOverlay
import ru.nksk.lctapp.core.ui.components.tour.SpotlightTargets

@Composable
internal fun MenuTourHost(
    state: MenuTourUiState,
    newPlayer: Boolean,
    available: Boolean,
    model: MenuTourViewModel,
    content: @Composable () -> Unit,
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle == Lifecycle.State.RESUMED
    val targets = remember { SpotlightTargets() }
    LaunchedEffect(available, resumed, newPlayer) { if (available && resumed) model.prepare(newPlayer) }
    val eligible = available && resumed && !state.dismissedForSession
    val requested = if (eligible) state.step?.let(MenuTourSteps::getOrNull) else null
    // Main steps always retain navigation, even before measurement or in a clipped small window.
    val step = requested?.takeIf { it.index < MainTourStepCount }
        ?: if (eligible) state.step?.let { visibleTourStep(it, targets.bounds.keys) } else null
    val blocking = eligible && (state.step == null || requested != null && requested.index < MainTourStepCount || step != null || state.busy)
    SideEffect {
        targets.expandedTarget = requested?.expansion
        targets.focusedTarget = requested?.pointerTarget
        targets.blocking = blocking
    }
    CompositionLocalProvider(LocalSpotlightTargets provides targets) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.onPreviewKeyEvent { blocking }.pointerInput(blocking) {
                if (blocking) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }.then(if (blocking) Modifier.clearAndSetSemantics {} else Modifier)) { content() }
            if (blocking && step == null) Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            })
            step?.let {
                SpotlightOverlay(
                    title = it.title, body = it.body,
                    progress = if (it.index < MainTourStepCount) "${it.index + 1} из $MainTourStepCount" else "Подсказка ${it.index - MainTourStepCount + 1} из 2",
                    highlights = it.targets.mapNotNull(targets::highlight), pointer = targets.highlight(it.pointerTarget)?.bounds,
                    busy = state.busy, hasPrevious = it.index in 1 until MainTourStepCount,
                    last = it.index >= MainTourStepCount - 1,
                    onPrevious = model::previous, onNext = model::next, onSkip = model::skip,
                    onScroll = { position, delta ->
                        targets.scrollHandlers.forEach { (id, scroll) ->
                            if (targets.bounds[id]?.contains(position) == true) scroll(delta)
                        }
                    },
                )
            }
            if (eligible && state.error) AlertDialog(
                onDismissRequest = model::dismissError,
                title = { Text("Не удалось сохранить обучение") },
                text = { Text("Попробуй ещё раз или закрой подсказки на этот запуск. Прогресс игры сохранён отдельно.") },
                confirmButton = { TextButton(model::retry) { Text("Повторить") } },
                dismissButton = { TextButton(model::dismissError) { Text("Закрыть подсказки") } },
            )
        }
    }
}
