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

class DaySummaryUiStateTest {
    private val catalog = bundledGameCatalog()

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
        assertTrue(summary.activities.contains(DaySummaryRow("Настроили малый телескоп", "Получили 6 монет")))
        assertTrue(summary.activities.any { it.label.contains("важную деталь Хроноскопа") })
        assertTrue(summary.activities.contains(DaySummaryRow("Купили: Карта звёзд", "Потратили 24 монеты")))
        assertEquals(2, summary.activities.count { it == DaySummaryRow("Пообедали", "Потратили 5 монет") })
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
        assertEquals(listOf(DaySummaryRow("Помогли бобру распутать канаты")), old.activities)
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
        assertEquals(listOf(DaySummaryRow("Купили: Карта звёзд", "Потратили 24 монеты")), summary.activities)
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
        assertTrue(summary.activities.contains(DaySummaryRow("Купили: Кепка исследователя", "Потратили 25 монет")))
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
        assertEquals(listOf(DaySummaryRow("Поели в бесплатной столовой", "Завтра будет меньше сил")), summary.activities)
    }
}
