package ru.nksk.lctapp.domain.finance

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class FinancialTrainingTest {
    private val state = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(BudgetPlan(35, 20, 35, 10), availableBalance = 100, savingsBalance = 20),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList(), selectedGoalId = "goal",
        financial = FinancialProgress("period", listOf(FinancialPeriod("period", "goal", 1, 1, 100, 20))))

    @Test fun allQuestionTemplatesAreDistinctAndTheirArithmeticAnswerKeysMatchTheFrozenLedger() {
        for (kind in FinancialQuestionKind.entries) {
            for (variant in 0..9) {
                val first = FinancialPeriods.question(state, "first:$kind:$variant", kind,
                    knownNeeds = 35, purchasePrice = 7, purchaseTitle = "Игра на ярмарке")
                val start = FinancialTraining.start(first, "series:$variant")
                assertEquals(start, FinancialTraining.start(first, "series:$variant"))
                val questions = listOf(start) + start.series!!.remainingQuestions
                assertEquals(4, questions.map { it.id }.distinct().size)
                assertEquals(4, questions.map { it.prompt }.distinct().size)
                assertTrue(questions.all { it.kind == kind && it.sourceActionIds.first() == "period" })
                assertTrue(questions.drop(1).all { it.reviewEvidence == null })
                questions.forEach { question ->
                    question.ledgerTask?.let { task ->
                        assertEquals(task.expectedAnswer().toString(), question.correctAnswerId)
                    }
                    assertEquals(question, HistoryCodec.decodeQuestion(HistoryCodec.encodeQuestion(question)))
                }
            }
        }
    }

    @Test fun pausedWrongAnswerAndFrozenQueueSurviveSnapshotRoundTrip() {
        val first = FinancialPeriods.question(state, "first", FinancialQuestionKind.SAVING_PRACTICE)
        val start = FinancialTraining.start(first, "series")
        val wrong = start.copy(answeredOptionId = start.options.first { it.id != start.correctAnswerId }.id, attempts = 2)
        val saved = state.copy(financial = state.financial.copy(practice = wrong))
        val snapshot = HistoryCodec.snapshot("run", saved, emptyList())
        val decoded = HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot))
        assertEquals(snapshot, decoded)
        assertEquals(wrong, decoded.state.financial.practice)
        assertEquals(3, decoded.state.financial.practice!!.series!!.remainingQuestions.size)
        assertEquals(2, decoded.state.financial.practice!!.attempts)
    }

    @Test fun standaloneTopicsRemainAvailableWithoutFabricatingAGamePeriodOrRealTransactions() {
        for (kind in FinancialQuestionKind.entries) {
            val first = FinancialTraining.standalone(kind, "after-campaign:$kind")
            val questions = listOf(first) + first.series!!.remainingQuestions
            assertEquals(4, questions.size)
            assertEquals(4, questions.map { it.prompt }.distinct().size)
            assertTrue(questions.all { it.sourceActionIds.single().startsWith("training:") && it.reviewEvidence == null })
            questions.forEach { question ->
                question.ledgerTask?.let { assertEquals(it.expectedAnswer().toString(), question.correctAnswerId) }
            }
        }
    }

    @Test fun legacyQuestionAndCommandKeepTheirOriginalWireShapeAndSnapshotChecksum() {
        val wire = """{"id":"legacy","kind":"PLAN_REVIEW","prompt":"Question","options":[{"id":"yes","text":"Yes"}],"correctAnswerId":"yes","explanation":"Explanation","sourceActionIds":["period"],"answeredOptionId":null,"usedHint":false,"attempts":0,"version":1,"ledgerTask":null,"comparisonFamily":null,"reviewEvidence":null}"""
        val question = HistoryCodec.decodeQuestion(wire)
        assertNull(question.series)
        assertEquals(wire, HistoryCodec.encodeQuestion(question))
        val request = HistoryCodec.encodeRequest(EngineRequest("old", null, EngineCommand.RequestFinancialPractice()))
        assertFalse(request.contains("\"series\""))
        val legacyState = state.copy(financial = state.financial.copy(practice = question))
        val stateWire = HistoryCodec.encodeState(legacyState)
        assertFalse(stateWire.contains("\"series\""))
        val checksum = HistoryCodec.sha256("4\nold-run\n0\n$stateWire\n[]")
        val snapshot = GameSnapshot(formatVersion = 4, runId = "old-run", state = legacyState, history = emptyList(), historySequence = 0, checksum = checksum)
        assertEquals(checksum, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot)).checksum)
    }
}
