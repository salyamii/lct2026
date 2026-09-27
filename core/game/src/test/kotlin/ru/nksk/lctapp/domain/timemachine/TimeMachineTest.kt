package ru.nksk.lctapp.domain.timemachine

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.location.*
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.*

class TimeMachineTest {
    @Test fun correctAnswerWithoutConfirmedQuestionPresentationKeepsIncompleteEvidence() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val live = HistoryCodec.encodeState(f.repo.state)
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass"))
        val quiz = requireNotNull(f.machine.quiz(requireNotNull(result.simulationId), TimeMachineQuizKind.CAUSE))
        assertTrue(f.machine.submitQuiz(quiz.id, "spent_less", "unseen-answer").correct)
        val answer = f.repo.entries.flatMap { it.facts }.single { it.detail is FactDetail.QuestionAnswer }
        assertEquals(quiz.id, answer.context.presentationId)
        assertFalse(answer.context.informationPresented)
        assertFalse(answer.context.complete)
        assertTrue(SkillEvaluator().evaluate(listOf(answer)).none { it.outcome == ObservationOutcome.SUPPORTED })
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))
    }

    @Test fun laterPresentationCannotUpgradeCommittedAnswerButNewAttemptCanUseItsOwnEvidence() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass"))
        val quiz = requireNotNull(f.machine.quiz(requireNotNull(result.simulationId), TimeMachineQuizKind.CAUSE))
        f.machine.submitQuiz(quiz.id, "spent_less", "original-answer")
        val first = f.repo.entries.flatMap { it.facts }.single { it.detail is FactDetail.QuestionAnswer }
        f.machine.recordQuestionPresented(quiz.id)
        val writes = f.repo.recordFactCalls
        val retry = f.machine.submitQuiz(quiz.id, "spent_less", "original-answer",
            usedHint = true, questionPresented = true)
        assertTrue(retry.alreadyRecorded)
        assertEquals(writes, f.repo.recordFactCalls)
        assertEquals(first, f.repo.entries.flatMap { it.facts }.single { it.detail is FactDetail.QuestionAnswer })
        assertFalse(first.context.complete)
        assertFalse(Assistance.HINT in first.context.assistance)

        f.machine.submitQuiz(quiz.id, "spent_less", "new-attempt", usedHint = true, questionPresented = true)
        val second = f.repo.entries.flatMap { it.facts }.single { it.eventId == "time-machine-answer:new-attempt" }
        assertTrue(second.context.informationPresented)
        assertTrue(second.context.complete)
        assertTrue(Assistance.HINT in second.context.assistance)
        assertEquals(2, (second.detail as FactDetail.QuestionAnswer).attempt)
    }

    @Test fun reflectionQuestionExplainsTheActualRepairMoneyAndEffortTradeoff() = runTest {
        val f = Fixture(manualRepair = true, shopTitle = "Заклинило компас", alternativeTitle = "Починить самому")
        val repair = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val live = HistoryCodec.encodeState(f.repo.state)
        val result = f.machine.simulate(TimeMachineRequest(repair.id, "choice:pass"))
        assertEquals(25L, result.baseline!!.spent)
        assertEquals(0L, result.alternative!!.spent)
        assertEquals(5, result.baseline!!.state.engine!!.energy)
        assertEquals(3, result.alternative!!.state.engine!!.energy)
        val quiz = requireNotNull(f.machine.quiz(requireNotNull(result.simulationId), TimeMachineQuizKind.CAUSE))
        assertTrue(quiz.prompt.contains("Заклинило компас"))
        assertTrue(quiz.prompt.contains("Починить самому"))
        assertEquals("Справились бы сами: сохранили 25 монет, но потратили 2 силы",
            quiz.options.single { it.id == "spent_less" }.text)
        assertFalse(f.machine.submitQuiz(quiz.id, "paid_for_help", "repair-wrong").correct)
        val answer = f.machine.submitQuiz(quiz.id, "spent_less", "repair-correct")
        assertTrue(answer.correct)
        assertTrue(answer.explanation.contains("25 монет за помощь"))
        assertTrue(answer.explanation.contains("потратили 2 силы"))
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))
    }

    @Test fun reflectionQuestionNamesTheOptionalOfferAndTheActualCostOfPassingIt() = runTest {
        val f = Fixture(shopTitle = "Игра с кольцами", alternativeTitle = "Пройти мимо", price = 7)
        val purchase = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val result = f.machine.simulate(TimeMachineRequest(purchase.id, "choice:pass"))
        val quiz = requireNotNull(f.machine.quiz(requireNotNull(result.simulationId), TimeMachineQuizKind.CAUSE))
        assertTrue(quiz.prompt.contains("Игра с кольцами"))
        assertTrue(quiz.prompt.contains("Пройти мимо"))
        assertEquals("Отказались бы от оплаты и сохранили 7 монет", quiz.options.single { it.id == "spent_less" }.text)
        assertFalse(quiz.options.any { it.id == "different_income" || it.id == "different_transfer" })
        val answer = f.machine.submitQuiz(quiz.id, "spent_less", "ring-answer")
        assertTrue(answer.correct)
        assertEquals("В этой ситуации мы заплатили 7 монет. Выбор «Пройти мимо» не требует оплаты, поэтому эти монеты остались бы у нас.",
            answer.explanation)
    }

    @Test fun reflectionsIncludePaidOptionalPurchaseButNotFoodOrSavingsMovements() = runTest {
        val f = Fixture()
        val purchase = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val meal = f.record(EngineCommand.Feed("basic"))
        val deposit = f.record(EngineCommand.DepositSavings(10))
        val live = HistoryCodec.encodeState(f.repo.state)
        val history = f.repo.entries.toList()

        val reflections = f.machine.availableReflections()
        assertEquals(TimeMachineStatus.READY, reflections.status)
        assertEquals(listOf(purchase.id), reflections.decisions.map { it.entryId })
        assertEquals(listOf("choice:pass"), reflections.decisions.single().alternatives.map { it.id })
        assertTrue(f.machine.availableDecisions().decisions.map { it.entryId }.containsAll(listOf(meal.id, deposit.id)))
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))
        assertEquals(history, f.repo.entries)
        assertEquals(0, f.repo.updateCalls)
        assertEquals(0, f.repo.recordFactCalls)
    }

    @Test fun foodReplacingPurchaseAndPassedPurchaseAreNotReflectionPrompts() = runTest {
        val food = Fixture(foodPurchase = true)
        val meal = food.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        assertTrue(food.machine.availableReflections().decisions.isEmpty())
        assertTrue(food.machine.availableDecisions().decisions.any { it.entryId == meal.id })

        val passed = Fixture()
        passed.record(EngineCommand.CompleteEvent("shop-occurrence", "pass"))
        assertTrue(passed.machine.availableReflections().decisions.isEmpty())
        assertTrue(passed.machine.availableDecisions().decisions.isNotEmpty())
    }

    @Test fun paidRepairIsOfferedOnlyWhenFreeWorkWasFeasibleAtThatTime() = runTest {
        for (type in listOf(EventType.RANDOM, EventType.STORY)) {
            val possible = Fixture(manualRepair = true, shopType = type)
            val repair = possible.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
            val reflected = possible.machine.availableReflections().decisions.single()
            assertEquals(repair.id, reflected.entryId)
            assertEquals(listOf("choice:pass"), reflected.alternatives.map { it.id })
            assertTrue(reflected.alternatives.single().assumesCompletedWork)

            val tired = Fixture(manualRepair = true, shopType = type, energy = 1)
            tired.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
            assertTrue(tired.machine.availableReflections().decisions.isEmpty())
        }
    }

    @Test fun ordinaryFeeAndStoryDetourAreNotMistakenForRepair() = runTest {
        val fee = Fixture(shopType = EventType.RANDOM)
        fee.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        assertTrue(fee.machine.availableReflections().decisions.isEmpty())

        val detour = Fixture(manualRepair = true, shopType = EventType.STORY, gameWork = false)
        detour.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        assertTrue(detour.machine.availableReflections().decisions.isEmpty())

        val legacyRepair = Fixture(manualRepair = true, gameWork = false)
        legacyRepair.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        assertEquals(1, legacyRepair.machine.availableReflections().decisions.size)
    }

    @Test fun reflectionRequiresCompatibleHistoryAndAnActualExpenseReceipt() = runTest {
        val f = Fixture()
        val purchase = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        f.repo.entries[0] = purchase.copy(operations = emptyList())
        assertTrue(f.machine.availableReflections().decisions.isEmpty())
        f.repo.entries[0] = purchase.copy(contentFingerprint = "old-rules")
        assertTrue(f.machine.availableReflections().decisions.isEmpty())
        f.repo.entries[0] = purchase
        f.repo.state = f.repo.state.copy(fatigue = f.repo.state.fatigue + 1)
        assertEquals(TimeMachineStatus.UNAVAILABLE, f.machine.availableReflections().status)
    }

    @Test fun paidRepairCanBeComparedWithCompletedManualWorkWithoutPlayingOrMutatingTheRealGame() = runTest {
        val f = Fixture(manualRepair = true)
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val before = HistoryCodec.encodeState(f.repo.state)
        val option = f.machine.availableDecisions().decisions.single().alternatives.single()
        assertTrue(option.assumesCompletedWork)
        assertTrue(option.title.contains("если закончить работу"))
        val result = f.machine.simulate(TimeMachineRequest(choice.id, option.id))
        assertEquals(TimeMachineStatus.COMPLETE, result.status)
        assertEquals(100L, result.alternative!!.state.economy.availableBalance)
        assertEquals(3, result.alternative!!.state.engine!!.energy)
        assertEquals(75L, result.baseline!!.state.economy.availableBalance)
        assertEquals(before, HistoryCodec.encodeState(f.repo.state))
        assertEquals(0, f.repo.updateCalls)
        assertTrue(f.repo.entries.flatMap { it.facts }.isEmpty())
        val actualBefore = choice.before!!
        assertEquals(BlockReason.InvalidEventAction, f.engine.blockReason(actualBefore,
            EngineCommand.CompleteEvent("shop-occurrence", "pass")))
    }

    @Test fun lifecycleFactsAreIdempotentAndDoNotChangeTheGameOrAwardSkillsForViewing() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val last = f.record(EngineCommand.Feed("basic"))
        val live = HistoryCodec.encodeState(f.repo.state)
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass", last.sequence))
        val id = requireNotNull(result.simulationId)
        assertEquals(0, f.repo.recordFactCalls)
        f.machine.recordSimulationLifecycle(id)
        f.machine.recordSimulationLifecycle(id)
        val simulationFacts = f.repo.entries.flatMap { it.facts }
        assertEquals(2, simulationFacts.size)
        assertEquals(setOf(TimeMachineLifecycleEvent.SIMULATION_STARTED, TimeMachineLifecycleEvent.SIMULATION_COMPLETED),
            simulationFacts.map { (it.detail as FactDetail.TimeMachineLifecycle).event }.toSet())
        assertTrue(simulationFacts.all { it.mode == AnalyticsMode.SIMULATION && it.learningContext == LearningContext.COUNTERFACTUAL })
        val quiz = requireNotNull(f.machine.quiz(id, TimeMachineQuizKind.CAUSE))
        assertEquals(2, f.repo.entries.flatMap { it.facts }.size) // creating a question is not showing it
        f.machine.recordQuestionPresented(quiz.id)
        f.machine.recordQuestionPresented(quiz.id)
        assertEquals(3, f.repo.entries.flatMap { it.facts }.size)
        assertTrue(SkillEvaluator().evaluate(f.repo.entries.flatMap { it.facts }).isEmpty())
        assertTrue(runCatching { f.machine.recordExplanationShown(quiz.id, "not-submitted") }.isFailure)
        val answer = f.machine.submitQuiz(quiz.id, "spent_less", "answer")
        assertEquals("answer", answer.submissionId)
        f.machine.recordExplanationShown(quiz.id, "answer")
        f.machine.recordExplanationShown(quiz.id, "answer")
        val explanation = f.repo.entries.flatMap { it.facts }.mapNotNull { it.detail as? FactDetail.TimeMachineLifecycle }
            .single { it.event == TimeMachineLifecycleEvent.EXPLANATION_SHOWN }
        assertEquals(quiz.id, explanation.questionId)
        assertEquals(answer.attempt, explanation.attempt)
        assertEquals("answer", explanation.submissionId)
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))
        assertEquals(0, f.repo.updateCalls)
        assertTrue(f.repo.entries.filter { it.type == AuditType.FACTS }.all { it.operations.isEmpty() && it.before == null && it.after == null })
        f.repo.entries.forEach { assertEquals(it, HistoryCodec.decodeEntry(HistoryCodec.encode(it))) }
    }

    @Test fun divergenceTelemetryKeepsReachedBoundaryAndCannotBeWrittenIntoAnotherPlaythrough() = runTest {
        val f = Fixture(balance = 30)
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "pass"))
        val last = f.record(EngineCommand.Feed("large"))
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:buy", last.sequence))
        val id = requireNotNull(result.simulationId)
        f.machine.recordSimulationLifecycle(id)
        val detail = f.repo.entries.flatMap { it.facts }.mapNotNull { it.detail as? FactDetail.TimeMachineLifecycle }
            .single { it.event == TimeMachineLifecycleEvent.SIMULATION_DIVERGED }
        assertEquals(choice.sequence, detail.reachedSequence)
        assertEquals(last.sequence, detail.requestedSequence)
        val factsBefore = f.repo.recordFactCalls
        // Restoring another run keeps its own history; a cached old calculation cannot add facts to it.
        f.repo.entries.clear()
        f.repo.entries += last.copy(id = "other", sequence = 1, runId = "other-run")
        assertTrue(runCatching { f.machine.recordSimulationLifecycle(id) }.isFailure)
        assertEquals(factsBefore, f.repo.recordFactCalls)
    }

    @Test fun laterRealProgressKeepsQuizValidButReplacingItsSameRunSourceInvalidatesIt() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val last = f.record(EngineCommand.Feed("basic"))
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass", last.sequence))
        val quiz = requireNotNull(f.machine.quiz(requireNotNull(result.simulationId), TimeMachineQuizKind.CAUSE))
        f.record(EngineCommand.DepositSavings(1))
        val newerGame = HistoryCodec.encodeState(f.repo.state)
        assertTrue(f.machine.submitQuiz(quiz.id, "spent_less", "accepted").correct)
        assertEquals(newerGame, HistoryCodec.encodeState(f.repo.state))
        val writes = f.repo.recordFactCalls
        // A valid imported history may retain the run/entry IDs while replacing presentation evidence.
        f.repo.entries[0] = choice.copy(context = DecisionContext(presentationId = "restored-other-context"))
        assertTrue(runCatching { f.machine.submitQuiz(quiz.id, "spent_less", "stale") }.isFailure)
        assertTrue(runCatching { f.machine.recordQuestionPresented(quiz.id) }.isFailure)
        assertEquals(writes, f.repo.recordFactCalls)
    }

    @Test fun sameRunRestoreBetweenValidationAndAppendRejectsQuizAndLifecycleFacts() = runTest {
        listOf(false, true).forEach { lifecycle ->
            val f = Fixture()
            val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
            val last = f.record(EngineCommand.Feed("basic"))
            val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass", last.sequence))
            val id = requireNotNull(result.simulationId)
            val quiz = requireNotNull(f.machine.quiz(id, TimeMachineQuizKind.CAUSE))
            val live = HistoryCodec.encodeState(f.repo.state)
            // Simulate the restore winning after TimeMachine's read but before the storage transaction checks its guard.
            f.repo.beforeFactsWrite = {
                f.repo.entries[0] = choice.copy(context = DecisionContext(presentationId = "restored-source"))
            }
            assertTrue(runCatching {
                if (lifecycle) f.machine.recordSimulationLifecycle(id)
                else f.machine.submitQuiz(quiz.id, "spent_less", "raced-answer")
            }.isFailure)
            assertEquals(0, f.repo.recordFactCalls)
            assertTrue(f.repo.entries.flatMap { it.facts }.isEmpty())
            assertEquals(live, HistoryCodec.encodeState(f.repo.state))
        }
    }

    @Test fun rejectedAttemptsDoNotReplayAsExpensesAndFinancialPeriodsRemainIsolated() = runTest {
        val f = Fixture()
        f.repo.state = f.repo.state.copy(selectedGoalId = "goal")
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        f.repo.entries += AuditEntry("rejected", 2, "run", AuditType.REJECTED,
            request = EngineRequest("failed-purchase", f.repo.state.engine?.revision,
                EngineCommand.DepositSavings(1000)))
        val last = f.record(EngineCommand.Feed("basic"))
        val realState = HistoryCodec.encodeState(f.repo.state)
        val historySize = f.repo.entries.size
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass", last.sequence))
        assertEquals(TimeMachineStatus.COMPLETE, result.status)
        assertEquals(30L, result.baseline!!.state.financial.currentPeriod!!.spentAvailable)
        assertEquals(5L, result.alternative!!.state.financial.currentPeriod!!.spentAvailable)
        assertEquals(30L, result.baseline!!.spent)
        assertEquals(5L, result.alternative!!.spent)
        assertEquals(realState, HistoryCodec.encodeState(f.repo.state))
        assertEquals(historySize, f.repo.entries.size)
        assertEquals(0, f.repo.updateCalls)
        assertEquals(0, f.repo.recordFactCalls)
    }

    @Test fun changedChoiceReplaysFollowingCommandsWithoutWritingLiveState() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val last = f.record(EngineCommand.Feed("basic"))
        val live = HistoryCodec.encodeState(f.repo.state)
        val availability = f.machine.availableDecisions()
        assertEquals(TimeMachineStatus.READY, availability.status)
        assertTrue(availability.decisions.any { it.entryId == choice.id })
        val request = TimeMachineRequest(choice.id, "choice:pass", last.sequence)
        val result = f.machine.simulate(request)
        val baseline = requireNotNull(result.baseline)
        val alternative = requireNotNull(result.alternative)
        assertEquals(TimeMachineStatus.COMPLETE, result.status)
        assertEquals(last.sequence, result.reachedSequence)
        assertEquals(70L, baseline.state.economy.availableBalance)
        assertEquals(95L, alternative.state.economy.availableBalance)
        assertEquals(30L, baseline.spent)
        assertEquals(5L, alternative.spent)
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))
        assertEquals(0, f.repo.updateCalls)
        assertEquals(0, f.repo.recordFactCalls)
        assertEquals(result, f.machine.simulate(request))
    }

    @Test fun impossibleFollowingCommandStopsAtTheSameComparableBoundary() = runTest {
        val f = Fixture(balance = 30)
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "pass"))
        val last = f.record(EngineCommand.Feed("large"))
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:buy", last.sequence))
        assertEquals(TimeMachineStatus.DIVERGED, result.status)
        assertEquals(choice.sequence, result.reachedSequence)
        assertEquals(last.sequence, result.requestedSequence)
        assertEquals(30L, result.baseline!!.state.economy.availableBalance)
        assertEquals(5L, result.alternative!!.state.economy.availableBalance)
        assertEquals(0L, f.repo.state.economy.availableBalance)
        assertEquals(0, f.repo.updateCalls)
    }

    @Test fun unknownHistoryVersionOrUnavailableAlternativeCannotBeSimulated() = runTest {
        val f = Fixture(balance = 10)
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "pass"))
        assertEquals(TimeMachineStatus.UNAVAILABLE,
            f.machine.simulate(TimeMachineRequest(choice.id, "choice:buy")).status)
        f.repo.entries[0] = choice.copy(contentFingerprint = null)
        assertEquals(TimeMachineStatus.UNAVAILABLE,
            f.machine.simulate(TimeMachineRequest(choice.id, "choice:buy")).status)
        assertEquals(0, f.repo.updateCalls)
    }

    @Test fun changedUnselectedChoiceInvalidatesContentVersion() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val changed = f.catalog.copy(content = f.catalog.content.copy(choices = f.catalog.content.choices.map {
            if (it.id == "pass") it.copy(moneyDelta = -1) else it
        }))
        val machine = TimeMachine(f.repo, f.engine, changed)
        assertEquals(TimeMachineStatus.INCOMPATIBLE_VERSION,
            machine.simulate(TimeMachineRequest(choice.id, "choice:pass")).status)
    }

    @Test fun originalPathMustReproduceItsActualStoredResult() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val originalAfter = requireNotNull(choice.after)
        val falseAfter = originalAfter.copy(fatigue = originalAfter.fatigue + 1)
        f.repo.entries[0] = choice.copy(after = falseAfter)
        f.repo.state = falseAfter
        assertEquals(TimeMachineStatus.INCOMPATIBLE_VERSION,
            f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass")).status)
    }

    @Test fun mapChangesCanReplayButUnexplainedTechnicalChangesStopTheBranch() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val map = f.technical { it.copy(locationScene = LocationScene(GameLocation.HILL)) }
        val afterMap = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass", map.sequence))
        assertEquals(TimeMachineStatus.COMPLETE, afterMap.status)
        assertEquals(GameLocation.HILL, afterMap.alternative!!.state.locationScene.location)
        val unknown = f.technical { it.copy(fatigue = it.fatigue + 1) }
        val stopped = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass", unknown.sequence))
        assertEquals(TimeMachineStatus.DIVERGED, stopped.status)
        assertEquals(map.sequence, stopped.reachedSequence)
    }

    @Test fun quizUsesComputedLedgerAndOnlyPersistsCounterfactualLearning() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val last = f.record(EngineCommand.Feed("basic"))
        val live = HistoryCodec.encodeState(f.repo.state)
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass", last.sequence))
        val quiz = requireNotNull(f.machine.quiz(result.simulationId!!, TimeMachineQuizKind.LEDGER))
        assertTrue(quiz.options.any { it.id == "coins:5" })
        val answer = f.machine.submitQuiz(quiz.id, "coins:5", "submission-1", questionPresented = true)
        assertTrue(answer.correct)
        assertEquals(1, answer.attempt)
        val retry = f.machine.submitQuiz(quiz.id, "coins:5", "submission-1", questionPresented = true)
        assertTrue(retry.alreadyRecorded)
        assertEquals(1, f.repo.recordFactCalls)
        val fact = f.repo.entries.flatMap { it.facts }.single()
        assertEquals(LearningContext.COUNTERFACTUAL, fact.learningContext)
        assertEquals(AnalyticsMode.REAL, fact.mode)
        assertEquals(SkillId.UNDERSTAND_INCOME_AND_EXPENSES, SkillEvaluator().evaluate(listOf(fact)).single().skill)
        assertFalse(SkillEvaluator().evaluate(listOf(fact)).single().independentOfGameHints)
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))
        assertEquals(0, f.repo.updateCalls)
    }

    @Test fun causeQuizDoesNotPretendSimulationIsApplicationInRealLife() = runTest {
        val f = Fixture()
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass"))
        val quiz = requireNotNull(f.machine.quiz(result.simulationId!!, TimeMachineQuizKind.CAUSE))
        assertTrue(f.machine.submitQuiz(quiz.id, "spent_less", "cause-answer", questionPresented = true).correct)
        val observation = SkillEvaluator().evaluate(f.repo.entries.flatMap { it.facts }).single()
        assertEquals(SkillId.RECONSIDER_DECISION, observation.skill)
        assertEquals(EpisodeCompletion.PENDING, observation.completion)
    }

    @Test fun firstCompatibleDecisionCanBeReviewedWithoutArtifactOrStoryUnlock() = runTest {
        val f = Fixture(unlocked = false)
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val live = HistoryCodec.encodeState(f.repo.state)
        val realHistory = f.repo.entries.toList()
        assertTrue(f.repo.state.ownedItems.isEmpty())
        assertFalse("chronoscope_unlocked" in f.catalog.storyProgress(f.repo.state).facts)
        val availability = f.machine.availableDecisions()
        assertEquals(TimeMachineStatus.READY, availability.status)
        assertEquals(choice.id, availability.decisions.single().entryId)
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass"))
        assertEquals(TimeMachineStatus.COMPLETE, result.status)
        assertEquals(realHistory, f.repo.entries)
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))

        val simulationId = requireNotNull(result.simulationId)
        f.machine.recordSimulationLifecycle(simulationId)
        val quiz = requireNotNull(f.machine.quiz(simulationId, TimeMachineQuizKind.CAUSE))
        f.machine.recordQuestionPresented(quiz.id)
        assertTrue(f.machine.submitQuiz(quiz.id, "spent_less", "first-review").correct)
        f.machine.recordExplanationShown(quiz.id, "first-review")
        assertEquals(realHistory, f.repo.entries.filter { it.type != AuditType.FACTS })
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))
        assertEquals(0, f.repo.updateCalls)
        assertTrue(f.repo.entries.filter { it.type == AuditType.FACTS }
            .all { it.operations.isEmpty() && it.before == null && it.after == null })
    }

    @Test fun earlyGameWithoutDecisionsIsReadyButHasNothingToCompare() = runTest {
        val f = Fixture(unlocked = false)
        val live = HistoryCodec.encodeState(f.repo.state)
        val availability = f.machine.availableDecisions()
        assertEquals(TimeMachineStatus.READY, availability.status)
        assertTrue(availability.decisions.isEmpty())
        assertEquals(TimeMachineStatus.UNAVAILABLE,
            f.machine.simulate(TimeMachineRequest("missing-decision", "choice:pass")).status)
        assertEquals(live, HistoryCodec.encodeState(f.repo.state))
        assertTrue(f.repo.entries.isEmpty())
        assertEquals(0, f.repo.updateCalls)
        assertEquals(0, f.repo.recordFactCalls)
    }

    @Test fun skippingDepositChangesAccountsWithoutCountingTransferAsSpending() = runTest {
        val f = Fixture()
        val deposit = f.record(EngineCommand.DepositSavings(10))
        val result = f.machine.simulate(TimeMachineRequest(deposit.id, "skip"))
        val baseline = requireNotNull(result.baseline)
        val alternative = requireNotNull(result.alternative)
        assertEquals(TimeMachineStatus.COMPLETE, result.status)
        assertEquals(10L, baseline.deposited)
        assertEquals(0L, baseline.spent)
        assertEquals(100L, alternative.state.economy.availableBalance)
        assertEquals(0L, alternative.state.economy.savingsBalance)
    }

    @Test fun historicalPlanReviewDoesNotMakeEarlierFinancialChoiceIncompatible() = runTest {
        val f = Fixture()
        f.repo.state = ru.nksk.lctapp.domain.finance.FinancialPeriods.adopt(
            f.repo.state.copy(selectedGoalId = "goal"), imported = false)
        f.repo.entries += AuditEntry("initial", 1, "run", AuditType.INITIALIZED, after = f.repo.state)
        val choice = f.record(EngineCommand.CompleteEvent("shop-occurrence", "buy"))
        val review = f.record(EngineCommand.RequestFinancialPractice())
        val result = f.machine.simulate(TimeMachineRequest(choice.id, "choice:pass", review.sequence))
        assertEquals(TimeMachineStatus.DIVERGED, result.status)
        assertEquals(choice.sequence, result.reachedSequence)
        assertTrue(result.reason.orEmpty().contains("разбор"))
        assertEquals(75L, f.repo.state.economy.availableBalance)
        assertEquals(0, f.repo.updateCalls)
    }

    @Test fun canonicalLedgerPreservesGrossOperationsWhenTheirNetIsZero() {
        val f = Fixture()
        val before = f.repo.state
        val day = requireNotNull(before.engine)
        val after = before.copy(engine = day.copy(journal = listOf(
            DayJournalEntry("expense", DayJournalKind.EVENT_START, "shop", -5),
            DayJournalEntry("income", DayJournalKind.EVENT_CHOICE, "choice", 5))))
        val entries = CanonicalLedger.fromTransition(before, after,
            EngineRequest("balanced", day.revision, EngineCommand.CompleteEvent("shop-occurrence", "buy")))
        assertEquals(listOf(LedgerKind.AVAILABLE_EXPENSE, LedgerKind.INCOME), entries.map { it.kind })
        assertEquals(listOf(5L, 5L), entries.map { it.amount })
    }

    @Test fun catalogFingerprintIsStableForMapOrderButChangesForAlternativeCosts() {
        val f = Fixture()
        val original = GameCatalogFingerprint.compute(f.catalog)
        val reordered = f.catalog.copy(policies = f.catalog.policies.entries.reversed().associate { it.key to it.value })
        assertEquals(original, GameCatalogFingerprint.compute(reordered))
        assertNotEquals(original, GameCatalogFingerprint.compute(f.catalog.copy(meals = f.catalog.meals.map { it.copy(price = it.price + 1) })))
    }

    private class Fixture(
        balance: Long = 100,
        unlocked: Boolean = false,
        manualRepair: Boolean = false,
        shopType: EventType = if (manualRepair) EventType.RANDOM else EventType.WANT,
        foodPurchase: Boolean = false,
        gameWork: Boolean = true,
        energy: Int = 5,
        private val shopTitle: String = "shop",
        private val alternativeTitle: String = "pass",
        price: Long = 25,
    ) {
        private fun event(id: String, type: EventType) = EventDefinition(id, type,
            if (id == "shop") shopTitle else id, id, null, null, null, 0, null, null)
        private fun choice(id: String, event: String, position: Int, delta: Long) =
            EventChoiceDefinition(id, event, position, if (id == "pass") alternativeTitle else id,
                delta, BudgetSection.WANTS, null, GoalImpact.NEUTRAL)
        val catalog = GameCatalog(
            content = StoryContent(
                chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
                days = listOf(GameDayDefinition("day", "chapter", 1)),
                events = listOf(event("unlock", EventType.STORY), event("shop", shopType), event("deed", EventType.EARNING)),
                choices = listOf(choice("unlocked", "unlock", 0, 0), choice("buy", "shop", 0, -price), choice("pass", "shop", 1, 0), choice("earn", "deed", 0, 10)),
                goals = listOf(GoalDefinition("goal", "Goal", "Goal"))),
            policies = linkedMapOf("unlock" to EventPolicy(0, factsByChoiceId = mapOf("unlocked" to setOf("chronoscope_unlocked"))),
                "shop" to if (manualRepair) EventPolicy(0, choiceEnergyCosts = mapOf("pass" to 2),
                    choiceGameKinds = if (gameWork) mapOf("pass" to ru.nksk.lctapp.domain.minigame.DeedGameKind.PRECISION) else emptyMap())
                    else EventPolicy(0, feedsPetChoiceIds = if (foodPurchase) setOf("buy") else emptySet()),
                "deed" to EventPolicy(1)),
            cards = emptyMap(), rules = EngineRules("rules", 5, 3, 1, 100),
            meals = listOf(MealDefinition("basic", 5, null), MealDefinition("large", 30, null)),
            storyDayId = "day", introductionId = "unlock", deedPool = listOf("deed"))
        val repo = RecordingRepository(GameState(PetState("plain", PetVisualState.NORMAL),
            EconomyState(BudgetPlan(balance, 0, 0, 0)),
            StoryState("day", null, "shop", if (unlocked) listOf(StoryDecision("unlock-action", "unlocked")) else emptyList()),
            0, 0, emptyList(),
            EngineState("rules", 1, 1, DayPhase.RUNNING, 0, energy, true, null, balance,
                listOf(EventOccurrence("shop-occurrence", "shop", EventOrigin.SCHEDULE, EventStatus.ACTIVE)), emptyList())))
        val engine = GameEngine(repo, EventFactory(catalog.content, catalog.policies, catalog.meals), catalog.rules)
        val machine = TimeMachine(repo, engine, catalog)
        fun record(command: EngineCommand): AuditEntry {
            val sequence = repo.entries.size + 1L
            val request = EngineRequest("action-$sequence", repo.state.engine?.revision, command)
            val before = repo.state
            val after = engine.transition(before, request, repo.entries.toList())
            return AuditEntry("entry-$sequence", sequence, "run", AuditType.COMMAND, request,
                before = before, after = after, operations = CanonicalLedger.fromTransition(before, after, request),
                contentFingerprint = GameCatalogFingerprint.compute(catalog)).also { repo.entries += it; repo.state = after }
        }
        fun technical(change: (GameState) -> GameState): AuditEntry {
            val sequence = repo.entries.size + 1L
            val after = change(repo.state)
            return AuditEntry("entry-$sequence", sequence, "run", AuditType.TECHNICAL_UPDATE, before = repo.state, after = after)
                .also { repo.entries += it; repo.state = after }
        }
    }

    private class RecordingRepository(var state: GameState) : GameRepository {
        val entries = mutableListOf<AuditEntry>()
        var updateCalls = 0
        var recordFactCalls = 0
        var beforeFactsWrite: () -> Unit = {}
        override fun observe() = flowOf(state)
        override suspend fun read() = state
        override suspend fun initializeIfAbsent(initial: GameState) = state
        override suspend fun update(transform: (GameState) -> GameState): GameState { updateCalls++; state = transform(state); return state }
        override suspend fun readHistory() = entries.toList()
        override suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard?) {
            beforeFactsWrite()
            sourceGuard?.requireMatches(entries)
            recordFactCalls++
            entries += AuditEntry("facts-$recordFactCalls", entries.size + 1L, "run", AuditType.FACTS, facts = facts)
        }
    }
}
