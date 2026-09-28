package ru.nksk.lctapp.feature.day.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.game.restingPetArtwork
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState

class DaySummaryUiStateTest {
    private val catalog = bundledGameCatalog()

    @Test fun parentGiftsHaveTheirOwnSourceAndAreNeverPresentedAsWork() {
        val recap = DaySummary(2, 30, 50, emptyList(), 0, journal = listOf(
            DayJournalEntry("gift-coins", DayJournalKind.PARENT_REWARD, "reward", 20),
            DayJournalEntry("gift-hat", DayJournalKind.PARENT_REWARD, "cosmetic-explorer-hat-v2", 0),
        )).toUiState(catalog, "Тоша")
        assertEquals(listOf(
            DaySummaryRow("Подарок от родителя", "Получили 20 монет", DaySummaryRowKind.COINS),
            DaySummaryRow("Подарок от родителя: Кепка исследователя", kind = DaySummaryRowKind.FOUND),
        ), recap.activities)
        assertEquals(listOf("Получено за день: 20 монет"), recap.moneyLines)
        assertNull(recap.detailsNote)
        assertFalse(recap.activities.any { it.kind == DaySummaryRowKind.WORK })
    }

    @Test fun recapDescribesWorkFindingsAndPurchasesWithGrossSpendingAndActualReward() {
        val deed = "figma-2163-43-v1:complete"
        val finding = "campaign-choice-v1:G3.05:continue"
        val journal = listOf(
            DayJournalEntry("meal-1", DayJournalKind.MEAL, "basic-v1", -5),
            DayJournalEntry("meal-2", DayJournalKind.MEAL, "basic-v1", -5),
            DayJournalEntry("purchase", DayJournalKind.ITEM_PURCHASE, "stargazing-star-map-v1", -24),
            DayJournalEntry("work", DayJournalKind.DEED, deed, 6, -1),
        )
        val summary = DaySummary(1, 100, 72, emptyList(), 4, 5, 4, journal,
            listOf(StoryDecision("work-done", deed), StoryDecision("found", finding))).toUiState(catalog, "Тоша")
        assertEquals("Сейчас 72 монеты", summary.remaining)
        assertEquals(listOf("Потрачено за день: 34 монеты", "Получено за день: 6 монет"), summary.moneyLines)
        assertNull(summary.detailsNote)
        assertTrue(summary.activities.contains(DaySummaryRow("Настроили малый телескоп", "Получили 6 монет", DaySummaryRowKind.WORK)))
        assertTrue(summary.activities.any { it.label.contains("важную деталь Хроноскопа") })
        assertTrue(summary.activities.contains(DaySummaryRow("Купили: Карта звёзд", "Потратили 24 монеты", DaySummaryRowKind.PURCHASE)))
        assertEquals(2, summary.activities.count { it == DaySummaryRow("Пообедали", "Потратили 5 монет", DaySummaryRowKind.MEAL) })
        assertFalse((summary.activities.map { it.toString() } + summary.moneyLines + summary.remaining).any { "→" in it })
    }

    @Test fun migrationTopupReconcilesWithoutBeingCountedAsDailyIncome() {
        val summary = DaySummary(4, 20, 36, emptyList(), 2, journal = listOf(
            DayJournalEntry("meal", DayJournalKind.MEAL, "basic-v1", -5),
            DayJournalEntry("work", DayJournalKind.DEED, "figma-2163-43-v1:complete", 6),
        ), balanceAdjustment = 15).toUiState(catalog, "Тоша")
        assertEquals(listOf("Потрачено за день: 5 монет", "Получено за день: 6 монет"), summary.moneyLines)
        assertEquals("Сейчас 36 монет", summary.remaining)
        assertNull(summary.detailsNote)
        assertEquals("При переходе на бюджет добавлено 15 монет. Это не заработок за день.", summary.adjustmentNote)
        assertFalse(summary.activities.any { it.value.contains("15") })
    }

    @Test fun migrationTopupDoesNotHideMissingHistoryOrCountAsNetEarnings() {
        val summary = DaySummary(4, 20, 32, emptyList(), 2,
            balanceAdjustment = 15).toUiState(catalog, "Тоша")
        assertEquals(listOf("За день монет стало меньше на 3"), summary.moneyLines)
        assertNotNull(summary.detailsNote)
        assertNotNull(summary.adjustmentNote)
    }

    @Test fun oldSaveStillShowsCompletedWorkButDoesNotTreatItsNetDecreaseAsGrossSpending() {
        val old = DaySummary(4, 97, 68, emptyList(), 3, completedDecisions = listOf(
            StoryDecision("work-done", "figma-2270-54-v1:complete"),
        )).toUiState(catalog, "Тоша")
        assertEquals(listOf(DaySummaryRow("Помогли бобру распутать канаты", kind = DaySummaryRowKind.WORK)), old.activities)
        assertEquals(listOf("За день монет стало меньше на 29"), old.moneyLines)
        assertNotNull(old.detailsNote)
        val quiet = DaySummary(1, 100, 100, emptyList(), 0, 5, 5).toUiState(catalog, "Тоша")
        assertEquals("Сейчас 100 монет", quiet.remaining)
        assertTrue(quiet.moneyLines.isEmpty())
        assertTrue(quiet.activities.isEmpty())
    }

    @Test fun partialReceiptsKeepKnownPurchasesWithoutInventingTransactionsForTheRemainder() {
        val summary = DaySummary(4, 97, 68, emptyList(), 3, journal = listOf(
            DayJournalEntry("purchase", DayJournalKind.ITEM_PURCHASE, "stargazing-star-map-v1", -24),
        )).toUiState(catalog, "Тоша")
        assertEquals(listOf("За день монет стало меньше на 29"), summary.moneyLines)
        assertEquals(listOf(DaySummaryRow("Купили: Карта звёзд", "Потратили 24 монеты", DaySummaryRowKind.PURCHASE)), summary.activities)
        assertNotNull(summary.detailsNote)
    }

    @Test fun choosingDetourOrSkippingNeverClaimsARepairAndRepeatedWorkKeepsItsOwnRewards() {
        fun result(choice: String) = DaySummary(1, 100, 100, emptyList(), 1, completedDecisions = listOf(
            StoryDecision("decision", choice),
        )).toUiState(catalog, "Тоша").activities.single().label
        assertEquals("Помогли укрепить мост и перешли на другой берег", result("campaign-choice-v1:G2.03:repair"))
        assertEquals("Добрались на другой берег по обходному пути", result("campaign-choice-v1:G2.03:detour"))
        assertEquals("Решили продолжить экспедицию обычным маршрутом", result("campaign-choice-v1:G5.02:skip"))
        val deed = "figma-2163-43-v1:complete"
        val summary = DaySummary(1, 100, 108, emptyList(), 2, journal = listOf(
            DayJournalEntry("first", DayJournalKind.DEED, deed, 6),
            DayJournalEntry("second", DayJournalKind.DEED, deed, 2),
        ), completedDecisions = listOf(StoryDecision("first-done", deed), StoryDecision("second-done", deed)))
            .toUiState(catalog, "Тоша")
        assertEquals(listOf("Получили 6 монет", "Получили 2 монеты"), summary.activities.map { it.value })
        val partial = DaySummary(1, 100, 108, emptyList(), 2, journal = listOf(
            DayJournalEntry("new-receipt", DayJournalKind.DEED, deed, 2),
        ), completedDecisions = listOf(StoryDecision("old-work", deed), StoryDecision("new-work", deed)))
            .toUiState(catalog, "Тоша")
        assertEquals(listOf("", "Получили 2 монеты"), partial.activities.map { it.value })
    }

    @Test fun anEventPurchaseIsShownOnceAndOpposingCostsAreNotHiddenByZeroNetChange() {
        val cap = "figma-2164-2-v1:buy"
        val summary = DaySummary(1, 100, 100, emptyList(), 1, journal = listOf(
            DayJournalEntry("buy", DayJournalKind.EVENT_CHOICE, cap, -25),
            DayJournalEntry("item", DayJournalKind.ITEM_RECEIVED, "figma-2164-2-explorer-cap-v1", 0),
            DayJournalEntry("income", DayJournalKind.WEEKLY_INCOME, "rules", 25),
        ), completedDecisions = listOf(StoryDecision("bought", cap))).toUiState(catalog, "Тоша")
        assertEquals(1, summary.activities.count { "Кепка" in it.label })
        assertTrue(summary.activities.contains(DaySummaryRow("Купили: Кепка исследователя", "Потратили 25 монет", DaySummaryRowKind.PURCHASE)))
        assertEquals(listOf("Потрачено за день: 25 монет", "Получено за день: 25 монет"), summary.moneyLines)
    }

    @Test fun sleepUsesTheSavedAgeAndFreeMealIsNotShownAsAnExpense() {
        assertEquals(R.drawable.ryzhik_cub_state_sleep_copper, restingPetArtwork(PetState("PLAIN", PetVisualState.NORMAL, age = PetAge.CUB)))
        assertEquals(R.drawable.ryzhik_teen_state_sleep_copper, restingPetArtwork(PetState("PLAIN", PetVisualState.NORMAL, age = PetAge.TEEN)))
        assertEquals(R.drawable.ryzhik_adult_state_sleep_copper, restingPetArtwork(PetState("PLAIN", PetVisualState.NORMAL, age = PetAge.ADULT)))
        assertEquals(R.drawable.ryzhik_senior_state_sleep_copper, restingPetArtwork(PetState("PLAIN", PetVisualState.NORMAL, age = PetAge.SENIOR)))
        val summary = DaySummary(1, 0, 0, emptyList(), 3, 5, 0,
            listOf(DayJournalEntry("free", DayJournalKind.MEAL, "community-v1", 0))).toUiState(catalog, "Тоша")
        assertTrue(summary.moneyLines.isEmpty())
        assertEquals(listOf(DaySummaryRow("Поели в бесплатной столовой", "Завтра будет меньше сил", DaySummaryRowKind.MEAL)), summary.activities)
    }

    @Test fun diarySeparatesDailyFlowsFromCurrentWalletAndBankWithoutGuessingMissingAmounts() {
        val economy = EconomyState(BudgetPlan(35, 6, 10, 10), availableBalance = 61, savingsBalance = 30)
        val recap = DaySummary(3, 100, 91, emptyList(), 2, journal = listOf(
            DayJournalEntry("meal", DayJournalKind.MEAL, "basic-v1", -15),
            DayJournalEntry("work", DayJournalKind.DEED, "figma-2163-43-v1:complete", 6),
        )).toUiState(catalog, "Тоша", economy)
        assertEquals(3, recap.day)
        assertEquals(6.toBigInteger(), recap.income)
        assertEquals(15.toBigInteger(), recap.spending)
        assertEquals(61L, recap.availableBalance)
        assertEquals(30L, recap.savingsBalance)
        assertNull(recap.netChange)
        assertNull(recap.detailsNote)

        val partial = DaySummary(3, 100, 91, emptyList(), 2, journal = listOf(
            DayJournalEntry("meal", DayJournalKind.MEAL, "basic-v1", -15),
        )).toUiState(catalog, "Тоша", economy)
        assertNull(partial.income)
        assertNull(partial.spending)
        assertEquals((-9).toBigInteger(), partial.netChange)
        assertEquals(61L, partial.availableBalance)
        assertEquals(30L, partial.savingsBalance)
        assertNotNull(partial.detailsNote)
        assertEquals(DaySummaryRowKind.MEAL, partial.activities.single().kind)
    }

    @Test fun refusalIsADiaryDecisionAndNeverARewardOrAnIncome() {
        val choice = "figma-2654-2-purchase-v2:pass"
        val recap = DaySummary(1, 100, 100, emptyList(), 1,
            journal = listOf(DayJournalEntry("pass", DayJournalKind.EVENT_CHOICE, choice, 0)),
            completedDecisions = listOf(StoryDecision("pass-decision", choice))).toUiState(catalog, "Тоша")
        assertEquals(DaySummaryRowKind.DECISION, recap.activities.single().kind)
        assertEquals("", recap.activities.single().value)
        assertEquals(0.toBigInteger(), recap.income)
        assertEquals(0.toBigInteger(), recap.spending)
    }

    @Test fun oldPaidChoicesWithoutReceiptsDoNotBecomeZeroIncomeWhenTheNetBalanceMatches() {
        val recap = DaySummary(1, 100, 100, emptyList(), 1,
            completedDecisions = listOf(StoryDecision("old-work", "figma-2163-43-v1:complete")))
            .toUiState(catalog, "Тоша")
        assertNull(recap.income)
        assertNull(recap.spending)
        assertEquals(0.toBigInteger(), recap.netChange)
        assertNotNull(recap.detailsNote)
        assertEquals(DaySummaryRowKind.WORK, recap.activities.single().kind)
        assertEquals("", recap.activities.single().value)
    }
}
