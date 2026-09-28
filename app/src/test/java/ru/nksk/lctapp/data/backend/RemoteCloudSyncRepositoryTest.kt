package ru.nksk.lctapp.data.backend

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.BackendSyncStateEntity
import ru.nksk.lctapp.data.game.local.PendingBackendRequestEntity
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.backend.*
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineRules
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.*

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
        val pendingRequests = mutableMapOf<Pair<String, String>, PendingBackendRequestEntity>()
        override suspend fun read(profileId: String) = metadata?.takeIf { it.profileId == profileId }
        override suspend fun save(state: BackendSyncStateEntity) { metadata = state }
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
            return HistoryCodec.snapshot(runId, value.value, history.toList(), archives.toList())
        }
        override suspend fun acknowledgeOutbox(ids: Set<String>) { acknowledged += ids }
        override suspend fun applyParentRewards(profileId: String, gameRunId: String, rewards: List<ParentRewardDto>,
            expectedRestoreGeneration: String): List<ParentRewardReceiptDto> {
            check(gameRunId == runId && exportSnapshot().localGeneration() == expectedRestoreGeneration)
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
        var beforeRewards: suspend () -> Unit = {}
        var beforeRewardAck: suspend (AckParentRewardsRequest) -> Unit = {}
        var loseNextSnapshotResponse = false
        var loseNextAnalyticsResponse = false
        var loseNextAckResponse = false
        var failSnapshotRequests = false
        val snapshotUploads = mutableListOf<Pair<String, SnapshotUploadRequest>>()
        val analyticsUploads = mutableListOf<Pair<String, AnalyticsUploadRequest>>()
        val rewardAcks = mutableListOf<AckParentRewardsRequest>()
        val rewardAckRequests = mutableListOf<Pair<String, AckParentRewardsRequest>>()
        private val snapshotReceipts = mutableMapOf<String, Pair<SnapshotUploadRequest, SnapshotUploadResponse>>()
        private val analyticsReceipts = mutableMapOf<String, Pair<AnalyticsUploadRequest, AnalyticsUploadResponse>>()
        private var acceptedAnalyticsSequence = 0L
        override suspend fun registerProfile(requestId: String, body: RegisterProfileRequest): RegisterProfileResponse {
            calls += "register"
            assertEquals(ProfileId, body.deviceId)
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
            analyticsReceipts[requestId]?.let { (original, response) -> check(original == body); return response }
            val response = AnalyticsUploadResponse(body.batchId, body.gameRunId, body.throughHistorySequence, body.facts.map { it.eventId })
            acceptedAnalyticsSequence = body.throughHistorySequence
            analyticsReceipts[requestId] = body to response
            if (loseNextAnalyticsResponse) { loseNextAnalyticsResponse = false; throw IOException("Lost committed analytics response") }
            return response
        }
        override suspend fun skills(body: SkillAssessmentsRequest): SkillAssessmentsResponse {
            calls += "get-skills"
            assertEquals(ProfileId, body.deviceId)
            return SkillAssessmentsResponse(body.gameRunId, acceptedAnalyticsSequence,
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
        fun GameSnapshot.downloadResponse() = SnapshotDownloadResponse(runId, 1, "test-content", HistoryCodec.encodeSnapshot(this))
    }
}
