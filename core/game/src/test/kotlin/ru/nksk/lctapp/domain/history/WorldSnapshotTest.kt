package ru.nksk.lctapp.domain.history

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.backend.SnapshotDownloadResponse
import ru.nksk.lctapp.domain.backend.snapshotUploadRequest
import ru.nksk.lctapp.domain.backend.worldSnapshot
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class WorldSnapshotTest {
    private val state = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(BudgetPlan(3, 4, 5, 6), savingsBalance = 9),
        StoryState(null, null, null, emptyList()), 72, 13,
        listOf(OwnedItem("second", "map"), OwnedItem("first", "map")))

    @Test fun compactRoundTripPreservesCurrentValuesAndOrderWithoutAnyCheckpointHistory() {
        val snapshot = WorldSnapshotCodec.create("run", state, 900, "generation")
        val encoded = WorldSnapshotCodec.encode(snapshot)
        assertEquals(snapshot, WorldSnapshotCodec.decode(encoded))
        val fields = Json.parseToJsonElement(encoded).jsonObject
        assertFalse("history" in fields)
        assertFalse("archivedRuns" in fields)
        assertEquals(listOf("second", "first"), snapshot.state.ownedItems.map { it.id })
        val request = snapshotUploadRequest("device", snapshot, "upload", 12, "content")
        assertEquals("CURRENT_WORLD", request.payloadKind)
        assertEquals(900L, request.throughHistorySequence)
        // The inner discriminator is sufficient when an older transparent server omits the optional envelope field.
        assertEquals(snapshot, SnapshotDownloadResponse("run", 13, "content", encoded).worldSnapshot())
    }

    @Test fun alteredCurrentWorldCannotReuseTheOriginalChecksum() {
        val snapshot = WorldSnapshotCodec.create("run", state, 900, "generation")
        assertThrows(IllegalArgumentException::class.java) {
            WorldSnapshotCodec.validate(snapshot.copy(state = state.copy(satiety = 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldSnapshotCodec.validate(snapshot.copy(historySequence = 901))
        }
    }

    @Test fun legacyDownloadIsValidatedAndAdaptedWithoutPuttingItsHistoryInTheNewBody() {
        val history = listOf(AuditEntry("first", 1, "run", AuditType.INITIALIZED, after = state))
        val local = HistoryCodec.snapshot("run", state, history)
        val response = SnapshotDownloadResponse("run", 1, "content", HistoryCodec.encodeSnapshot(local))
        val world = response.worldSnapshot()
        assertEquals(state, world.state)
        assertEquals(1L, world.historySequence)
        assertFalse(WorldSnapshotCodec.encode(world).contains("\"history\":"))
        assertFalse(HistoryCodec.encode(history.single()).contains("worldRestore"))
    }

    @Test fun restoredLocalCursorCanBeAboveOrBelowItsSourceWithoutPaddingOrLosingHistory() {
        val world = WorldSnapshotCodec.create("run", state, 20, "generation")
        val behind = CloudWorldRead(world, localHistorySequence = 4, latestHistoryId = "last",
            baselineSequence = 2, sourceHistorySequence = 18)
        assertEquals(19L, behind.transportSequence(3))
        val ahead = CloudWorldRead(world, localHistorySequence = 104, latestHistoryId = "last",
            baselineSequence = 102, sourceHistorySequence = 18)
        assertEquals(19L, ahead.transportSequence(103))
        assertThrows(IllegalArgumentException::class.java) { ahead.transportSequence(101) }
    }
}
