package ru.nksk.lctapp.data.game

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.analytics.AnalyticsActor
import ru.nksk.lctapp.domain.analytics.SkillEvaluator
import ru.nksk.lctapp.domain.backend.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.history.*

/** Production repository checks; device execution needs separate user authorization. */
@RunWith(AndroidJUnit4::class)
class ParentRewardPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "parent-rewards-${java.util.UUID.randomUUID()}.db"
    private val policy = ParentRewardPolicy(ParentCoinAllocation.RESERVE, ParentRewardOutcome.ALREADY_OWNED)
    private lateinit var db: GameDatabase
    private lateinit var games: RoomGameRepository
    private lateinit var baseline: GameSnapshot
    private val profile = "profile"
    private val hat = "cosmetic-explorer-hat-v2"

    @Before fun setup() = runBlocking {
        db = GameDatabase.open(context, name)
        games = RoomGameRepository(db, policy)
        RoomStoryContentRepository(db).install(bundledGameCatalog().content)
        games.initializeIfAbsent(createInitialGameState().copy(economy = EconomyState(
            BudgetPlan(5, 7, 0, 18), availableBalance = 30, savingsBalance = 8)))
        baseline = games.exportSnapshot()
    }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    private fun grant(id: String, payload: ParentRewardPayload, sequence: Long = 1) =
        ParentRewardDto(id, profile, baseline.runId, sequence, payload, "2026-09-27T12:00:00Z")
    private suspend fun apply(vararg rewards: ParentRewardDto, generation: String = baseline.localGeneration()) =
        games.applyParentRewards(profile, baseline.runId, rewards.toList(), generation)
    private suspend fun restore(snapshot: GameSnapshot) {
        games.restoreSnapshot(snapshot, RestoreGuard(games.read()!!.engine?.revision, games.readHistory().last().sequence))
    }

    @Test fun delayedGiftMergesIntoLatestWorldAndConcurrentRedeliverySurvivesReopen() = runBlocking {
        // The network request began before this local purchase/name change.
        games.update { it.copy(economy = EconomyOperations.spend(it.economy, 5, SpendingKind.WANT),
            pet = it.pet.copy(name = "Новое имя")) }
        val latest = games.read()!!
        val coins = grant("coins", ParentRewardPayload.Coins(20))
        val accessory = grant("hat", ParentRewardPayload.Accessory(hat), 2)
        val replies = coroutineScope {
            listOf(async { apply(coins, accessory) }, async {
                RoomGameRepository(db, policy).applyParentRewards(profile, baseline.runId,
                    listOf(coins, accessory), baseline.localGeneration())
            }).map { it.await() }
        }
        assertEquals(replies[0], replies[1])
        val saved = games.read()!!
        assertEquals(45L, saved.economy.availableBalance)
        assertEquals(38L, saved.economy.plan.reserve)
        assertEquals(latest.economy.savingsBalance, saved.economy.savingsBalance)
        assertEquals(latest.pet, saved.pet)
        assertEquals(listOf(hat), saved.ownedItems.map { it.itemId })
        val entries = games.readHistory().filter { it.type == AuditType.PARENT_REWARD }
        assertEquals(2, entries.size)
        assertTrue(entries.flatMap { it.facts }.all { it.actor == AnalyticsActor.PARENT })
        assertTrue(SkillEvaluator().evaluate(entries.flatMap { it.facts }).isEmpty())
        assertTrue(games.pendingOutbox().containsAll(entries))
        db.close()
        db = GameDatabase.open(context, name)
        games = RoomGameRepository(db, policy)
        assertEquals(replies[0], apply(coins, accessory))
        assertEquals(saved, games.read())
        HistoryCodec.validate(games.exportSnapshot())
    }

    @Test fun committedIdentityConflictRollsBackWholeBatchAndRejectsWrongTargets() = runBlocking {
        val coins = grant("coins", ParentRewardPayload.Coins(20))
        apply(coins)
        val before = games.exportSnapshot()
        expectFailure { apply(grant("new", ParentRewardPayload.Coins(5), 2),
            coins.copy(reward = ParentRewardPayload.Coins(21))) }
        // This failure occurs after the first grant was written inside the transaction.
        expectFailure { apply(grant("valid-before-overflow", ParentRewardPayload.Coins(5), 2),
            grant("overflow", ParentRewardPayload.Coins(Long.MAX_VALUE), 3)) }
        expectFailure { apply(coins.copy(profileId = "another-profile")) }
        expectFailure { games.applyParentRewards(profile, "another-run", emptyList(), before.localGeneration()) }
        assertEquals(before, games.exportSnapshot())
    }

    @Test fun draftAndUnknownAccessoryRemainPendingButDoNotBlockLaterKnownAccessory() = runBlocking {
        games.update { it.copy(economy = EconomyOperations.beginManual(it.economy, "draft")) }
        val draft = games.read()!!
        val coins = grant("coins", ParentRewardPayload.Coins(20))
        val unknown = grant("future-item", ParentRewardPayload.Accessory("not-installed"), 2)
        val accessory = grant("hat", ParentRewardPayload.Accessory(hat), 3)
        val receipt = apply(coins, unknown, accessory).single()
        assertEquals(accessory.rewardId, receipt.rewardId)
        assertEquals(draft.economy, games.read()!!.economy)
        val owned = games.read()!!
        val duplicate = apply(grant("second-hat", ParentRewardPayload.Accessory(hat), 4)).single()
        assertEquals(ParentRewardOutcome.ALREADY_OWNED, duplicate.outcome)
        assertEquals(owned, games.read())
        assertEquals(listOf("hat", "second-hat"), games.readHistory().mapNotNull { it.parentReward?.reward?.rewardId })
    }

    @Test fun receiptRestoresWithWorldOldResponseIsRejectedAndOlderBackupMayReceiveGiftAgainOnce() = runBlocking {
        val coins = grant("coins", ParentRewardPayload.Coins(20))
        val originalReceipt = apply(coins).single()
        val withGift = HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(games.exportSnapshot()))
        restore(withGift)
        expectFailure { apply(coins) } // The same run has a new restore generation.
        var restored = games.exportSnapshot()
        assertEquals(listOf(originalReceipt), apply(coins, generation = restored.localGeneration()))
        assertEquals(50L, games.read()!!.economy.availableBalance)
        restore(baseline)
        restored = games.exportSnapshot()
        val newReceipt = apply(coins, generation = restored.localGeneration()).single()
        assertNotEquals(originalReceipt.applicationId, newReceipt.applicationId)
        assertEquals(50L, games.read()!!.economy.availableBalance)
        assertEquals(listOf(newReceipt), apply(coins, generation = restored.localGeneration()))
        HistoryCodec.validate(games.exportSnapshot())
    }

    @Test fun productionPendingPolicyOnlyAcknowledgesNewKnownAccessories() = runBlocking {
        games = RoomGameRepository(db)
        val coins = grant("coins", ParentRewardPayload.Coins(20))
        val accessory = grant("hat", ParentRewardPayload.Accessory(hat), 2)
        assertEquals(listOf("hat"), apply(coins, accessory).map { it.rewardId })
        assertTrue(apply(grant("duplicate", ParentRewardPayload.Accessory(hat), 3)).isEmpty())
        assertEquals(30L, games.read()!!.economy.availableBalance)
        assertEquals(1, games.read()!!.ownedItems.size)
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        assertTrue("Expected rejection without changing the world", runCatching { block() }.isFailure)
    }
}
