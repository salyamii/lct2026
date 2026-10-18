package ru.nksk.lctapp.data.survey

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.GameSnapshotHead
import ru.nksk.lctapp.domain.parentlink.ParentLinkCode
import ru.nksk.lctapp.domain.parentlink.ParentLinkProfile
import ru.nksk.lctapp.domain.parentlink.ParentLinkRepository

class DataStoreSurveyRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun linkReadsCurrentIdentityAndRunEveryTimeAndEncodesValuesWithoutGameWrites() = runTest {
        val games = Games()
        val profiles = Profiles()
        withRepository(games, profiles) { repository ->
            val first = repository.formUrl().toHttpUrl()
            assertEquals("https", first.scheme)
            assertEquals("forms.yandex.ru", first.host)
            assertEquals("/u/6ad49a05852af395fcc7a0af/", first.encodedPath)
            assertEquals(setOf("deviceId", "gameRunId"), first.queryParameterNames)
            assertEquals(profiles.id, first.queryParameter("deviceId"))
            assertEquals(games.runId, first.queryParameter("gameRunId"))
            profiles.id = "device + & ? = / # новый"
            games.runId = "new + & ? = / # run"
            val second = repository.formUrl().toHttpUrl()
            assertEquals(profiles.id, second.queryParameter("deviceId"))
            assertEquals(games.runId, second.queryParameter("gameRunId"))
            assertEquals(0, games.writes)
            assertFalse(repository.observeCompleted().first())
        }
    }

    @Test fun onlyFinishedDaysQualifyAndSimultaneousClaimsCannotShowTwice() = runTest {
        val games = Games()
        withRepository(games) { repository ->
            games.setDay(6)
            assertNull(repository.claimInvitation())
            games.setDay(7, DayPhase.READY_TO_END)
            assertNull(repository.claimInvitation())
            games.setDay(7)
            val claims = (1..4).map { async { repository.claimInvitation() } }.awaitAll()
            assertEquals(1, claims.count { it != null })
            games.setDay(13)
            assertNull(repository.claimInvitation())
            games.setDay(14)
            assertEquals(14, repository.claimInvitation()?.day)
            games.setDay(7) // Older cloud/time-machine saves do not reset the device preference.
            assertNull(repository.claimInvitation())
            games.runId = "another-run"
            assertEquals("another-run", repository.claimInvitation()?.runId)
            games.runId = "current-run"
            assertNull(repository.claimInvitation())
            assertEquals(0, games.writes)
        }
    }

    @Test fun completedPreferenceSurvivesRecreationAndAnotherGameAndOpeningIsNotCompletion() = runTest {
        val file = File(temporary.root, "survey.preferences_pb")
        val games = Games().apply { setDay(7) }
        withRepository(games, file = file) { repository ->
            assertNotNull(repository.claimInvitation())
            repository.formUrl()
            assertFalse(repository.observeCompleted().first())
        }
        withRepository(games, file = file) { repository ->
            assertNull(repository.claimInvitation())
            repository.markCompleted()
            assertTrue(repository.observeCompleted().first())
        }
        games.runId = "new-run"
        games.setDay(100)
        withRepository(games, file = file) { repository ->
            assertTrue(repository.observeCompleted().first())
            assertNull(repository.claimInvitation())
            assertEquals("new-run", repository.formUrl().toHttpUrl().queryParameter("gameRunId"))
            assertEquals(0, games.writes)
        }
    }

    private suspend fun TestScope.withRepository(games: Games, profiles: Profiles = Profiles(),
        file: File = File(temporary.root, "${System.nanoTime()}.preferences_pb"),
        block: suspend (DataStoreSurveyRepository) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try { block(DataStoreSurveyRepository(store, profiles, games)) }
        finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    private class Profiles : ParentLinkRepository {
        var id = "current-device"
        override suspend fun profile() = ParentLinkProfile(id, backendConfigured = true)
        override suspend fun createCode(): ParentLinkCode = error("No QR expected")
        override suspend fun registerProfile() = error("No backend changes expected")
    }

    private class Games : GameRepository {
        var value = createInitialGameState()
        var runId = "current-run"
        var writes = 0
        fun setDay(day: Int, phase: DayPhase = DayPhase.FINISHED) {
            value = value.copy(engine = EngineState("rules", 1, day, phase, 0, 0, true,
                null, 0, emptyList(), emptyList()))
        }
        override suspend fun readSnapshotHead() = GameSnapshotHead(runId, value, 1)
        override fun observe() = flowOf(value)
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState): GameState = error("No initialization expected")
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            writes++
            error("Survey must not write the world")
        }
    }
}
