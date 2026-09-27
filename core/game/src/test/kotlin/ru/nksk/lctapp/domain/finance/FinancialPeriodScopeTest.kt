package ru.nksk.lctapp.domain.finance

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.CompletedGoalProject
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.story.StoryState

class FinancialPeriodScopeTest {
    private val oldQuestion = FinancialQuestion("old-question", FinancialQuestionKind.PLAN_REVIEW, "Вопрос",
        listOf(FinancialAnswerOption("correct", "Ответ")), "correct", "Объяснение", sourceActionIds = listOf("old-period"))
    private val selected = GameState(PetState("PLAIN", PetVisualState.NORMAL), EconomyState(BudgetPlan(20, 20, 20, 20)),
        StoryState(null, null, null, listOf(StoryDecision("finished", "choice"))), 0, 0, emptyList(),
        selectedGoalId = "new-goal", completedGoalProjects = listOf(CompletedGoalProject("old-goal", "finished")),
        financial = FinancialProgress(periods = listOf(FinancialPeriod("old-period", "old-goal", 1, 1, 50, 20,
            closedDay = 1, imported = true)), practice = oldQuestion))

    @Test fun selectingNextGoalCannotReuseUnansweredPracticeFromImportedGoal() {
        val adopted = FinancialPeriods.adopt(selected, imported = false)
        assertNull(adopted.financial.practice)
        assertEquals(2, adopted.financial.currentPeriod!!.ordinal)
        assertFalse(adopted.financial.currentPeriod!!.reviewedPlan)
        assertEquals(selected.economy, adopted.economy)
    }

    @Test fun oldAnswerCannotMarkNewPeriodReviewed() {
        val adopted = FinancialPeriods.adopt(selected, imported = false)
        val foreignAnswer = adopted.copy(financial = adopted.financial.copy(
            practice = oldQuestion.copy(answeredOptionId = "correct", attempts = 1)))
        val command = EngineCommand.AnswerFinancialQuestion(oldQuestion.id, "correct")
        val recorded = FinancialPeriods.record(adopted, foreignAnswer, EngineRequest("answer", null, command))
        assertFalse(recorded.financial.currentPeriod!!.reviewedPlan)
        assertNull(FinancialPeriods.adopt(foreignAnswer).financial.practice)
    }
}
