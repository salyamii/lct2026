package ru.nksk.lctapp.feature.learning.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.timemachine.*

class ChronoscopeUiStateTest {
    private val initial = createInitialGameState().copy(economy = EconomyState(BudgetPlan(35, 20, 20, 25)))
    private val branch = TimeMachineBranch(initial, emptyList())
    private val request = TimeMachineRequest("entry", "alternative", 8)

    @Test fun failedReplayCannotExposePlausibleBalancesAsAComparison() {
        val result = TimeMachineResult(TimeMachineStatus.DIVERGED, request, simulationId = "simulation",
            reachedSequence = 3, requestedSequence = 8, baseline = branch, alternative = branch)
        assertFalse(result.hasComparablePaths(targetSequence = 4))
        assertTrue(result.copy(reachedSequence = 4).hasComparablePaths(targetSequence = 4))
        assertFalse(result.copy(status = TimeMachineStatus.INCOMPATIBLE_VERSION, reachedSequence = 8)
            .hasComparablePaths(targetSequence = 4))
    }

    @Test fun horizonDoesNotCallASavedFragmentTheEndOfTheDay() {
        val result = TimeMachineResult(TimeMachineStatus.COMPLETE, request)
        assertEquals("Сравниваем сыгранную часть дня 9", chronoscopeHorizon(result, 9))
        assertEquals("День 9: до момента, когда пути разошлись", chronoscopeHorizon(result.copy(status = TimeMachineStatus.DIVERGED), 9))
    }

    @Test fun partialBoundaryNamesOnlyAnActionThatWasActuallyReached() {
        val catalogue = bundledGameCatalog()
        val target = AuditEntry("entry", 1, "run", AuditType.TECHNICAL_UPDATE, before = initial, after = initial)
        val spent = initial.copy(economy = EconomyOperations.spend(initial.economy, 5, SpendingKind.FEEDING))
        val meal = AuditEntry("meal", 2, "run", AuditType.TECHNICAL_UPDATE, before = initial, after = spent,
            operations = listOf(LedgerEntry("meal", LedgerKind.AVAILABLE_EXPENSE, 5)))
        val result = TimeMachineResult(TimeMachineStatus.DIVERGED, request, reachedSequence = 1, requestedSequence = 2)
        assertNull(chronoscopeBoundary(result, listOf(target, meal), catalogue))
        val reachedBoundary = chronoscopeBoundary(result.copy(reachedSequence = 2), listOf(target, meal), catalogue)!!
        assertTrue(reachedBoundary.startsWith("Последний общий момент:"))
        assertTrue(reachedBoundary.contains("5 монет"))
        assertNull(chronoscopeBoundary(result.copy(reachedSequence = 0), listOf(target, meal), catalogue))
    }

    @Test fun transferIsShownSeparatelyFromExpensesAndItemOccurrencesArePreserved() {
        val catalogue = bundledGameCatalog()
        val item = catalogue.content.items.first()
        val before = initial.copy(ownedItems = listOf(OwnedItem("already-owned", item.id)))
        val after = before.copy(ownedItems = before.ownedItems + OwnedItem("new-copy", item.id))
        val result = chronoscopePath(TimeMachineBranch(after, listOf(
            LedgerEntry("deposit", LedgerKind.DEPOSIT, 20),
            LedgerEntry("food", LedgerKind.AVAILABLE_EXPENSE, 5),
        )), before, catalogue)
        assertEquals(5L, result.money.toMap().getValue("Потрачено"))
        assertEquals(20L, result.money.toMap().getValue("Отложено"))
        assertEquals(listOf(item.id), result.gainedItems.map { it.itemId })
        assertEquals(1, result.gainedItems.single().count)
        assertEquals(1, before.ownedItems.size)
    }

    @Test fun purchaseComparisonOnlyShowsTheDifferentItemAndPaymentNotSharedFoodEnergyOrEmptySavings() {
        val catalog = bundledGameCatalog()
        val item = catalog.content.items.first()
        val before = initial.copy(engine = EngineState(catalog.rules.id, 1, 1, DayPhase.RUNNING,
            0, 4, true, null, 100, emptyList(), emptyList()))
        val purchased = before.copy(economy = EconomyOperations.spend(before.economy, 7, SpendingKind.WANT),
            ownedItems = listOf(OwnedItem("bought", item.id)))
        val original = chronoscopePath(TimeMachineBranch(purchased,
            listOf(LedgerEntry("purchase", LedgerKind.AVAILABLE_EXPENSE, 7))), before, catalog)
        val alternative = chronoscopePath(TimeMachineBranch(before, emptyList()), before, catalog)
        val compared = chronoscopeDifferences(original, alternative)

        assertEquals(listOf("Получили: ${original.gainedItems.single().title}"), compared.original)
        assertEquals(listOf("Не получили: ${original.gainedItems.single().title}"), compared.alternative)
        assertEquals(listOf("Осталось монет", "Потрачено"), compared.money.map { it.label })
        assertEquals(7L, compared.money.single { it.label == "Потрачено" }.original)
        assertEquals(0L, compared.money.single { it.label == "Потрачено" }.alternative)
    }

    @Test fun sharedItemIdentityIsComparedByIdAndCountNotOccurrenceIdOrVisibleTitle() {
        val catalog = bundledGameCatalog()
        val item = catalog.content.items.first()
        val first = chronoscopePath(TimeMachineBranch(initial.copy(
            ownedItems = listOf(OwnedItem("first-occurrence", item.id))), emptyList()), initial, catalog)
        val sameItem = chronoscopePath(TimeMachineBranch(initial.copy(
            ownedItems = listOf(OwnedItem("second-occurrence", item.id))), emptyList()), initial, catalog)
        assertTrue(chronoscopeDifferences(first, sameItem).original.isEmpty())
        assertTrue(chronoscopeDifferences(first, sameItem).alternative.isEmpty())

        val repeated = first.copy(gainedItems = first.gainedItems.map { it.copy(count = 2) })
        assertTrue(chronoscopeDifferences(repeated, sameItem).original.single().endsWith("× 2"))
        val differentItemWithSameTitle = sameItem.copy(gainedItems = sameItem.gainedItems.map { it.copy(itemId = "another-item") })
        val different = chronoscopeDifferences(first, differentItemWithSameTitle)
        assertEquals(2, different.original.size)
        assertEquals(2, different.alternative.size)
    }

    @Test fun paidCleaningAndManualCleaningShowBothTheMoneyAndEffortTradeoff() {
        val catalog = bundledGameCatalog()
        val rested = initial.copy(engine = EngineState(catalog.rules.id, 1, 1, DayPhase.RUNNING,
            0, 4, true, null, 100, emptyList(), emptyList()))
        val tired = rested.copy(engine = rested.engine!!.copy(energy = 2))
        val paid = rested.copy(economy = EconomyOperations.spend(rested.economy, 3, SpendingKind.GENERAL))
        val first = chronoscopePath(TimeMachineBranch(paid,
            listOf(LedgerEntry("cleaning-pay", LedgerKind.AVAILABLE_EXPENSE, 3))), initial, catalog)
        val second = chronoscopePath(TimeMachineBranch(tired, emptyList()), initial, catalog)
        val compared = chronoscopeDifferences(first, second)

        assertEquals(listOf("Сохранили больше сил"), compared.original)
        assertEquals(listOf("Потратили больше сил"), compared.alternative)
        assertEquals(3L, compared.money.single { it.label == "Осталось монет" }.alternative -
            compared.money.single { it.label == "Осталось монет" }.original)
        assertEquals(ChronoscopeAmountDifference("Потрачено", 3, 0), compared.money.single { it.label == "Потрачено" })
        assertFalse(compared.money.any { it.label == "В копилке" })
    }

    @Test fun aLedgerQuestionStillShowsTheSpendingTotalItAsksAboutEvenWhenItIsEqual() {
        val catalog = bundledGameCatalog()
        val path = chronoscopePath(TimeMachineBranch(initial,
            listOf(LedgerEntry("meal", LedgerKind.AVAILABLE_EXPENSE, 5))), initial, catalog)

        assertTrue(chronoscopeDifferences(path, path).money.isEmpty())
        assertEquals(listOf(ChronoscopeAmountDifference("Потрачено", 5, 5)),
            chronoscopeDifferences(path, path, showLedgerTotal = true).money)
    }

    @Test fun backFromQuestionOrExplanationKeepsTheSameComparison() {
        listOf(ChronoscopeStep.QUIZ, ChronoscopeStep.RETRY, ChronoscopeStep.EXPLANATION).forEach {
            assertEquals(ChronoscopeStep.COMPARISON, chronoscopeBack(it))
        }
        assertEquals(ChronoscopeStep.ALTERNATIVES, chronoscopeBack(ChronoscopeStep.CONSEQUENCES))
        assertNull(chronoscopeBack(ChronoscopeStep.PRESENT))
    }

    @Test fun shorterReflectionFlowDoesNotReopenRetiredIntermediateScreens() {
        assertEquals(ChronoscopeStep.ALTERNATIVES, chronoscopeBack(ChronoscopeStep.COMPARISON))
        assertEquals(ChronoscopeStep.MOMENTS, chronoscopeBack(ChronoscopeStep.ALTERNATIVES))
        assertNull(chronoscopeBack(ChronoscopeStep.MOMENTS))
        assertEquals(ChronoscopeStep.MOMENTS, chronoscopeBack(ChronoscopeStep.UNAVAILABLE))
    }
}
