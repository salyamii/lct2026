package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.CanonicalLedger
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class SavingsTransferGuardTest {
    private val initial = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(BudgetPlan(80, 0, 0, 0), availableBalance = 80, savingsBalance = 60),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList())
    private val expected = SavingsTransferExpectation(80, 60, null)

    @Test fun changedAllocationsWithTheSameBalancesRejectAnUnseenDepositSource() = runTest {
        val repo = Repository(initial)
        val newest = initial.copy(economy = initial.economy.copy(plan = BudgetPlan(35, 0, 45, 0)))
        repo.changeBeforeTransform = newest
        val command = EngineCommand.DepositSavings(10, expected = expected.copy(allocation = initial.economy.plan))
        val result = engine(repo).dispatch(EngineRequest("deposit-source", null, command))
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), result)
        assertEquals(newest, repo.value)
    }

    @Test fun changedBalancesInsideTransactionRejectPreDayDepositAndWithdrawal() = runTest {
        for (command in listOf(EngineCommand.DepositSavings(10, expected = expected),
            EngineCommand.WithdrawSavings(10, confirmed = true, expected = expected))) {
            val repo = Repository(initial)
            val engine = engine(repo)
            val newest = initial.copy(economy = initial.economy.copy(plan = BudgetPlan(75, 0, 0, 0),
                availableBalance = 75, savingsBalance = 65))
            // The caller can have read the old values immediately before dispatch. The write
            // transaction sees the concurrent transfer while its engine revision is still null.
            repo.changeBeforeTransform = newest
            assertEquals(initial, repo.read())
            val result = engine.dispatch(EngineRequest("confirmed", null, command))
            assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), result)
            assertEquals(newest, repo.value)
            assertNull(repo.value.engine)
        }
    }

    @Test fun changedTargetWithIdenticalMoneyRequiresReviewingTheConsequencesAgain() = runTest {
        val repo = Repository(initial)
        val engine = engine(repo)
        val newest = initial.copy(selectedSavingItemId = "different-part")
        repo.changeBeforeTransform = newest
        val request = EngineRequest("confirmed", null, EngineCommand.WithdrawSavings(10, true, expected))
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), engine.dispatch(request))
        assertEquals(newest, repo.value)
    }

    @Test fun matchingPreDayConfirmationReturnsMoneyToReserveWithoutStartingTheDay() = runTest {
        val repo = Repository(initial)
        val engine = engine(repo)
        val request = EngineRequest("confirmed", null, EngineCommand.WithdrawSavings(10, true, expected))
        assertTrue(engine.dispatch(request) is EngineResult.Applied)
        assertEquals(initial.copy(economy = initial.economy.copy(plan = BudgetPlan(80, 0, 0, 10),
            availableBalance = 90, savingsBalance = 50)), repo.value)
        assertNull(repo.value.engine)
    }

    @Test fun replayUsesTheSavedDecisionWithoutReusingTheOldUiConcurrencySnapshot() {
        val repo = Repository(initial)
        val engine = engine(repo)
        val alternative = initial.copy(economy = initial.economy.copy(plan = BudgetPlan(80, 0, 0, 20),
            availableBalance = 100, savingsBalance = 40))
        val request = EngineRequest("historical", null, EngineCommand.WithdrawSavings(10, true, expected))
        val replayed = engine.transition(alternative, request)
        assertEquals(110L, replayed.economy.availableBalance)
        assertEquals(30L, replayed.economy.savingsBalance)
        assertEquals(initial, repo.value)
    }

    @Test fun legacyTransferEncodingAndOldSnapshotChecksumsRetainTheirOriginalShape() {
        val codec = Json { classDiscriminator = "_type" }
        for ((name, fields) in listOf("DepositSavings" to "\"amount\":10,\"acceptFoodRisk\":false",
            "WithdrawSavings" to "\"amount\":10,\"confirmed\":true")) {
            val old = "{\"id\":\"old\",\"expectedRevision\":null,\"command\":{\"_type\":\"ru.nksk.lctapp.domain.engine.EngineCommand.$name\",$fields},\"context\":null}"
            val decoded = codec.decodeFromString<EngineRequest>(old)
            assertEquals(old, HistoryCodec.encodeRequest(decoded))
        }
        val legacyRequest = EngineRequest("old", null, EngineCommand.DepositSavings(10))
        val after = initial.copy(economy = initial.economy.copy(availableBalance = 70, savingsBalance = 70))
        val entries = listOf(AuditEntry("old-entry", 1, "run", AuditType.COMMAND,
            request = legacyRequest, before = initial, after = after,
            operations = CanonicalLedger.fromTransition(initial, after, legacyRequest)))
        val snapshot = HistoryCodec.snapshot("run", after, entries)
        val encoded = HistoryCodec.encodeSnapshot(snapshot)
        assertFalse(encoded.contains("\"expected\":"))
        assertEquals(snapshot, HistoryCodec.decodeSnapshot(encoded))
        val guarded = legacyRequest.copy(command = EngineCommand.DepositSavings(10, expected = expected))
        assertEquals(guarded, codec.decodeFromString<EngineRequest>(HistoryCodec.encodeRequest(guarded)))
    }

    private fun engine(repo: Repository) = GameEngine(repo,
        EventFactory(StoryContent(), emptyMap(), listOf(MealDefinition("meal", 5, null))),
        EngineRules("transfer-guard", 5, 3, 1))

    private class Repository(initial: GameState) : GameRepository {
        private val state = MutableStateFlow(initial)
        val value get() = state.value
        var changeBeforeTransform: GameState? = null
        override fun observe() = state
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            changeBeforeTransform?.let { state.value = it; changeBeforeTransform = null }
            return transform(value).also { state.value = it }
        }
    }
}
