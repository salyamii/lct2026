package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.BackendSyncStateEntity
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.*

/** Source coverage only: device execution requires the user's authorization. */
@RunWith(AndroidJUnit4::class)
class CampaignRestartPersistenceTest {
    private lateinit var db: GameDatabase
    private lateinit var games: RoomGameRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder<GameDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(BundledSQLiteDriver()).build()
        games = RoomGameRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun rewindKeepsCompleteHistoryAndTransportThenDeduplicatesTheSameIntent() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        games.update { it.copy(pet = it.pet.copy(name = "Старый мир")) }
        val old = games.exportSnapshot()
        val transport = BackendSyncStateEntity("device", "https://example.test/", serverRevision = 7, gameRunId = old.runId)
        db.backendSyncDao().upsertState(transport)
        val request = old.rewindRequest("once")
        games.restartCampaign(request) { _, original -> checkNotNull(original) }
        val fresh = games.exportSnapshot()
        assertNotEquals(old.runId, fresh.runId)
        assertEquals(old, games.archivedRun(old.runId))
        assertEquals(listOf(old.runId), games.archivedRuns().map { it.runId })
        assertEquals(transport, db.backendSyncDao().readState("device"))
        assertEquals(1, fresh.history.size)
        assertEquals(fresh, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(fresh)))
        games.update { it.copy(pet = it.pet.copy(name = "Уже новый мир")) }
        val progressed = games.exportSnapshot()
        games.restartCampaign(request) { _, _ -> error("Retry must not execute") }
        assertEquals(progressed, games.exportSnapshot())
    }

    @Test fun aFailedNewWorldRollsBackArchiveAndTheEntireCurrentRun() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val before = games.exportSnapshot()
        try {
            games.restartCampaign(before.rewindRequest("bad")) { current, _ ->
                current.copy(ownedItems = listOf(OwnedItem("bad", "missing-definition")))
            }
            fail("A missing content reference must reject the entire transaction")
        } catch (_: Exception) { }
        assertEquals(before, games.exportSnapshot())
        assertTrue(games.archivedRuns().isEmpty())
    }

    @Test fun staleConfirmationCannotArchiveANewerWorldAndRepeatedCyclesStayFlatAcrossRestore() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val first = games.exportSnapshot()
        games.update { it.copy(pet = it.pet.copy(name = "Изменён")) }
        try {
            games.restartCampaign(first.rewindRequest("stale")) { _, original -> checkNotNull(original) }
            fail("A stale confirmation must be rejected")
        } catch (_: CampaignRestartConflictException) { }
        val older = games.exportSnapshot()
        games.restartCampaign(older.rewindRequest("first")) { _, original -> checkNotNull(original) }
        val middle = games.exportSnapshot()
        games.restartCampaign(middle.rewindRequest("second")) { _, original -> checkNotNull(original) }
        val last = games.exportSnapshot()
        assertEquals(2, last.archivedRuns.size)
        assertTrue(last.archivedRuns.all { it.snapshot.archivedRuns.isEmpty() })
        assertEquals(older.history, games.archivedRun(older.runId)!!.history)
        games.restoreSnapshot(last, RestoreGuard(last.state.engine?.revision, last.historySequence))
        assertEquals(last.archivedRuns, games.exportSnapshot().archivedRuns)
        assertEquals(middle.history, games.archivedRun(middle.runId)!!.history)
    }

    private fun GameSnapshot.rewindRequest(id: String) = CampaignRestartRequest(id, runId, state.engine?.revision, historySequence)
}
