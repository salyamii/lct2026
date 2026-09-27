package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.finance.FinancialAnswerOption
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.finance.FinancialTraining

/** Compiled with androidTest sources; run only when device testing is explicitly authorized. */
@RunWith(AndroidJUnit4::class)
class FinancialHistoryPersistenceTest {
    private lateinit var db: GameDatabase
    private lateinit var games: RoomGameRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder<GameDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(BundledSQLiteDriver()).build()
        games = RoomGameRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun trainingSeriesPreservesItsQueueAndRetryAcrossRepositoryReadAndSnapshotRestore() = runBlocking {
        val first = FinancialQuestion("training-first", FinancialQuestionKind.SAVING_PRACTICE,
            "Как сберечь монеты?", listOf(FinancialAnswerOption("save", "Понемногу откладывать"),
                FinancialAnswerOption("circle", "Взять и вернуть те же монеты")), "save", "Регулярно откладываем часть заработка.",
            sourceActionIds = listOf("training-period"))
        val training = FinancialTraining.start(first, "training-series")
        val initial = createInitialGameState()
        games.initializeIfAbsent(initial.copy(financial = initial.financial.copy(practice = training)))
        val originalPayload = db.financialProgressDao().practice("current")!!.taskPayload
        val wrong = training.copy(answeredOptionId = "circle", attempts = 1)
        games.update { it.copy(financial = it.financial.copy(practice = wrong)) }
        games = RoomGameRepository(db)
        assertEquals(wrong, games.read()!!.financial.practice)
        assertEquals(originalPayload, db.financialProgressDao().practice("current")!!.taskPayload)

        val correct = wrong.copy(answeredOptionId = "save", attempts = 2)
        games.update { it.copy(financial = it.financial.copy(practice = correct)) }
        val second = FinancialTraining.advance(correct)
        games.update { it.copy(financial = it.financial.copy(practice = second)) }
        val snapshot = games.exportSnapshot()
        assertEquals(2, snapshot.state.financial.practice!!.series!!.questionNumber)
        assertEquals(2, snapshot.state.financial.practice!!.series!!.remainingQuestions.size)
        games.update { it.copy(pet = it.pet.copy(name = "Временное имя")) }
        games.restoreSnapshot(snapshot, RestoreGuard(games.read()!!.engine?.revision, games.readHistory().last().sequence))
        assertEquals(second, games.read()!!.financial.practice)
        assertEquals(initial.economy, games.read()!!.economy)
        HistoryCodec.validate(games.exportSnapshot())
    }

    @Test fun commandRetryIsIdempotentAndConflictCannotRewriteHistory() = runBlocking {
        val initial = games.initializeIfAbsent(createInitialGameState())
        val request = EngineRequest("tap", null, EngineCommand.RenamePet("Лис", initial.pet.name))
        val saved = games.commit(request) { it.copy(pet = it.pet.copy(name = "Лис")) }
        games.commit(request) { error("Duplicate must not execute") }
        assertEquals(saved, games.read())
        assertEquals(2, games.readHistory().size)
        expectFailure { games.commit(request.copy(command = EngineCommand.RenamePet("Другой", initial.pet.name))) { it } }
        assertEquals(2, games.readHistory().size)
    }

    @Test fun invalidChildRollsBackSaveFactsAuditAndOutbox() = runBlocking {
        val before = games.initializeIfAbsent(createInitialGameState())
        val request = EngineRequest("broken", null, EngineCommand.RenamePet("Лис", before.pet.name))
        expectFailure { games.commit(request, facts = { _, _, run, seq ->
            listOf(AnalyticsFact("broken-fact", run, "episode", request.id, seq, FactDetail.Interaction("rename")))
        }) { it.copy(ownedItems = listOf(OwnedItem("bad", "not-in-catalog"))) } }
        assertEquals(before, games.read())
        assertEquals(1, games.readHistory().size)
        assertEquals(1, games.pendingOutbox().size)
    }

    @Test fun observationalFactsHaveNoSnapshotsAndDeduplicateAcrossRetries() = runBlocking {
        val before = games.initializeIfAbsent(createInitialGameState())
        val fact = AnalyticsFact("shown-once", "current", "episode", "presentation", 0, FactDetail.Interaction("shown"))
        games.recordFacts(listOf(fact)); games.recordFacts(listOf(fact))
        assertEquals(before, games.read())
        val entry = games.readHistory().last()
        assertEquals(AuditType.FACTS, entry.type)
        assertNull(entry.before); assertNull(entry.after)
        assertEquals(2, games.readHistory().size)
        games.acknowledgeOutbox(setOf(entry.id))
        assertEquals(1, games.pendingOutbox().size)
        assertEquals(2, games.readHistory().size)
    }

    @Test fun guardedLearningFactsAllowLaterProgressButRejectAReplacedSourceInsideTheWrite() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val source = games.exportSnapshot()
        val guard = HistorySourceGuard.capture(source.runId, 1, source.historySequence, source.history)
        games.update { it.copy(pet = it.pet.copy(name = "Новое имя")) }
        val fact = AnalyticsFact("guarded-fact", source.runId, "episode", "presentation", 0, FactDetail.Interaction("shown"))
        games.recordFacts(listOf(fact), guard)
        assertTrue(games.readHistory().flatMap { it.facts }.any { it.eventId == fact.eventId })

        val replacement = HistoryCodec.snapshot(source.runId, source.state, source.history.map {
            it.copy(context = DecisionContext(presentationId = "replacement-source"))
        })
        games.restoreSnapshot(replacement, RestoreGuard(games.read()!!.engine?.revision, games.readHistory().last().sequence))
        val gameBefore = games.read()
        val historyBefore = games.readHistory()
        val outboxBefore = games.pendingOutbox()
        expectFailure { games.recordFacts(listOf(fact.copy(eventId = "stale-after-restore")), guard) }
        assertEquals(gameBefore, games.read())
        assertEquals(historyBefore, games.readHistory())
        assertEquals(outboxBefore, games.pendingOutbox())
    }

    @Test fun blockedIntentIsDurableIdempotentAndNeverChangesTheSave() = runBlocking {
        val before = games.initializeIfAbsent(createInitialGameState())
        val request = EngineRequest("blocked-tap", null, EngineCommand.Feed("basic"),
            DecisionContext(presentationId = "shown", informationPresented = true, complete = true))
        games.recordRejected(request, "StaleRevision", "catalog")
        games.recordRejected(request, "StaleRevision", "catalog")
        assertEquals(before, games.read())
        val entry = games.readHistory().last()
        assertEquals(2, games.readHistory().size)
        assertEquals(AuditType.REJECTED, entry.type)
        assertEquals(request, entry.request)
        assertNull(entry.before); assertNull(entry.after)
        assertTrue(entry.operations.isEmpty())
        assertFalse(entry.facts.single().context.complete)
        assertEquals("blocked-tap:blocked:StaleRevision", entry.facts.single().eventId)
        expectFailure { games.recordRejected(request.copy(command = EngineCommand.Feed("other")), "StaleRevision", "catalog") }
        HistoryCodec.validate(games.exportSnapshot())
    }

    @Test fun technicalWriteChangesRestoreGuardEvenWithoutEngineRevision() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val snapshot = games.exportSnapshot()
        games.update { it.copy(pet = it.pet.copy(name = "Новое имя")) }
        expectFailure { games.restoreSnapshot(snapshot, RestoreGuard(snapshot.state.engine?.revision, snapshot.historySequence)) }
        assertEquals("Новое имя", games.read()!!.pet.name)
        games.restoreSnapshot(snapshot, RestoreGuard(games.read()!!.engine?.revision, games.readHistory().last().sequence))
        assertEquals(snapshot.state, games.read())
        assertEquals(AuditType.RESTORED, games.readHistory().last().type)
        HistoryCodec.validate(games.exportSnapshot())
    }

    @Test fun oldOpenQuestionWithoutReviewEvidenceSurvivesWritesAndAnswersWithoutReencodingItsTemplate() = runBlocking {
        val question = FinancialQuestion("legacy-review", FinancialQuestionKind.PLAN_REVIEW,
            "Что изменилось в плане?", listOf(FinancialAnswerOption("expense", "Появилась трата"),
                FinancialAnswerOption("income", "Появился доход")), "expense", "Расход нужно учитывать в плане.")
        val initial = createInitialGameState()
        games.initializeIfAbsent(initial.copy(financial = initial.financial.copy(practice = question)))
        val finance = db.financialProgressDao()
        val installed = finance.practice("current")!!
        val oldPayload = JsonObject(Json.parseToJsonElement(installed.taskPayload).jsonObject - "reviewEvidence").toString()
        assertFalse(oldPayload.contains("reviewEvidence"))
        finance.writePractice(installed.copy(taskPayload = oldPayload))
        assertEquals(question, games.read()!!.financial.practice)

        games.update { it.copy(pet = it.pet.copy(name = "Лис")) }
        assertEquals(oldPayload, finance.practice("current")!!.taskPayload)
        val answered = question.copy(answeredOptionId = "expense", usedHint = true, attempts = 1)
        games.update { it.copy(financial = it.financial.copy(practice = answered)) }
        val stored = finance.practice("current")!!
        assertEquals(oldPayload, stored.taskPayload)
        assertEquals("expense", stored.answeredOptionId)
        assertTrue(stored.usedHint)
        assertEquals(1, stored.attempts)
        assertEquals(answered, games.read()!!.financial.practice)
        assertEquals(question, HistoryCodec.decodeQuestion(stored.taskPayload))
        HistoryCodec.validate(games.exportSnapshot())

        val beforeConflict = games.read()
        val historySize = games.readHistory().size
        expectFailure { games.update { it.copy(financial = it.financial.copy(
            practice = answered.copy(correctAnswerId = "income"))) } }
        assertEquals(beforeConflict, games.read())
        assertEquals(historySize, games.readHistory().size)
        assertEquals(oldPayload, finance.practice("current")!!.taskPayload)
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        val result = runCatching { block() }
        assertTrue("Expected validation or constraint failure", result.isFailure)
    }
}
