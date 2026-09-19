package ru.nksk.lctapp.feature.tasks.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.tasks.ui.*

@Serializable
@SerialName("deed_game")
data class DeedGame(val occurrenceId: String) : NavKey

fun EntryProviderScope<NavKey>.deedGameEntry(onFinished: (DeedGame, String?) -> Unit) {
    entry<DeedGame> { source ->
        val model = hiltViewModel<DeedGameViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val lifecycleState by lifecycle.currentStateAsState()
        val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
        LaunchedEffect(model, source.occurrenceId) { model.load(source.occurrenceId) }
        LaunchedEffect(model, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.exit.collect { onFinished(source, it) }
            }
        }
        BackHandler(enabled = resumed) { model.leave() }
        val leave = { if (resumed) model.leave() }
        DeedGameHost(state, model::retry, leave) {
            when (state.type) {
                DeedGameType.MEMORY -> {
                    val game = hiltViewModel<MemoryGameViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishMemory(board.game) }
                    MemoryGameScreen(board, { if (resumed && state.presentation?.canPlay == true) game.onAction(it) },
                        leave, state.presentation)
                }
                DeedGameType.COMPARISON -> {
                    val game = hiltViewModel<PriceQuizViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishComparison(board.game) }
                    PriceQuizScreen(board, { if (resumed && state.presentation?.canPlay == true) game.onAction(it) },
                        leave, state.presentation)
                }
                DeedGameType.PRECISION -> {
                    val game = hiltViewModel<TargetStopViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishPrecision(board.game) }
                    TargetStopScreen(board, { if (resumed && state.presentation?.canPlay == true) game.onAction(it) },
                        leave, state.presentation)
                }
                null -> Unit
            }
        }
    }
}
