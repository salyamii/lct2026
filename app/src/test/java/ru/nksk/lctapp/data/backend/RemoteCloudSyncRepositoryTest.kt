package ru.nksk.lctapp.data.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.BackendSyncStateEntity
import ru.nksk.lctapp.data.game.local.PendingBackendRequestEntity
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.backend.AckParentRewardsRequest
import ru.nksk.lctapp.domain.backend.AckParentRewardsResponse
import ru.nksk.lctapp.domain.backend.AnalyticsUploadRequest
import ru.nksk.lctapp.domain.backend.AnalyticsUploadResponse
import ru.nksk.lctapp.domain.backend.CloudSyncPhase
import ru.nksk.lctapp.domain.backend.CloudSyncResult
import ru.nksk.lctapp.domain.backend.ParentCoinAllocation
import ru.nksk.lctapp.domain.backend.ParentRewardApplication
import ru.nksk.lctapp.domain.backend.ParentRewardDto
import ru.nksk.lctapp.domain.backend.ParentRewardPayload
import ru.nksk.lctapp.domain.backend.ParentRewardPolicy
import ru.nksk.lctapp.domain.backend.ParentRewardReceiptDto
import ru.nksk.lctapp.domain.backend.ParentRewardsResponse
import ru.nksk.lctapp.domain.backend.PullParentRewardsRequest
import ru.nksk.lctapp.domain.backend.RegisterProfileRequest
import ru.nksk.lctapp.domain.backend.RegisterProfileResponse
import ru.nksk.lctapp.domain.backend.SkillAssessmentDto
import ru.nksk.lctapp.domain.backend.SkillAssessmentsRequest
import ru.nksk.lctapp.domain.backend.SkillAssessmentsResponse
import ru.nksk.lctapp.domain.backend.SkillStatus
import ru.nksk.lctapp.domain.backend.SkillSyncPhase
import ru.nksk.lctapp.domain.backend.SnapshotDownloadRequest
import ru.nksk.lctapp.domain.backend.SnapshotDownloadResponse
import ru.nksk.lctapp.domain.backend.SnapshotUploadRequest
import ru.nksk.lctapp.domain.backend.SnapshotUploadResponse
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineRules
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.ArchivedGameRun
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.history.RestoreGuard
import ru.nksk.lctapp.domain.history.localGeneration
import java.io.IOException

class RemoteCloudSyncRepositoryTest {
    @Test fun unchangedPollingExportsOnceAndChecksOnlyTheHistoryHead() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())
        fixture.games.snapshotExports = 0
        fixture.games.historyReads = 0

        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())

        assertEquals(1, fixture.games.snapshotExports)
        assertEquals(0, fixture.games.historyReads)
        assertEquals(1, fixture.api.snapshotUploads.size)
    }

    @Test fun unchangedParentRefreshExportsOnceWithoutReadingTheWholeHistoryAgain() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        fixture.games.snapshotExports = 0
        fixture.games.historyReads = 0

        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())

        assertEquals(1, fixture.games.snapshotExports)
        assertEquals(0, fixture.games.historyReads)
        assertEquals(SkillSyncPhase.IDLE, fixture.repository.state.value.skillsPhase)
    }

    @Test fun progressDuringRegistrationRefreshesTheSnapshotBeforeUploading() = runTest {
        val fixture = Fixture()
        fixture.api.beforeRegister = { fixture.games.rename("Имя после регистрации") }

        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())

        val uploaded = HistoryCodec.decodeSnapshot(fixture.api.snapshotUploads.single().second.snapshotJson)
        assertEquals(fixture.games.read(), uploaded.state)
        assertEquals(2L, uploaded.historySequence)
        assertEquals(uploaded.historySequence, fixture.api.analyticsUploads.single().second.throughHistorySequence)
        assertEquals(2, fixture.games.snapshotExports)
        assertEquals(0, fixture.games.historyReads)
    }

    @Test fun parentRefreshRegistersUploadsEvidenceAndQueriesSkillsWithoutTouchingWorldOrRewards() = runTest {
        val fixture = Fixture()
        val before = fixture.games.exportSnapshot()

        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())

        assertEquals(listOf("register", "post-analytics", "get-skills"), fixture.api.calls)
        assertEquals(before, fixture.games.exportSnapshot())
        assertTrue(fixture.games.acknowledged.isEmpty())
        assertEquals(SkillSyncPhase.IDLE, fixture.repository.state.value.skillsPhase)
        assertEquals(before.historySequence, fixture.repository.state.value.skills?.basedOnHistorySequence)
        assertEquals(before.localGeneration(), fixture.repository.state.value.skillsGeneration)
        assertNotNull(fixture.store.read(ProfileId)?.skillsPayload)
    }

    @Test fun parentRefreshLoadsCurrentPersistedAssessmentsBeforeAnOfflineRequestInANewProcess() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        val cached = fixture.repository.state.value.skills
        val recreated = fixture.newRepository()
        fixture.api.beforeSkills = {
            assertEquals(cached, recreated.state.value.skills)
            throw IOException("Offline")
        }

        assertEquals(CloudSyncResult.RETRY, recreated.refreshSkills())

        assertEquals(cached, recreated.state.value.skills)
        assertEquals(fixture.games.exportSnapshot().localGeneration(), recreated.state.value.skillsGeneration)
        assertEquals(SkillSyncPhase.OFFLINE, recreated.state.value.skillsPhase)
    }

    @Test fun parentRefreshReplaysFrozenEvidenceThenUploadsItsNewTailBeforeQueryingSkills() = runTest {
        val fixture = Fixture()
        fixture.api.loseNextAnalyticsResponse = true
        assertEquals(CloudSyncResult.RETRY, fixture.repository.refreshSkills())
        val frozen = fixture.api.analyticsUploads.single()
        fixture.games.rename("Более свежая история")
        fixture.api.calls.clear()

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().refreshSkills())

        assertEquals(listOf("post-analytics", "post-analytics", "get-skills"), fixture.api.calls)
        assertEquals(frozen, fixture.api.analyticsUploads[1])
        assertEquals(fixture.games.exportSnapshot().historySequence,
            fixture.api.analyticsUploads.last().second.throughHistorySequence)
        assertTrue(fixture.store.pendingRequests.isEmpty())
    }

    @Test fun staleRestoredAnalyticsResumesWithANewBatchAfterProgressAndProcessRecreation() = runTest {
        val fixture = restoredBehindAcceptedAnalytics()
        val restored = fixture.games.exportSnapshot()
        assertEquals(11L, restored.historySequence)

        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.synchronize())
        val rejected = checkNotNull(fixture.store.pending(ProfileId, "analytics"))
        val rejectedBody = BackendJson.decodeFromString<AnalyticsUploadRequest>(rejected.payload)
        assertEquals(11L, rejectedBody.throughHistorySequence)
        assertEquals(0L, fixture.store.read(ProfileId)?.lastAnalyticsSequence)
        assertTrue(fixture.games.acknowledged.isEmpty())
        assertEquals(restored, fixture.games.exportSnapshot())

        // No new boundary means no new key and no acknowledgement of the rejected facts.
        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.newRepository().refreshSkills())
        assertEquals(rejected, fixture.store.pending(ProfileId, "analytics"))
        repeat(10) { fixture.games.rename("После восстановления $it") }
        val current = fixture.games.exportSnapshot()
        assertEquals(21L, current.historySequence)

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())

        val replacement = fixture.api.analyticsUploads.last()
        assertNotEquals(rejected.requestId, replacement.first)
        assertEquals(21L, replacement.second.throughHistorySequence)
        assertEquals(rejectedBody.gameRunId, replacement.second.gameRunId)
        assertTrue(fixture.api.analyticsUploads.filter { it.first == rejected.requestId }
            .all { it.second == rejectedBody })
        assertEquals(21L, fixture.store.read(ProfileId)?.lastAnalyticsSequence)
        assertEquals(current.history.map { it.id }.toSet(), fixture.games.acknowledged)
        assertEquals(current, fixture.games.exportSnapshot())
        assertNull(fixture.store.pending(ProfileId, "analytics"))
    }

    @Test fun anotherStaleResponseRetainsTheNewBatchWithoutLoopingOrInventingTheServerBoundary() = runTest {
        val fixture = restoredBehindAcceptedAnalytics()
        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.refreshSkills())
        val rejected = checkNotNull(fixture.store.pending(ProfileId, "analytics"))
        fixture.games.rename("Граница 12 всё ещё ниже серверной 20")
        val before = fixture.api.analyticsUploads.size

        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.newRepository().refreshSkills())

        assertEquals(2, fixture.api.analyticsUploads.size - before)
        val replacement = checkNotNull(fixture.store.pending(ProfileId, "analytics"))
        assertNotEquals(rejected.requestId, replacement.requestId)
        assertEquals(12L, BackendJson.decodeFromString<AnalyticsUploadRequest>(replacement.payload).throughHistorySequence)
        assertEquals(0L, fixture.store.read(ProfileId)?.lastAnalyticsSequence)
        assertTrue(fixture.games.acknowledged.isEmpty())
        val after = fixture.api.analyticsUploads.size
        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.newRepository().refreshSkills())
        assertEquals(1, fixture.api.analyticsUploads.size - after)
        assertEquals(replacement, fixture.store.pending(ProfileId, "analytics"))
    }

    @Test fun lostReplacementResponseKeepsItsNewKeyAndBodyForTheNextProcess() = runTest {
        val fixture = restoredBehindAcceptedAnalytics()
        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.refreshSkills())
        val rejected = checkNotNull(fixture.store.pending(ProfileId, "analytics"))
        repeat(10) { fixture.games.rename("После старого batch $it") }
        fixture.api.loseNextAnalyticsResponse = true

        assertEquals(CloudSyncResult.RETRY, fixture.newRepository().refreshSkills())

        val uncertain = checkNotNull(fixture.store.pending(ProfileId, "analytics"))
        assertNotEquals(rejected.requestId, uncertain.requestId)
        assertEquals(21L, BackendJson.decodeFromString<AnalyticsUploadRequest>(uncertain.payload).throughHistorySequence)
        assertEquals(0L, fixture.store.read(ProfileId)?.lastAnalyticsSequence)
        assertTrue(fixture.games.acknowledged.isEmpty())
        val sent = fixture.api.analyticsUploads.last()

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().refreshSkills())

        assertEquals(sent, fixture.api.analyticsUploads.last())
        assertEquals(21L, fixture.store.read(ProfileId)?.lastAnalyticsSequence)
        assertNull(fixture.store.pending(ProfileId, "analytics"))
        assertTrue(fixture.games.acknowledged.isEmpty())
    }

    @Test fun analyticsConflictsWithoutAnExactStaleCodeNeverReplaceOrAcknowledgeThePendingBatch() = runTest {
        val failures = listOf(
            409 to "{\"code\":\"FACT_CONFLICT\"}",
            409 to "{\"code\":\"IDEMPOTENCY_CONFLICT\"}",
            409 to "{\"code\":\"GAME_RUN_CONFLICT\"}",
            409 to "{}",
            409 to "{\"message\":\"STALE_ANALYTICS\"}",
            409 to "{\"code\":null}",
            409 to "not json",
            500 to "{\"code\":\"STALE_ANALYTICS\"}",
        )
        for ((status, body) in failures) {
            val fixture = Fixture()
            fixture.api.beforeAnalytics = { throw httpFailure(status, body) }
            val expected = if (status == 500) CloudSyncResult.RETRY else CloudSyncResult.NEEDS_ATTENTION
            assertEquals(expected, fixture.repository.refreshSkills())
            val frozen = fixture.store.pending(ProfileId, "analytics")
            fixture.games.rename("Новая граница не разрешает забыть конфликт")

            assertEquals(expected, fixture.newRepository().refreshSkills())

            assertEquals(frozen, fixture.store.pending(ProfileId, "analytics"))
            assertEquals(2, fixture.api.analyticsUploads.size)
            assertEquals(fixture.api.analyticsUploads.first(), fixture.api.analyticsUploads.last())
            assertEquals(0L, fixture.store.read(ProfileId)?.lastAnalyticsSequence)
            assertTrue(fixture.games.acknowledged.isEmpty())
        }
    }

    @Test fun staleReplyCannotSupersedeAPendingBatchAfterAnotherRestoreGenerationAppears() = runTest {
        val fixture = Fixture()
        val original = fixture.games.exportSnapshot()
        fixture.api.beforeAnalytics = { throw httpFailure(409, "{\"code\":\"STALE_ANALYTICS\"}") }
        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.refreshSkills())
        val frozen = fixture.store.pending(ProfileId, "analytics")
        fixture.games.rename("Более новая история прежнего поколения")
        fixture.api.beforeAnalytics = {
            val current = fixture.games.exportSnapshot()
            fixture.games.restoreSnapshot(original, RestoreGuard(current.state.engine?.revision,
                current.historySequence, null))
            throw httpFailure(409, "{\"code\":\"STALE_ANALYTICS\"}")
        }

        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.refreshSkills())

        assertEquals(frozen, fixture.store.pending(ProfileId, "analytics"))
        assertEquals(2, fixture.api.analyticsUploads.size)
        assertEquals(0L, fixture.store.read(ProfileId)?.lastAnalyticsSequence)
        assertTrue(fixture.games.acknowledged.isEmpty())
    }

    @Test fun aStaleArchivedBatchCanBeReplacedByItsPreservedTailBeforeSynchronizingTheNextRun() = runTest {
        val fixture = restoredBehindAcceptedAnalytics()
        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.refreshSkills())
        val rejected = checkNotNull(fixture.store.pending(ProfileId, "analytics"))
        repeat(10) { fixture.games.rename("Действие перед перемоткой $it") }
        fixture.games.restart("next-run")
        val current = fixture.games.exportSnapshot()

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())

        val oldRunReplacement = fixture.api.analyticsUploads.last { it.second.gameRunId == "local-run" }
        assertEquals(21L, oldRunReplacement.second.throughHistorySequence)
        assertNotEquals(rejected.requestId, oldRunReplacement.first)
        assertEquals("next-run", fixture.api.analyticsUploads.last().second.gameRunId)
        assertEquals(current, fixture.games.exportSnapshot())
        assertTrue(fixture.store.pendingRequests.isEmpty())
    }

    @Test fun missingOrNotYetReadyAssessmentsRemainUnavailableWithoutErasingTheCache() = runTest {
        for (code in listOf(404, 409)) {
            val fixture = Fixture()
            assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
            val cached = fixture.repository.state.value.skills
            fixture.api.beforeSkills = { throw httpFailure(code) }

            assertEquals(CloudSyncResult.RETRY, fixture.repository.refreshSkills())

            assertEquals(SkillSyncPhase.UNAVAILABLE, fixture.repository.state.value.skillsPhase)
            assertEquals(cached, fixture.repository.state.value.skills)
            assertEquals(cached, BackendJson.decodeFromString<SkillAssessmentsResponse>(
                checkNotNull(fixture.store.read(ProfileId)?.skillsPayload)))
        }
    }

    @Test fun foreignFutureAndRegressingAssessmentsAreRejectedWithoutReplacingValidCache() = runTest {
        val fixture = Fixture()
        fixture.games.rename("Граница 2")
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        val cached = checkNotNull(fixture.repository.state.value.skills)
        for (invalid in listOf(cached.copy(gameRunId = "foreign-run"),
            cached.copy(basedOnHistorySequence = cached.basedOnHistorySequence + 1),
            cached.copy(basedOnHistorySequence = cached.basedOnHistorySequence - 1),
            cached.copy(schemaVersion = 2))) {
            fixture.api.assessments = invalid

            assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.refreshSkills())

            assertEquals(SkillSyncPhase.ERROR, fixture.repository.state.value.skillsPhase)
            assertEquals(cached, fixture.repository.state.value.skills)
            assertEquals(cached, BackendJson.decodeFromString<SkillAssessmentsResponse>(
                checkNotNull(fixture.store.read(ProfileId)?.skillsPayload)))
        }
    }

    @Test fun aFailedCacheCommitDoesNotPublishTheNewServerResponse() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        val cached = checkNotNull(fixture.repository.state.value.skills)
        val newer = cached.copy(skills = cached.skills.map { it.copy(status = SkillStatus.MASTERED) })
        fixture.api.assessments = newer
        fixture.api.beforeSkills = { fixture.store.failNextSave = true }

        assertEquals(CloudSyncResult.RETRY, fixture.repository.refreshSkills())

        assertEquals(cached, fixture.repository.state.value.skills)
        assertEquals(cached, BackendJson.decodeFromString<SkillAssessmentsResponse>(
            checkNotNull(fixture.store.read(ProfileId)?.skillsPayload)))
    }

    @Test fun previousRunCacheAndRepliesAreHiddenWhenTheWorldRestarts() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        fixture.api.beforeSkills = { fixture.games.restart("new-run") }

        assertEquals(CloudSyncResult.RETRY, fixture.repository.refreshSkills())

        assertNull(fixture.repository.state.value.skills)
        assertNull(fixture.repository.state.value.skillsGeneration)
        assertEquals(SkillSyncPhase.UNAVAILABLE, fixture.repository.state.value.skillsPhase)
    }

    @Test fun parentRefreshPreservesPreviousRunWorldAndRewardRetriesWithoutReplayingThem() = runTest {
        val fixture = Fixture()
        fixture.api.loseNextSnapshotResponse = true
        fixture.api.loseNextAnalyticsResponse = true
        assertEquals(CloudSyncResult.RETRY, fixture.repository.synchronize())
        val snapshotRetry = fixture.store.pending(ProfileId, "snapshot")
        val analyticsRetry = fixture.api.analyticsUploads.single()
        fixture.games.restart("new-run")
        val before = fixture.games.exportSnapshot()
        fixture.api.calls.clear()

        assertEquals(CloudSyncResult.RETRY, fixture.repository.refreshSkills())

        assertEquals(listOf("post-analytics"), fixture.api.calls)
        assertEquals(analyticsRetry, fixture.api.analyticsUploads.last())
        assertEquals(snapshotRetry, fixture.store.pending(ProfileId, "snapshot"))
        assertEquals(before, fixture.games.exportSnapshot())
        assertNull(fixture.repository.state.value.skills)
        assertEquals(SkillSyncPhase.UNAVAILABLE, fixture.repository.state.value.skillsPhase)
    }

    @Test fun persistedAssessmentsFromAnotherServerAreNeverDisplayedOrSentElsewhere() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        fixture.store.save(checkNotNull(fixture.store.read(ProfileId)).copy(backendUrl = "https://other.example.test/"))
        fixture.api.calls.clear()
        val recreated = fixture.newRepository()

        assertEquals(CloudSyncResult.NEEDS_ATTENTION, recreated.refreshSkills())

        assertNull(recreated.state.value.skills)
        assertTrue(fixture.api.calls.isEmpty())
    }

    @Test fun sameRunRestoreInvalidatesPreviousGenerationCacheEvenWhenItsSequenceStillFits() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        val saved = fixture.games.exportSnapshot()
        fixture.games.restoreSnapshot(saved, RestoreGuard(saved.state.engine?.revision,
            saved.historySequence, "sync-test-rules"))
        fixture.api.beforeSkills = { throw IOException("Offline") }
        val recreated = fixture.newRepository()

        assertEquals(CloudSyncResult.RETRY, recreated.refreshSkills())

        assertNull(recreated.state.value.skills)
        assertNull(recreated.state.value.skillsGeneration)
        assertNotEquals(saved.localGeneration(), fixture.games.exportSnapshot().localGeneration())
        assertEquals(saved.runId, fixture.games.exportSnapshot().runId)
    }

    @Test fun cancellationReleasesSkillLoadingWithoutLosingCachedDataOrPendingRequests() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        val cached = fixture.repository.state.value.skills
        fixture.api.beforeSkills = { throw CancellationException("Parent screen closed") }
        try {
            fixture.repository.refreshSkills()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }

        assertEquals(cached, fixture.repository.state.value.skills)
        assertEquals(SkillSyncPhase.IDLE, fixture.repository.state.value.skillsPhase)
        fixture.api.beforeSkills = {}
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
    }

    @Test fun fullSyncUsesTheSameAssessmentUnavailableStateAndKeepsTheCurrentCache() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())
        val cached = fixture.repository.state.value.skills
        fixture.api.beforeSkills = { throw httpFailure(409) }

        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())

        assertEquals(SkillSyncPhase.UNAVAILABLE, fixture.repository.state.value.skillsPhase)
        assertEquals(cached, fixture.repository.state.value.skills)
        assertEquals(CloudSyncPhase.IDLE, fixture.repository.state.value.phase)
    }

    @Test fun firstSyncRegistersAndBacksUpTheWorldBeforeUploadingEvidenceAndAcknowledgingOutbox() = runTest {
        val fixture = Fixture()
        val before = fixture.games.exportSnapshot()

        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())

        assertEquals(listOf("register", "get-snapshot", "put-snapshot", "get-rewards", "post-analytics", "get-skills"),
            fixture.api.calls)
        assertEquals(before, fixture.games.exportSnapshot())
        assertEquals(before, HistoryCodec.decodeSnapshot(fixture.api.snapshotUploads.single().second.snapshotJson))
        assertEquals(before.historySequence, fixture.api.analyticsUploads.single().second.throughHistorySequence)
        assertEquals(before.history.map { it.id }.toSet(), fixture.games.acknowledged)
        assertEquals(before.historySequence, fixture.repository.state.value.skills?.basedOnHistorySequence)
        assertTrue(fixture.store.pendingRequests.isEmpty())
        assertEquals(0, fixture.games.restoreAttempts)
    }

    @Test fun lostUploadResponsesRetryFrozenBodiesAndNeverReportSuccessWithAnUnsentAnalyticsTail() = runTest {
        val fixture = Fixture()
        fixture.api.loseNextSnapshotResponse = true
        fixture.api.loseNextAnalyticsResponse = true
        assertEquals(CloudSyncResult.RETRY, fixture.repository.synchronize())
        val frozenSnapshot = fixture.api.snapshotUploads.single()
        val frozenAnalytics = fixture.api.analyticsUploads.single()
        assertTrue(fixture.games.acknowledged.isEmpty())
        fixture.games.rename("Новое локальное имя")
        val newer = fixture.games.exportSnapshot()

        // New repository instance simulates process recreation; only the stores survive.
        val recreated = fixture.newRepository()
        val result = recreated.synchronize()
        assertEquals(frozenSnapshot, fixture.api.snapshotUploads[1])
        assertEquals(frozenAnalytics, fixture.api.analyticsUploads[1])
        val metadata = checkNotNull(fixture.store.read(ProfileId))
        assertTrue("SUCCESS must cover the newest captured evidence boundary",
            result != CloudSyncResult.SUCCESS || metadata.lastAnalyticsSequence >= newer.historySequence)
        if (result == CloudSyncResult.RETRY) assertEquals(CloudSyncResult.SUCCESS, recreated.synchronize())
        else assertEquals(CloudSyncResult.SUCCESS, result)

        assertEquals(newer, HistoryCodec.decodeSnapshot(checkNotNull(fixture.api.remote).snapshotJson))
        assertEquals(newer.historySequence, fixture.api.analyticsUploads.last().second.throughHistorySequence)
        assertEquals(newer.history.map { it.id }.toSet(), fixture.games.acknowledged)
        assertEquals(newer, fixture.games.exportSnapshot())
        assertTrue(fixture.store.pendingRequests.isEmpty())
    }

    @Test fun olderFrozenRequestsGainTheSavedDeviceIdWithoutChangingOperationIdsOrSnapshotBytes() = runTest {
        val fixture = Fixture()
        fixture.api.loseNextSnapshotResponse = true
        fixture.api.loseNextAnalyticsResponse = true
        assertEquals(CloudSyncResult.RETRY, fixture.repository.synchronize())
        val originalSnapshot = fixture.api.snapshotUploads.single()
        val originalAnalytics = fixture.api.analyticsUploads.single()
        val oldRequests = fixture.store.pendingRequests.mapValues { (_, request) ->
            val oldBody = JsonObject(BackendJson.parseToJsonElement(request.payload).jsonObject - "deviceId")
            request.copy(payload = BackendJson.encodeToString(oldBody))
        }
        fixture.store.pendingRequests.putAll(oldRequests)

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())

        assertEquals(originalSnapshot, fixture.api.snapshotUploads.last())
        assertEquals(originalAnalytics, fixture.api.analyticsUploads.last())
        assertTrue(fixture.store.pendingRequests.isEmpty())
        assertEquals(fixture.games.exportSnapshot(), HistoryCodec.decodeSnapshot(originalSnapshot.second.snapshotJson))
    }

    @Test fun cloudConflictDoesNotReplaceLocalPlayOrPreventACommittedGiftFromBeingAcknowledged() = runTest {
        val fixture = Fixture()
        val foreign = MemoryGames("other-run").apply { rename("Чужая облачная история") }.exportSnapshot()
        fixture.api.remote = foreign.downloadResponse()
        fixture.api.rewards = listOf(ParentRewardDto("gift-1", ProfileId, fixture.games.runId, 1,
            ParentRewardPayload.Coins(7), "2026-09-27T14:00:00Z"))
        fixture.api.beforeRewards = { fixture.games.rename("Последнее действие ребёнка") }
        fixture.api.beforeRewardAck = { request ->
            // The transport may acknowledge only a receipt that is already in the latest local world.
            val snapshot = fixture.games.exportSnapshot()
            assertEquals("Последнее действие ребёнка", snapshot.state.pet.name)
            assertEquals(107L, snapshot.state.economy.availableBalance)
            assertEquals(request.receipts, snapshot.history.mapNotNull { it.parentReward?.receipt })
        }

        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.synchronize())

        assertEquals(CloudSyncPhase.CONFLICT, fixture.repository.state.value.phase)
        assertEquals("Последнее действие ребёнка", fixture.games.read().pet.name)
        assertEquals(107L, fixture.games.read().economy.availableBalance)
        assertEquals(0, fixture.games.restoreAttempts)
        assertTrue(fixture.api.snapshotUploads.isEmpty())
        assertEquals(1, fixture.api.rewardAcks.size)
        assertEquals(foreign, HistoryCodec.decodeSnapshot(checkNotNull(fixture.api.remote).snapshotJson))
        assertTrue(fixture.games.acknowledged.isEmpty())
    }

    @Test fun restartRecoversLostPreviousRunRepliesBeforeUploadingTheNewRun() = runTest {
        val fixture = Fixture()
        fixture.api.loseNextSnapshotResponse = true
        fixture.api.loseNextAnalyticsResponse = true
        assertEquals(CloudSyncResult.RETRY, fixture.repository.synchronize())
        val oldSnapshotRequest = fixture.api.snapshotUploads.single()
        val oldAnalyticsRequest = fixture.api.analyticsUploads.single()
        val identity = fixture.identities.value
        fixture.games.rename("Конец прежней истории")
        fixture.games.restart("next-run")
        val restarted = fixture.games.exportSnapshot()

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())

        assertEquals(oldSnapshotRequest, fixture.api.snapshotUploads[1])
        assertEquals(oldAnalyticsRequest, fixture.api.analyticsUploads[1])
        assertEquals(1L, fixture.api.snapshotUploads.last().second.expectedServerRevision)
        assertEquals("next-run", fixture.api.snapshotUploads.last().second.gameRunId)
        assertEquals("next-run", fixture.api.analyticsUploads.last().second.gameRunId)
        assertEquals(identity, fixture.identities.value)
        assertEquals(1, fixture.api.calls.count { it == "register" })
        assertEquals(restarted, HistoryCodec.decodeSnapshot(checkNotNull(fixture.api.remote).snapshotJson))
        assertEquals(restarted, fixture.games.exportSnapshot())
        assertEquals(restarted.localGeneration(), fixture.store.read(ProfileId)?.localGeneration)
        assertTrue(fixture.store.pendingRequests.isEmpty())
    }

    @Test fun restartKeepsFrozenPreviousRequestsAndTransportGenerationUntilReplaySucceeds() = runTest {
        val fixture = Fixture()
        fixture.api.loseNextSnapshotResponse = true
        fixture.api.loseNextAnalyticsResponse = true
        assertEquals(CloudSyncResult.RETRY, fixture.repository.synchronize())
        val beforeRestart = checkNotNull(fixture.store.read(ProfileId))
        val pending = fixture.store.pendingRequests.toMap()
        fixture.games.restart("next-run")
        val restarted = fixture.games.exportSnapshot()
        fixture.api.failSnapshotRequests = true

        assertEquals(CloudSyncResult.RETRY, fixture.newRepository().synchronize())

        assertEquals(beforeRestart, fixture.store.read(ProfileId))
        assertEquals(pending, fixture.store.pendingRequests)
        assertEquals(restarted, fixture.games.exportSnapshot())
        fixture.api.failSnapshotRequests = false
        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())
        assertTrue(fixture.store.pendingRequests.isEmpty())
    }

    @Test fun restartAcknowledgesAnArchivedGiftWithItsOriginalRunWithoutGrantingItAgain() = runTest {
        val fixture = Fixture()
        fixture.api.rewards = listOf(ParentRewardDto("gift-1", ProfileId, fixture.games.runId, 1,
            ParentRewardPayload.Coins(7), "2026-09-27T14:00:00Z"))
        fixture.api.loseNextAckResponse = true
        assertEquals(CloudSyncResult.RETRY, fixture.repository.synchronize())
        val pendingAck = fixture.api.rewardAckRequests.single()
        assertEquals(107L, fixture.games.read().economy.availableBalance)
        fixture.games.restart("next-run")
        val restarted = fixture.games.exportSnapshot()

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())

        assertEquals(listOf(pendingAck, pendingAck), fixture.api.rewardAckRequests)
        assertEquals("local-run", pendingAck.second.gameRunId)
        assertEquals(100L, fixture.games.read().economy.availableBalance)
        assertTrue(fixture.games.readHistory().none { it.parentReward != null })
        assertEquals(1, restarted.archivedRuns.single().snapshot.history.count { it.parentReward != null })
        assertEquals(restarted, fixture.games.exportSnapshot())
        assertTrue(fixture.store.pendingRequests.isEmpty())
    }

    @Test fun lostTransportMetadataRecoversACloudPrefixFromAnExplicitRestartAncestor() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())
        fixture.games.rename("Позднее действие прежнего прохождения")
        fixture.games.restart("middle-run")
        fixture.games.rename("Второе прохождение")
        fixture.games.restart("next-run")
        val restarted = fixture.games.exportSnapshot()
        fixture.store.forgetMetadata()

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())

        assertEquals(1L, fixture.api.snapshotUploads.last().second.expectedServerRevision)
        assertEquals(restarted, HistoryCodec.decodeSnapshot(checkNotNull(fixture.api.remote).snapshotJson))
        assertEquals(restarted, fixture.games.exportSnapshot())
    }

    @Test fun anArchivedRunWithDivergentCloudHistoryStillConflicts() = runTest {
        val fixture = Fixture()
        fixture.games.rename("Локальный выбор")
        fixture.games.restart("next-run")
        val cloud = MemoryGames().apply { rename("Другой выбор") }.exportSnapshot()
        fixture.api.remote = cloud.downloadResponse()
        val restarted = fixture.games.exportSnapshot()

        assertEquals(CloudSyncResult.NEEDS_ATTENTION, fixture.repository.synchronize())

        assertEquals(CloudSyncPhase.CONFLICT, fixture.repository.state.value.phase)
        assertTrue(fixture.api.snapshotUploads.isEmpty())
        assertEquals(cloud, HistoryCodec.decodeSnapshot(checkNotNull(fixture.api.remote).snapshotJson))
        assertEquals(restarted, fixture.games.exportSnapshot())
    }

    @Test fun restartRecoversARestoreCommittedBeforeItsTransportBookkeeping() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())
        val cloud = MemoryGames("cloud-run").apply { rename("Облачная история") }.exportSnapshot()
        fixture.api.remote = cloud.downloadResponse()
        val preview = fixture.repository.prepareRestore()
        fixture.store.failNextRestoreReplacement = true
        try {
            fixture.repository.restore(preview.id)
            fail("Simulated process interruption must leave the durable restore intent")
        } catch (_: IOException) { }
        assertEquals(1, fixture.games.restoredWorlds)
        assertNotNull(fixture.store.pending(ProfileId, "restore"))
        fixture.games.restart("next-run")
        val restarted = fixture.games.exportSnapshot()

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())

        assertEquals(restarted, fixture.games.exportSnapshot())
        assertEquals(1, fixture.games.restoredWorlds)
        assertEquals(1L, fixture.api.snapshotUploads.last().second.expectedServerRevision)
        assertEquals(restarted.localGeneration(), fixture.store.read(ProfileId)?.localGeneration)
        assertTrue(fixture.store.pendingRequests.isEmpty())
    }

    @Test fun restartClearsAnUncommittedRestoreIntentWithoutReplayingIt() = runTest {
        val fixture = Fixture()
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())
        val preview = fixture.repository.prepareRestore()
        fixture.games.rename("Новее предпросмотра")
        try {
            fixture.repository.restore(preview.id)
            fail("A stale restore must fail")
        } catch (_: IllegalStateException) { }
        assertNotNull(fixture.store.pending(ProfileId, "restore"))
        fixture.games.restart("next-run")
        val restarted = fixture.games.exportSnapshot()

        assertEquals(CloudSyncResult.SUCCESS, fixture.newRepository().synchronize())

        assertEquals(0, fixture.games.restoredWorlds)
        assertEquals(restarted, fixture.games.exportSnapshot())
        assertTrue(fixture.store.pendingRequests.isEmpty())
    }

    @Test fun previewDoesNotMutateAndRejectedStaleRestoreDoesNotPoisonFutureSync() = runTest {
        val fixture = Fixture()
        val foreign = MemoryGames("cloud-run").apply { rename("Облачный лис") }.exportSnapshot()
        fixture.api.remote = foreign.downloadResponse()
        val localBefore = fixture.games.exportSnapshot()
        val preview = fixture.repository.prepareRestore()
        assertEquals("Облачный лис", preview.petName)
        assertEquals(localBefore, fixture.games.exportSnapshot())
        assertEquals(0, fixture.games.restoreAttempts)
        fixture.games.rename("Новее предпросмотра")
        val latest = fixture.games.exportSnapshot()

        try {
            fixture.repository.restore(preview.id)
            fail("A stale preview must not replace the newer local world")
        } catch (_: IllegalStateException) { }
        assertEquals(latest, fixture.games.exportSnapshot())
        assertEquals(0, fixture.games.restoredWorlds)
        fixture.api.calls.clear()
        fixture.repository.synchronize()
        assertTrue("An uncommitted restore intent must not block the independent reward lane",
            "get-rewards" in fixture.api.calls)
        assertNull(fixture.store.pending(ProfileId, "restore"))
        assertEquals(latest, fixture.games.exportSnapshot())

        val confirmed = fixture.repository.prepareRestore()
        fixture.repository.restore(confirmed.id)
        assertEquals(foreign.state, fixture.games.read())
        assertEquals(1, fixture.games.restoredWorlds)
        assertEquals(foreign.history, fixture.games.readHistory().dropLast(1))
        assertEquals(AuditType.RESTORED, fixture.games.readHistory().last().type)
        assertNull(fixture.store.pending(ProfileId, "restore"))
        assertEquals(fixture.games.exportSnapshot().localGeneration(), fixture.store.read(ProfileId)?.localGeneration)
    }

    /** Skill-only refresh deliberately leaves snapshot 10 behind accepted evidence 20. */
    private suspend fun restoredBehindAcceptedAnalytics(): Fixture {
        val fixture = Fixture()
        fixture.api.rejectRegressingAnalytics = true
        repeat(9) { fixture.games.rename("До облачной копии $it") }
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.synchronize())
        val cloud = checkNotNull(fixture.api.remote)
        assertEquals(10L, HistoryCodec.decodeSnapshot(cloud.snapshotJson).historySequence)
        repeat(10) { fixture.games.rename("Только аналитика $it") }
        assertEquals(CloudSyncResult.SUCCESS, fixture.repository.refreshSkills())
        assertEquals(20L, fixture.api.analyticsUploads.last().second.throughHistorySequence)
        assertEquals(cloud, fixture.api.remote)
        val preview = fixture.repository.prepareRestore()
        fixture.repository.restore(preview.id)
        return fixture
    }

    private class Fixture {
        val games = MemoryGames()
        val identities = MemoryIdentities()
        val api = MemoryApi()
        val store = MemorySyncStore()
        private val connection = BackendConnection("https://backend.example.test/") { api }
        private val content = object : StoryContentRepository {
            override suspend fun read() = StoryContent()
            override suspend fun install(content: StoryContent) = Unit
        }
        private val catalog = GameCatalog(StoryContent(), emptyMap(), emptyMap(),
            EngineRules("sync-test-rules", 5, 2, 1), emptyList(), "day", "intro", emptyList())
        fun newRepository(): RemoteCloudSyncRepository {
            val session = GameSession(games, content, catalog, games.value.value)
            return RemoteCloudSyncRepository(connection, identities,
                RemoteParentLinkRepository(identities, connection, games), games, session, store)
        }
        val repository = newRepository()
    }

    private class MemoryIdentities : ParentIdentityStore {
        var value = ParentIdentity(ProfileId, "ca6e9018-fbe4-4b24-91cc-757c333c85b4")
        override suspend fun getOrCreate() = value
        override suspend fun update(transform: (ParentIdentity) -> ParentIdentity) = transform(value).also { value = it }
    }

    private class MemorySyncStore : BackendSyncStore {
        private var metadata: BackendSyncStateEntity? = null
        var failNextRestoreReplacement = false
        var failNextSave = false
        val pendingRequests = mutableMapOf<Pair<String, String>, PendingBackendRequestEntity>()
        override suspend fun read(profileId: String) = metadata?.takeIf { it.profileId == profileId }
        override suspend fun save(state: BackendSyncStateEntity) {
            if (failNextSave) { failNextSave = false; throw IOException("Cache write failed") }
            metadata = state
        }
        override suspend fun pending(profileId: String, kind: String) = pendingRequests[profileId to kind]
        override suspend fun stage(request: PendingBackendRequestEntity) {
            val key = request.profileId to request.kind
            check(pendingRequests[key] == null || pendingRequests[key] == request)
            pendingRequests[key] = request
        }
        override suspend fun complete(state: BackendSyncStateEntity, kind: String, requestId: String) {
            check(pendingRequests[state.profileId to kind]?.requestId == requestId)
            metadata = state
            pendingRequests.remove(state.profileId to kind)
        }
        override suspend fun replaceAfterRestore(state: BackendSyncStateEntity) {
            if (failNextRestoreReplacement) { failNextRestoreReplacement = false; throw IOException("Interrupted bookkeeping") }
            metadata = state
            pendingRequests.keys.removeAll { it.first == state.profileId }
        }
        override suspend fun replaceAfterRestart(state: BackendSyncStateEntity) {
            check(pendingRequests.keys.none { it.first == state.profileId })
            metadata = state
        }
        fun forgetMetadata() { metadata = null }
        override suspend fun clear(profileId: String, kind: String, requestId: String) {
            if (pendingRequests[profileId to kind]?.requestId == requestId) pendingRequests.remove(profileId to kind)
        }
    }

    /** In-memory atomic checkpoint fixture. Reward arithmetic delegates to the existing domain policy. */
    private class MemoryGames(var runId: String = "local-run") : GameRepository {
        var snapshotExports = 0
        var historyReads = 0
        val value = MutableStateFlow(createInitialGameState().copy(
            economy = EconomyState(BudgetPlan(35, 20, 20, 25))))
        private val history = mutableListOf(AuditEntry("$runId:initial", 1, runId, AuditType.INITIALIZED, after = value.value))
        private val archives = mutableListOf<ArchivedGameRun>()
        val acknowledged = mutableSetOf<String>()
        var restoreAttempts = 0
        var restoredWorlds = 0
        override fun observe() = value
        override suspend fun read() = value.value
        override suspend fun initializeIfAbsent(initial: GameState) = value.value
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            val before = value.value
            val next = transform(before)
            if (next != before) {
                val sequence = history.last().sequence + 1
                history += AuditEntry("$runId:local:$sequence", sequence, runId, AuditType.TECHNICAL_UPDATE,
                    before = before, after = next)
                value.value = next
            }
            return next
        }
        suspend fun rename(name: String) { update { it.copy(pet = it.pet.copy(name = name)) } }
        fun restart(nextRunId: String) {
            val previous = HistoryCodec.snapshot(runId, value.value, history.toList())
            archives += ArchivedGameRun("restart:$nextRunId", nextRunId, previous)
            runId = nextRunId
            value.value = createInitialGameState().copy(economy = EconomyState(BudgetPlan(35, 20, 20, 25)))
            history.clear()
            history += AuditEntry("$runId:initial", 1, runId, AuditType.INITIALIZED, after = value.value)
        }
        override suspend fun readHistory(): List<AuditEntry> {
            historyReads++
            return history.toList()
        }
        override suspend fun latestHistoryId() = history.lastOrNull()?.id
        override suspend fun exportSnapshot(): GameSnapshot {
            snapshotExports++
            return currentSnapshot()
        }
        private fun currentSnapshot() = HistoryCodec.snapshot(runId, value.value, history.toList(), archives.toList())
        override suspend fun acknowledgeOutbox(ids: Set<String>) { acknowledged += ids }
        override suspend fun applyParentRewards(profileId: String, gameRunId: String, rewards: List<ParentRewardDto>,
            expectedRestoreGeneration: String): List<ParentRewardReceiptDto> {
            check(gameRunId == runId && currentSnapshot().localGeneration() == expectedRestoreGeneration)
            return rewards.map { reward ->
                history.firstOrNull { it.parentReward?.reward?.rewardId == reward.rewardId }?.parentReward?.receipt
                    ?: run {
                        val applicationId = "apply:${reward.rewardId}"
                        val before = value.value
                        val change = checkNotNull(ParentRewardPolicy(coinAllocation = ParentCoinAllocation.RESERVE)
                            .apply(before, reward, applicationId, emptySet()))
                        val sequence = history.last().sequence + 1
                        val receipt = ParentRewardReceiptDto(reward.rewardId, applicationId, "receipt:$applicationId",
                            sequence, change.outcome)
                        history += AuditEntry(receipt.historyEntryId, sequence, runId, AuditType.PARENT_REWARD,
                            before = before, after = change.state, operations = change.operations,
                            parentReward = ParentRewardApplication(reward, receipt))
                        value.value = change.state
                        receipt
                    }
            }
        }
        override suspend fun restoreSnapshot(snapshot: GameSnapshot, expected: RestoreGuard): GameState {
            restoreAttempts++
            check(expected.historySequence == history.last().sequence && expected.engineRevision == value.value.engine?.revision) {
                "World changed since preview"
            }
            HistoryCodec.validate(snapshot)
            runId = snapshot.runId
            value.value = snapshot.state
            archives.clear()
            archives += snapshot.archivedRuns
            history.clear()
            history += snapshot.history
            acknowledged.clear()
            restoredWorlds++
            history += AuditEntry("restore:$restoredWorlds", snapshot.historySequence + 1, runId, AuditType.RESTORED,
                after = snapshot.state)
            return value.value
        }
    }

    private class MemoryApi : BackendApi {
        val calls = mutableListOf<String>()
        var remote: SnapshotDownloadResponse? = null
        var rewards: List<ParentRewardDto> = emptyList()
        var beforeRegister: suspend () -> Unit = {}
        var beforeRewards: suspend () -> Unit = {}
        var beforeRewardAck: suspend (AckParentRewardsRequest) -> Unit = {}
        var loseNextSnapshotResponse = false
        var loseNextAnalyticsResponse = false
        var loseNextAckResponse = false
        var failSnapshotRequests = false
        var rejectRegressingAnalytics = false
        var beforeAnalytics: suspend () -> Unit = {}
        var beforeSkills: suspend () -> Unit = {}
        var assessments: SkillAssessmentsResponse? = null
        val snapshotUploads = mutableListOf<Pair<String, SnapshotUploadRequest>>()
        val analyticsUploads = mutableListOf<Pair<String, AnalyticsUploadRequest>>()
        val rewardAcks = mutableListOf<AckParentRewardsRequest>()
        val rewardAckRequests = mutableListOf<Pair<String, AckParentRewardsRequest>>()
        private val snapshotReceipts = mutableMapOf<String, Pair<SnapshotUploadRequest, SnapshotUploadResponse>>()
        private val analyticsReceipts = mutableMapOf<String, Pair<AnalyticsUploadRequest, AnalyticsUploadResponse>>()
        private val acceptedAnalyticsSequences = mutableMapOf<String, Long>()
        override suspend fun registerProfile(requestId: String, body: RegisterProfileRequest): RegisterProfileResponse {
            calls += "register"
            assertEquals(ProfileId, body.deviceId)
            beforeRegister()
            return RegisterProfileResponse(body.deviceId)
        }
        override suspend fun downloadSnapshot(body: SnapshotDownloadRequest): SnapshotDownloadResponse {
            calls += "get-snapshot"
            assertEquals(ProfileId, body.deviceId)
            return remote ?: throw HttpException(Response.error<Any>(404, "{}".toResponseBody("application/json".toMediaType())))
        }
        override suspend fun uploadSnapshot(requestId: String,
            body: SnapshotUploadRequest): SnapshotUploadResponse {
            calls += "put-snapshot"
            assertEquals(ProfileId, body.deviceId)
            snapshotUploads += requestId to body
            if (failSnapshotRequests) throw IOException("Offline")
            snapshotReceipts[requestId]?.let { (original, response) -> check(original == body); return response }
            check(body.expectedServerRevision == remote?.serverRevision)
            val response = SnapshotUploadResponse(body.uploadId, body.gameRunId, (remote?.serverRevision ?: 0) + 1, body.checksum)
            remote = SnapshotDownloadResponse(body.gameRunId, response.serverRevision, body.currentContentFingerprint, body.snapshotJson)
            snapshotReceipts[requestId] = body to response
            if (loseNextSnapshotResponse) { loseNextSnapshotResponse = false; throw IOException("Lost committed upload response") }
            return response
        }
        override suspend fun uploadAnalytics(requestId: String,
            body: AnalyticsUploadRequest): AnalyticsUploadResponse {
            calls += "post-analytics"
            assertEquals(ProfileId, body.deviceId)
            analyticsUploads += requestId to body
            beforeAnalytics()
            analyticsReceipts[requestId]?.let { (original, response) -> check(original == body); return response }
            if (rejectRegressingAnalytics && body.throughHistorySequence < (acceptedAnalyticsSequences[body.gameRunId] ?: 0)) {
                throw httpFailure(409, "{\"code\":\"STALE_ANALYTICS\"}")
            }
            val response = AnalyticsUploadResponse(body.batchId, body.gameRunId, body.throughHistorySequence, body.facts.map { it.eventId })
            acceptedAnalyticsSequences[body.gameRunId] = body.throughHistorySequence
            analyticsReceipts[requestId] = body to response
            if (loseNextAnalyticsResponse) { loseNextAnalyticsResponse = false; throw IOException("Lost committed analytics response") }
            return response
        }
        override suspend fun skills(body: SkillAssessmentsRequest): SkillAssessmentsResponse {
            calls += "get-skills"
            assertEquals(ProfileId, body.deviceId)
            beforeSkills()
            return assessments ?: SkillAssessmentsResponse(body.gameRunId, acceptedAnalyticsSequences[body.gameRunId] ?: 0,
                SkillId.entries.map { SkillAssessmentDto(it, SkillStatus.NO_DATA, "test-policy") })
        }
        override suspend fun parentRewards(body: PullParentRewardsRequest): ParentRewardsResponse {
            calls += "get-rewards"
            assertEquals(ProfileId, body.deviceId)
            beforeRewards()
            val runRewards = rewards.filter { it.gameRunId == body.gameRunId }
            val page = runRewards.filter { it.sequence > body.afterSequence }.take(body.limit)
            return ParentRewardsResponse(body.deviceId, body.gameRunId, page, page.lastOrNull()?.sequence ?: body.afterSequence,
                runRewards.count { it.sequence > body.afterSequence } > page.size)
        }
        override suspend fun acknowledgeParentRewards(requestId: String,
            body: AckParentRewardsRequest): AckParentRewardsResponse {
            calls += "post-reward-ack"
            assertEquals(ProfileId, body.deviceId)
            beforeRewardAck(body)
            rewardAcks += body
            rewardAckRequests += requestId to body
            if (loseNextAckResponse) { loseNextAckResponse = false; throw IOException("Lost committed ACK response") }
            return AckParentRewardsResponse(body.gameRunId, body.receipts.map { it.applicationId })
        }
    }

    private companion object {
        const val ProfileId = "a1a81f35-ae8b-44dd-925d-659d88c6cd45"
        fun httpFailure(code: Int, body: String = "{}") = HttpException(Response.error<Any>(code,
            body.toResponseBody("application/json".toMediaType())))
        fun GameSnapshot.downloadResponse() = SnapshotDownloadResponse(runId, 1, "test-content", HistoryCodec.encodeSnapshot(this))
    }
}
