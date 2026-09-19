package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase

@RunWith(AndroidJUnit4::class)
class DebugGameResetTest {
    @Test fun resetRemovesEntireAggregateAndAllowsFreshInitializationAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "debug-reset-${java.util.UUID.randomUUID()}.db"
        var db = GameDatabase.open(context, name)
        try {
            RoomStoryContentRepository(db).install(testContent())
            RoomGameRepository(db).initializeIfAbsent(savedGame().copy(completedMiniGames = setOf("done")))
            db.withWriteTransaction {
                usePrepared("INSERT INTO ENGINE_STATE VALUES ('current', 'rules', 9, 3, 'RUNNING', 2, 4, 1, NULL, 300)") { it.step() }
                usePrepared("INSERT INTO ENGINE_DEED VALUES ('offer', 'current', 0, 'earning', 5, 0)") { it.step() }
                usePrepared("INSERT INTO ENGINE_EVENT VALUES ('occurrence', 'current', 0, NULL, 'RESULT', 'offer')") { it.step() }
                usePrepared("INSERT INTO LEGACY_EXPENSE_STATE VALUES ('current', 2, 'CHOOSING', 7)") { it.step() }
            }
            val contentBefore = RoomStoryContentRepository(db).read()
            DebugGameResetRepository(db).reset()
            db.close()
            db = GameDatabase.open(context, name)
            assertNull(RoomGameRepository(db).read())
            val dao = db.gameStateDao()
            assertTrue(dao.readOwnedItems("current").isEmpty())
            assertTrue(dao.readDecisions("current").isEmpty())
            assertTrue(dao.readEngineEvents("current").isEmpty())
            assertTrue(dao.readEngineDeeds("current").isEmpty())
            assertTrue(dao.readMiniGameCompletions("current").isEmpty())
            assertNull(dao.readEngine("current"))
            assertEquals(contentBefore, RoomStoryContentRepository(db).read())
            DebugGameResetRepository(db).reset() // Also safe before a save exists.
            val initial = createInitialGameState()
            assertEquals(initial, RoomGameRepository(db).initializeIfAbsent(initial))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun failedResetRollsBackAlreadyDeletedChildren() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "debug-reset-rollback-${java.util.UUID.randomUUID()}.db"
        val db = GameDatabase.open(context, name)
        try {
            RoomStoryContentRepository(db).install(testContent())
            val before = savedGame().copy(completedMiniGames = setOf("done"))
            val games = RoomGameRepository(db)
            games.initializeIfAbsent(before)
            db.withWriteTransaction {
                usePrepared("CREATE TRIGGER reject_debug_reset BEFORE DELETE ON GAME_STATE BEGIN SELECT RAISE(ABORT, 'reset rejected'); END") { it.step() }
            }
            try {
                DebugGameResetRepository(db).reset()
                fail("Reset should fail")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
            }
            assertEquals(before, games.read())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
