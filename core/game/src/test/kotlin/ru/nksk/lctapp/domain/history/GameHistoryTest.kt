package ru.nksk.lctapp.domain.history

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EventExposure
import ru.nksk.lctapp.domain.finance.FinancialPeriod
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.ReserveApplication

class GameHistoryTest {
    private val state = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(BudgetPlan(0, 0, 0, 0), availableBalance = 81, savingsBalance = 19),
        StoryState(null, null, null, emptyList()), 72, 13,
        listOf(OwnedItem("second", "map"), OwnedItem("first", "map")))

    @Test fun snapshotRoundTripPreservesAccountsDuplicatesAndTypedRequest() {
        val request = EngineRequest("save-1", null, EngineCommand.DepositSavings(11))
        val after = state.copy(economy = state.economy.copy(availableBalance = 70, savingsBalance = 30))
        val entries = listOf(AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = state),
            AuditEntry("commit", 2, "run", AuditType.COMMAND, request = request, before = state, after = after,
                operations = CanonicalLedger.fromTransition(state, after, request), contentFingerprint = "catalog-v1"))
        val snapshot = HistoryCodec.snapshot("run", after, entries)
        assertEquals(snapshot, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot)))
        assertEquals(listOf("second", "first"), snapshot.state.ownedItems.map { it.id })
        assertEquals(100L, snapshot.state.economy.balance)
    }

    @Test fun alteredPayloadCannotReuseOriginalChecksum() {
        val snapshot = HistoryCodec.snapshot("run", state, emptyList())
        expectFailure { HistoryCodec.validate(snapshot.copy(state = state.copy(satiety = 1))) }
    }

    @Test fun setOrderDoesNotChangeSnapshotIdentityButListOrderDoes() {
        val first = state.copy(completedMiniGames = linkedSetOf("z", "a"))
        val second = state.copy(completedMiniGames = linkedSetOf("a", "z"))
        assertEquals(HistoryCodec.snapshot("run", first, emptyList()).checksum,
            HistoryCodec.snapshot("run", second, emptyList()).checksum)
        assertNotEquals(HistoryCodec.snapshot("run", first, emptyList()).checksum,
            HistoryCodec.snapshot("run", first.copy(ownedItems = first.ownedItems.reversed()), emptyList()).checksum)
    }

    @Test fun validChecksumDoesNotAllowMissingHistoryEntriesOrDifferentTip() {
        expectFailure { HistoryCodec.validate(HistoryCodec.snapshot("run", state,
            listOf(AuditEntry("gap", 2, "run", AuditType.INITIALIZED, after = state)))) }
        expectFailure { HistoryCodec.validate(HistoryCodec.snapshot("run", state,
            listOf(AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = state.copy(fatigue = 99))))) }
    }

    @Test fun transferReceiptMustMatchBothAccounts() {
        val request = EngineRequest("deposit", null, EngineCommand.DepositSavings(5))
        expectFailure { CanonicalLedger.fromTransition(state,
            state.copy(economy = state.economy.copy(availableBalance = 76, savingsBalance = 20)), request) }
    }

    @Test fun originalV1ChecksumSurvivesNewDefaultFieldsWithoutRewritingItsSignature() {
        val legacyState = state.copy(financial = FinancialProgress(currentPeriodId = "period", periods = listOf(
            FinancialPeriod("period", "goal", 1, 1, 81, 19))))
        fun oldShape(value: JsonElement): JsonElement = when (value) {
            is JsonArray -> JsonArray(value.map(::oldShape))
            is JsonObject -> JsonObject(value.filterKeys { it !in setOf("eventHistory", "savingPractice", "reviewEvidence", "selectedSavingItemId") }
                .mapValues { oldShape(it.value) })
            else -> value
        }
        val oldState = oldShape(Json.parseToJsonElement(HistoryCodec.encodeState(legacyState)))
        val signature = HistoryCodec.sha256("1\nold-run\n0\n$oldState\n[]")
        val v1 = GameSnapshot(formatVersion = 1, runId = "old-run", state = legacyState,
            history = emptyList(), historySequence = 0, checksum = signature)
        val encoded = HistoryCodec.encodeSnapshot(v1)
        assertFalse(encoded.contains("eventHistory"))
        assertFalse(encoded.contains("savingPractice"))
        assertEquals(v1, HistoryCodec.decodeSnapshot(encoded))
        assertEquals(signature, HistoryCodec.decodeSnapshot(encoded).checksum)
        val nested = HistoryCodec.snapshot("new", state, emptyList(), listOf(ArchivedGameRun("rewind-v1", "new", v1)))
        val nestedJson = Json.parseToJsonElement(HistoryCodec.encodeSnapshot(nested)).jsonObject
        assertEquals(Json.parseToJsonElement(encoded), nestedJson.getValue("archivedRuns").jsonArray.single().jsonObject.getValue("snapshot"))
        assertEquals(nested, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(nested)))
        expectFailure { HistoryCodec.validate(v1.copy(state = legacyState.copy(
            eventHistory = listOf(EventExposure("event", 1, null, 1))))) }
    }

    @Test fun currentRoundTripPreservesActualExposureOrderAndUnknownCompletion() {
        val tracked = state.copy(eventHistory = listOf(EventExposure("second", 5, null, 2), EventExposure("first", 2, 3, 1, 1)))
        val snapshot = HistoryCodec.snapshot("run", tracked, emptyList())
        assertEquals(5, snapshot.formatVersion)
        assertEquals(tracked.eventHistory, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot)).state.eventHistory)
    }

    @Test fun v4KeepsItsOriginalChecksumAndDoesNotInventAnArchive() {
        val signature = HistoryCodec.sha256("4\nold-run\n0\n${HistoryCodec.encodeState(state)}\n[]")
        val legacy = GameSnapshot(formatVersion = 4, runId = "old-run", state = state,
            history = emptyList(), historySequence = 0, checksum = signature)
        assertEquals(legacy, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(legacy)))
        assertFalse(HistoryCodec.encodeSnapshot(legacy).contains("archivedRuns"))
        val current = HistoryCodec.snapshot("new", state, emptyList(), listOf(ArchivedGameRun("rewind", "new", legacy)))
        assertEquals(legacy, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(current)).archivedRuns.single().snapshot)
    }

    @Test fun archivedRunsKeepEveryHistoricalEntryAndAreCoveredByTheOuterChecksum() {
        val oldHistory = listOf(AuditEntry("old-init", 1, "old", AuditType.INITIALIZED, after = state),
            AuditEntry("old-facts", 2, "old", AuditType.FACTS))
        val previous = HistoryCodec.snapshot("old", state, oldHistory)
        val archive = ArchivedGameRun("rewind", "new", previous)
        val current = HistoryCodec.snapshot("new", state, emptyList(), listOf(archive))
        assertEquals(current, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(current)))
        assertEquals(oldHistory, current.archivedRuns.single().snapshot.history)
        expectFailure { HistoryCodec.validate(current.copy(archivedRuns = emptyList())) }
        expectFailure { HistoryCodec.validate(current.copy(archivedRuns = listOf(archive.copy(restartRequestId = "changed")))) }
        expectFailure { ArchivedGameRun("nested", "newest", current) }
        expectFailure { HistoryCodec.snapshot("old", state, emptyList(), listOf(archive)) }
    }

    @Test fun originalV3ChecksumAndHistoricalIntentRemainUnchangedUntilExplicitImportUpgrade() {
        val legacy = state.copy(economy = state.economy.copy(plan = BudgetPlan(35, 20, 35, 0)))
        val encodedState = HistoryCodec.encodeState(legacy)
        val signature = HistoryCodec.sha256("3\nold-run\n0\n$encodedState\n[]")
        val archive = GameSnapshot(formatVersion = 3, runId = "old-run", state = legacy,
            history = emptyList(), historySequence = 0, checksum = signature)
        val decoded = HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(archive))
        assertEquals(archive, decoded)
        assertEquals(90L, decoded.state.economy.plan.total)
        assertEquals(81L, decoded.state.economy.availableBalance)
        assertEquals(signature, decoded.checksum)
        expectFailure { HistoryCodec.validate(archive.copy(formatVersion = 4)) }
    }

    @Test fun originalV2ChecksumRemainsValidWithoutAFabricatedSavingTarget() {
        val oldState = JsonObject(Json.parseToJsonElement(HistoryCodec.encodeState(state)).jsonObject - "selectedSavingItemId")
        val signature = HistoryCodec.sha256("2\nold-run\n0\n$oldState\n[]")
        val archive = GameSnapshot(formatVersion = 2, runId = "old-run", state = state,
            history = emptyList(), historySequence = 0, checksum = signature)
        val encoded = HistoryCodec.encodeSnapshot(archive)
        assertFalse(encoded.contains("selectedSavingItemId"))
        assertEquals(archive, HistoryCodec.decodeSnapshot(encoded))
        expectFailure { HistoryCodec.validate(archive.copy(state = state.copy(selectedSavingItemId = "new-target"))) }
    }

    @Test fun originalV1ReserveFactWithoutApplicationsStillAuthenticatesItsOriginalPayload() {
        val fact = AnalyticsFact("reserve-closed", "old-run", "reserve-episode", "close-window", 1,
            FactDetail.ReserveDecision("reserve-intention", 10, 6, 0, true))
        val entry = AuditEntry("observation", 1, "old-run", AuditType.FACTS, facts = listOf(fact))
        // Build the old stored wire shape explicitly. applications did not exist in format1.
        val oldState = JsonObject(Json.parseToJsonElement(HistoryCodec.encodeState(state)).jsonObject - "eventHistory" - "selectedSavingItemId")
        val entryJson = Json.parseToJsonElement(HistoryCodec.encode(entry)).jsonObject
        val factJson = entryJson.getValue("facts").jsonArray.single().jsonObject
        val oldFact = JsonObject(factJson + ("detail" to JsonObject(factJson.getValue("detail").jsonObject - "applications")))
        val oldHistory = JsonArray(listOf(JsonObject(entryJson + ("facts" to JsonArray(listOf(oldFact))))))
        val signature = HistoryCodec.sha256("1\nold-run\n1\n$oldState\n$oldHistory")
        val archive = JsonObject(mapOf("formatVersion" to JsonPrimitive(1), "runId" to JsonPrimitive("old-run"),
            "state" to oldState, "history" to oldHistory, "historySequence" to JsonPrimitive(1),
            "rulesId" to JsonNull, "checksum" to JsonPrimitive(signature))).toString()
        val restored = HistoryCodec.decodeSnapshot(archive)
        assertEquals(signature, restored.checksum)
        assertEquals(fact, restored.history.single().facts.single())
        assertFalse(HistoryCodec.encodeSnapshot(restored).contains("\"applications\""))
        assertEquals(restored, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(restored)))
        val withNewEvidence = fact.copy(detail = (fact.detail as FactDetail.ReserveDecision).copy(
            applications = listOf(ReserveApplication("expense", 4))))
        expectFailure { HistoryCodec.validate(restored.copy(history = listOf(entry.copy(facts = listOf(withNewEvidence))))) }
        val v2 = HistoryCodec.snapshot("old-run", state, listOf(entry.copy(facts = listOf(withNewEvidence))))
        assertEquals(withNewEvidence, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(v2)).history.single().facts.single())
    }

    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }
}
