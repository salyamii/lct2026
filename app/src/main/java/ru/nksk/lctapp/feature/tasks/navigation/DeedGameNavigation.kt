package ru.nksk.lctapp.feature.tasks.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
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
import ru.nksk.lctapp.core.ui.game.EventAudioEffect
import ru.nksk.lctapp.feature.tasks.ui.*
import ru.nksk.lctapp.domain.minigame.DeedGameKind

@Serializable
@SerialName("deed_game")
data class DeedGame(val occurrenceId: String, val choiceId: String? = null) : NavKey

fun EntryProviderScope<NavKey>.deedGameEntry(onFinished: (DeedGame, String?) -> Unit,
    isCurrentEntry: (DeedGame) -> Boolean = { true },
    chapterContent: @Composable (ChapterDeedContent) -> Unit = {}) {
    entry<DeedGame> { source ->
        val model = hiltViewModel<DeedGameViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val lifecycleState by lifecycle.currentStateAsState()
        val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
        EventAudioEffect(state.audioOccurrenceId, state.eventMedia, isCurrentEntry(source))
        LaunchedEffect(model, source.occurrenceId, source.choiceId) { model.load(source.occurrenceId, source.choiceId) }
        LaunchedEffect(model, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.exit.collect { onFinished(source, it) }
            }
        }
        BackHandler(enabled = resumed) { model.leave() }
        val leave = { if (resumed) model.leave() }
        DeedGameHost(state, model::retry, leave, onSkipGame = { if (resumed) model.skipGame() },
            onOpenSavings = { if (resumed) model.openRewardTransfer() },
            onSavingsInput = { if (resumed) model.changeRewardTransferInput(it) },
            onConfirmSavings = { if (resumed) model.confirmRewardTransfer() },
            onCancelSavings = { if (resumed) model.cancelRewardTransfer() }) {
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
                    LaunchedEffect(game, resumed, board.game.current) {
                        if (resumed) game.questionPresented(board.game.current)
                    }
                    LaunchedEffect(board.game) { model.finishComparison(board.game, game.comparisonEvidence()) }
                    PriceQuizScreen(board, { action ->
                        if (resumed && state.presentation?.canPlay == true) {
                            if (action is PriceQuizAction.Answer) action.questionIndex?.let(game::questionPresented)
                            game.onAction(action)
                            model.recordComparisonAnswers(game.comparisonEvidence())
                        }
                    },
                        leave, state.presentation)
                }
                DeedGameType.PRECISION -> {
                    val game = hiltViewModel<TargetStopViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishPrecision(board.game) }
                    TargetStopScreen(board, { if (resumed && state.presentation?.canPlay == true) game.onAction(it) },
                        leave, state.presentation)
                }
                DeedGameType.LIGHTS -> {
                    val game = hiltViewModel<LightsGameViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishLights(board.game) }
                    LightsGameScreen(board, { if (resumed && state.presentation?.canPlay == true) game.onAction(it) },
                        leave, state.presentation)
                }
                DeedGameType.SEQUENCE -> {
                    val game = hiltViewModel<SequenceGameViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishSequence(board.game) }
                    SequenceGameScreen(board, {
                        if (it == SequenceGameAction.ArtworkReady || (resumed && state.presentation?.canPlay == true)) {
                            game.onAction(it)
                        }
                    },
                        leave, state.presentation)
                }
                DeedGameType.PIPES -> {
                    val game = hiltViewModel<PipesGameViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishPipes(board.game) }
                    PipesGameScreen(board, { if (resumed && state.presentation?.canPlay == true) game.onAction(it) },
                        leave, state.presentation)
                }
                DeedGameType.DIFFERENCES -> {
                    val game = hiltViewModel<DifferencesGameViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishDifferences(board.game) }
                    DifferencesGameScreen(board, { if (resumed && state.presentation?.canPlay == true) game.onAction(it) },
                        leave, state.presentation)
                }
                DeedGameType.STACKING -> {
                    val game = hiltViewModel<StackingGameViewModel>()
                    val board by game.uiState.collectAsStateWithLifecycle()
                    val position = game.position.collectAsStateWithLifecycle()
                    LaunchedEffect(board.game) { model.finishStacking(board.game) }
                    StackingGameScreen(board, { if (resumed && state.presentation?.canPlay == true) game.onAction(it) },
                        leave, state.presentation, position = { position.value })
                }
                DeedGameType.TELESCOPE, DeedGameType.MARK, DeedGameType.WHEEL,
                DeedGameType.STRAP, DeedGameType.SIGNAL, DeedGameType.ROUTE_MAP, DeedGameType.BOAT,
                DeedGameType.TRIP_PACKING, DeedGameType.SHOPPING_COMPARISON, DeedGameType.RECORD_JOURNAL,
                DeedGameType.RECORD_LENSES, DeedGameType.RECORD_LIGHTS,
                DeedGameType.OBSERVATORY_OPENING, DeedGameType.PLATE_HAND, DeedGameType.PLATE_SOLUTION,
                DeedGameType.LIGHT_INSCRIPTION, DeedGameType.SERVICE_EMBLEM, DeedGameType.SIGNAL_POST,
                DeedGameType.CHAPTER_FINALE, DeedGameType.RING_TOSS -> {
                    chapterContent(ChapterDeedContent(
                        kind = DeedGameKind.valueOf(checkNotNull(state.type).name),
                        interactionBlocked = !resumed || state.busy || state.presentation?.canPlay != true,
                        repairCoins = state.repairCoins,
                        onCompleted = { if (resumed) model.finishChapterGame(it) },
                        onPayRepair = { id, rock -> model.payBoatRepair(id, rock) },
                        onExit = leave,
                        onShoppingProgress = model::recordShoppingProgress,
                        onPackingCompleted = { if (resumed) model.finishTripPacking(it) },
                    ))
                }
                DeedGameType.NEED_WANT, DeedGameType.REAL_DISCOUNT, DeedGameType.COIN_BALANCE,
                DeedGameType.FINANCIAL_TRAP, DeedGameType.EXTRA_PURCHASE -> FinancialDeedContent(
                    occurrenceId = source.occurrenceId,
                    kind = DeedGameKind.valueOf(checkNotNull(state.type).name),
                    deed = checkNotNull(state.presentation), resumed = resumed,
                    onCompleted = model::finishFinancialGame, onBack = leave,
                )
                null -> Unit
            }
        }
    }
}
