package ru.nksk.lctapp.data.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import ru.nksk.lctapp.data.game.local.BackendSyncStateEntity
import ru.nksk.lctapp.data.game.local.PendingBackendRequestEntity
import ru.nksk.lctapp.data.telemetry.Telemetry
import ru.nksk.lctapp.domain.backend.AckParentRewardsRequest
import ru.nksk.lctapp.domain.backend.AnalyticsUploadRequest
import ru.nksk.lctapp.domain.backend.BackendError
import ru.nksk.lctapp.domain.backend.CloudRestorePreview
import ru.nksk.lctapp.domain.backend.CloudSyncPhase
import ru.nksk.lctapp.domain.backend.CloudSyncRepository
import ru.nksk.lctapp.domain.backend.CloudSyncResult
import ru.nksk.lctapp.domain.backend.CloudSyncState
import ru.nksk.lctapp.domain.backend.PullParentRewardsRequest
import ru.nksk.lctapp.domain.backend.SkillAssessmentsRequest
import ru.nksk.lctapp.domain.backend.SkillAssessmentsResponse
import ru.nksk.lctapp.domain.backend.SkillSyncPhase
import ru.nksk.lctapp.domain.backend.SnapshotDownloadRequest
import ru.nksk.lctapp.domain.backend.SnapshotDownloadResponse
import ru.nksk.lctapp.domain.backend.SnapshotUploadRequest
import ru.nksk.lctapp.domain.backend.analyticsUploadRequest
import ru.nksk.lctapp.domain.backend.snapshotUploadRequest
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.history.CloudWorldRead
import ru.nksk.lctapp.domain.history.CloudWorldAncestor
import ru.nksk.lctapp.domain.history.CloudEvidenceRead
import ru.nksk.lctapp.domain.history.WorldSnapshot
import ru.nksk.lctapp.domain.history.HistoryLearningProjection
import ru.nksk.lctapp.domain.backend.worldSnapshot
import ru.nksk.lctapp.domain.history.RestoreGuard
import ru.nksk.lctapp.domain.parentlink.ParentLinkRepository
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One writer for worker, foreground polling, settings and parents; HTTP never owns the live world. */
@Singleton
internal class RemoteCloudSyncRepository @Inject constructor(
    private val connection: BackendConnection,
    private val identities: ParentIdentityStore,
    private val parents: ParentLinkRepository,
    private val games: GameRepository,
    private val session: GameSession,
    private val store: BackendSyncStore,
) : CloudSyncRepository {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(CloudSyncState())
    override val state = mutableState.asStateFlow()
    @Volatile private var restoreCandidate: RestoreCandidate? = null

    override suspend fun synchronize(): CloudSyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            Telemetry.traced("sync.synchronize") { synchronizeLocked() }
        }
    }

    private suspend fun synchronizeLocked(): CloudSyncResult {
        if (!connection.configured) return CloudSyncResult.NO_GAME
        if (games.read() == null) return CloudSyncResult.NO_GAME
        mutableState.value = mutableState.value.copy(phase = CloudSyncPhase.SYNCING,
            skillsPhase = SkillSyncPhase.SYNCING, message = null)
        return try {
            // Previously downloaded feedback remains visible even when registration/network fails.
            var snapshot = readWorld()
            loadCachedSkills(identities.getOrCreate(), snapshot)
            // Never initialize a fixture in a background worker before onboarding has saved a game.
            session.prepare()
            parents.registerProfile()
            val identity = identity()
            // Preparation may reconcile a save; registration can overlap local progress.
            if (games.latestHistoryId() != snapshot.latestHistoryId) {
                snapshot = readWorld()
            }
            var metadata = metadata(identity, snapshot)
            metadata = recoverRestore(identity, metadata, snapshot)
            if (metadata.localGeneration != snapshot.localGeneration()) {
                val previousRun = snapshot.world.predecessors.firstOrNull {
                    it.runId == metadata.gameRunId && it.generation == metadata.localGeneration
                }
                if (previousRun != null) metadata = finishArchivedRequests(identity, metadata, previousRun)
                metadata = metadata.copy(gameRunId = snapshot.runId, localGeneration = snapshot.localGeneration(),
                    lastSnapshotChecksum = null, lastAnalyticsSequence = 0, rewardFetchCursor = null, skillsPayload = null)
                if (previousRun != null) store.replaceAfterRestart(metadata) else store.replaceAfterRestore(metadata)
            }
            publishCachedSkills(identity, metadata, snapshot)

            // Independent lanes: a snapshot conflict must not prevent delivery of a parent's gift.
            val failures = mutableListOf<Exception>()
            suspend fun attempt(block: suspend () -> Unit) {
                try { block() } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { failures += failure }
            }
            attempt { metadata = uploadWorld(identity, metadata, snapshot) }
            var moreRewards = false
            attempt { moreRewards = receiveRewards(identity, snapshot.runId, snapshot.localGeneration()) }
            metadata = checkNotNull(store.read(identity.profileId))
            // Reuse the immutable world unless a gift, restore or local action committed.
            if (games.latestHistoryId() != snapshot.latestHistoryId) {
                snapshot = readWorld()
            }
            check(snapshot.localGeneration() == metadata.localGeneration) { "World was restored during synchronization" }
            if (failures.isEmpty()) attempt { metadata = uploadWorld(identity, metadata, snapshot) }
            attempt {
                try { metadata = refreshSkillLane(identity, metadata, snapshot) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { skillFailure(failure); throw failure }
            }
            if (failures.isNotEmpty()) return failed(failures.first())

            metadata = metadata.copy(lastSyncedAtEpochMs = System.currentTimeMillis())
            store.save(metadata)
            // Acknowledging delivery is local bookkeeping, never an additional gameplay event.
            if (metadata.lastSnapshotChecksum == snapshot.checksum && metadata.lastAnalyticsSequence >= snapshot.historySequence) {
                acknowledgeDeliveredOutbox(snapshot)
            }
            mutableState.value = mutableState.value.copy(phase = CloudSyncPhase.IDLE,
                lastSyncedAt = metadata.lastSyncedAtEpochMs?.let(::displayTime), message = null)
            val changedDuringUpload = games.latestHistoryId() != snapshot.latestHistoryId
            if (moreRewards || changedDuringUpload || metadata.lastSnapshotChecksum != snapshot.checksum ||
                metadata.lastAnalyticsSequence < snapshot.historySequence) CloudSyncResult.RETRY else CloudSyncResult.SUCCESS
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(phase = CloudSyncPhase.IDLE,
                skillsPhase = SkillSyncPhase.IDLE)
            throw cancelled
        } catch (failure: Exception) { failed(failure) }
    }

    override suspend fun refreshSkills(): CloudSyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            Telemetry.traced("sync.refresh_skills") { refreshSkillsLocked() }
        }
    }

    private suspend fun refreshSkillsLocked(): CloudSyncResult {
        mutableState.value = mutableState.value.copy(skillsPhase = SkillSyncPhase.SYNCING)
        return try {
            if (games.read() == null) {
                mutableState.value = mutableState.value.copy(skills = null, skillsGeneration = null,
                    skillsPhase = SkillSyncPhase.IDLE)
                return CloudSyncResult.NO_GAME
            }
            val snapshot = readWorld()
            val savedIdentity = identities.getOrCreate()
            loadCachedSkills(savedIdentity, snapshot)
            if (!connection.configured) {
                mutableState.value = mutableState.value.copy(skillsPhase = SkillSyncPhase.UNAVAILABLE)
                return CloudSyncResult.NO_GAME
            }
            // Validate the saved server before attempting any HTTP request.
            var metadata = metadata(savedIdentity, snapshot)
            metadata = recoverRestore(savedIdentity, metadata, snapshot)
            parents.registerProfile()
            val identity = identity()
            if (metadata.localGeneration != snapshot.localGeneration() || metadata.gameRunId != snapshot.runId) {
                val previous = snapshot.world.predecessors.firstOrNull {
                    it.runId == metadata.gameRunId && it.generation == metadata.localGeneration
                }
                // Only this lane may run on parent entry. Snapshot/ACK retries are left for full sync.
                if (previous != null && store.pending(identity.profileId, ANALYTICS) != null) {
                    metadata = retryArchivedAnalytics(identity, metadata, previous)
                }
                if (listOf(SNAPSHOT, ANALYTICS, ACK, RESTORE).any { store.pending(identity.profileId, it) != null }) {
                    throw SkillsAwaitingWorldTransition()
                }
                metadata = metadata.copy(gameRunId = snapshot.runId, localGeneration = snapshot.localGeneration(),
                    lastSnapshotChecksum = null, lastAnalyticsSequence = 0, rewardFetchCursor = null,
                    skillsPayload = null)
                store.replaceAfterRestart(metadata)
            }
            publishCachedSkills(identity, metadata, snapshot)
            refreshSkillLane(identity, metadata, snapshot)
            if (mutableState.value.skillsPhase == SkillSyncPhase.UNAVAILABLE) CloudSyncResult.RETRY
            else CloudSyncResult.SUCCESS
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(skillsPhase = SkillSyncPhase.IDLE)
            throw cancelled
        } catch (failure: Exception) { skillFailure(failure) }
    }

    private suspend fun loadCachedSkills(identity: ParentIdentity, snapshot: CloudWorldRead) {
        publishCachedSkills(identity, store.read(identity.profileId), snapshot)
    }

    private fun publishCachedSkills(identity: ParentIdentity, metadata: BackendSyncStateEntity?, snapshot: CloudWorldRead) {
        val cached = cachedSkills(identity, metadata, snapshot)
        mutableState.value = mutableState.value.copy(
            skills = cached,
            skillsGeneration = snapshot.localGeneration().takeIf { cached != null },
            lastSyncedAt = metadata?.takeIf { it.backendUrl == connection.baseUrl }
                ?.lastSyncedAtEpochMs?.let(::displayTime),
        )
    }

    private fun cachedSkills(identity: ParentIdentity, metadata: BackendSyncStateEntity?,
        snapshot: CloudWorldRead): SkillAssessmentsResponse? {
        if (metadata == null || metadata.profileId != identity.profileId || metadata.backendUrl != connection.baseUrl ||
            identity.backendUrl != metadata.backendUrl || metadata.gameRunId != snapshot.runId ||
            metadata.localGeneration != snapshot.localGeneration() ||
            metadata.lastAnalyticsSequence !in 0..snapshot.historySequence) return null
        val payload = metadata.skillsPayload ?: return null
        val response = try { BackendJson.decodeFromString<SkillAssessmentsResponse>(payload) }
            catch (_: IllegalArgumentException) { return null }
        return response.takeIf { it.schemaVersion == 1 && it.gameRunId == snapshot.runId &&
            it.basedOnHistorySequence in 0..metadata.lastAnalyticsSequence }
    }

    private suspend fun refreshSkillLane(identity: ParentIdentity, saved: BackendSyncStateEntity,
        snapshot: CloudWorldRead): BackendSyncStateEntity {
        check(saved.profileId == identity.profileId && saved.backendUrl == connection.baseUrl &&
            saved.gameRunId == snapshot.runId && saved.localGeneration == snapshot.localGeneration() &&
            saved.lastAnalyticsSequence in 0..snapshot.historySequence) { "Invalid skill transport boundary" }
        var metadata = uploadSkills(identity, saved, snapshot)
        // A frozen body may cover an earlier boundary; acknowledge it before sending the latest tail.
        if (metadata.lastAnalyticsSequence < snapshot.historySequence) metadata = uploadSkills(identity, metadata, snapshot)
        return readSkills(identity, metadata, snapshot)
    }

    private suspend fun identity(): ParentIdentity = identities.getOrCreate().also {
        check(it.registered && it.backendUrl == connection.baseUrl) { "Profile is not registered with this server" }
    }

    private suspend fun metadata(identity: ParentIdentity, snapshot: CloudWorldRead): BackendSyncStateEntity =
        store.read(identity.profileId)?.also {
            check(it.backendUrl == connection.baseUrl) { "Synchronization belongs to another server" }
        } ?: BackendSyncStateEntity(identity.profileId, checkNotNull(connection.baseUrl),
            gameRunId = snapshot.runId, localGeneration = snapshot.localGeneration()).also { store.save(it) }

    private suspend fun uploadWorld(identity: ParentIdentity, saved: BackendSyncStateEntity,
        snapshot: CloudWorldRead): BackendSyncStateEntity = Telemetry.traced("sync.upload_world",
        "sync.run_id" to snapshot.runId, "sync.generation" to snapshot.localGeneration()) {
        var metadata = saved
        var pending = store.pending(identity.profileId, SNAPSHOT)
        if (pending == null && metadata.lastSnapshotChecksum == snapshot.checksum) return metadata
        if (pending == null && metadata.serverRevision == null) {
            val remote = try { connection.api.downloadSnapshot(SnapshotDownloadRequest(identity.profileId)) }
                catch (failure: HttpException) { if (failure.code() == 404) null else throw failure }
            if (remote != null) {
                val world = remote.worldSnapshot()
                // A coherent targeted checkpoint check replaces exporting every local audit world.
                if (!games.cloudContains(world)) {
                    throw CloudConflict("На сервере другая история. Откройте облачную копию в настройках.")
                }
                metadata = metadata.copy(serverRevision = remote.serverRevision)
                store.save(metadata)
            }
        }
        val request = if (pending == null) {
            val request = snapshotUploadRequest(identity.profileId, snapshot.world, newId(), metadata.serverRevision, session.contentFingerprint)
            pending = PendingBackendRequestEntity(identity.profileId, SNAPSHOT, request.uploadId, encodeSnapshotUpload(request))
            store.stage(pending)
            // Stage the exact body before HTTP and send that same immutable typed request.
            request
        } else pending.forDevice<SnapshotUploadRequest>(identity.profileId)
        check(request.gameRunId == snapshot.runId && request.uploadId == pending.requestId)
        val response = connection.api.uploadSnapshot(pending.requestId, request)
        check(response.uploadId == request.uploadId && response.gameRunId == request.gameRunId &&
            response.checksum == request.checksum && response.serverRevision == (request.expectedServerRevision ?: 0) + 1) {
            "Invalid snapshot acknowledgement"
        }
        metadata = metadata.copy(serverRevision = response.serverRevision, lastSnapshotChecksum = request.checksum,
            gameRunId = request.gameRunId)
        store.complete(metadata, SNAPSHOT, pending.requestId)
        // A frozen retry can precede newer local actions. The next pass must upload those as well.
        return metadata
    }

    /** Frozen bodies already contain every byte needed for a retry, including an archived run. */
    private suspend fun finishArchivedRequests(identity: ParentIdentity, saved: BackendSyncStateEntity,
        archived: CloudWorldAncestor): BackendSyncStateEntity {
        var metadata = saved
        store.pending(identity.profileId, SNAPSHOT)?.let { pending ->
            val request = pending.forDevice<SnapshotUploadRequest>(identity.profileId)
            check(request.gameRunId == archived.runId && request.uploadId == pending.requestId)
            val response = connection.api.uploadSnapshot(pending.requestId, request)
            check(response.uploadId == request.uploadId && response.gameRunId == request.gameRunId &&
                response.checksum == request.checksum && response.serverRevision == (request.expectedServerRevision ?: 0) + 1) {
                "Invalid snapshot acknowledgement"
            }
            metadata = metadata.copy(serverRevision = response.serverRevision, lastSnapshotChecksum = request.checksum)
            store.complete(metadata, SNAPSHOT, pending.requestId)
        }
        if (store.pending(identity.profileId, ANALYTICS) != null) metadata = retryArchivedAnalytics(identity, metadata, archived)
        sendPendingAck(identity, archived.runId)
        return metadata
    }

    private suspend fun retryArchivedAnalytics(identity: ParentIdentity, metadata: BackendSyncStateEntity,
        archived: CloudWorldAncestor): BackendSyncStateEntity {
        var pending = checkNotNull(store.pending(identity.profileId, ANALYTICS))
        var request = pending.forDevice<AnalyticsUploadRequest>(identity.profileId)
        check(request.gameRunId == archived.runId && request.batchId == pending.requestId &&
            request.throughHistorySequence <= archived.historySequence)
        val response = try { connection.api.uploadAnalytics(pending.requestId, request) }
        catch (failure: HttpException) {
            if (!failure.isStaleAnalyticsRejection() || request.throughHistorySequence >= archived.historySequence) throw failure
            val evidence = games.readArchivedCloudEvidence(archived.runId) ?: throw failure
            val current = readWorld()
            if (evidence.head.generation != archived.generation ||
                evidence.head.world.historySequence != archived.historySequence ||
                current.world.predecessors.none { it == archived }) throw failure
            // Definite rejection permits a new ID for the preserved tail; an uncertain reply never does.
            val replacement = prepareAnalytics(identity.profileId, evidence.head, evidence)
            val staged = PendingBackendRequestEntity(identity.profileId, ANALYTICS, replacement.batchId,
                BackendJson.encodeToString(replacement))
            store.clear(identity.profileId, ANALYTICS, pending.requestId)
            store.stage(staged)
            pending = staged
            request = replacement
            connection.api.uploadAnalytics(pending.requestId, request)
        }
        validateAnalyticsReply(request, response)
        return metadata.copy(lastAnalyticsSequence = request.throughHistorySequence).also {
            store.complete(it, ANALYTICS, pending.requestId)
        }
    }

    private suspend fun uploadSkills(identity: ParentIdentity, metadata: BackendSyncStateEntity,
        snapshot: CloudWorldRead, mayReplaceRejectedBatch: Boolean = true): BackendSyncStateEntity =
        Telemetry.traced("sync.upload_skills",
            "sync.through_sequence" to snapshot.historySequence.toString()) {
        var pending = store.pending(identity.profileId, ANALYTICS)
        if (pending == null && metadata.lastAnalyticsSequence >= snapshot.historySequence &&
            (snapshot.historySequence > 0 || metadata.skillsPayload != null)) return metadata
        val request = if (pending == null) {
            val request = prepareAnalytics(identity.profileId, snapshot)
            pending = PendingBackendRequestEntity(identity.profileId, ANALYTICS, request.batchId, BackendJson.encodeToString(request))
            store.stage(pending)
            request
        } else pending.forDevice<AnalyticsUploadRequest>(identity.profileId)
        check(request.gameRunId == snapshot.runId && request.batchId == pending.requestId)
        val response = try { connection.api.uploadAnalytics(pending.requestId, request) }
        catch (failure: HttpException) {
            if (!mayReplaceRejectedBatch || request.throughHistorySequence >= snapshot.historySequence ||
                !failure.isStaleAnalyticsRejection() || !canReplaceRejectedAnalytics(identity, metadata, snapshot)) {
                throw failure
            }
            // This batch was definitely rejected, not delivered. Never acknowledge its facts or
            // mutate its body under the old ID. A new boundary gets one new durable attempt.
            store.clear(identity.profileId, ANALYTICS, pending.requestId)
            return uploadSkills(identity, metadata, snapshot, mayReplaceRejectedBatch = false)
        }
        validateAnalyticsReply(request, response)
        return metadata.copy(lastAnalyticsSequence = request.throughHistorySequence).also {
            store.complete(it, ANALYTICS, pending.requestId)
        }
    }

    private fun HttpException.isStaleAnalyticsRejection(): Boolean {
        if (code() != 409) return false
        return try {
            response()?.errorBody()?.string()?.let { BackendJson.decodeFromString<BackendError>(it).code } == "STALE_ANALYTICS"
        } catch (_: IllegalArgumentException) { false }
        catch (_: IOException) { false }
    }

    private suspend fun canReplaceRejectedAnalytics(identity: ParentIdentity, metadata: BackendSyncStateEntity,
        snapshot: CloudWorldRead): Boolean {
        if (metadata.profileId != identity.profileId || metadata.backendUrl != connection.baseUrl ||
            metadata.gameRunId != snapshot.runId || metadata.localGeneration != snapshot.localGeneration()) return false
        val current = readWorld()
        return current.runId == snapshot.runId && current.localGeneration() == snapshot.localGeneration() &&
            current.historySequence >= snapshot.historySequence
    }

    private suspend fun readSkills(identity: ParentIdentity, metadata: BackendSyncStateEntity,
        snapshot: CloudWorldRead): BackendSyncStateEntity =
        Telemetry.traced("sync.read_skills",
            "sync.through_sequence" to snapshot.historySequence.toString()) {
        val response = try { connection.api.skills(SkillAssessmentsRequest(identity.profileId, snapshot.runId)) }
            catch (failure: HttpException) {
                if (failure.code() != 404 && failure.code() != 409) throw failure
                requireCurrentSkillWorld(snapshot)
                mutableState.value = mutableState.value.copy(skillsPhase = SkillSyncPhase.UNAVAILABLE)
                return metadata
            }
        requireCurrentSkillWorld(snapshot)
        val previous = cachedSkills(identity, metadata, snapshot)
        check(response.schemaVersion == 1 && response.gameRunId == snapshot.runId &&
            response.basedOnHistorySequence in (previous?.basedOnHistorySequence ?: 0)..metadata.lastAnalyticsSequence &&
            response.basedOnHistorySequence <= snapshot.historySequence) { "Invalid skill assessment boundary" }
        val saved = metadata.copy(skillsPayload = BackendJson.encodeToString(response))
        store.save(saved)
        requireCurrentSkillWorld(snapshot)
        mutableState.value = mutableState.value.copy(skills = response, skillsGeneration = snapshot.localGeneration(),
            skillsPhase = SkillSyncPhase.IDLE)
        return saved
    }

    private suspend fun requireCurrentSkillWorld(expected: CloudWorldRead) {
        // The same committed audit head also means the same run and restore generation.
        // A different head still requires the complete check: it may be ordinary progress or a restored world.
        val expectedHead = expected.latestHistoryId
        if (expectedHead != null && games.latestHistoryId() == expectedHead) return
        val current = readWorld()
        if (current.runId != expected.runId || current.localGeneration() != expected.localGeneration() ||
            current.historySequence < expected.historySequence) {
            mutableState.value = mutableState.value.copy(skills = null, skillsGeneration = null)
            throw SkillsAwaitingWorldTransition()
        }
    }

    private suspend fun receiveRewards(identity: ParentIdentity, runId: String, generation: String): Boolean =
        Telemetry.traced("sync.receive_rewards", "sync.run_id" to runId, "sync.generation" to generation) {
        sendPendingAck(identity, runId)
        var metadata = checkNotNull(store.read(identity.profileId))
        var cursor = metadata.rewardFetchCursor ?: 0L
        repeat(5) {
            val page = connection.api.parentRewards(PullParentRewardsRequest(identity.profileId, runId, cursor, 50))
            check(page.schemaVersion == 1 && page.profileId == identity.profileId && page.gameRunId == runId &&
                page.rewards.size <= 50 && page.rewards.map { it.rewardId }.distinct().size == page.rewards.size &&
                page.rewards.withIndex().all { (index, reward) -> reward.profileId == identity.profileId &&
                    reward.gameRunId == runId && reward.sequence == Math.addExact(cursor, index.toLong() + 1) } &&
                page.nextAfterSequence == (page.rewards.lastOrNull()?.sequence ?: cursor) &&
                (page.rewards.isNotEmpty() || !page.hasMore)) { "Invalid reward page" }
            val receipts = games.applyParentRewards(identity.profileId, runId, page.rewards, generation)
            if (receipts.isNotEmpty()) {
                val request = AckParentRewardsRequest(identity.profileId, runId, receipts)
                store.stage(PendingBackendRequestEntity(identity.profileId, ACK, newId(), BackendJson.encodeToString(request)))
                sendPendingAck(identity, runId)
            }
            metadata = checkNotNull(store.read(identity.profileId)).copy(
                rewardFetchCursor = page.nextAfterSequence.takeIf { page.hasMore })
            store.save(metadata)
            cursor = page.nextAfterSequence
            if (!page.hasMore) return false
        }
        return true
    }

    private suspend fun sendPendingAck(identity: ParentIdentity, runId: String) = Telemetry.traced(
        "sync.send_ack", "sync.run_id" to runId) {
        val pending = store.pending(identity.profileId, ACK) ?: return
        val request = pending.forDevice<AckParentRewardsRequest>(identity.profileId)
        check(request.gameRunId == runId)
        val response = connection.api.acknowledgeParentRewards(pending.requestId, request)
        check(response.schemaVersion == 1 && response.gameRunId == runId &&
            response.acceptedApplicationIds.toSet() == request.receipts.map { it.applicationId }.toSet()) { "Invalid reward acknowledgement" }
        store.clear(identity.profileId, ACK, pending.requestId)
    }

    override suspend fun prepareRestore(): CloudRestorePreview = mutex.withLock {
        withContext(Dispatchers.IO) {
            restoreCandidate = null
            parents.registerProfile()
            val identity = identity()
            val before = readWorld()
            val remote = connection.api.downloadSnapshot(SnapshotDownloadRequest(identity.profileId))
            val world = remote.worldSnapshot()
            val rulesId = world.state.engine?.rulesId
            require(rulesId == null || rulesId == session.catalog.rules.id) {
                "Incompatible game rules"
            }
            val id = newId()
            restoreCandidate = RestoreCandidate(id, remote, world,
                RestoreGuard(before.state.engine?.revision, before.localHistorySequence, session.catalog.rules.id), before.generation)
            CloudRestorePreview(id, world.state.pet.name, world.state.engine?.day,
                world.state.economy.availableBalance, world.state.economy.savingsBalance)
        }
    }

    override fun dismissRestore(previewId: String) {
        if (restoreCandidate?.id == previewId) restoreCandidate = null
    }

    override suspend fun restore(previewId: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val candidate = checkNotNull(restoreCandidate?.takeIf { it.id == previewId }) { "Reload the cloud preview" }
            val identity = identity()
            val current = readWorld()
            check(current.generation == candidate.generation) { "World changed since the preview" }
            store.pending(identity.profileId, RESTORE)?.let { store.clear(it.profileId, it.kind, it.requestId) }
            store.stage(PendingBackendRequestEntity(identity.profileId, RESTORE, candidate.id,
                BackendJson.encodeToString(RestoreIntent(candidate.response, candidate.generation))))
            restoreCandidate = null
            try {
                games.restoreCloudWorld(candidate.world, candidate.guard, candidate.id)
                recoverRestore(identity, metadata(identity, current), readWorld())
                mutableState.value = CloudSyncState(message = "Облачная копия восстановлена.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failed(failure); throw failure }
        }
    }

    /** A source checksum and durable restore intent identify the committed baseline after process death. */
    private suspend fun recoverRestore(identity: ParentIdentity, metadata: BackendSyncStateEntity,
        local: CloudWorldRead): BackendSyncStateEntity {
        val pending = store.pending(identity.profileId, RESTORE) ?: return metadata
        val intent = BackendJson.decodeFromString<RestoreIntent>(pending.payload)
        if (local.generation == intent.previousGeneration) {
            store.clear(identity.profileId, RESTORE, pending.requestId)
            return metadata
        }
        val remote = intent.response.worldSnapshot()
        val receipt = games.findCloudRestoreReceipt(pending.requestId, remote)
        if (receipt == null && local.world.predecessors.any { it.generation == intent.previousGeneration }) {
            store.clear(identity.profileId, RESTORE, pending.requestId)
            return metadata
        }
        checkNotNull(receipt) { "Восстановление было прервано. Снова откройте облачную копию в настройках." }
        check(receipt.runId == remote.runId && receipt.sourceChecksum == remote.checksum &&
            receipt.restoreRequestId == pending.requestId)
        return metadata.copy(serverRevision = intent.response.serverRevision, lastSnapshotChecksum = remote.checksum,
            gameRunId = receipt.runId, localGeneration = receipt.generation, lastAnalyticsSequence = 0,
            rewardFetchCursor = null, skillsPayload = null, lastSyncedAtEpochMs = null).also { store.replaceAfterRestore(it) }
    }

    private suspend fun readWorld(): CloudWorldRead = checkNotNull(games.readCloudWorld()) { "No saved game" }
    private val CloudWorldRead.runId get() = world.runId
    private val CloudWorldRead.state get() = world.state
    private val CloudWorldRead.historySequence get() = world.historySequence
    private val CloudWorldRead.checksum get() = world.checksum
    private fun CloudWorldRead.localGeneration() = generation

    private suspend fun prepareAnalytics(deviceId: String, expected: CloudWorldRead,
        captured: CloudEvidenceRead? = null): AnalyticsUploadRequest {
        val source = captured ?: checkNotNull(games.readCloudEvidence()) { "No saved evidence" }
        if (source.head.world.runId != expected.runId || source.head.generation != expected.generation ||
            source.head.localHistorySequence < expected.localHistorySequence) throw SkillsAwaitingWorldTransition()
        // Keep an indexed lazy view, not a list of every before/after world. Missing pre-restore history
        // is explicit; neither old local branches nor invented remote decisions become evidence.
        val history = source.history
        val first = history.indexOfFirst { it.sequence > (expected.baselineSequence ?: 0) }
        val end = history.indexOfLast { it.sequence <= expected.localHistorySequence } + 1
        val clipped = if (first < 0 || end <= first) emptyList() else object : AbstractList<ru.nksk.lctapp.domain.history.AuditEntry>() {
            override val size = end - first
            override fun get(index: Int) = history[first + index]
        }
        val facts = withContext(Dispatchers.Default) {
            HistoryLearningProjection.facts(clipped, session.catalog.content).map { fact ->
                fact.copy(sequence = expected.transportSequence(fact.sequence))
            }
        }
        return analyticsUploadRequest(deviceId, newId(), expected.runId, expected.historySequence, facts)
            .copy(historyStartSequence = if (expected.baselineSequence != null) expected.sourceHistorySequence else 0)
    }

    private fun validateAnalyticsReply(request: AnalyticsUploadRequest,
        response: ru.nksk.lctapp.domain.backend.AnalyticsUploadResponse) {
        check(response.schemaVersion == 1 && response.batchId == request.batchId && response.gameRunId == request.gameRunId &&
            response.acceptedThroughHistorySequence == request.throughHistorySequence &&
            response.acceptedEventIds.toSet() == request.facts.map { it.eventId }.toSet()) { "Invalid analytics acknowledgement" }
    }

    private suspend fun acknowledgeDeliveredOutbox(snapshot: CloudWorldRead) {
        if (!games.acknowledgeOutboxThrough(snapshot.runId, snapshot.generation, snapshot.localHistorySequence)) {
            throw SkillsAwaitingWorldTransition()
        }
    }

    private fun failed(failure: Exception): CloudSyncResult {
        if (mutableState.value.skillsPhase == SkillSyncPhase.SYNCING) skillFailure(failure)
        val http = (failure as? HttpException)?.code()
        val retryable = failure is IOException || failure is SkillsAwaitingWorldTransition ||
            http == 408 || http == 429 || http != null && http >= 500
        val conflict = failure is CloudConflict || http == 409
        mutableState.value = mutableState.value.copy(
            phase = when { retryable -> CloudSyncPhase.OFFLINE; conflict -> CloudSyncPhase.CONFLICT; else -> CloudSyncPhase.ERROR },
            message = when {
                retryable -> "Сервер пока недоступен. Игра сохранена на устройстве; попробуем отправить позже."
                conflict -> "Облачная копия отличается. Локальный мир сохранён; откройте облачную копию в настройках."
                else -> "Не удалось завершить обмен с сервером. Локальная игра сохранена."
            })
        return if (retryable) CloudSyncResult.RETRY else CloudSyncResult.NEEDS_ATTENTION
    }

    private fun skillFailure(failure: Exception): CloudSyncResult {
        val http = (failure as? HttpException)?.code()
        val retryable = failure is IOException || http == 408 || http == 429 || http != null && http >= 500
        val waiting = failure is SkillsAwaitingWorldTransition
        mutableState.value = mutableState.value.copy(skillsPhase = when {
            waiting -> SkillSyncPhase.UNAVAILABLE
            retryable -> SkillSyncPhase.OFFLINE
            else -> SkillSyncPhase.ERROR
        })
        return if (retryable || waiting) CloudSyncResult.RETRY else CloudSyncResult.NEEDS_ATTENTION
    }

    private data class RestoreCandidate(val id: String, val response: SnapshotDownloadResponse,
        val world: WorldSnapshot, val guard: RestoreGuard, val generation: String)
    @Serializable private data class RestoreIntent(val response: SnapshotDownloadResponse, val previousGeneration: String)
    private class CloudConflict(message: String) : IllegalStateException(message)
    private class SkillsAwaitingWorldTransition : IllegalStateException("Skill refresh awaits the current game generation")
    /** Adapt old frozen bodies at the transport boundary without changing their intent or receipt IDs. */
    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> PendingBackendRequestEntity.forDevice(deviceId: String): T {
        check(profileId == deviceId) { "Pending request belongs to another device" }
        // Current bodies decode directly. Only legacy bodies without deviceId need a JSON-tree adapter.
        try {
            val decoded = BackendJson.decodeFromString<T>(payload)
            val storedId = when (decoded) {
                is SnapshotUploadRequest -> decoded.deviceId
                is AnalyticsUploadRequest -> decoded.deviceId
                is AckParentRewardsRequest -> decoded.deviceId
                else -> error("Unsupported pending request")
            }
            check(storedId == deviceId) { "Pending body belongs to another device" }
            return decoded
        } catch (missing: MissingFieldException) {
            if ("deviceId" !in missing.missingFields) throw missing
        }
        val body = BackendJson.parseToJsonElement(payload).jsonObject
        val storedId = body["deviceId"]?.jsonPrimitive?.content
        check(storedId == null || storedId == deviceId) { "Pending body belongs to another device" }
        return BackendJson.decodeFromJsonElement(JsonObject(body + ("deviceId" to JsonPrimitive(deviceId))))
    }
    private fun newId() = UUID.randomUUID().toString()
    private fun displayTime(value: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(value))
    private companion object { const val SNAPSHOT = "snapshot"; const val ANALYTICS = "analytics"; const val ACK = "ack"; const val RESTORE = "restore" }
}
