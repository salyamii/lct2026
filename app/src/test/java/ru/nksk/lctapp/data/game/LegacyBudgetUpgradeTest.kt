package ru.nksk.lctapp.data.game

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.finance.BudgetPlanRevision
import ru.nksk.lctapp.domain.game.OwnedItem

class LegacyBudgetUpgradeTest {
    private val intention = BudgetPlan(35, 20, 35, 0)
    private fun legacy(available: Long = 32) = createInitialGameState().let { original ->
        original.copy(economy = EconomyState(intention, availableBalance = available, savingsBalance = 41),
            ownedItems = listOf(OwnedItem("second", "map"), OwnedItem("first", "map")),
            financial = original.financial.copy(plans = listOf(BudgetPlanRevision("old-plan", null, 1, 1,
                90, intention, BudgetRevisionReason.INITIAL))))
    }

    @Test fun staleIntentionBecomesOneExplicitRedistributionWithoutChangingMoneyOrHistory() {
        val old = legacy()
        val upgraded = upgradeLegacyBudget(old, "upgrade")
        assertEquals(32L, upgraded.economy.availableBalance)
        assertEquals(41L, upgraded.economy.savingsBalance)
        assertEquals(73L, upgraded.economy.balance)
        assertEquals(BudgetPlan(0, 0, 0, 0), upgraded.economy.plan)
        assertEquals(32L, upgraded.economy.unallocated)
        assertEquals(BudgetPlanning("upgrade", BudgetPlanningReason.MIGRATION, BudgetPlanningStage.ALLOCATION,
            0, draft = BudgetPlan(0, 0, 0, 0), baseAmount = 32), upgraded.economy.planning)
        assertEquals(old, upgraded.copy(economy = old.economy))
        assertEquals(intention, old.economy.plan)
    }

    @Test fun anUnfinishedValidDraftKeepsItsAmountsIdentityRevisionAndStage() {
        val old = legacy().let { it.copy(economy = it.economy.copy(unallocated = 10,
            planning = BudgetPlanning("draft", BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION,
                0, revision = 8, draft = BudgetPlan(10, 3, 4, 5), baseAmount = 32))) }
        assertEquals(old, upgradeLegacyBudget(old, "upgrade"))
        val receipt = createInitialGameState()
        assertEquals(receipt, upgradeLegacyBudget(receipt, "upgrade"))
    }

    @Test fun invalidDraftBasisIsNotAppliedToTheActualAccount() {
        val old = legacy().let { it.copy(economy = it.economy.copy(unallocated = 40,
            planning = BudgetPlanning("stale", BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION,
                0, draft = BudgetPlan(35, 0, 15, 0), baseAmount = 90))) }
        val upgraded = upgradeLegacyBudget(old, "upgrade")
        assertEquals(32L, upgraded.economy.unallocated)
        assertEquals(32L, upgraded.economy.planning!!.baseAmount)
        assertEquals(73L, upgraded.economy.balance)
        assertEquals(old.financial, upgraded.financial)
    }

    @Test fun zeroAvailableNeedsNoSessionAndNeverConsumesSavings() {
        val old = legacy(available = 0)
        val upgraded = upgradeLegacyBudget(old, "upgrade")
        assertEquals(BudgetPlan(0, 0, 0, 0), upgraded.economy.plan)
        assertEquals(0L, upgraded.economy.unallocated)
        assertNull(upgraded.economy.planning)
        assertEquals(41L, upgraded.economy.savingsBalance)
        assertEquals(old.financial, upgraded.financial)
    }

    @Test fun coincidentallyMatchingLegacyAmountsDoNotPretendToBeKnownLiveEnvelopes() {
        val old = legacy(available = 90)
        val upgraded = upgradeLegacyBudget(old, "upgrade")
        assertEquals(90L, upgraded.economy.unallocated)
        assertEquals(BudgetPlanningReason.MIGRATION, upgraded.economy.planning!!.reason)
        assertEquals(131L, upgraded.economy.balance)
    }

    @Test fun liveSnapshotValidationRejectsMismatchedReadyAmountsAndInvalidDraftsButAllowsReceipt() {
        assertFalse(legacy().economy.hasValidLiveBudget())
        val ready = legacy(90).economy
        assertTrue(ready.hasValidLiveBudget())
        assertFalse(ready.copy(unallocated = 1).hasValidLiveBudget())
        assertTrue(createInitialGameState().economy.hasValidLiveBudget())
        val drafting = legacy().economy.copy(unallocated = 22, planning = BudgetPlanning("draft",
            BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION, 0,
            draft = BudgetPlan(10, 0, 0, 0), baseAmount = 32))
        assertTrue(drafting.hasValidLiveBudget())
        assertFalse(drafting.copy(unallocated = 21).hasValidLiveBudget())
        assertFalse(drafting.copy(planning = drafting.planning!!.copy(baseAmount = 90)).hasValidLiveBudget())
    }
}
