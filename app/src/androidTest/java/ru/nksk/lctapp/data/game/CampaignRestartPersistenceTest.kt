package ru.nksk.lctapp.data.game

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.BackendSyncStateEntity
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.onboarding.OnboardingDraft
import ru.nksk.lctapp.domain.onboarding.OnboardingStep
import ru.nksk.lctapp.domain.pet.PetCustomization
import ru.nksk.lctapp.domain.pet.PetFur
import ru.nksk.lctapp.domain.pet.PetTemperament
import ru.nksk.lctapp.domain.pet.toPetState

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

    @Test fun longRunArchivesRecordsSeparatelyAndResumesOnlyTheChosenNewCharacter() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        // Many distinct sizeable checkpoints cross multiple archive pages. No test helper
        // joins them into a complete encoded snapshot, including when checking preservation.
        val padding = "история🙂".repeat(256)
        repeat(384) { index -> games.update { it.copy(pet = it.pet.copy(name = "Шаг $index $padding")) } }
        val original = games.exportSnapshot()
        val request = original.rewindRequest("long-character-setup")
        val sourceRows = db.gameHistoryDao().read()
        val sourceBytes = sourceRows.sumOf { it.payload.length.toLong() }
        games.prepareCampaignRestart(request) { assertEquals(original.state, it) }

        assertNull(games.read())
        assertTrue(games.readHistory().isEmpty())
        assertEquals("CHARACTER", db.onboardingDraftDao().read()!!.step)
        val stored = checkNotNull(db.gameRunArchiveDao().forRestart(request.id))
        val header = Json.parseToJsonElement(stored.snapshotPayload).jsonObject
        assertTrue(header.getValue("history").jsonArray.isEmpty())
        assertEquals(original.historySequence, header.getValue("historySequence").jsonPrimitive.long)
        assertEquals(original.checksum, header.getValue("checksum").jsonPrimitive.content)
        assertTrue("The archive header must not grow with all past checkpoints",
            stored.snapshotPayload.length.toLong() * 20 < sourceBytes)
        val archiveRows = db.gameRunArchiveDao().readHistory(original.runId)
        assertEquals(sourceRows.map { it.sequence }, archiveRows.map { it.sequence })
        assertEquals(sourceRows.map { it.payload }, archiveRows.map { it.payload })
        assertEquals(archiveRows.filter { it.sequence > 127L }.take(64),
            db.gameRunArchiveDao().readHistoryPage(original.runId, 127L, 64))
        val archived = checkNotNull(games.archivedRun(original.runId))
        HistoryCodec.validate(archived)
        assertEquals(original, archived)
        assertEquals(original.historySequence.toInt(), games.archivedRuns().single().historyEntries)

        val reopened = RoomGameRepository(db)
        reopened.prepareCampaignRestart(request) { error("The committed archive must not be written again") }
        assertEquals(stored, db.gameRunArchiveDao().forRestart(request.id))
        assertTrue(runCatching { reopened.prepareCampaignRestart(request.copy(expectedHistorySequence = request.expectedHistorySequence + 1)) { } }
            .exceptionOrNull() is CampaignRestartConflictException)
        assertEquals(original, reopened.archivedRun(original.runId))

        val profile = PetCustomization("Новый путешественник", PetTemperament.Joyful, PetFur.Sand)
        val draft = OnboardingDraft(profile, OnboardingStep.Introduction, "BANDANA", "stargazing-tripod-v1")
        RoomOnboardingDraftRepository(db).save(draft)
        val session = GameSession(reopened, RoomStoryContentRepository(db), bundledGameCatalog(), createInitialGameState())
        session.prepare(profile.toPetState(draft.accessoryId), savingItemId = draft.savingItemId, beginInitialAllocation = true)
        val started = checkNotNull(reopened.readSnapshotHead())
        assertEquals(stored.nextRunId, started.runId)
        assertEquals(profile.toPetState("BANDANA"), started.state.pet)
        assertEquals(listOf("starter-bandana-v1"), started.state.ownedItems.map { it.itemId })
        assertTrue(started.state.story.decisions.isEmpty())
        assertEquals(1L, started.historySequence)
        assertNull(db.onboardingDraftDao().read())
        reopened.prepareCampaignRestart(request) { error("A late reply must preserve the chosen character") }
        assertEquals(started, reopened.readSnapshotHead())
        assertEquals(original, reopened.archivedRun(original.runId))
        assertEquals(archiveRows, db.gameRunArchiveDao().readHistory(original.runId))
    }

    @Test fun separateArchiveRowsPreserveCloudRestoreReceiptAndPredecessorBoundary() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val before = checkNotNull(games.readSnapshotHead())
        val source = WorldSnapshotCodec.create(before.runId, before.state, 40, "remote-generation")
        games.restoreCloudWorld(source, RestoreGuard(before.state.engine?.revision, before.historySequence), "before-archive")
        games.update { it.copy(pet = it.pet.copy(name = "После восстановления")) }
        val archivedWorld = checkNotNull(games.readCloudWorld())
        val receipt = checkNotNull(games.findCloudRestoreReceipt("before-archive"))
        val original = games.exportSnapshot()
        games.prepareCampaignRestart(original.rewindRequest("archive-restored-run")) { }

        assertNull(games.read())
        assertEquals(receipt, games.findCloudRestoreReceipt("before-archive"))
        val evidence = checkNotNull(games.readArchivedCloudEvidence(before.runId))
        assertEquals(archivedWorld, evidence.head)
        assertEquals(original.history, evidence.history)
        val draft = checkNotNull(db.onboardingDraftDao().read()).copy(name = "Новое прохождение", step = "INTRODUCTION")
        db.onboardingDraftDao().save(draft)
        games.initializeIfAbsent(createInitialGameState())
        val next = checkNotNull(games.readCloudWorld())
        assertEquals(listOf(CloudWorldAncestor(original.runId, receipt.generation, 41)), next.world.predecessors)
        assertEquals(receipt, games.findCloudRestoreReceipt("before-archive"))
        assertTrue(games.cloudContains(archivedWorld.world))
        assertEquals(original, games.archivedRun(original.runId))
    }

    @Test fun preparedRewindLeavesCharacterDraftAndCannotSilentlyInitializeTheGap() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        games.update { it.copy(pet = it.pet.copy(name = "Завершённый мир")) }
        val old = games.exportSnapshot()
        val request = old.rewindRequest("prepare-character")
        val transport = BackendSyncStateEntity("device", "https://example.test/", serverRevision = 8, gameRunId = old.runId)
        db.backendSyncDao().upsertState(transport)
        var validations = 0
        games.prepareCampaignRestart(request) { validations++; assertEquals(old.state, it) }
        assertEquals(1, validations)
        assertNull(games.read())
        assertNull(games.readSnapshotHead())
        assertNull(games.readCloudWorld())
        assertTrue(games.readHistory().isEmpty())
        val draft = checkNotNull(db.onboardingDraftDao().read())
        assertEquals("CHARACTER", draft.step)
        assertEquals("", draft.name)
        assertEquals(old, games.archivedRun(old.runId))
        assertEquals(transport, db.backendSyncDao().readState("device"))
        val pendingRun = checkNotNull(db.gameRunArchiveDao().forRestart(request.id)).nextRunId
        val reopened = RoomGameRepository(db)
        assertTrue(runCatching { reopened.initializeIfAbsent(createInitialGameState()) }.isFailure)
        assertNull(reopened.read())
        assertEquals(draft, db.onboardingDraftDao().read())
        assertEquals(pendingRun, db.gameRunArchiveDao().latestNextRunId())
        reopened.prepareCampaignRestart(request) { error("An already archived intent must not validate again") }
        assertEquals(1, reopened.archivedRuns().size)
        assertEquals(draft, db.onboardingDraftDao().read())
    }

    @Test fun preparedRewindRetryKeepsDraftEditsAndIntroductionInitializesItsReservedRunOnlyOnce() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val old = games.exportSnapshot()
        val request = old.rewindRequest("prepare-introduction")
        games.prepareCampaignRestart(request) { }
        val reservedRun = checkNotNull(db.gameRunArchiveDao().forRestart(request.id)).nextRunId
        val edited = checkNotNull(db.onboardingDraftDao().read()).copy(name = "Новый друг", step = "PROFILE")
        db.onboardingDraftDao().save(edited)
        games.prepareCampaignRestart(request) { error("Retry must keep the edited onboarding draft") }
        assertEquals(edited, db.onboardingDraftDao().read())
        assertTrue(runCatching { games.initializeIfAbsent(createInitialGameState()) }.isFailure)
        db.onboardingDraftDao().save(edited.copy(step = "INTRODUCTION"))
        val chosen = createInitialGameState().copy(pet = createInitialGameState().pet.copy(name = edited.name))
        assertEquals(chosen, games.initializeIfAbsent(chosen))
        val next = games.exportSnapshot()
        assertEquals(reservedRun, next.runId)
        assertNotEquals(old.runId, next.runId)
        assertEquals(chosen, next.state)
        assertEquals(listOf(AuditType.INITIALIZED), next.history.map { it.type })
        assertEquals(1L, next.historySequence)
        assertNull(db.onboardingDraftDao().read())
        games.update { it.copy(pet = it.pet.copy(name = "Уже играем")) }
        val progressed = games.exportSnapshot()
        games.prepareCampaignRestart(request) { error("Retry must not erase the initialized world") }
        assertEquals(progressed.state, games.initializeIfAbsent(createInitialGameState()))
        assertEquals(progressed, games.exportSnapshot())
        assertNull(db.onboardingDraftDao().read())
        assertEquals(old, games.archivedRun(old.runId))
    }

    @Test fun preparedRewindRejectsStaleOrFailedValidationWithoutArchiveOrDraftChanges() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val before = games.exportSnapshot()
        assertTrue(runCatching { games.prepareCampaignRestart(before.rewindRequest("denied")) {
            error("Campaign is not complete")
        } }.isFailure)
        assertEquals(before, games.exportSnapshot())
        assertTrue(games.archivedRuns().isEmpty())
        assertTrue(db.gameRunArchiveDao().readHistory(before.runId).isEmpty())
        assertNull(db.onboardingDraftDao().read())
        games.update { it.copy(pet = it.pet.copy(name = "Новее подтверждения")) }
        val changed = games.exportSnapshot()
        assertTrue(runCatching { games.prepareCampaignRestart(before.rewindRequest("stale-prepare")) { } }.isFailure)
        assertEquals(changed, games.exportSnapshot())
        assertTrue(games.archivedRuns().isEmpty())
        assertNull(db.onboardingDraftDao().read())
    }

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
        assertTrue(db.gameRunArchiveDao().readHistory(before.runId).isEmpty())
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

    @Test fun exportsKeepOneCompleteWorldWhileAnotherRepositoryUpdatesAndRestarts() = runBlocking {
        games.initializeIfAbsent(createInitialGameState())
        val original = games.exportSnapshot()
        val start = CompletableDeferred<Unit>()
        val exporters = List(2) {
            async(Dispatchers.Default) {
                val reader = RoomGameRepository(db)
                start.await()
                List(8) { reader.exportSnapshot().also { yield() } }
            }
        }
        val writer = async(Dispatchers.Default) {
            val other = RoomGameRepository(db)
            start.await()
            repeat(3) { cycle ->
                other.update { it.copy(pet = it.pet.copy(name = "Мир $cycle")) }
                val beforeRestart = other.exportSnapshot()
                other.restartCampaign(beforeRestart.rewindRequest("concurrent-$cycle")) { _, initial ->
                    checkNotNull(initial)
                }
                yield()
            }
        }
        start.complete(Unit)
        writer.await()
        val snapshots = exporters.flatMap { it.await() }
        // Validate after all writes: export decoding happens outside the transaction, so neither
        // its current checkpoint nor its ordered archive chain may come from a later world.
        snapshots.forEach { snapshot ->
            HistoryCodec.validate(snapshot)
            assertEquals(snapshot.archivedRuns.lastOrNull()?.nextRunId ?: original.runId, snapshot.runId)
            snapshot.archivedRuns.zipWithNext().forEach { (previous, next) ->
                assertEquals(previous.nextRunId, next.snapshot.runId)
            }
        }
        val final = games.exportSnapshot()
        HistoryCodec.validate(final)
        assertEquals(3, final.archivedRuns.size)
        assertEquals(original.history.first(), final.archivedRuns.first().snapshot.history.first())
    }

    private fun GameSnapshot.rewindRequest(id: String) = CampaignRestartRequest(id, runId, state.engine?.revision, historySequence)
}
