package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.minigame.MiniGameKind

@OptIn(ExperimentalCoroutinesApi::class)
class MiniGameSessionTest {
    @Test fun unsuccessfulAndAbandonedAttemptsDoNotChangeResources() = runTest {
        val repository = MiniGameTestRepository()
        val session = MiniGameSession(MiniGameKind.PRICE_QUIZ, repository, SavedStateHandle(), backgroundScope) { }
        session.observe()
        runCurrent()
        assertEquals(0, repository.read()!!.satiety)
        assertEquals(0, repository.read()!!.fatigue)
        session.finish(false)
        runCurrent()
        assertTrue(session.state.resultReady)
        assertFalse(session.state.successful)
        assertEquals(0, repository.read()!!.satiety)
        assertTrue(repository.read()!!.completedMiniGames.isEmpty())
    }

    @Test fun restoredSuccessAndRetryDoNotDoubleCharge() = runTest {
        val repository = MiniGameTestRepository()
        val saved = SavedStateHandle()
        val session = MiniGameSession(MiniGameKind.MEMORY, repository, saved, backgroundScope) { }
        session.observe()
        runCurrent()
        session.finish(true)
        runCurrent()
        val restored = MiniGameSession(MiniGameKind.MEMORY, repository, saved, backgroundScope) { }
        restored.observe()
        restored.finish(true)
        runCurrent()
        restored.retry()
        runCurrent()
        assertEquals(20, repository.read()!!.satiety)
        assertEquals(30, repository.read()!!.fatigue)
        assertEquals(1, repository.read()!!.completedMiniGames.size)
    }

    @Test fun failedWriteCanRetryAndOnlyThenPermitReplay() = runTest {
        val repository = MiniGameTestRepository()
        val session = MiniGameSession(MiniGameKind.TELESCOPE, repository, SavedStateHandle(), backgroundScope) { }
        session.observe()
        runCurrent()
        repository.failure = IOException("disk")
        session.finish(true)
        runCurrent()
        assertTrue(session.state.error)
        assertFalse(session.restart())
        assertEquals(0, repository.read()!!.fatigue)
        repository.failure = null
        session.retry()
        runCurrent()
        assertTrue(session.state.resultReady)
        assertEquals(40, repository.read()!!.fatigue)
        assertTrue(session.restart())
        session.finish(true)
        runCurrent()
        assertEquals(80, repository.read()!!.fatigue)
        assertFalse(session.restart())
    }

    @Test fun restoredUnavailableEntryCannotStart() = runTest {
        val repository = MiniGameTestRepository(createInitialGameState().copy(satiety = 81))
        val session = MiniGameSession(MiniGameKind.MEMORY, repository, SavedStateHandle(), backgroundScope) { }
        session.observe()
        runCurrent()
        assertFalse(session.state.canPlay)
        assertFalse(session.restart())
    }
}
