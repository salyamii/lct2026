package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.*
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.finance.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.story.StoryDecision
import java.util.UUID

/** Production Room boundary; compiled only unless device execution is separately authorized. */
@RunWith(AndroidJUnit4::class)
class CampaignReconciliationPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "campaign-reconciliation-${UUID.randomUUID()}.db"
    private val catalog = bundledGameCatalog()
    private lateinit var db: GameDatabase
    private lateinit var games: RoomGameRepository

    @Before fun open() { reopen() }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    @Test fun preparationRebindsOnlyTheOpenPeriodAndSurvivesReopenWithoutRewritingHistory() = runBlocking {
        val original = installLegacySave()
        val history = games.readHistory()
        session().prepare()
        val adopted = checkNotNull(games.read())
        assertEquals(STARS_GOAL, adopted.selectedGoalId)
        assertNull(adopted.selectedSavingItemId)
        assertEquals(original.economy, adopted.economy)
        assertEquals(original.story, adopted.story)
        assertEquals(original.ownedItems, adopted.ownedItems)
        assertEquals(original.engine!!.revision + 1, adopted.engine!!.revision)
        assertEquals(original.financial.currentPeriod!!.copy(goalId = STARS_GOAL, imported = true),
            adopted.financial.currentPeriod)
        assertEquals(original.financial.periods.first(), adopted.financial.periods.first())
        assertEquals(original.financial.plans, adopted.financial.plans)
        assertEquals(original.financial.practice, adopted.financial.practice)
        assertEquals(history, games.readHistory().take(history.size))
        val correction = games.readHistory().last()
        assertEquals(AuditType.TECHNICAL_UPDATE, correction.type)
        assertEquals(original, correction.before)
        assertEquals(adopted, correction.after)
        assertTrue(correction.operations.isEmpty())
        assertTrue(correction.facts.isEmpty())
        assertTrue(games.pendingOutbox().any { it.id == correction.id })
        val savedHistory = games.readHistory()
        db.close()
        reopen()
        session().prepare()
        assertEquals(adopted, games.read())
        assertEquals(savedHistory, games.readHistory())
        HistoryCodec.validate(games.exportSnapshot())
    }

    @Test fun restoredLegacySnapshotKeepsItsOriginalCheckpointsAndGetsOneCompatibilityRecord() = runBlocking {
        val original = installLegacySave()
        val snapshot = games.exportSnapshot()
        session().prepare()
        val beforeRestore = games.readHistory()
        val restored = session().restoreSnapshot(snapshot,
            RestoreGuard(games.read()!!.engine!!.revision, beforeRestore.last().sequence, catalog.rules.id))
        assertEquals(STARS_GOAL, restored.selectedGoalId)
        assertEquals(original.financial.currentPeriod!!.copy(goalId = STARS_GOAL, imported = true),
            restored.financial.currentPeriod)
        assertEquals(snapshot.history, games.readHistory().take(snapshot.history.size))
        assertEquals(listOf(AuditType.RESTORED, AuditType.TECHNICAL_UPDATE),
            games.readHistory().drop(snapshot.history.size).map { it.type })
        val finalSnapshot = games.exportSnapshot()
        HistoryCodec.validate(finalSnapshot)
        assertEquals(finalSnapshot, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(finalSnapshot)))
        assertEquals(original.economy, restored.economy)
    }

    @Test fun ordinaryWritesStillCannotRewritePeriodOriginsOrClosedPeriods() = runBlocking {
        installLegacySave()
        session().prepare()
        val current = games.read()!!
        val history = games.readHistory()
        val outbox = games.pendingOutbox()
        val variants = listOf<(FinancialPeriod) -> FinancialPeriod>(
            { it.copy(goalId = TOWER_GOAL) }, { it.copy(imported = false) },
            { it.copy(openingAvailable = it.openingAvailable + 1) },
            { it.copy(startedDay = it.startedDay + 1) },
        )
        for (change in variants) {
            expectFailure { games.update { game -> game.copy(financial = game.financial.copy(
                periods = game.financial.periods.map { if (it.id == game.financial.currentPeriodId) change(it) else it })) } }
        }
        expectFailure { games.update { game -> game.copy(financial = game.financial.copy(
            periods = game.financial.periods.map { if (it.closedDay != null) it.copy(income = it.income + 1) else it })) } }
        assertEquals(current, games.read())
        assertEquals(history, games.readHistory())
        assertEquals(outbox, games.pendingOutbox())
    }

    @Test fun invalidTargetRollsBackTheWholeCompatibilityPatchAndItsAudit() = runBlocking {
        installLegacySave()
        val current = games.read()!!
        val history = games.readHistory()
        val outbox = games.pendingOutbox()
        expectFailure { games.reconcileCampaign { game -> CampaignReconciliation(game.pet.age,
            STARS_GOAL, "missing-item", rebindCurrentPeriod = true) } }
        assertEquals(current, games.read())
        assertEquals(history, games.readHistory())
        assertEquals(outbox, games.pendingOutbox())
    }

    private suspend fun installLegacySave(): GameState {
        RoomStoryContentRepository(db).install(catalog.content)
        val initial = createInitialGameState()
        val plan = BudgetPlan(35, 25, 30, 10)
        val closed = FinancialPeriod("closed-period", STARS_GOAL, 1, 1, 100, 0, closedDay = 1)
        val active = FinancialPeriod("legacy-open-period", TOWER_GOAL, 2, 2, 100, 12,
            needsProvided = true, income = 8, spentAvailable = 3)
        val question = FinancialQuestion("saved-question", FinancialQuestionKind.SAVING_PRACTICE,
            "Как накопить?", listOf(FinancialAnswerOption("save", "Откладывать")), "save", "Сохраняем часть монет.",
            sourceActionIds = listOf(active.id))
        return games.initializeIfAbsent(initial.copy(
            economy = EconomyState(plan = plan, unallocated = 0, availableBalance = 100, savingsBalance = 12),
            selectedGoalId = TOWER_GOAL, selectedSavingItemId = "$TOWER_GOAL:lantern",
            ownedItems = listOf(OwnedItem("first-copy", "$TOWER_GOAL:lantern"), OwnedItem("second-copy", "$TOWER_GOAL:lantern")),
            story = initial.story.copy(currentDayId = catalog.storyDayId,
                decisions = listOf(StoryDecision("old-clue", "campaign-choice-v1:G1.01:continue"))),
            engine = EngineState(catalog.rules.id, 4, 3, DayPhase.RUNNING, 1, 4, false, null, 100, emptyList(), emptyList()),
            financial = FinancialProgress(active.id, listOf(closed, active), listOf(
                BudgetPlanRevision("saved-plan", active.id, 1, 2, 100, plan, BudgetRevisionReason.INITIAL)), question),
        ))
    }

    private fun session() = GameSession(games, RoomStoryContentRepository(db), catalog, createInitialGameState())
    private fun reopen() { db = GameDatabase.open(context, name); games = RoomGameRepository(db) }
    private suspend fun expectFailure(block: suspend () -> Unit) {
        assertTrue("Expected constraint or validation failure", runCatching { block() }.isFailure)
    }
}
