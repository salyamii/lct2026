package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.demo.DemoPreferences
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.HistorySourceGuard
import ru.nksk.lctapp.domain.timemachine.TimeMachineDemo
import ru.nksk.lctapp.domain.timemachine.TimeMachineStatus

@OptIn(ExperimentalCoroutinesApi::class)
class ReflectionDemoTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val catalog = bundledGameCatalog()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun juryExampleUsesRealPricesAndTheWholeQuizNeverWritesThePlayersWorldOrEvidence() = runTest(dispatcher) {
        val (repository, preferences, model) = fixture()
        model.uiState.first { !it.loading }
        val realBefore = repository.state.value
        val writesBefore = repository.worldWrites
        assertTrue(preferences.read().demoModeEnabled)

        model.openReflection(9)
        val opened = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.MOMENTS }
        assertTrue(opened.demoExample)
        assertEquals(realBefore, opened.realGame)
        val moment = opened.timeMachine!!.decisions.single()
        model.onAction(ReflectionAction.SelectMoment(moment.entryId))
        val memory = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.ALTERNATIVES }.memory!!
        model.onAction(ReflectionAction.Simulate(moment.entryId, moment.alternatives.single().id))
        val compared = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.COMPARISON }
        val simulation = checkNotNull(compared.simulation)
        val original = checkNotNull(compared.originalPath)
        val alternative = checkNotNull(compared.alternativePath)
        assertEquals(TimeMachineStatus.COMPLETE, simulation.status)
        val purchase = catalog.content.choices.single { it.id ==
            original.state.story.decisions.last().choiceId }
        val price = -purchase.moneyDelta
        assertTrue(price > 0)
        assertEquals(price, simulation.baseline!!.spent)
        assertEquals(0L, simulation.alternative!!.spent)
        assertEquals(memory.before.economy.availableBalance - price,
            original.state.economy.availableBalance)
        assertEquals(memory.before.economy.availableBalance, alternative.state.economy.availableBalance)
        assertEquals(35L, original.state.economy.plan.needs)
        assertNotEquals(original.state.ownedItems, alternative.state.ownedItems)

        model.onAction(ReflectionAction.StartQuiz)
        val quiz = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.QUIZ }.quiz!!
        model.onAction(ReflectionAction.ChronoscopeQuestionPresented(quiz.id))
        model.onAction(ReflectionAction.AnswerQuiz(quiz.id, "paid_anyway"))
        val wrong = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.RETRY }
        assertFalse(wrong.quizAnswer!!.correct)
        model.onAction(ReflectionAction.RetryQuiz)
        model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.QUIZ }
        model.onAction(ReflectionAction.AnswerQuiz(quiz.id, "spent_less"))
        val correct = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.EXPLANATION }
        val answer = checkNotNull(correct.quizAnswer)
        assertTrue(answer.correct)
        assertEquals(2, answer.attempt)
        model.onAction(ReflectionAction.ChronoscopeExplanationPresented(quiz.id, answer.submissionId!!))
        model.onAction(ReflectionAction.ReturnToPresent)
        val present = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.PRESENT }
        assertEquals(realBefore, present.realGame)
        assertEquals(realBefore, repository.state.value)
        assertEquals(writesBefore, repository.worldWrites)
        assertEquals(0, repository.factWrites)
        assertTrue(repository.entries.isEmpty())
    }

    @Test fun existingPaidMemoryWinsOverTheJuryExample() = runTest(dispatcher) {
        val (repository, _, model) = fixture()
        model.uiState.first { !it.loading }
        val example = checkNotNull(TimeMachineDemo.create(catalog, 9))
        repository.entries = example.history.map { it.copy(id = "real-purchase", runId = "real-run") }
        repository.state.value = example.game
        model.uiState.first { it.realGame == example.game }

        model.openReflection(9)
        val opened = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.MOMENTS }
        assertFalse(opened.demoExample)
        assertNull(opened.exampleGame)
        assertEquals("real-purchase", opened.timeMachine!!.decisions.single().entryId)
        model.onAction(ReflectionAction.SelectMoment("real-purchase"))
        val selected = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.ALTERNATIVES }
        assertEquals("real-run", selected.memory!!.runId)
        assertEquals(0, repository.factWrites)
    }

    @Test fun normalModeHasNoSyntheticMemoriesAndDisablingDemoDiscardsTheExample() = runTest(dispatcher) {
        val (repository, preferences, model) = fixture()
        model.uiState.first { !it.loading }
        model.openReflection(9)
        model.uiState.first { !it.busy && it.demoExample }
        val before = repository.state.value

        preferences.setDemoModeEnabled(false)
        val ordinary = model.uiState.first { !it.busy && !it.demoExample && it.chronoscopeStep == ChronoscopeStep.MOMENTS }
        assertTrue(ordinary.timeMachine!!.decisions.isEmpty())
        assertNull(ordinary.exampleGame)
        assertNull(ordinary.memory)
        assertEquals(before, repository.state.value)
        assertEquals(0, repository.factWrites)
    }

    @Test fun unreadableHistoryIsAnErrorInsteadOfBeingReplacedByADemo() = runTest(dispatcher) {
        val (repository, _, model) = fixture()
        model.uiState.first { !it.loading }
        repository.failHistory = true
        model.openReflection(9)
        val failure = model.uiState.first { !it.busy && it.error != null }
        assertFalse(failure.demoExample)
        assertNull(failure.exampleGame)
        assertNull(failure.chronoscopeStep)
    }

    @Test fun sandboxDoesNotInventMissingOffers() = runTest(dispatcher) {
        assertNull(TimeMachineDemo.create(catalog.copy(dailyEventPool = emptyList()), 9))
    }

    private fun fixture(): Triple<DemoRealRepository, DemoSetting, ReflectionViewModel> {
        val initial = createInitialGameState().copy(
            engine = EngineState(catalog.rules.id, 1, 9, DayPhase.FINISHED, 0, catalog.rules.fullEnergy,
                true, null, 100, emptyList(), emptyList()),
        )
        val repository = DemoRealRepository(initial)
        val preferences = DemoSetting()
        val session = GameSession(repository, object : StoryContentRepository {
            override suspend fun read(): StoryContent = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial, preferences)
        val model = ReflectionViewModel(session)
        store.put("demo-reflection", model)
        model.setActive(true)
        return Triple(repository, preferences, model)
    }
}

private class DemoSetting : DemoPreferencesRepository {
    private val state = MutableStateFlow(DemoPreferences(true))
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun setDemoModeEnabled(enabled: Boolean) { state.value = DemoPreferences(enabled) }
}

private class DemoRealRepository(initial: GameState) : GameRepository {
    val state = MutableStateFlow(initial)
    var entries: List<AuditEntry> = emptyList()
    var worldWrites = 0
    var factWrites = 0
    var failHistory = false
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        worldWrites++
        return transform(state.value).also { state.value = it }
    }
    override suspend fun readHistory(): List<AuditEntry> {
        if (failHistory) throw IOException("Unavailable real history")
        return entries
    }
    override suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard?) {
        factWrites++
        error("A jury example must never send evidence to the real run")
    }
}
