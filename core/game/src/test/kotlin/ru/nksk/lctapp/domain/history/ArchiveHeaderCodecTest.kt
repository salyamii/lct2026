package ru.nksk.lctapp.domain.history

import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class ArchiveHeaderCodecTest {
    private val state = GameState(PetState("PLAIN", PetVisualState.NORMAL, name = "Путь 🦊 \"звёзды\"\\\n"),
        EconomyState(BudgetPlan(0, 0, 0, 0), availableBalance = 81, savingsBalance = 19),
        StoryState(null, null, null, emptyList()), 72, 13, emptyList())

    @Test fun pagedHeaderReconstructsTheExactExistingSnapshotSignatureAndWire() = runTest {
        val request = EngineRequest("save", null, EngineCommand.DepositSavings(5))
        val after = state.copy(economy = state.economy.copy(availableBalance = 76, savingsBalance = 24))
        val entries = listOf(
            AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = state),
            AuditEntry("command", 2, "run", AuditType.COMMAND, request = request, before = state, after = after,
                operations = CanonicalLedger.fromTransition(state, after, request)),
        ) + (3L..49L).map { AuditEntry("entry-$it", it, "run", AuditType.TECHNICAL_UPDATE,
            before = after, after = after, contentFingerprint = "карта 🌌 [\"x\"]\\\n") }
        val expected = HistoryCodec.snapshot("run", after, entries)
        val headerJson = header(entries, after)
        val header = HistoryCodec.decodeArchiveHeader(headerJson)
        assertEquals(expected.historySequence, header.historySequence)
        assertEquals(expected.checksum, header.checksum)
        assertEquals(expected.formatVersion, header.formatVersion)
        assertTrue(header.history.isEmpty())
        assertTrue(header.archivedRuns.isEmpty())
        assertFalse(headerJson.contains("contentFingerprint"))
        val restored = header.copy(history = HistoryCodec.encodedHistory(entries.map(HistoryCodec::encode)))
        HistoryCodec.validate(restored)
        assertEquals(expected, restored)
        assertEquals(HistoryCodec.encodeSnapshot(expected), HistoryCodec.encodeSnapshot(restored))
    }

    @Test fun aLongHistoryIsProducedAndSignedInPagesWhileTheStoredHeaderStaysSmall() = runTest {
        val sequence = 257L
        val fingerprint = "Запись 🌌\\\"\n".repeat(1_500)
        fun entry(number: Long) = AuditEntry("entry-$number", number, "long-run", AuditType.TECHNICAL_UPDATE,
            before = state, after = state, contentFingerprint = fingerprint)
        val expected = MessageDigest.getInstance("SHA-256")
        fun sign(text: String) { expected.update(text.toByteArray(Charsets.UTF_8)) }
        sign("5\nlong-run\n$sequence\n${HistoryCodec.encodeState(state)}\n[")
        for (number in 1..sequence) {
            if (number > 1) sign(",")
            sign(HistoryCodec.encode(entry(number)))
        }
        sign("]\n[]")
        val cursors = mutableListOf<Long>()
        var largestPage = 0
        val encoded = HistoryCodec.createArchiveHeader("long-run", state, sequence) { after, limit ->
            assertTrue(limit in 1..16)
            cursors += after
            ((after + 1)..minOf(sequence, after + limit)).map { HistoryCodec.encode(entry(it)) }
                .also { largestPage = maxOf(largestPage, it.size) }
        }
        val header = HistoryCodec.decodeArchiveHeader(encoded)
        assertEquals(expected.digest().joinToString("") { "%02x".format(it) }, header.checksum)
        assertEquals(sequence, header.historySequence)
        assertEquals((0L..256L step 16).toList() + sequence, cursors)
        assertEquals(16, largestPage)
        assertTrue("The archive header must contain only one current world", encoded.length < 16 * 1024)
        assertFalse(encoded.contains(fingerprint))
    }

    @Test fun emptyHistoryUsesTheExistingFormatFiveSignature() = runTest {
        var reads = 0
        val encoded = HistoryCodec.createArchiveHeader("empty", state, 0) { after, _ ->
            assertEquals(0L, after)
            reads++
            emptyList()
        }
        assertEquals(1, reads)
        assertEquals(HistoryCodec.snapshot("empty", state, emptyList()), HistoryCodec.decodeSnapshot(encoded))
    }

    @Test fun oldRowsWithOmittedDefaultFieldsHaveTheReconstructedCanonicalSignature() = runTest {
        val entry = AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = state)
        val original = HistoryCodec.encode(entry)
        val oldRow = original.replace(",\"contentFingerprint\":null", "")
        assertNotEquals(original, oldRow)
        val encoded = HistoryCodec.createArchiveHeader("run", state, 1) { after, _ ->
            if (after == 0L) listOf(oldRow) else emptyList()
        }
        val restored = HistoryCodec.decodeArchiveHeader(encoded).copy(
            history = HistoryCodec.encodedHistory(listOf(oldRow), canonical = false))
        assertEquals(HistoryCodec.snapshot("run", state, listOf(entry)), restored)
        HistoryCodec.validate(restored)
    }

    @Test fun incompleteChangedOrMismatchedHistoryCannotBeArchived() = runTest {
        val first = AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = state)
        fails("sequence") { header(listOf(first), sequence = 2) }
        fails("changed") { header(listOf(first, first.copy(id = "second", sequence = 2)), sequence = 1) }
        fails("gaps") { header(listOf(first.copy(sequence = 2))) }
        fails("another run") { header(listOf(first.copy(runId = "other"))) }
        fails("Repeated history") { header(listOf(first, first.copy(sequence = 2))) }
        fails("chain") { header(listOf(first, AuditEntry("second", 2, "run", AuditType.TECHNICAL_UPDATE,
            before = state.copy(fatigue = 0), after = state))) }
        fails("tip") { header(listOf(first), state.copy(fatigue = 0)) }
        fails("limit") {
            HistoryCodec.createArchiveHeader("run", state, 17) { _, _ -> List(17) { HistoryCodec.encode(first) } }
        }
    }

    @Test fun pagedValidationRejectsRepeatedCommandAndInvalidFinancialReceipts() = runTest {
        val request = EngineRequest("deposit", null, EngineCommand.DepositSavings(5))
        val after = state.copy(economy = state.economy.copy(availableBalance = 76, savingsBalance = 24))
        val command = AuditEntry("command", 1, "run", AuditType.COMMAND, request = request,
            before = state, after = after, operations = CanonicalLedger.fromTransition(state, after, request))
        fails("receipt") { header(listOf(command.copy(operations = emptyList())), after) }
        fails("Repeated command") {
            header(listOf(command, command.copy(id = "again", sequence = 2, before = after)), after)
        }
    }

    @Test fun archiveHeadersPreserveLegacyFormatAndChecksumWithoutPretendingToBeCompleteSnapshots() {
        for (version in 1..5) {
            val history = listOf(AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = state))
            val unsigned = GameSnapshot(formatVersion = version, runId = "run", state = state,
                history = history, historySequence = 1, checksum = "unsigned")
            val wire = Json.parseToJsonElement(HistoryCodec.encodeSnapshot(unsigned)).jsonObject
            // These neutral fixtures have no version-specific non-default fields or unordered sets.
            val signature = HistoryCodec.sha256("$version\nrun\n1\n${wire.getValue("state")}\n" +
                wire.getValue("history") + if (version >= 5) "\n[]" else "")
            val complete = unsigned.copy(checksum = signature)
            HistoryCodec.validate(complete)
            val header = HistoryCodec.decodeArchiveHeader(HistoryCodec.encodeArchiveHeader(complete))
            assertEquals(version, header.formatVersion)
            assertEquals(signature, header.checksum)
            assertEquals(1L, header.historySequence)
            assertTrue(header.history.isEmpty())
            val restored = header.copy(history = HistoryCodec.encodedHistory(
                listOf(wire.getValue("history").toString().removePrefix("[").removeSuffix("]")), canonical = false))
            HistoryCodec.validate(restored)
            assertEquals(HistoryCodec.encodeSnapshot(complete), HistoryCodec.encodeSnapshot(restored))
        }
        try {
            HistoryCodec.decodeArchiveHeader(HistoryCodec.encodeSnapshot(HistoryCodec.snapshot("run", state,
                listOf(AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = state)))))
            fail("Complete histories must not be accepted as local archive headers")
        } catch (_: IllegalArgumentException) { }
    }

    private suspend fun header(entries: List<AuditEntry>, current: GameState = state,
        sequence: Long = entries.lastOrNull()?.sequence ?: 0): String =
        HistoryCodec.createArchiveHeader("run", current, sequence) { after, limit ->
            entries.filter { it.sequence > after }.take(limit).map(HistoryCodec::encode)
        }

    private suspend fun fails(reason: String, action: suspend () -> Unit) {
        try {
            action()
            fail("Expected archive validation to fail: $reason")
        } catch (failure: IllegalArgumentException) {
            assertTrue("Expected '$reason', got '${failure.message}'",
                failure.message.orEmpty().contains(reason, ignoreCase = true))
        }
    }
}
