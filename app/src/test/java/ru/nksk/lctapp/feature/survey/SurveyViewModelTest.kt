package ru.nksk.lctapp.feature.survey

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.domain.survey.SurveyInvitation
import ru.nksk.lctapp.domain.survey.SurveyRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun openingAndBrowserFailureNeverMarkTheSurveyCompleted() = runTest(dispatcher) {
        val repository = Repository()
        val model = model(repository)
        runCurrent()
        assertEquals("https://forms.yandex.ru/test", model.prepareLink())
        model.linkFailed()
        assertEquals(SurveyError.OPEN, model.uiState.value.error)
        assertEquals(false, model.uiState.value.completed)
        assertEquals(0, repository.completions)
        assertFalse(model.uiState.value.busy)
    }

    @Test fun failedCompletionCanBeRetriedAndOnlyASavedMarkStopsInvitations() = runTest(dispatcher) {
        val repository = Repository().apply { failSave = true }
        val model = model(repository)
        runCurrent()
        model.markCompleted()
        runCurrent()
        assertEquals(SurveyError.SAVE, model.uiState.value.error)
        assertEquals(false, model.uiState.value.completed)
        repository.failSave = false
        model.markCompleted()
        runCurrent()
        assertEquals(true, model.uiState.value.completed)
        assertNull(model.uiState.value.error)
        assertNull(model.uiState.value.invitation)
        model.prepareInvitation()
        runCurrent()
        assertEquals(0, repository.claims)
        model.markCompleted()
        assertEquals(2, repository.completions)
    }

    private fun model(repository: Repository) = SurveyViewModel(repository).also { store.put("survey", it) }

    private class Repository : SurveyRepository {
        val completed = MutableStateFlow(false)
        var completions = 0
        var claims = 0
        var failSave = false
        override fun observeCompleted() = completed
        override suspend fun markCompleted() {
            completions++
            if (failSave) throw IOException("Cannot persist")
            completed.value = true
        }
        override suspend fun formUrl() = "https://forms.yandex.ru/test"
        override suspend fun claimInvitation(): SurveyInvitation? { claims++; return null }
    }
}
