package ru.nksk.lctapp.data.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class LocalRunArchiveCodecTest {
    private val runId = "run-\"путь\"\n🦊"
    private val initial = GameState(PetState("PLAIN", PetVisualState.NORMAL, name = "Рыжик 🦊 \"звёзды\"\\n"),
        EconomyState(BudgetPlan(0, 0, 0, 0), availableBalance = 81, savingsBalance = 19),
        StoryState(null, null, null, emptyList()), 72, 13, emptyList())
    private val current = initial.copy(pet = initial.pet.copy(name = "Тоша"))
    private val entries = listOf(
        AuditEntry("init", 1, runId, AuditType.INITIALIZED, after = initial),
        AuditEntry("rename", 2, runId, AuditType.TECHNICAL_UPDATE, before = initial, after = current),
    )

    @Test fun localHeaderRoundTripsAndMaterializesTheOrdinaryTransportSnapshot() {
        val payload = LocalRunArchiveCodec.create(runId, current, 2)
        val header = LocalRunArchiveCodec.decode(payload)
        assertEquals(runId, header.runId)
        assertEquals(current, header.state)
        assertEquals(2L, header.historySequence)
        val restored = header.snapshot(HistoryCodec.encodedHistory(entries.map(HistoryCodec::encode)))
        val expected = HistoryCodec.snapshot(runId, current, entries)
        assertEquals(expected, restored)
        assertEquals(HistoryCodec.encodeSnapshot(expected), HistoryCodec.encodeSnapshot(restored))
    }

    @Test fun creatingAndListingALongRunNeedsOnlyItsCurrentWorldAndCursor() {
        // No audit list or page callback exists on this API, even for a very long run.
        val payload = LocalRunArchiveCodec.create(runId, current, Long.MAX_VALUE)
        val fields = Json.parseToJsonElement(payload).jsonObject
        assertEquals(setOf("archiveStorageVersion", "runId", "state", "historySequence", "checksum"), fields.keys)
        val header = LocalRunArchiveCodec.decode(payload)
        assertEquals(Long.MAX_VALUE, header.historySequence)
        assertEquals(current, header.state)
        assertTrue(payload.length < HistoryCodec.encodeState(current).length + 300)
        fails { header.snapshot(emptyList()) }
    }

    @Test fun zeroCursorRemainsExplicitForSqlMetadataReaders() {
        val payload = LocalRunArchiveCodec.create(runId, initial, 0)
        assertEquals(JsonPrimitive(0), Json.parseToJsonElement(payload).jsonObject.getValue("historySequence"))
        assertEquals(HistoryCodec.snapshot(runId, initial, emptyList()),
            LocalRunArchiveCodec.decode(payload).snapshot(emptyList()))
    }

    @Test fun changesToMetadataOrStateAreDetectedBeforeHistoryIsRequested() {
        val fields = Json.parseToJsonElement(LocalRunArchiveCodec.create(runId, current, 2)).jsonObject
        val corrupted = listOf(
            fields + ("runId" to JsonPrimitive("another-run")),
            fields + ("historySequence" to JsonPrimitive(3)),
            fields + ("state" to Json.parseToJsonElement(HistoryCodec.encodeState(initial))),
            fields + ("checksum" to JsonPrimitive("changed")),
            fields + ("archiveStorageVersion" to JsonPrimitive(2)),
            fields + ("unexpected" to JsonPrimitive(true)),
        )
        corrupted.forEach { fields -> fails { LocalRunArchiveCodec.decode(JsonObject(fields).toString()) } }
        fails { LocalRunArchiveCodec.create("", current, 2) }
        fails { LocalRunArchiveCodec.create(runId, current, -1) }
    }

    @Test fun oldHeadersKeepTheirOriginalFormatAndFullHistoryChecksum() {
        for (version in 1..5) {
            val unsigned = GameSnapshot(formatVersion = version, runId = "legacy-run", state = initial,
                history = listOf(entries.first().copy(runId = "legacy-run")), historySequence = 1, checksum = "unsigned")
            val wire = Json.parseToJsonElement(HistoryCodec.encodeSnapshot(unsigned)).jsonObject
            val signature = HistoryCodec.sha256("$version\nlegacy-run\n1\n${wire.getValue("state")}\n" +
                wire.getValue("history") + if (version >= 5) "\n[]" else "")
            val expected = unsigned.copy(checksum = signature)
            HistoryCodec.validate(expected)
            val header = LocalRunArchiveCodec.decode(HistoryCodec.encodeArchiveHeader(expected))
            val restored = header.snapshot(expected.history)
            assertEquals(version, restored.formatVersion)
            assertEquals(signature, restored.checksum)
            assertEquals(HistoryCodec.encodeSnapshot(expected), HistoryCodec.encodeSnapshot(restored))
            // A semantically valid historical edit must still fail the old stored signature.
            fails { header.snapshot(listOf(expected.history.single().copy(contentFingerprint = "changed"))) }
        }
    }

    @Test fun historyCorruptionIsRejectedWhenTheArchiveIsMaterialized() {
        val header = LocalRunArchiveCodec.decode(LocalRunArchiveCodec.create(runId, current, 2))
        fails { header.snapshot(entries.take(1)) }
        fails { header.snapshot(listOf(entries.last())) }
        fails { header.snapshot(entries.map { it.copy(runId = "other") }) }
        fails { header.snapshot(listOf(entries.first(), entries.last().copy(id = entries.first().id))) }
        fails { header.snapshot(listOf(entries.first(), entries.last().copy(before = initial.copy(satiety = 0)))) }
        fails { header.snapshot(listOf(entries.first(), entries.last().copy(after = initial))) }
        val request = EngineRequest("deposit", null, EngineCommand.DepositSavings(5))
        val after = initial.copy(economy = initial.economy.copy(availableBalance = 76, savingsBalance = 24))
        val invalidReceipt = AuditEntry("deposit", 2, runId, AuditType.COMMAND,
            request = request, before = initial, after = after, operations = emptyList())
        val financialHeader = LocalRunArchiveCodec.decode(LocalRunArchiveCodec.create(runId, after, 2))
        fails { financialHeader.snapshot(listOf(entries.first(), invalidReceipt)) }
    }

    @Test fun ordinaryTransportDocumentsCannotBeMistakenForLocalHeaders() {
        val complete = HistoryCodec.snapshot(runId, current, entries)
        fails { LocalRunArchiveCodec.decode(HistoryCodec.encodeSnapshot(complete)) }
        fails { HistoryCodec.decodeSnapshot(LocalRunArchiveCodec.create(runId, current, 2)) }
    }

    private fun fails(action: () -> Unit) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue("Expected archive validation to reject the invalid document, got $failure",
            failure is IllegalArgumentException || failure is IllegalStateException)
    }
}
