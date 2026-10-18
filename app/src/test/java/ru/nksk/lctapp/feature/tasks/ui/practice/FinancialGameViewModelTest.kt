package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.*

class FinancialGameViewModelTest {
    @Test fun everyBoardRestoresAfterRecreationWithoutSharingAnotherOffersProgress() {
        val kinds = listOf(DeedGameKind.NEED_WANT, DeedGameKind.REAL_DISCOUNT,
            DeedGameKind.FINANCIAL_TRAP, DeedGameKind.EXTRA_PURCHASE, DeedGameKind.COIN_BALANCE)
        for (kind in kinds) {
            val saved = SavedStateHandle()
            val model = FinancialGameViewModel(saved)
            model.bind("offer", kind)
            if (kind == DeedGameKind.COIN_BALANCE) {
                model.onBalance(CoinBalanceAction.Start)
                model.onBalance(CoinBalanceAction.Toggle(PracticeItem.BUN, 0))
            } else model.onChoice(PracticeChoiceAction.Answer(0, 0))
            val progress = model.uiState.value
            val restored = FinancialGameViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
            restored.bind("offer", kind)
            assertEquals(progress, restored.uiState.value)
            restored.onChoice(PracticeChoiceAction.Restart)
            restored.onBalance(CoinBalanceAction.Restart)
            assertEquals(progress, restored.uiState.value)
            restored.bind("different-offer", kind)
            assertEquals(FinancialBoard.create(kind), restored.uiState.value)
        }
    }

    @Test fun restoredLastFeedbackMustBeAcknowledgedBeforeCompletion() {
        val saved = SavedStateHandle()
        val model = FinancialGameViewModel(saved)
        model.bind("offer", DeedGameKind.EXTRA_PURCHASE)
        PracticeGames.extraPurchases().questions.forEachIndexed { index, question ->
            model.onChoice(PracticeChoiceAction.Answer(question.correctChoice, index))
            if (index < 2) model.onChoice(PracticeChoiceAction.Next(index))
        }
        val restored = FinancialGameViewModel(saved)
        restored.bind("offer", DeedGameKind.EXTRA_PURCHASE)
        assertNull(restored.uiState.value!!.score())
        restored.onChoice(PracticeChoiceAction.Next(1))
        assertNull(restored.uiState.value!!.score())
        restored.onChoice(PracticeChoiceAction.Next(2))
        assertEquals(5L, restored.uiState.value!!.score()!!.reward(5))
    }
}
