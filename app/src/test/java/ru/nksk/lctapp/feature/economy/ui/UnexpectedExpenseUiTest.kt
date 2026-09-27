package ru.nksk.lctapp.feature.economy.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.AnalyticsMode
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

class UnexpectedExpenseUiTest {
    private val catalog = bundledGameCatalog()

    @Test fun candidatesUseTheCommittedAmountAndStoryTitleMostRecentFirst() {
        val options = unexpectedExpenseOptions(listOf(expenseHistoryEntry("first", 7),
            expenseHistoryEntry("second", 13, 2)), catalog)
        assertEquals(listOf("second", "first"), options.map { it.operationId })
        assertEquals(13L, options.first().amount)
        assertEquals(2, options.first().day)
        assertEquals(catalog.content.events.first { it.type == EventType.RANDOM }.title, options.first().title)
    }

    @Test fun factsWithoutAnActualMatchingExpenseAreNotOffered() {
        val entry = expenseHistoryEntry("operation", 7)
        assertTrue(unexpectedExpenseOptions(listOf(entry.copy(operations = emptyList())), catalog).isEmpty())
        assertTrue(unexpectedExpenseOptions(listOf(entry.copy(operations = entry.operations.map {
            it.copy(kind = LedgerKind.DEPOSIT)
        })), catalog).isEmpty())
        assertTrue(unexpectedExpenseOptions(listOf(entry.copy(operations = entry.operations.map {
            it.copy(amount = 8)
        })), catalog).isEmpty())
    }

    @Test fun disclosedSimulatedAndAlreadyHandledExpensesAreExcluded() {
        val real = expenseHistoryEntry("real", 7)
        val simulated = expenseHistoryEntry("simulation", 9, 2).let { entry ->
            entry.copy(facts = entry.facts.map { it.copy(mode = AnalyticsMode.SIMULATION) })
        }
        val recovery = AuditEntry("recovery", 4, "run", AuditType.FACTS,
            facts = listOf(AnalyticsFact("recovered", "run", "recovery:real", "fix", 4,
                FactDetail.RecoveryAction("real", "fix", true))))
        assertTrue(unexpectedExpenseOptions(listOf(real, simulated,
            expenseHistoryEntry("known", 10, 3, previouslyDisclosed = true), recovery), catalog).isEmpty())
    }
}
