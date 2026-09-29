package ru.nksk.lctapp.data.backend

import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import ru.nksk.lctapp.data.game.local.PendingBackendRequestEntity
import ru.nksk.lctapp.domain.backend.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.pet.ParentRewardCaps

/** Issuance is parent-only. Application and duplicate handling use the ordinary game transaction. */
@Singleton
internal class RemoteParentQuestRewardsRepository internal constructor(
    private val backend: BackendConnection,
    private val identities: ParentIdentityStore,
    private val games: GameRepository,
    private val store: BackendSyncStore,
    private val cloud: CloudSyncRepository,
    private val scheduleSync: () -> Unit,
) : ParentQuestRewardsRepository {
    @Inject constructor(backend: BackendConnection, identities: ParentIdentityStore, games: GameRepository,
        store: BackendSyncStore, cloud: CloudSyncRepository, scheduler: BackendSyncScheduler) :
        this(backend, identities, games, store, cloud, scheduler::requestSync)

    private val mutex = Mutex()

    override fun inventory() = games.observeHistorySequence().map {
        games.readSnapshotHead()?.let { head ->
            ParentRewardInventory(head.runId, head.state.ownedItems.map { it.itemId }.toSet())
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override suspend fun pending(questId: String): PendingParentQuestReward? = withContext(Dispatchers.IO) {
        val identity = identities.getOrCreate()
        val runId = games.readSnapshotHead()?.runId ?: return@withContext null
        store.pending(identity.deviceId, kind(questId, runId))?.let {
            val request = decode(it).request
            PendingParentQuestReward(request.gameRunId, (request.reward as ParentRewardPayload.Accessory).itemId)
        }
    }

    override suspend fun issue(questId: String, itemId: String, expectedRunId: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(ParentRewardCaps.forItem(itemId) != null)
            val url = backend.baseUrl ?: throw ParentQuestRewardException("Сервер наград не настроен.")
            val identity = identities.getOrCreate()
            val world = games.readCloudWorld() ?: throw ParentQuestRewardException("Сначала начните игру.")
            if (world.world.runId != expectedRunId) throw changedRun()
            val requestKind = kind(questId, expectedRunId)
            val previous = store.pending(identity.deviceId, requestKind)
            val row = previous ?: run {
                if (world.world.state.ownedItems.any { it.itemId == itemId })
                    throw ParentQuestRewardException("Эта кепка уже есть в инвентаре. Выберите другую.")
                val request = CreateParentRewardRequest(identity.deviceId, expectedRunId, ParentRewardPayload.Accessory(itemId))
                PendingBackendRequestEntity(identity.deviceId, requestKind, UUID.randomUUID().toString(),
                    BackendJson.encodeToString(FrozenQuestReward(url, request))).also { store.stage(it) }
            }
            val frozen = decode(row)
            check(frozen.backendUrl == url) { "Сервер изменился. Прежняя выдача требует проверки." }
            val request = frozen.request
            if (request.deviceId != identity.deviceId || request.gameRunId != expectedRunId) throw changedRun()
            check(request.reward == ParentRewardPayload.Accessory(itemId)) {
                "Сначала повторите выдачу ранее выбранной кепки."
            }
            suspend fun send(allowRegistration: Boolean): ParentRewardDto = try {
                backend.api.createParentReward(row.requestId, request)
            } catch (error: HttpException) {
                // A pet registration alone does not register its run. Only this definite
                // rejection permits preparing a snapshot and retrying the same frozen request.
                val body = error.response()?.errorBody()?.string()
                val code = runCatching { body?.let { BackendJson.parseToJsonElement(it).jsonObject["code"]?.jsonPrimitive?.content } }.getOrNull()
                if (error.code() == 422 && code in setOf("UNKNOWN_ACCESSORY", "INVALID_REWARD")) {
                    store.clear(identity.deviceId, requestKind, row.requestId)
                    throw ParentQuestRewardException(if (code == "UNKNOWN_ACCESSORY")
                        "Эта кепка пока недоступна на сервере. Попробуйте позже."
                    else "Сервер отклонил запрос выдачи награды. Попробуйте позже.")
                }
                if (!allowRegistration || error.code() != 409 || code != "GAME_RUN_NOT_REGISTERED") throw error
                cloud.synchronize()
                if (games.readSnapshotHead()?.runId != expectedRunId) throw changedRun()
                send(allowRegistration = false)
            }
            val grant = send(allowRegistration = true)
            check(grant.profileId == request.deviceId && grant.gameRunId == request.gameRunId && grant.reward == request.reward) {
                "Ответ сервера не соответствует выбранной награде. Повторите запрос."
            }
            // Schedule delivery/ACK even if the parent screen disappears during local application.
            scheduleSync()
            val receipts = games.applyParentRewards(identity.deviceId, expectedRunId, listOf(grant), world.generation)
            check(receipts.any { it.rewardId == grant.rewardId }) { "Награда выдана. Повторите обновление инвентаря." }
            store.clear(identity.deviceId, requestKind, row.requestId)
            scheduleSync()
        }
    }

    private fun decode(row: PendingBackendRequestEntity): FrozenQuestReward = BackendJson.decodeFromString(row.payload)
    private fun kind(questId: String, runId: String): String {
        require(questId in setOf("SHOPPING", "WEEKEND", "SECOND_LIFE"))
        return "parent-quest-reward:$questId:$runId"
    }
    private fun changedRun() = ParentQuestRewardException("Прохождение изменилось. Награда относится к прежней игре.")
}

/** The transport document is immutable across cancellation, retries and process death. */
@Serializable
private data class FrozenQuestReward(val backendUrl: String, val request: CreateParentRewardRequest)
