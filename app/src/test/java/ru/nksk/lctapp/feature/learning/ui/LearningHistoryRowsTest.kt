package ru.nksk.lctapp.feature.learning.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.DayJournalEntry
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.story.StoryState

class LearningHistoryRowsTest {
    private val catalog = bundledGameCatalog()
    private val initial = GameState(PetState("PLAIN", PetVisualState.NORMAL, name = "Тоша"),
        EconomyState(BudgetPlan(35, 25, 25, 15)), StoryState(null, null, null, emptyList()),
        0, 0, emptyList(), EngineState("rules", 0, 1, DayPhase.RUNNING, 0, 5, false,
            null, 100, emptyList(), emptyList()))

    private fun audit(sequence: Long, before: GameState, after: GameState, operations: List<LedgerEntry> = emptyList()) =
        AuditEntry("entry:$sequence", sequence, "run", AuditType.TECHNICAL_UPDATE,
            before = before, after = after, operations = operations)

    @Test fun namedRepeatedWorkShowsItsOwnActualRewardOnceAndNewestFirst() {
        val choice = "figma-2163-43-v1:complete"
        fun completed(before: GameState, id: String, reward: Long): GameState {
            val engine = checkNotNull(before.engine)
            return before.copy(story = before.story.copy(decisions = before.story.decisions + StoryDecision(id, choice)),
                engine = engine.copy(journal = engine.journal + DayJournalEntry(id, DayJournalKind.DEED, choice, reward)))
        }
        val first = completed(initial, "first", 6)
        val second = completed(first, "second", 2)
        val rows = learningHistoryRows(listOf(
            audit(1, initial, first, listOf(LedgerEntry("first", LedgerKind.INCOME, 6))),
            audit(2, first, second, listOf(LedgerEntry("second", LedgerKind.INCOME, 2))),
        ), catalog, "Тоша")
        assertEquals(listOf("День 1: Настроили малый телескоп. Получили 2 монеты",
            "День 1: Настроили малый телескоп. Получили 6 монет"), rows)
    }

    @Test fun accessoryPurchaseAndAcquisitionAreOneNamedReceipt() {
        val choice = "figma-2164-2-v1:buy"
        val item = "figma-2164-2-explorer-cap-v1"
        val after = initial.copy(story = initial.story.copy(decisions = listOf(StoryDecision("bought", choice))),
            ownedItems = listOf(OwnedItem("cap", item)), engine = initial.engine!!.copy(journal = listOf(
                DayJournalEntry("payment", DayJournalKind.EVENT_CHOICE, choice, -25),
                DayJournalEntry("item", DayJournalKind.ITEM_RECEIVED, item, 0))))
        val rows = learningHistoryRows(listOf(audit(1, initial, after,
            listOf(LedgerEntry("payment", LedgerKind.AVAILABLE_EXPENSE, 25)))), catalog, "Тоша")
        assertEquals(listOf("День 1: Купили: Кепка исследователя. Потратили 25 монет"), rows)
    }

    @Test fun zeroMoneyDecisionAndActualFindingStillAppearAndNamesAreRendered() {
        val choice = catalog.content.choices.first { it.id == "campaign-choice-v1:G3.05:continue" }
        val custom = catalog.copy(cards = catalog.cards - choice.eventId,
            content = catalog.content.copy(events = catalog.content.events.map {
                if (it.id == choice.eventId) it.copy(title = "Находка {petName}") else it
            }, choices = catalog.content.choices.map { if (it.id == choice.id) it.copy(text = "Сохранить находку") else it }))
        val after = initial.copy(story = initial.story.copy(decisions = listOf(StoryDecision("found", choice.id))),
            ownedItems = listOf(OwnedItem("map", "stargazing-star-map-v1")))
        val rows = learningHistoryRows(listOf(audit(1, initial, after)), custom, "Тоша")
        assertTrue(rows.any { "Находка Тоша: Сохранить находку" in it })
        assertTrue(rows.any { "Получили: Карта звёзд" in it })
        assertFalse(rows.any { "Потратили" in it || "{petName}" in it })
    }

    @Test fun mixedGoalPurchaseNamesBothAccountsInOneRowAndKeepsUnrelatedReceipts() {
        val item = "stargazing-star-map-v1"
        val after = initial.copy(ownedItems = listOf(OwnedItem("map", item)),
            engine = initial.engine!!.copy(journal = listOf(
                DayJournalEntry("purchase", DayJournalKind.ITEM_PURCHASE, item, -24))))
        val rows = learningHistoryRows(listOf(audit(1, initial, after, listOf(
            LedgerEntry("purchase", LedgerKind.SAVINGS_EXPENSE, 21),
            LedgerEntry("purchase:available", LedgerKind.AVAILABLE_EXPENSE, 3),
            LedgerEntry("separate-deposit", LedgerKind.DEPOSIT, 2),
        ))), catalog, "Тоша")
        assertEquals(listOf(
            "День 1: Купили: Карта звёзд. Потратили 21 монету из копилки и 3 из денег с собой",
            "День 1: Отложили в копилку 2 монеты",
        ), rows)
    }

    @Test fun goalPurchaseWithOneAccountKeepsTheExistingNamedPayment() {
        val item = "stargazing-star-map-v1"
        val after = initial.copy(ownedItems = listOf(OwnedItem("map", item)),
            engine = initial.engine!!.copy(journal = listOf(
                DayJournalEntry("purchase", DayJournalKind.ITEM_PURCHASE, item, -24))))
        listOf(
            LedgerKind.SAVINGS_EXPENSE to "Потратили из копилки 24 монеты",
            LedgerKind.AVAILABLE_EXPENSE to "Потратили 24 монеты",
        ).forEach { (kind, payment) ->
            val rows = learningHistoryRows(listOf(audit(1, initial, after,
                listOf(LedgerEntry("purchase", kind, 24)))), catalog, "Тоша", includeDay = false)
            assertEquals(listOf("Купили: Карта звёзд. $payment"), rows)
        }
    }

    @Test fun baselineDoesNotPretendOlderActionsHappenedNowAndNewest50AreKept() {
        val imported = AuditEntry("baseline", 1, "run", AuditType.IMPORTED_BASELINE, after = initial.copy(
            ownedItems = listOf(OwnedItem("map", "stargazing-star-map-v1"))))
        val transfers = (2L..56L).map { number -> audit(number, initial, initial,
            listOf(LedgerEntry("deposit:$number", LedgerKind.DEPOSIT, number))) }
        val rows = learningHistoryRows(listOf(imported) + transfers, catalog, "Тоша")
        assertEquals(50, rows.size)
        assertEquals("День 1: Отложили в копилку 56 монет", rows.first())
        assertEquals("День 1: Отложили в копилку 7 монет", rows.last())
        assertTrue(learningHistoryRows(listOf(imported), catalog, "Тоша").isEmpty())
    }
}
