package ru.nksk.lctapp.data.backend

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.local.BackendSyncStateEntity
import ru.nksk.lctapp.data.game.local.PendingBackendRequestEntity
import ru.nksk.lctapp.domain.backend.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.ParentRewardCaps

class RemoteParentQuestRewardsRepositoryTest {
    @Test fun responseLossKeepsFrozenKeyAndBodyAcrossRepositoryRecreation() = runTest {
        val f = Fixture()
        f.api.fail = IOException("Lost response")
        try { f.repository().issue("SHOPPING", cap, "run"); fail() } catch (_: IOException) { }
        assertEquals(PendingParentQuestReward("run", cap), f.repository().pending("SHOPPING"))
        assertTrue(f.games.value.ownedItems.isEmpty())
        f.api.fail = null
        f.repository().issue("SHOPPING", cap, "run")
        assertEquals(f.api.requests[0], f.api.requests[1])
        assertEquals(listOf(cap), f.games.value.ownedItems.map { it.itemId })
        assertNull(f.repository().pending("SHOPPING"))
        assertTrue(f.scheduled > 0)
    }

    @Test fun receivedGrantUpdatesInventoryBeforeReturningWithoutEquippingAndOwnedIsBlockedBeforeHttp() = runTest {
        val f = Fixture()
        val pet = f.games.value.pet
        f.repository().issue("SHOPPING", cap, "run")
        assertEquals(pet, f.games.value.pet)
        assertEquals(cap, f.games.value.ownedItems.single().itemId)
        try { f.repository().issue("WEEKEND", cap, "run"); fail() } catch (_: ParentQuestRewardException) { }
        assertEquals(1, f.api.requests.size)
    }

    @Test fun receiptRecoveryAfterClearFailureDoesNotDuplicateInventory() = runTest {
        val f = Fixture()
        f.store.failClear = true
        try { f.repository().issue("SHOPPING", cap, "run"); fail() } catch (_: IOException) { }
        f.store.failClear = false
        f.repository().issue("SHOPPING", cap, "run")
        assertEquals(1, f.games.value.ownedItems.size)
        assertEquals(f.api.requests[0], f.api.requests[1])
    }

    @Test fun anotherGrantArrivingDuringHttpCannotCreateDuplicateCap() = runTest {
        val f = Fixture()
        f.api.beforeResponse = { f.games.value = f.games.value.copy(ownedItems = listOf(OwnedItem("other-grant", cap))) }
        f.repository().issue("SHOPPING", cap, "run")
        assertEquals(listOf(OwnedItem("other-grant", cap)), f.games.value.ownedItems)
        assertEquals(ParentRewardOutcome.ALREADY_OWNED, f.games.receipts.values.single().outcome)
    }

    @Test fun staleResponseAfterRestoreCannotMutateTheRestoredWorld() = runTest {
        val f = Fixture()
        f.api.beforeResponse = { f.games.generation = "restored" }
        try { f.repository().issue("SHOPPING", cap, "run"); fail() } catch (_: ParentRewardTargetChangedException) { }
        assertTrue(f.games.value.ownedItems.isEmpty())
        assertNotNull(f.repository().pending("SHOPPING"))
    }

    @Test fun newRunDoesNotInheritAnUncertainRequestFromTheOldRun() = runTest {
        val f = Fixture()
        f.api.fail = IOException("Lost response")
        try { f.repository().issue("SHOPPING", cap, "run"); fail() } catch (_: IOException) { }
        f.games.runId = "new-run"
        f.api.fail = null
        assertNull(f.repository().pending("SHOPPING"))
        f.repository().issue("SHOPPING", cap, "new-run")
        assertNotEquals(f.api.requests[0].first, f.api.requests[1].first)
        assertEquals("new-run", f.api.requests[1].second.gameRunId)
    }

    @Test fun definitiveRejectionAllowsAnotherChoiceButDoesNotWriteInventory() = runTest {
        val f = Fixture()
        f.api.fail = HttpException(retrofit2.Response.error<Any>(422,
            "{\"code\":\"UNKNOWN_ACCESSORY\"}".toResponseBody("application/json".toMediaType())))
        try { f.repository().issue("SHOPPING", cap, "run"); fail() } catch (_: ParentQuestRewardException) { }
        assertNull(f.repository().pending("SHOPPING"))
        assertTrue(f.games.value.ownedItems.isEmpty())
        f.api.fail = null
        f.repository().issue("SHOPPING", ParentRewardCaps.all[1].itemId, "run")
        assertNotEquals(f.api.requests[0].first, f.api.requests[1].first)
    }

    @Test fun unregisteredRunSynchronizesAndRetriesTheSameGrantRequest() = runTest {
        val f = Fixture()
        f.api.failOnce = HttpException(retrofit2.Response.error<Any>(409,
            "{\"code\":\"GAME_RUN_NOT_REGISTERED\"}".toResponseBody("application/json".toMediaType())))
        f.repository().issue("SHOPPING", cap, "run")
        assertEquals(1, f.synchronized)
        assertEquals(f.api.requests[0], f.api.requests[1])
        assertEquals(cap, f.games.value.ownedItems.single().itemId)
    }

    @Test fun mismatchedServerGrantIsNotAppliedOrForgotten() = runTest {
        val f = Fixture()
        f.api.wrongRun = true
        try { f.repository().issue("SHOPPING", cap, "run"); fail() } catch (_: IllegalStateException) { }
        assertTrue(f.games.value.ownedItems.isEmpty())
        assertNotNull(f.repository().pending("SHOPPING"))
    }

    private class Fixture {
        val games = Games()
        val store = Store()
        val api = Api()
        var scheduled = 0
        var synchronized = 0
        val identities = object : ParentIdentityStore {
            override suspend fun getOrCreate() = ParentIdentity("device", "registration")
            override suspend fun update(transform: (ParentIdentity) -> ParentIdentity) = transform(getOrCreate())
        }
        val cloud = object : CloudSyncRepository {
            override val state = MutableStateFlow(CloudSyncState())
            override suspend fun synchronize(): CloudSyncResult { synchronized++; return CloudSyncResult.SUCCESS }
            override suspend fun refreshSkills() = error("Not used")
            override suspend fun prepareRestore(): CloudRestorePreview = error("Not used")
            override suspend fun restore(previewId: String) = error("Not used")
            override fun dismissRestore(previewId: String) = Unit
        }
        fun repository() = RemoteParentQuestRewardsRepository(BackendConnection("https://backend.example.test/") { api },
            identities, games, store, cloud) { scheduled++ }
    }

    private class Games : GameRepository {
        var value = createInitialGameState().copy(ownedItems = emptyList())
        var runId = "run"
        var generation = "generation"
        val receipts = mutableMapOf<String, ParentRewardReceiptDto>()
        override fun observe() = flowOf(value)
        override fun observeHistorySequence() = flowOf(1L)
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { value = it }
        override suspend fun readSnapshotHead() = GameSnapshotHead(runId, value, 1)
        override suspend fun readCloudWorld() = CloudWorldRead(WorldSnapshotCodec.create(runId, value, 1, generation), 1, "head")
        override suspend fun applyParentRewards(profileId: String, gameRunId: String, rewards: List<ParentRewardDto>,
            expectedRestoreGeneration: String): List<ParentRewardReceiptDto> {
            if (gameRunId != runId || generation != expectedRestoreGeneration) throw ParentRewardTargetChangedException()
            return rewards.map { grant -> receipts.getOrPut(grant.rewardId) {
                val change = checkNotNull(ParentRewardPolicy().apply(value, grant, "application", ParentRewardCaps.all.map { it.itemId }.toSet()))
                value = change.state
                ParentRewardReceiptDto(grant.rewardId, "application", "audit", 2, change.outcome)
            } }
        }
    }
    private class Store : BackendSyncStore {
        val requests = mutableMapOf<Pair<String, String>, PendingBackendRequestEntity>()
        var failClear = false
        override suspend fun read(profileId: String): BackendSyncStateEntity? = null
        override suspend fun save(state: BackendSyncStateEntity) = Unit
        override suspend fun pending(profileId: String, kind: String) = requests[profileId to kind]
        override suspend fun stage(request: PendingBackendRequestEntity) {
            val key = request.profileId to request.kind
            check(requests[key] == null || requests[key] == request)
            requests[key] = request
        }
        override suspend fun clear(profileId: String, kind: String, requestId: String) {
            if (failClear) throw IOException("Clear failed")
            if (requests[profileId to kind]?.requestId == requestId) requests.remove(profileId to kind)
        }
        override suspend fun complete(state: BackendSyncStateEntity, kind: String, requestId: String) = error("Not used")
        override suspend fun replaceAfterRestore(state: BackendSyncStateEntity) = error("Not used")
        override suspend fun replaceAfterRestart(state: BackendSyncStateEntity) = error("Not used")
    }
    private class Api : BackendApi {
        val requests = mutableListOf<Pair<String, CreateParentRewardRequest>>()
        var fail: Exception? = null
        var failOnce: Exception? = null
        var beforeResponse: () -> Unit = {}
        var wrongRun = false
        override suspend fun createParentReward(requestId: String, body: CreateParentRewardRequest): ParentRewardDto {
            requests += requestId to body
            val once = failOnce
            failOnce = null
            once?.let { throw it }
            fail?.let { throw it }
            beforeResponse()
            return ParentRewardDto("reward:$requestId", body.deviceId, if (wrongRun) "other-run" else body.gameRunId,
                1, body.reward, "2026-09-29T12:00:00Z")
        }
        override suspend fun parentMaterials(): ParentMaterialsCatalog = error("Not used")
        override suspend fun registerProfile(requestId: String, body: RegisterProfileRequest): RegisterProfileResponse = error("Not used")
        override suspend fun uploadSnapshot(requestId: String, body: SnapshotUploadRequest): SnapshotUploadResponse = error("Not used")
        override suspend fun downloadSnapshot(body: SnapshotDownloadRequest): SnapshotDownloadResponse = error("Not used")
        override suspend fun uploadAnalytics(requestId: String, body: AnalyticsUploadRequest): AnalyticsUploadResponse = error("Not used")
        override suspend fun skills(body: SkillAssessmentsRequest): SkillAssessmentsResponse = error("Not used")
        override suspend fun parentRewards(body: PullParentRewardsRequest): ParentRewardsResponse = error("Not used")
        override suspend fun acknowledgeParentRewards(requestId: String, body: AckParentRewardsRequest): AckParentRewardsResponse = error("Not used")
    }
    companion object { private val cap = ParentRewardCaps.all.first().itemId }
}
