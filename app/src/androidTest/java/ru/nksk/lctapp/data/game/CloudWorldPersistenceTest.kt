package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.backend.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.history.*

/** Source coverage only; device execution needs explicit user authorization. */
@RunWith(AndroidJUnit4::class)
class CloudWorldPersistenceTest {
    private lateinit var db: GameDatabase
    private lateinit var games: RoomGameRepository

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder<GameDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(BundledSQLiteDriver()).build()
        games = RoomGameRepository(db, ParentRewardPolicy(ParentCoinAllocation.RESERVE, ParentRewardOutcome.ALREADY_OWNED))
        games.initializeIfAbsent(createInitialGameState().copy(economy = EconomyState(
            BudgetPlan(5, 7, 0, 18), availableBalance = 30, savingsBalance = 8)))
    }
    @After fun close() { db.close() }

    @Test fun sameRunRestoreKeepsAuditUsesSourceCursorAndRetriesWithoutRevertingProgress() = runBlocking {
        val source = checkNotNull(games.readCloudWorld())
        games.update { it.copy(pet = it.pet.copy(name = "Локальное имя")) }
        val local = checkNotNull(games.readSnapshotHead())
        val oldHistory = games.readHistory()
        val guard = RestoreGuard(local.state.engine?.revision, local.historySequence)
        games.restoreCloudWorld(source.world, guard, "restore-once")
        val restored = checkNotNull(games.readCloudWorld())
        assertEquals(oldHistory, games.readHistory().take(oldHistory.size))
        assertEquals(source.world.state, restored.world.state)
        assertEquals(source.world.historySequence, restored.world.historySequence)
        assertEquals(local.historySequence + 1, restored.baselineSequence)
        assertNotEquals(source.generation, restored.generation)
        assertTrue(games.cloudContains(source.world))
        games.update { it.copy(pet = it.pet.copy(name = "После восстановления")) }
        val progressed = checkNotNull(games.readCloudWorld())
        assertEquals(source.world.historySequence + 1, progressed.world.historySequence)
        assertEquals(progressed.world.state, games.restoreCloudWorld(source.world, guard, "restore-once"))
        assertEquals(progressed, games.readCloudWorld())
        val evidence = checkNotNull(games.readCloudEvidence())
        assertEquals(progressed, evidence.head)
        assertTrue(evidence.history.all { it.sequence >= checkNotNull(restored.baselineSequence) })
        assertEquals("restore-once", games.findCloudRestoreReceipt("restore-once")!!.restoreRequestId)
        HistoryCodec.validate(games.exportSnapshot())
    }

    @Test fun compactGiftReceiptDeduplicatesAndOlderWorldCanReceiveRolledBackGiftOnce() = runBlocking {
        val empty = checkNotNull(games.readCloudWorld())
        val gift = ParentRewardDto("gift", "profile", empty.world.runId, 1,
            ParentRewardPayload.Coins(20), "2026-09-29T00:00:00Z")
        val original = games.applyParentRewards("profile", empty.world.runId, listOf(gift), empty.generation).single()
        val withGift = checkNotNull(games.readCloudWorld())
        restore(withGift.world, "restore-gift")
        val received = checkNotNull(games.readCloudWorld())
        assertEquals(listOf(original), games.applyParentRewards("profile", empty.world.runId, listOf(gift), received.generation))
        assertEquals(50L, games.read()!!.economy.availableBalance)
        assertEquals(1, games.readHistory().count { it.type == AuditType.PARENT_REWARD })
        restore(empty.world, "restore-before-gift")
        val older = checkNotNull(games.readCloudWorld())
        val reapplied = games.applyParentRewards("profile", empty.world.runId, listOf(gift), older.generation).single()
        assertNotEquals(original.applicationId, reapplied.applicationId)
        assertEquals(50L, games.read()!!.economy.availableBalance)
        assertEquals(listOf(reapplied), games.applyParentRewards("profile", empty.world.runId, listOf(gift), older.generation))
        assertEquals(listOf(reapplied), games.readCloudWorld()!!.world.parentRewards.map { it.receipt })
        HistoryCodec.validate(games.exportSnapshot())
    }

    @Test fun differentRunRestorePreservesCompletedArchiveAndRejectsItsRunCollision() = runBlocking {
        val initial = checkNotNull(games.readCloudWorld())
        val head = checkNotNull(games.readSnapshotHead())
        games.restartCampaign(CampaignRestartRequest("restart", head.runId, head.state.engine?.revision,
            head.historySequence)) { current, _ -> current.copy(pet = current.pet.copy(name = "Новый цикл")) }
        val archives = games.archivedRuns()
        val foreign = WorldSnapshotCodec.create("foreign-run", initial.world.state, 100, "foreign-generation")
        restore(foreign, "foreign-restore")
        assertEquals(archives, games.archivedRuns())
        assertEquals(1, games.readHistory().size)
        assertEquals(100L, games.readCloudWorld()!!.world.historySequence)
        val before = games.readCloudWorld()
        assertTrue(runCatching { restore(initial.world, "archived-collision") }.isFailure)
        assertEquals(before, games.readCloudWorld())
        assertEquals(archives, games.archivedRuns())
    }

    @Test fun containsUsesExactGenerationCheckpointAndOutboxAckCannotCrossRestore() = runBlocking {
        val first = checkNotNull(games.readCloudWorld())
        games.update { it.copy(pet = it.pet.copy(name = "Позже")) }
        val later = checkNotNull(games.readCloudWorld())
        assertTrue(games.cloudContains(first.world))
        assertFalse(games.cloudContains(WorldSnapshotCodec.create(first.world.runId,
            first.world.state.copy(pet = first.world.state.pet.copy(name = "Подмена")), first.world.historySequence, first.generation)))
        assertTrue(games.acknowledgeOutboxThrough(first.world.runId, first.generation, first.localHistorySequence))
        assertTrue(games.pendingOutbox().all { it.sequence > first.localHistorySequence })
        restore(first.world, "cut")
        assertFalse(games.cloudContains(later.world))
        assertFalse(games.acknowledgeOutboxThrough(later.world.runId, later.generation, later.localHistorySequence))
        assertTrue(games.pendingOutbox().isNotEmpty())
    }

    @Test fun legacyBudgetRestoreKeepsOriginalCheckpointAndPerformsTheExistingExplicitUpgrade() = runBlocking {
        val before = checkNotNull(games.readCloudWorld())
        val legacyState = before.world.state.copy(economy = EconomyState(BudgetPlan(35, 20, 20, 25),
            availableBalance = 70, savingsBalance = 8))
        val unmarked = WorldSnapshotCodec.create(before.world.runId, legacyState, 40, "old-format-generation")
        assertTrue(runCatching { restore(unmarked, "invalid-current-budget") }.isFailure)
        assertEquals(before, games.readCloudWorld())
        val legacy = WorldSnapshotCodec.withLegacyBudgetModel(unmarked)
        restore(legacy, "legacy-budget")
        val history = games.readHistory()
        val baseline = history.single { it.worldRestore?.restoreRequestId == "legacy-budget" }
        assertEquals(legacyState, baseline.after)
        val upgrade = history.last()
        assertEquals(AuditType.TECHNICAL_UPDATE, upgrade.type)
        assertEquals(legacyState, upgrade.before)
        assertEquals(70L, upgrade.after!!.economy.availableBalance)
        assertEquals(8L, upgrade.after!!.economy.savingsBalance)
        assertTrue(upgrade.after!!.economy.hasValidLiveBudget())
        val cloud = checkNotNull(games.readCloudWorld())
        assertEquals(41L, cloud.world.historySequence)
        assertFalse(cloud.world.legacyBudgetModel)
        assertTrue(games.cloudContains(legacy))
        HistoryCodec.validate(games.exportSnapshot())
    }

    private suspend fun restore(world: WorldSnapshot, id: String) {
        val current = checkNotNull(games.readSnapshotHead())
        games.restoreCloudWorld(world, RestoreGuard(current.state.engine?.revision, current.historySequence), id)
    }
}
