package ru.nksk.lctapp.feature.tasks.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.minigame.*

class EventGameStartEvidenceTest {
    private val state = createInitialGameState()
    private val context = DecisionContext("shown-on-card", true, true, FinancialPosition(100, 0, 35), alternativeAvailable = true)
    private val start = EngineRequest("start", null, EngineCommand.StartStoryGame("occurrence", "work", "urgent-deed"), context)
    private val receipt = AuditEntry("start", 1, "run", AuditType.COMMAND, start, before = state, after = state)
    private val score = checkNotNull(DeedGameScore.fromPrecision(TargetStopState.create().copy(round = 5, hits = 3, lastHit = true)))
    private val completion = EngineCommand.CompleteStoryGame("occurrence", "work", score)

    @Test fun shownContextAndDeclaredPriorityComeOnlyFromTheMatchingCommittedStart() {
        val evidence = checkNotNull(eventGameStartEvidence(receipt, state, completion))
        assertEquals(context, evidence.context)
        assertEquals("urgent-deed", evidence.priorityOfferId)
        assertNull(eventGameStartEvidence(null, state, completion))
        assertNull(eventGameStartEvidence(receipt, state, completion.copy(choiceId = "another-choice")))
    }

    @Test fun changedMoneyOrAnyGameRevisionCannotReuseOldPresentation() {
        assertNull(eventGameStartEvidence(receipt, state.copy(economy = state.economy.copy(availableBalance = 90)), completion))
        assertNull(eventGameStartEvidence(receipt, state.copy(pet = state.pet.copy(name = "Другой")), completion))
        val unseen = receipt.copy(request = start.copy(context = null))
        assertNull(eventGameStartEvidence(unseen, state, completion)!!.context)
    }

    @Test fun latestCommandMustItselfBeTheMatchingStartEvenIfItsWorldMatches() {
        assertNull(eventGameStartEvidence(receipt.copy(request = start.copy(command = EngineCommand.OpenNextEvent)), state, completion))
        assertNull(eventGameStartEvidence(receipt.copy(type = AuditType.REJECTED, before = null, after = null), state, completion))
        assertNull(eventGameStartEvidence(receipt.copy(request = start.copy(
            command = EngineCommand.StartStoryGame("another-occurrence", "work"))), state, completion))
    }
}
