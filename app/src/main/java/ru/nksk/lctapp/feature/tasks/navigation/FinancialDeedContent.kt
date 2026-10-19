package ru.nksk.lctapp.feature.tasks.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.domain.minigame.*
import ru.nksk.lctapp.feature.tasks.ui.DeedGamePresentation
import ru.nksk.lctapp.feature.tasks.ui.practice.*

@Composable
internal fun FinancialDeedContent(
    occurrenceId: String,
    kind: DeedGameKind,
    deed: DeedGamePresentation,
    resumed: Boolean,
    onCompleted: (DeedGameScore) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val model = hiltViewModel<FinancialGameViewModel>()
    val board by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model, occurrenceId, kind) { model.bind(occurrenceId, kind) }
    val current = board?.takeIf { it.kind == kind } ?: return
    val canPlay = resumed && deed.canPlay
    LaunchedEffect(current, canPlay) {
        if (canPlay) current.score()?.let(onCompleted)
    }
    val choiceAction: (PracticeChoiceAction) -> Unit = { if (canPlay) model.onChoice(it) }
    val presentation = deed.copy(canPlay = canPlay)
    when (current) {
        is FinancialBoard.Needs -> NeedWantScreen(current.game, choiceAction, onBack, presentation, onHome)
        is FinancialBoard.Discounts -> RealDiscountScreen(current.game, choiceAction, onBack, presentation, onHome)
        is FinancialBoard.Traps -> FinancialTrapScreen(current.game, choiceAction, onBack, presentation, onHome)
        is FinancialBoard.Extra -> ExtraPurchaseScreen(current.game, choiceAction, onBack, presentation, onHome)
        is FinancialBoard.Balance -> CoinBalanceScreen(current.game,
            { if (canPlay) model.onBalance(it) }, onBack, presentation, onHome)
    }
}
