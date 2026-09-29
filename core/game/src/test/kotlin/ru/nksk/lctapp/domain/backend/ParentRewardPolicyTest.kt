package ru.nksk.lctapp.domain.backend

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.finance.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.StoryState

class ParentRewardPolicyTest {
    private val policy = ParentRewardPolicy(ParentCoinAllocation.RESERVE, ParentRewardOutcome.ALREADY_OWNED)
    private val initial = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(BudgetPlan(5, 7, 0, 18), availableBalance = 30, savingsBalance = 8),
        StoryState(null, null, null, emptyList()), 3, 2, emptyList(),
        engine = EngineState("rules", 7, 3, DayPhase.RUNNING, 2, 4, true, null, 30, emptyList(), emptyList()),
        financial = FinancialProgress(currentPeriodId = "period", periods = listOf(
            FinancialPeriod("period", "goal", 1, 1, 30, 8))))
    private val hat = "cosmetic-explorer-hat-v2"
    private fun reward(payload: ParentRewardPayload) = ParentRewardDto("reward", "profile", "run", 1, payload, "2026-09-27T12:00:00Z")

    @Test fun giftAddsToLatestReserveAndPeriodWithoutChildEarningOrChangingStoryEnergyAndDay() {
        val changed = policy.apply(initial, reward(ParentRewardPayload.Coins(20)), "application", emptySet())!!
        assertEquals(50L, changed.state.economy.availableBalance)
        assertEquals(8L, changed.state.economy.savingsBalance)
        assertEquals(initial.economy.plan.copy(reserve = 38), changed.state.economy.plan)
        assertEquals(20L, changed.state.financial.currentPeriod!!.income)
        assertNull(changed.state.financial.currentPeriod!!.savingPractice)
        assertEquals(initial.pet, changed.state.pet)
        assertEquals(initial.story, changed.state.story)
        assertEquals(initial.engine!!.copy(revision = 8, journal = listOf(
            DayJournalEntry("application:income", DayJournalKind.PARENT_REWARD, "reward", 20))), changed.state.engine)
        CanonicalLedger.validate(initial, changed.state, changed.operations)
    }

    @Test fun planningDefersCoinsButDoesNotBlockKnownAccessoryAndUnknownItemsRemainPending() {
        val draft = initial.copy(economy = EconomyOperations.beginManual(initial.economy, "draft"))
        assertNull(policy.apply(draft, reward(ParentRewardPayload.Coins(20)), "coins", setOf(hat)))
        assertNull(policy.apply(draft, reward(ParentRewardPayload.Accessory("future-item")), "unknown", setOf("future-item")))
        val accessory = policy.apply(draft, reward(ParentRewardPayload.Accessory(hat)), "hat", setOf(hat))!!
        assertEquals(draft.economy, accessory.state.economy)
        assertEquals(listOf(OwnedItem("hat:item", hat)), accessory.state.ownedItems)
        assertEquals(listOf(DayJournalEntry("hat:accessory", DayJournalKind.PARENT_REWARD, hat, 0)),
            accessory.state.engine!!.journal)
        assertEquals(draft.pet, accessory.state.pet)
        assertEquals(ParentRewardOutcome.APPLIED, accessory.outcome)
    }

    @Test fun duplicateAccessoryKeepsOriginalOwnershipAndDoesNotCompensateOrEquip() {
        val owned = initial.copy(ownedItems = listOf(OwnedItem("first", "figma-2164-2-explorer-cap-v1")))
        val result = policy.apply(owned, reward(ParentRewardPayload.Accessory(hat)), "application", setOf(hat))!!
        assertSame(owned, result.state)
        assertEquals(ParentRewardOutcome.ALREADY_OWNED, result.outcome)
        assertTrue(result.operations.isEmpty())
    }

    @Test fun unconfirmedPoliciesNeverApplyCoinsOrAcknowledgeDuplicateAccessories() {
        val pending = ParentRewardPolicy()
        assertNull(pending.apply(initial, reward(ParentRewardPayload.Coins(20)), "coins", emptySet()))
        val owned = initial.copy(ownedItems = listOf(OwnedItem("first", hat)))
        assertNull(pending.apply(owned, reward(ParentRewardPayload.Accessory(hat)), "duplicate", setOf(hat)))
    }

    @Test fun eachNamedCapIsASeparateGiftAndDoesNotReplaceTheExplorerHatOrEquipItself() {
        val caps = PetCosmetics.parentRewards
        val installed = caps.flatMap { it.itemIds }.toSet()
        assertEquals(6, installed.size)
        assertEquals(6, caps.map { it.lookId }.distinct().size)
        var current = initial.copy(ownedItems = listOf(OwnedItem("explorer", hat)))
        for ((index, cap) in caps.withIndex()) {
            val itemId = cap.itemIds.single()
            assertFalse(PetCosmetics.canEquip(current, cap.lookId))
            val gift = reward(ParentRewardPayload.Accessory(itemId)).copy(rewardId = "cap-$index")
            assertNull(ParentRewardPolicy().apply(current, gift, "missing-$index", emptySet()))
            val applied = ParentRewardPolicy().apply(current, gift, "cap-$index", installed)!!
            assertEquals(ParentRewardOutcome.APPLIED, applied.outcome)
            assertEquals(current.ownedItems + OwnedItem("cap-$index:item", itemId), applied.state.ownedItems)
            assertEquals(initial.pet, applied.state.pet)
            assertEquals(initial.economy, applied.state.economy)
            for (age in PetAge.entries) for (color in PetColor.entries) {
                val appearance = applied.state.copy(pet = applied.state.pet.copy(age = age, color = color))
                assertTrue(PetCosmetics.canEquip(appearance, cap.lookId))
            }
            assertTrue(PetCosmetics.canEquip(applied.state, "HAT"))
            // A genuine repeat retains the existing pending policy; another cap is not a repeat.
            assertNull(ParentRewardPolicy().apply(applied.state, gift, "again-$index", installed))
            current = applied.state
        }
        assertEquals(7, current.ownedItems.size)
    }

    @Test fun absentRewardMetadataKeepsTheLegacyAuditShapeAndSnapshotChecksum() {
        val entry = AuditEntry("initialize:run", 1, "run", AuditType.INITIALIZED, after = initial)
        val encoded = HistoryCodec.encode(entry)
        assertFalse(encoded.contains("parentReward"))
        assertEquals(entry, HistoryCodec.decodeEntry(encoded))
        val snapshot = HistoryCodec.snapshot("run", initial, listOf(entry))
        assertEquals(HistoryCodec.sha256("4\nrun\n1\n${HistoryCodec.encodeState(initial)}\n[$encoded]"), snapshot.checksum)
        assertEquals(snapshot, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot)))
    }
}
