package ru.nksk.lctapp.feature.learning.ui

import org.junit.Assert.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.domain.engine.DayJournalEntry
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.finance.BudgetPlanRevision
import ru.nksk.lctapp.domain.finance.FinancialBudgetProjection
import ru.nksk.lctapp.domain.finance.FinancialPeriod
import ru.nksk.lctapp.domain.finance.FinancialPeriods
import ru.nksk.lctapp.domain.finance.FinancialPracticeSeries
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.HistoryCodec

class FinancialPracticePresentationTest {
    @Test fun legacyWordingUsesOriginalRecordedPlanAndSpendingWithoutChangingQuestionIdentity() {
        val fixture = Fixture()
        val saved = fixture.legacy.copy(answeredOptionId = "more", attempts = 2, usedHint = true,
            version = 3, series = FinancialPracticeSeries("saved-series", 4))
        val encoded = HistoryCodec.encodeQuestion(saved)
        val historyBefore = fixture.history.map(HistoryCodec::encode)
        val presented = requireNotNull(financialPracticePresentation(saved, fixture.history, fixture.catalog))

        assertEquals("less", presented.correctAnswerId)
        assertNotEquals(saved.prompt, presented.prompt)
        assertTrue(presented.prompt.contains("35"))
        assertTrue(presented.prompt.contains("5"))
        assertEquals(fixture.fresh.prompt, presented.prompt)
        assertEquals(fixture.fresh.explanation, presented.explanation)
        assertEquals(saved.options.map { it.id }, presented.options.map { it.id })
        assertEquals(fixture.fresh.options.associate { it.id to it.text }, presented.options.associate { it.id to it.text })
        assertEquals(saved, presented.copy(prompt = saved.prompt, explanation = saved.explanation, options = saved.options))
        assertEquals(encoded, HistoryCodec.encodeQuestion(saved))
        assertEquals(historyBefore, fixture.history.map(HistoryCodec::encode))
    }

    @Test fun laterSpendingAndRepeatedRequestCannotReplaceTheOriginalQuestionBoundary() {
        val fixture = Fixture()
        val anchor = fixture.history.last()
        val previous = requireNotNull(anchor.after)
        val later = previous.copy(economy = EconomyState(BudgetPlan(1, 0, 0, 0)),
            financial = previous.financial.copy(periods = listOf(previous.financial.currentPeriod!!.copy(spentAvailable = 99))))
        val history = fixture.history + listOf(
            AuditEntry("later", 5, "run", AuditType.TECHNICAL_UPDATE, before = previous, after = later),
            AuditEntry("resume", 6, "run", AuditType.COMMAND,
                request = EngineRequest("resume", null, EngineCommand.RequestFinancialPractice()), before = later, after = later),
        )
        val presented = requireNotNull(financialPracticePresentation(fixture.legacy, history, fixture.catalog))
        assertEquals(fixture.fresh.prompt, presented.prompt)
        assertEquals(fixture.fresh.explanation, presented.explanation)
        assertEquals(fixture.legacy.reviewEvidence, presented.reviewEvidence)
    }

    @Test fun missingHistoryOrQuestionBoundaryKeepsTheStoredWording() {
        val fixture = Fixture()
        assertSame(fixture.legacy, financialPracticePresentation(fixture.legacy, emptyList(), fixture.catalog))
        assertSame(fixture.legacy, financialPracticePresentation(fixture.legacy, fixture.history.dropLast(1), fixture.catalog))
        assertSame(fixture.legacy, financialPracticePresentation(fixture.legacy,
            fixture.history.filterNot { it.sequence == 3L }, fixture.catalog))
        assertNull(financialPracticePresentation(null, fixture.history, fixture.catalog))
    }

    @Test fun changedAnswerOptionsEvidenceOrSourcesNeverRelabelTheSavedTask() {
        val fixture = Fixture()
        val changes = listOf(
            fixture.legacy.copy(correctAnswerId = "more"),
            fixture.legacy.copy(options = fixture.legacy.options.map { if (it.id == "more") it.copy(id = "another") else it }),
            fixture.legacy.copy(reviewEvidence = fixture.legacy.reviewEvidence!!.copy(planRevisionId = "another-plan")),
            fixture.legacy.copy(sourceActionIds = listOf("another-action")),
        )
        changes.forEach { question ->
            assertSame(question, financialPracticePresentation(question, fixture.history, fixture.catalog))
        }
    }

    @Test fun genericTrainingAndOtherTopicsRemainExactlyAsSaved() {
        val fixture = Fixture()
        val example = fixture.legacy.copy(reviewEvidence = null)
        val otherTopic = fixture.legacy.copy(kind = FinancialQuestionKind.SAVING_PRACTICE)
        assertSame(example, financialPracticePresentation(example, fixture.history, fixture.catalog))
        assertSame(otherTopic, financialPracticePresentation(otherTopic, fixture.history, fixture.catalog))
    }

    @Test fun cachedLegacyWordingPreservesAnswerProgressAndReadsOnlyForAnImmutableQuestionChange() = runTest {
        val fixture = Fixture()
        val current = checkNotNull(fixture.history.last().after)
        var reads = 0
        val repository = object : GameRepository {
            override fun observe() = MutableStateFlow(current)
            override suspend fun read() = current
            override suspend fun initializeIfAbsent(initial: GameState) = current
            override suspend fun update(transform: (GameState) -> GameState) = error("Presentation cannot write")
            override suspend fun readHistory(): List<AuditEntry> { reads += 1; return fixture.history }
        }
        val session = GameSession(repository, object : StoryContentRepository {
            override suspend fun read(): StoryContent = fixture.catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, fixture.catalog, current)
        val cache = TrainingQuestionPresentation(session)
        val first = checkNotNull(cache.display(fixture.legacy))
        val answered = fixture.legacy.copy(answeredOptionId = "more", attempts = 2, usedHint = true)
        val displayed = checkNotNull(cache.display(answered))
        assertEquals(1, reads)
        assertEquals(first.prompt, displayed.prompt)
        assertEquals(first.explanation, displayed.explanation)
        assertEquals(answered.answeredOptionId, displayed.answeredOptionId)
        assertEquals(2, displayed.attempts)
        assertTrue(displayed.usedHint)
        cache.display(answered.copy(version = answered.version + 1))
        assertEquals(2, reads)
        cache.display(answered.copy(kind = FinancialQuestionKind.CONSEQUENCE))
        assertEquals(2, reads)
    }

    private class Fixture {
        val catalog = bundledGameCatalog()
        private val period = FinancialPeriod("period", "goal", 1, 1, 100, 0)
        private val initial = createInitialGameState().copy(
            economy = EconomyState(BudgetPlan(0, 0, 0, 100)),
            financial = FinancialProgress(period.id, listOf(period)),
            engine = EngineState(catalog.rules.id, 0, 1, DayPhase.RUNNING, 0, 5, false, null, 100,
                emptyList(), emptyList()),
        )
        private val allocation = BudgetPlan(35, 20, 35, 10)
        private val revision = BudgetPlanRevision("plan", period.id, 1, 1, 100, allocation, BudgetRevisionReason.INITIAL)
        private val planned = initial.copy(economy = initial.economy.copy(plan = allocation),
            financial = initial.financial.copy(plans = listOf(revision)))
        private val spent = planned.copy(
            economy = EconomyOperations.spend(planned.economy, 5, SpendingKind.FEEDING),
            financial = planned.financial.copy(periods = listOf(period.copy(spentAvailable = 5))),
            engine = planned.engine!!.copy(journal = listOf(DayJournalEntry("meal", DayJournalKind.MEAL, "basic", -5))),
        )
        private val priorHistory = listOf(
            AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = initial),
            AuditEntry("plan", 2, "run", AuditType.COMMAND,
                request = EngineRequest("confirm-plan", null, EngineCommand.ConfirmBudget("plan", 0)),
                before = initial, after = planned),
            AuditEntry("meal", 3, "run", AuditType.COMMAND,
                request = EngineRequest("meal-action", null, EngineCommand.Feed("basic")),
                before = planned, after = spent, operations = listOf(LedgerEntry("meal", LedgerKind.AVAILABLE_EXPENSE, 5))),
        )
        val fresh = FinancialPeriods.question(spent, "review", FinancialQuestionKind.PLAN_REVIEW,
            budgetReport = FinancialBudgetProjection.report(spent, priorHistory, catalog.content).single())
        val legacy = fresh.copy(prompt = "Старый текст про план и факт", explanation = "Старое объяснение",
            options = fresh.options.reversed().map { it.copy(text = "Старая подпись ${it.id}") })
        val history = priorHistory + AuditEntry("request-review", 4, "run", AuditType.COMMAND,
            request = EngineRequest("request-review", null, EngineCommand.RequestFinancialPractice()), before = spent,
            after = spent.copy(financial = spent.financial.copy(practice = legacy)))
    }
}
