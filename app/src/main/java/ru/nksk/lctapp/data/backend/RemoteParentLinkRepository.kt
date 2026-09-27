package ru.nksk.lctapp.data.backend

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nksk.lctapp.domain.backend.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.parentlink.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RemoteParentLinkRepository @Inject constructor(
    private val identities: ParentIdentityStore,
    private val connection: BackendConnection,
    private val games: GameRepository,
) : ParentLinkRepository {
    private val requests = Mutex()

    override suspend fun profile(): ParentLinkProfile = identities.getOrCreate().let {
        ParentLinkProfile(it.profileId, connection.configured)
    }

    override suspend fun createCode(): ParentLinkCode = ParentLinkCode(identities.getOrCreate().profileId)

    override suspend fun registerProfile(): Unit = requests.withLock {
        val url = connection.baseUrl ?: throw ParentLinkUnavailableException()
        var identity = identities.getOrCreate()
        check(identity.backendUrl == null || identity.backendUrl == url) { "Profile belongs to another backend" }
        if (!identity.registered) {
            if (identity.registration == null) {
                val pet = checkNotNull(games.read()) { "No game to register" }.pet.registrationDto()
                identity = identities.update { it.copy(backendUrl = url,
                    registration = RegisterProfileRequest(it.profileId, it.installationId, pet)) }
            }
            val registered = connection.api.registerProfile(identity.authorization(), identity.registrationRequestId,
                checkNotNull(identity.registration))
            check(registered.profileId == identity.profileId && registered.installationId == identity.installationId)
            identities.update { it.copy(registered = true) }
        }
        Unit
    }

    private fun ParentIdentity.authorization() = "Bearer $credential"
}
