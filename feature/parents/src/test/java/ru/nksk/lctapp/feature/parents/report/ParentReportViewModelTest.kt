package ru.nksk.lctapp.feature.parents.report

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParentReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun reportObservationsOnlyRunWhileAnUnlockedScreenCollects() = runTest(dispatcher) {
        var activeObservers = 0
        val repository = object : ParentReportRepository {
            override suspend fun refreshAssessments() = Unit
            override fun observeReport() = flow {
                activeObservers++
                try {
                    emit(ParentReport())
                    awaitCancellation()
                } finally { activeObservers-- }
            }
        }
        val model = ParentReportViewModel(repository)
        runCurrent()
        assertEquals(0, activeObservers)

        val collection = backgroundScope.launch { model.uiState.collect {} }
        runCurrent()
        assertEquals(1, activeObservers)
        assertTrue(model.uiState.value is ParentReportUiState.Ready)

        collection.cancel()
        runCurrent()
        assertEquals(0, activeObservers)
        assertEquals(ParentReportUiState.Loading, model.uiState.value)
    }

    @Test fun aReadFailureIsVisibleAndExplicitRetryReadsFreshData() = runTest(dispatcher) {
        var reads = 0
        val report = ParentReport(pet = ParentPet("Лис", 20, 30))
        val repository = object : ParentReportRepository {
            override suspend fun refreshAssessments() = Unit
            override fun observeReport() = flow {
                reads++
                if (reads == 1) throw IOException("database read failed")
                emit(report)
            }
        }
        val model = ParentReportViewModel(repository)
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()
        assertEquals(ParentReportUiState.Error, model.uiState.value)

        model.retry()
        runCurrent()

        assertEquals(ParentReportUiState.Ready(report), model.uiState.value)
        assertEquals(2, reads)
    }

    @Test fun refreshKeepsLocalReportVisibleAndDoesNotDuplicateAnActiveRequest() = runTest(dispatcher) {
        var requests = 0
        var activeRequests = 0
        val report = ParentReport(pet = ParentPet("Лис", 20, 30))
        val repository = object : ParentReportRepository {
            override fun observeReport() = flow { emit(report); awaitCancellation() }
            override suspend fun refreshAssessments() {
                requests++
                activeRequests++
                try { awaitCancellation() } finally { activeRequests-- }
            }
        }
        val model = ParentReportViewModel(repository)
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()
        assertEquals(0, requests)

        val refresh = backgroundScope.launch { model.refreshAssessments() }
        runCurrent()
        backgroundScope.launch { model.refreshAssessments() }
        runCurrent()
        assertEquals(1, requests)
        assertEquals(1, activeRequests)
        assertEquals(ParentReportUiState.Ready(report), model.uiState.value)

        refresh.cancel()
        runCurrent()
        assertEquals(0, activeRequests)
        backgroundScope.launch { model.refreshAssessments() }
        runCurrent()
        assertEquals(2, requests)
    }
}
