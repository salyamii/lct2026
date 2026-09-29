package ru.nksk.lctapp.domain.history

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.Assistance
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.ReserveApplication
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EventExposure
import ru.nksk.lctapp.domain.finance.FinancialAnswerOption
import ru.nksk.lctapp.domain.finance.FinancialPeriod
import ru.nksk.lctapp.domain.finance.FinancialProgress
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.story.StoryState

class HistoryCodecCompatibilityTest {
    @Test fun emptyHistoryAndArchivesKeepTheExactOriginalWireAndSignatureForEveryVersion() {
        for (version in 1..5) {
            val snapshot = oldSnapshot(version, "empty-$version", state(version), emptyList())
            assertCompatible(snapshot)
            assertFalse(HistoryCodec.encodeSnapshot(snapshot).contains("\"archivedRuns\""))
            if (version == 5) assertEquals(snapshot.checksum,
                HistoryCodec.snapshot(snapshot.runId, snapshot.state, snapshot.history).checksum)
        }
    }

    @Test fun versionsOneThroughFiveKeepOrderedListsAndNormalizeOnlySets() {
        for (version in 1..5) {
            val run = "run-$version"
            val state = state(version)
            val history = history(run, state, version)
            val snapshot = oldSnapshot(version, run, state, history)
            val decoded = assertCompatible(snapshot)
            assertEquals(state.ownedItems, decoded.state.ownedItems)
            assertEquals(listOf("map", "compass", "map"), decoded.state.ownedItems.map { it.itemId })
            assertEquals(state.story.decisions, decoded.state.story.decisions)
            assertEquals(listOf("late", "early", "late"), decoded.state.financial.practice?.sourceActionIds)
            assertEquals(listOf("a", "z", "ёж"), decoded.state.completedMiniGames.toList())
            val sortedAssistance = context().assistance.sortedBy { it.name }
            assertEquals(sortedAssistance, decoded.history[1].context?.assistance?.toList())
            assertEquals(sortedAssistance, decoded.history[1].request?.context?.assistance?.toList())
            assertEquals(sortedAssistance, decoded.history[2].facts.single().context.assistance.toList())
            if (version == 5) assertEquals(snapshot.checksum, HistoryCodec.snapshot(run, state, history).checksum)
        }
    }

    @Test fun mixedVersionArchivesRetainTheirOwnBytesAndTheOuterSignature() {
        for ((firstVersion, secondVersion) in listOf(1 to 2, 3 to 4, 1 to 5)) {
            val firstRun = "older-$firstVersion"
            val secondRun = "later-$secondVersion"
            val firstState = state(firstVersion)
            val secondState = state(secondVersion)
            val archives = listOf(
                ArchivedGameRun("rewind-old", secondRun,
                    oldSnapshot(firstVersion, firstRun, firstState, history(firstRun, firstState, firstVersion))),
                ArchivedGameRun("rewind-later", "current",
                    oldSnapshot(secondVersion, secondRun, secondState, history(secondRun, secondState, secondVersion))),
            )
            val currentState = state(5)
            val currentHistory = history("current", currentState, 5)
            val expected = oldSnapshot(5, "current", currentState, currentHistory, archives)
            val actual = HistoryCodec.snapshot("current", currentState, currentHistory, archives)
            assertEquals(expected.checksum, actual.checksum)
            val decoded = assertCompatible(expected)
            val wireArchives = Json.parseToJsonElement(HistoryCodec.encodeSnapshot(actual)).jsonObject
                .getValue("archivedRuns").jsonArray
            archives.forEachIndexed { index, archived ->
                assertEquals(OldCodec.encodeSnapshot(archived.snapshot),
                    wireArchives[index].jsonObject.getValue("snapshot").toString())
                assertEquals(archived.snapshot.formatVersion, decoded.archivedRuns[index].snapshot.formatVersion)
            }
            assertNotEquals(expected.checksum, OldCodec.checksum(expected.copy(archivedRuns = archives.reversed())))
        }
    }

    @Test fun legacyReserveApplicationsAndFinancialDefaultsKeepTheirOriginalPresenceRules() {
        for (version in 1..5) {
            val currentState = state(version)
            val snapshot = oldSnapshot(version, "legacy", currentState, history("legacy", currentState, version))
            val wire = OldCodec.encodeSnapshot(snapshot)
            assertEquals(version > 1, wire.contains("\"applications\""))
            assertEquals(version > 1, wire.contains("\"savingPractice\""))
            assertEquals(version > 1, wire.contains("\"reviewEvidence\""))
            assertEquals(version > 1, wire.contains("\"eventHistory\""))
            assertEquals(version > 2, wire.contains("\"selectedSavingItemId\""))
            val decoded = assertCompatible(snapshot)
            val reserve = decoded.history[2].facts.single().detail as FactDetail.ReserveDecision
            assertEquals(if (version == 1) emptyList<ReserveApplication>()
                else listOf(ReserveApplication("expense-b", 2), ReserveApplication("expense-a", 1)), reserve.applications)
            assertNull(decoded.state.financial.periods.single().savingPractice)
            assertNull(decoded.state.financial.periods.single().reviewEvidence)
        }
    }

    @Test fun multiMegabyteHistoryHasTheSameSignatureAndCompleteWireDocument() {
        val state = state(5)
        val payload = "Запись 🌌: \"звёзды\" / \\ путь\t\n".repeat(700)
        val entries = listOf(AuditEntry("init", 1, "large", AuditType.INITIALIZED, after = state)) +
            (2..193).map { sequence ->
                AuditEntry("entry-$sequence", sequence.toLong(), "large", AuditType.TECHNICAL_UPDATE,
                    before = state, after = state, contentFingerprint = payload)
            }
        val expected = oldSnapshot(5, "large", state, entries)
        val oldWire = OldCodec.encodeSnapshot(expected)
        assertTrue("Fixture must exercise a multi-MiB snapshot", oldWire.length > 2 * 1024 * 1024)
        val actual = HistoryCodec.snapshot("large", state, entries)
        assertEquals(expected.checksum, actual.checksum)
        assertEquals(oldWire, HistoryCodec.encodeSnapshot(actual))
        assertEquals(expected, HistoryCodec.decodeSnapshot(oldWire))
        HistoryCodec.validate(actual)
    }

    @Test fun utf8HashMatchesTheOriginalJdkEncoderAroundChunkBoundariesAndForMalformedSurrogates() {
        val suffixes = listOf("Кириллица ёж 🌌", "\uD83D\uDE80", "\uD83D", "\uDE80",
            "\uD83Dx\uDE80", "\uD800\uD800\uDC00", "\"\\\t\r\n\u0000")
        for (size in listOf(0, 4095, 4096, 8190, 8191, 8192, 8193, 16383)) {
            for (suffix in suffixes) {
                val text = "a".repeat(size) + suffix + "конец"
                assertEquals("UTF-8 mismatch at prefix length $size", OldCodec.sha256(text), HistoryCodec.sha256(text))
            }
        }
        for (text in listOf("", "Ж".repeat(4095) + "🌌", "🌌".repeat(4096), "\uD800", "\uDC00")) {
            assertEquals(OldCodec.sha256(text), HistoryCodec.sha256(text))
        }
    }

    private fun assertCompatible(snapshot: GameSnapshot): GameSnapshot {
        val expected = OldCodec.encodeSnapshot(snapshot)
        assertEquals("Wire format ${snapshot.formatVersion}", expected, HistoryCodec.encodeSnapshot(snapshot))
        val decoded = HistoryCodec.decodeSnapshot(expected)
        assertEquals(snapshot, decoded)
        assertEquals(expected, HistoryCodec.encodeSnapshot(decoded))
        HistoryCodec.validate(snapshot)
        return decoded
    }

    private fun oldSnapshot(version: Int, run: String, state: GameState, history: List<AuditEntry>,
        archives: List<ArchivedGameRun> = emptyList()): GameSnapshot {
        val unsigned = GameSnapshot(version, run, state, history, history.lastOrNull()?.sequence ?: 0,
            checksum = "unsigned", archivedRuns = archives)
        return unsigned.copy(checksum = OldCodec.checksum(unsigned))
    }

    private fun state(version: Int) = GameState(
        pet = PetState("PLAIN", PetVisualState.NORMAL, name = "Тагил 🌌"),
        economy = EconomyState(BudgetPlan(35, 0, 10, 0), availableBalance = 81, savingsBalance = 19),
        story = StoryState("day", 1, null, listOf(StoryDecision("z", "repeat"),
            StoryDecision("a", "other"), StoryDecision("middle", "repeat"))),
        satiety = 72, fatigue = 13,
        ownedItems = listOf(OwnedItem("second", "map"), OwnedItem("first", "compass"), OwnedItem("third", "map")),
        completedMiniGames = linkedSetOf("ёж", "z", "a"),
        financial = FinancialProgress(currentPeriodId = "period",
            periods = listOf(FinancialPeriod("period", "goal", 1, 1, 81, 19)),
            practice = FinancialQuestion("question", FinancialQuestionKind.PLAN_REVIEW,
                "Хватит ли на \"карту\"? 🌌", listOf(FinancialAnswerOption("yes", "Да"), FinancialAnswerOption("no", "Нет")),
                "yes", "Путь \\ карта\tи звёзды\nГотово!", sourceActionIds = listOf("late", "early", "late"))),
        eventHistory = if (version == 1) emptyList() else listOf(EventExposure("second", 3, null, 2), EventExposure("first", 1, 2, 1, 1)),
        selectedSavingItemId = if (version < 3) null else "target",
    )

    private fun context() = DecisionContext(presentationId = "контекст \"🌌\"\\\n", informationPresented = true,
        assistance = linkedSetOf(Assistance.WORKED_EXAMPLE, Assistance.ADULT_REPORTED, Assistance.HINT))

    private fun history(run: String, state: GameState, version: Int): List<AuditEntry> {
        val context = context()
        val fact = AnalyticsFact("reserve", run, "episode", "action", 3,
            FactDetail.ReserveDecision("intention", 10, 6, 3, true, applications = if (version == 1) emptyList()
                else listOf(ReserveApplication("expense-b", 2), ReserveApplication("expense-a", 1))), context)
        return listOf(
            AuditEntry("init", 1, run, AuditType.INITIALIZED, after = state),
            AuditEntry("rejected", 2, run, AuditType.REJECTED,
                request = EngineRequest("attempt", null, EngineCommand.Feed("meal"), context), context = context),
            AuditEntry("facts", 3, run, AuditType.FACTS, context = context, facts = listOf(fact)),
        )
    }

    /** Independent pre-streaming implementation from b1f9ffb. No production codec helpers. */
    private object OldCodec {
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; classDiscriminator = "_type" }

        fun checksum(snapshot: GameSnapshot): String = with(snapshot) {
            val original = "$formatVersion\n$runId\n$historySequence\n${versioned(json.encodeToString(normalize(state)), formatVersion)}\n" +
                versioned(json.encodeToString(history.map(::normalize)), formatVersion)
            sha256(if (formatVersion < 5) original else "$original\n${archivePayload(archivedRuns)}")
        }

        fun encodeSnapshot(snapshot: GameSnapshot): String {
            val value = json.parseToJsonElement(json.encodeToString(snapshot.copy(
                state = normalize(snapshot.state), history = snapshot.history.map(::normalize), archivedRuns = emptyList()))).jsonObject
            val encoded = if (snapshot.archivedRuns.isEmpty()) value else
                JsonObject(value + ("archivedRuns" to archivePayload(snapshot.archivedRuns)))
            return versioned(encoded.toString(), snapshot.formatVersion)
        }

        private fun archivePayload(archives: List<ArchivedGameRun>) = JsonArray(archives.map { archive -> buildJsonObject {
            put("restartRequestId", archive.restartRequestId)
            put("nextRunId", archive.nextRunId)
            put("snapshot", json.parseToJsonElement(encodeSnapshot(archive.snapshot)))
        } })

        private fun versioned(encoded: String, version: Int): String {
            if (version >= 3) return encoded
            val v2 = legacyV2(json.parseToJsonElement(encoded))
            return (if (version == 1) legacyV1(v2) else v2).toString()
        }

        private fun legacyV2(value: JsonElement): JsonElement = when (value) {
            is JsonArray -> JsonArray(value.map(::legacyV2))
            is JsonObject -> JsonObject(value.entries.mapNotNull { (key, child) ->
                if (key == "selectedSavingItemId") { require(child == JsonNull); null }
                else key to legacyV2(child)
            }.toMap())
            else -> value
        }

        private fun legacyV1(value: JsonElement): JsonElement = when (value) {
            is JsonArray -> JsonArray(value.map(::legacyV1))
            is JsonObject -> JsonObject(value.entries.mapNotNull { (key, child) ->
                when (key) {
                    "eventHistory" -> { require(child == JsonArray(emptyList())); null }
                    "savingPractice", "reviewEvidence" -> { require(child == JsonNull); null }
                    "applications" -> if (value["_type"] == JsonPrimitive("reserve_decision")) {
                        require(child == JsonArray(emptyList())); null
                    } else key to legacyV1(child)
                    else -> key to legacyV1(child)
                }
            }.toMap())
            else -> value
        }

        private fun normalize(state: GameState) = state.copy(completedMiniGames = state.completedMiniGames.sorted().toSet())
        private fun normalize(context: DecisionContext) = context.copy(assistance = context.assistance.sortedBy { it.name }.toSet())
        private fun normalize(entry: AuditEntry) = entry.copy(
            request = entry.request?.let { it.copy(context = it.context?.let(::normalize)) },
            context = entry.context?.let(::normalize), before = entry.before?.let(::normalize), after = entry.after?.let(::normalize),
            facts = entry.facts.map { it.copy(context = normalize(it.context)) },
        )

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
