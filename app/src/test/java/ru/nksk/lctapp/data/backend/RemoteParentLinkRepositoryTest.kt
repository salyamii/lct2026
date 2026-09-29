package ru.nksk.lctapp.data.backend

import java.io.IOException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.backend.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.parentlink.ParentLinkUnavailableException

class RemoteParentLinkRepositoryTest {
    @Test fun qrContainsOnlyTheSameSavedDeviceIdAcrossRecreationAndNeedsNoBackendOrGameWrite() = runTest {
        val store = MemoryIdentityStore()
        val games = MemoryGames()
        val connection = BackendConnection("") { error("No network expected") }
        val first = RemoteParentLinkRepository(store, connection, games)
        val second = RemoteParentLinkRepository(store, connection, games)
        assertFalse(first.profile().backendConfigured)
        assertEquals(store.value.profileId, first.createCode().qrPayload)
        assertEquals(first.createCode(), second.createCode())
        assertFalse(store.value.registered)
        try { first.registerProfile(); fail("Expected unavailable backend") } catch (_: ParentLinkUnavailableException) { }
        assertEquals(store.value.profileId, second.createCode().qrPayload)
        assertEquals(0, games.writes)
    }

    @Test fun uncertainRegistrationPreservesKeyAndBodyWhileQrRemainsAvailable() = runTest {
        val store = MemoryIdentityStore()
        val games = MemoryGames()
        val api = FakeApi().apply { failRegistration = true }
        val connection = BackendConnection("https://backend.example.test/") { api }
        val first = RemoteParentLinkRepository(store, connection, games)
        val qr = first.createCode()
        assertTrue(api.registrationKeys.isEmpty())
        try { first.registerProfile(); fail("Expected timeout") } catch (_: IOException) { }
        val originalPet = api.registrationBodies.single().pet
        games.value = games.value.copy(pet = games.value.pet.copy(name = "Новое имя"))
        val recreated = RemoteParentLinkRepository(store, connection, games)
        assertEquals(qr, recreated.createCode())
        api.failRegistration = false
        recreated.registerProfile()
        recreated.registerProfile()
        assertEquals(listOf(store.value.registrationRequestId, store.value.registrationRequestId), api.registrationKeys)
        assertEquals(listOf(originalPet, originalPet), api.registrationBodies.map { it.pet })
        assertTrue(store.value.registered)
        assertEquals(qr, recreated.createCode())
        assertTrue(api.registrationBodies.all { it.deviceId == store.value.profileId })
        assertEquals(0, games.writes)
    }

    @Test fun obsoleteInvitationMetadataDoesNotReplaceAnExistingIdentity() {
        val identity = MemoryIdentityStore().value
        val saved = BackendJson.encodeToString(ParentIdentity.serializer(), identity)
        val legacy = saved.dropLast(1) + ",\"pendingPairingRequestId\":\"old-request\"}"
        assertEquals(identity, BackendJson.decodeFromString<ParentIdentity>(legacy))
    }

    @Test fun registrationIsNotReusedAtAnotherBackendAndUnsafeUrlsAreRejected() = runTest {
        val store = MemoryIdentityStore().apply { value = value.copy(backendUrl = "https://original.example.test/") }
        val repository = RemoteParentLinkRepository(store, BackendConnection("https://other.example.test/") { error("Must not send") }, MemoryGames())
        assertEquals(store.value.profileId, repository.createCode().qrPayload)
        try { repository.registerProfile(); fail("Backend identity mismatch") } catch (_: IllegalStateException) { }
        for (url in listOf("http://backend.example.test/", "https://user:secret@backend.example.test/", "https://backend.example.test/?token=x")) {
            try { BackendConnection(url); fail("Unsafe URL accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    private class MemoryIdentityStore : ParentIdentityStore {
        var value = ParentIdentity("a1a81f35-ae8b-44dd-925d-659d88c6cd45", "ca6e9018-fbe4-4b24-91cc-757c333c85b4")
        override suspend fun getOrCreate() = value
        override suspend fun update(transform: (ParentIdentity) -> ParentIdentity): ParentIdentity = transform(value).also { value = it }
    }

    private class MemoryGames : GameRepository {
        var value = createInitialGameState()
        var writes = 0
        override fun observe() = flowOf(value)
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState): GameState { writes++; value = transform(value); return value }
    }

    private class FakeApi : BackendApi {
        override suspend fun parentMaterials(): ru.nksk.lctapp.domain.backend.ParentMaterialsCatalog = error("Unexpected material request")

        var failRegistration = false
        val registrationKeys = mutableListOf<String>()
        val registrationBodies = mutableListOf<RegisterProfileRequest>()
        override suspend fun registerProfile(requestId: String, body: RegisterProfileRequest): RegisterProfileResponse {
            registrationKeys += requestId; registrationBodies += body
            if (failRegistration) throw IOException("Lost registration response")
            return RegisterProfileResponse(body.deviceId)
        }
        override suspend fun uploadSnapshot(requestId: String, body: SnapshotUploadRequest): SnapshotUploadResponse = error("Not used")
        override suspend fun downloadSnapshot(body: SnapshotDownloadRequest): SnapshotDownloadResponse = error("Not used")
        override suspend fun uploadAnalytics(requestId: String, body: AnalyticsUploadRequest): AnalyticsUploadResponse = error("Not used")
        override suspend fun skills(body: SkillAssessmentsRequest): SkillAssessmentsResponse = error("Not used")
        override suspend fun parentRewards(body: PullParentRewardsRequest): ParentRewardsResponse = error("Not used")
        override suspend fun acknowledgeParentRewards(requestId: String, body: AckParentRewardsRequest): AckParentRewardsResponse = error("Not used")
    }
}
