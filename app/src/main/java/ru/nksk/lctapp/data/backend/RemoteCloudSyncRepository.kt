package ru.nksk.lctapp.data.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import ru.nksk.lctapp.data.game.local.BackendSyncStateEntity
import ru.nksk.lctapp.data.game.local.PendingBackendRequestEntity
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
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.GameSnapshot
import ru.nksk.lctapp.domain.history.HistoryCodec
import ru.nksk.lctapp.domain.history.RestoreGuard
import ru.nksk.lctapp.domain.history.localGeneration
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
            if (!connection.configured) return@withContext CloudSyncResult.NO_GAME
            if (games.read() == null) return@withContext CloudSyncResult.NO_GAME
            mutableState.value = mutableState.value.copy(phase = CloudSyncPhase.SYNCING,
                skillsPhase = SkillSyncPhase.SYNCING, message = null)
            try {
                // Previously downloaded feedback remains visible even when registration/network fails.
                loadCachedSkills(identities.getOrCreate(), session.exportSnapshot())
                // Never initialize a fixture in a background worker before onboarding has saved a game.
                session.prepare()
                parents.registerProfile()
                val identity = identity()
                var snapshot = session.exportSnapshot()
                var metadata = metadata(identity, snapshot)
                metadata = recoverRestore(identity, metadata, snapshot)
                if (metadata.localGeneration != snapshot.localGeneration()) {
                    val previousRun = snapshot.predecessorSnapshots().firstOrNull {
                        it.runId == metadata.gameRunId && it.localGeneration() == metadata.localGeneration
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
                snapshot = session.exportSnapshot()
                check(snapshot.localGeneration() == metadata.localGeneration) { "World was restored during synchronization" }
                if (failures.isEmpty()) attempt { metadata = uploadWorld(identity, metadata, snapshot) }
                attempt {
                    try { metadata = refreshSkillLane(identity, metadata, snapshot) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { skillFailure(failure); throw failure }
                }
                if (failures.isNotEmpty()) return@withContext failed(failures.first())

                metadata = metadata.copy(lastSyncedAtEpochMs = System.currentTimeMillis())
                store.save(metadata)
                // Acknowledging delivery is local bookkeeping, never an additional gameplay event.
                if (metadata.lastSnapshotChecksum == snapshot.checksum && metadata.lastAnalyticsSequence >= snapshot.historySequence) {
                    games.acknowledgeOutbox(snapshot.history.map { it.id }.toSet())
                }
                mutableState.value = mutableState.value.copy(phase = CloudSyncPhase.IDLE,
                    lastSyncedAt = metadata.lastSyncedAtEpochMs?.let(::displayTime), message = null)
                val changedDuringUpload = games.readHistory().lastOrNull()?.id != snapshot.history.lastOrNull()?.id
                if (moreRewards || changedDuringUpload || metadata.lastSnapshotChecksum != snapshot.checksum ||
                    metadata.lastAnalyticsSequence < snapshot.historySequence) CloudSyncResult.RETRY else CloudSyncResult.SUCCESS
            } catch (cancelled: CancellationException) {
                mutableState.value = mutableState.value.copy(phase = CloudSyncPhase.IDLE,
                    skillsPhase = SkillSyncPhase.IDLE)
                throw cancelled
            } catch (failure: Exception) { failed(failure) }
        }
    }

    override suspend fun refreshSkills(): CloudSyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.value = mutableState.value.copy(skillsPhase = SkillSyncPhase.SYNCING)
            try {
                if (games.read() == null) {
                    mutableState.value = mutableState.value.copy(skills = null, skillsGeneration = null,
                        skillsPhase = SkillSyncPhase.IDLE)
                    return@withContext CloudSyncResult.NO_GAME
                }
                val snapshot = session.exportSnapshot()
                val savedIdentity = identities.getOrCreate()
                loadCachedSkills(savedIdentity, snapshot)
                if (!connection.configured) {
                    mutableState.value = mutableState.value.copy(skillsPhase = SkillSyncPhase.UNAVAILABLE)
                    return@withContext CloudSyncResult.NO_GAME
                }
                // Validate the saved server before attempting any HTTP request.
                var metadata = metadata(savedIdentity, snapshot)
                metadata = recoverRestore(savedIdentity, metadata, snapshot)
                parents.registerProfile()
                val identity = identity()
                if (metadata.localGeneration != snapshot.localGeneration() || metadata.gameRunId != snapshot.runId) {
                    val previous = snapshot.predecessorSnapshots().firstOrNull {
                        it.runId == metadata.gameRunId && it.localGeneration() == metadata.localGeneration
                    }
                    // Only this lane may run on parent entry. Snapshot/ACK retries are left for full sync.
                    if (previous != null && store.pending(identity.profileId, ANALYTICS) != null) {
                        metadata = uploadSkills(identity, metadata, previous)
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
    }

    private suspend fun loadCachedSkills(identity: ParentIdentity, snapshot: GameSnapshot) {
        publishCachedSkills(identity, store.read(identity.profileId), snapshot)
    }

    private fun publishCachedSkills(identity: ParentIdentity, metadata: BackendSyncStateEntity?, snapshot: GameSnapshot) {
        val cached = cachedSkills(identity, metadata, snapshot)
        mutableState.value = mutableState.value.copy(
            skills = cached,
            skillsGeneration = snapshot.localGeneration().takeIf { cached != null },
            lastSyncedAt = metadata?.takeIf { it.backendUrl == connection.baseUrl }
                ?.lastSyncedAtEpochMs?.let(::displayTime),
        )
    }

    private fun cachedSkills(identity: ParentIdentity, metadata: BackendSyncStateEntity?,
        snapshot: GameSnapshot): SkillAssessmentsResponse? {
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
        snapshot: GameSnapshot): BackendSyncStateEntity {
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

    private suspend fun metadata(identity: ParentIdentity, snapshot: GameSnapshot): BackendSyncStateEntity =
        store.read(identity.profileId)?.also {
            check(it.backendUrl == connection.baseUrl) { "Synchronization belongs to another server" }
        } ?: BackendSyncStateEntity(identity.profileId, checkNotNull(connection.baseUrl),
            gameRunId = snapshot.runId, localGeneration = snapshot.localGeneration()).also { store.save(it) }

    private suspend fun uploadWorld(identity: ParentIdentity, saved: BackendSyncStateEntity,
        snapshot: GameSnapshot): BackendSyncStateEntity {
        var metadata = saved
        var pending = store.pending(identity.profileId, SNAPSHOT)
        if (pending == null && metadata.lastSnapshotChecksum == snapshot.checksum) return metadata
        if (pending == null && metadata.serverRevision == null) {
            val remote = try { connection.api.downloadSnapshot(SnapshotDownloadRequest(identity.profileId)) }
                catch (failure: HttpException) { if (failure.code() == 404) null else throw failure }
            if (remote != null) {
                val archive = remote.archive()
                // Recover a lost transport journal only when the cloud history is an exact prefix.
                // The cloud may still contain an archived predecessor after an offline restart.
                val matchingRun = if (archive.runId == snapshot.runId) snapshot
                    else snapshot.predecessorSnapshots().firstOrNull { it.runId == archive.runId }
                if (matchingRun == null || !archive.isHistoryPrefixOf(matchingRun) ||
                    archive.archivedRuns.any { remoteRun -> snapshot.archivedRuns.none { localRun -> remoteRun == localRun } }) {
                    throw CloudConflict("На сервере другая история. Откройте облачную копию в настройках.")
                }
                metadata = metadata.copy(serverRevision = remote.serverRevision)
                store.save(metadata)
            }
        }
        if (pending == null) {
            val request = snapshotUploadRequest(identity.profileId, snapshot, newId(), metadata.serverRevision, session.contentFingerprint)
            pending = PendingBackendRequestEntity(identity.profileId, SNAPSHOT, request.uploadId, BackendJson.encodeToString(request))
            store.stage(pending)
        }
        val request = pending.forDevice<SnapshotUploadRequest>(identity.profileId)
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

    /** A late reply belongs to the archived run; it never applies that run's state to the live game. */
    private suspend fun finishArchivedRequests(identity: ParentIdentity, saved: BackendSyncStateEntity,
        archived: GameSnapshot): BackendSyncStateEntity {
        var metadata = saved
        if (store.pending(identity.profileId, SNAPSHOT) != null) metadata = uploadWorld(identity, metadata, archived)
        if (store.pending(identity.profileId, ANALYTICS) != null) metadata = uploadSkills(identity, metadata, archived)
        sendPendingAck(identity, archived.runId)
        return metadata
    }

    /** Follow explicit restart links, rather than treating every retained archive as an ancestor. */
    private fun GameSnapshot.predecessorSnapshots(): List<GameSnapshot> {
        val result = mutableListOf<GameSnapshot>()
        val visited = mutableSetOf(runId)
        var nextRun = runId
        while (true) {
            val archive = archivedRuns.firstOrNull { it.nextRunId == nextRun } ?: return result
            check(visited.add(archive.snapshot.runId)) { "Cyclic archived game runs" }
            result += archive.snapshot
            nextRun = archive.snapshot.runId
        }
    }

    private fun GameSnapshot.isHistoryPrefixOf(other: GameSnapshot): Boolean =
        runId == other.runId && history.size <= other.history.size && history.indices.all {
            HistoryCodec.encode(history[it]) == HistoryCodec.encode(other.history[it])
        }

    private suspend fun uploadSkills(identity: ParentIdentity, metadata: BackendSyncStateEntity,
        snapshot: GameSnapshot, mayReplaceRejectedBatch: Boolean = true): BackendSyncStateEntity {
        var pending = store.pending(identity.profileId, ANALYTICS)
        if (pending == null && metadata.lastAnalyticsSequence >= snapshot.historySequence && snapshot.historySequence > 0) return metadata
        if (pending == null) {
            val request = analyticsUploadRequest(identity.profileId, newId(), snapshot, session.catalog.content)
            pending = PendingBackendRequestEntity(identity.profileId, ANALYTICS, request.batchId, BackendJson.encodeToString(request))
            store.stage(pending)
        }
        val request = pending.forDevice<AnalyticsUploadRequest>(identity.profileId)
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
        check(response.schemaVersion == 1 && response.batchId == request.batchId && response.gameRunId == request.gameRunId &&
            response.acceptedThroughHistorySequence == request.throughHistorySequence &&
            response.acceptedEventIds.toSet() == request.facts.map { it.eventId }.toSet()) { "Invalid analytics acknowledgement" }
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
        snapshot: GameSnapshot): Boolean {
        if (metadata.profileId != identity.profileId || metadata.backendUrl != connection.baseUrl ||
            metadata.gameRunId != snapshot.runId || metadata.localGeneration != snapshot.localGeneration()) return false
        val current = session.exportSnapshot()
        // An explicit campaign restart preserves the same generation in its immutable archive.
        // A restore to another branch must never authorize replacement of the old pending body.
        val owner = (listOf(current) + current.predecessorSnapshots()).firstOrNull {
            it.runId == snapshot.runId && it.localGeneration() == snapshot.localGeneration()
        } ?: return false
        return snapshot.isHistoryPrefixOf(owner)
    }

    private suspend fun readSkills(identity: ParentIdentity, metadata: BackendSyncStateEntity,
        snapshot: GameSnapshot): BackendSyncStateEntity {
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

    private suspend fun requireCurrentSkillWorld(expected: GameSnapshot) {
        val current = session.exportSnapshot()
        if (current.runId != expected.runId || current.localGeneration() != expected.localGeneration() ||
            current.historySequence < expected.historySequence) {
            mutableState.value = mutableState.value.copy(skills = null, skillsGeneration = null)
            throw SkillsAwaitingWorldTransition()
        }
    }

    private suspend fun receiveRewards(identity: ParentIdentity, runId: String, generation: String): Boolean {
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

    private suspend fun sendPendingAck(identity: ParentIdentity, runId: String) {
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
            val before = session.exportSnapshot()
            val remote = connection.api.downloadSnapshot(SnapshotDownloadRequest(identity.profileId))
            val archive = remote.archive()
            require(archive.rulesId == null || archive.rulesId == session.catalog.rules.id) { "Incompatible game rules" }
            val id = newId()
            restoreCandidate = RestoreCandidate(id, remote, archive,
                RestoreGuard(before.state.engine?.revision, before.historySequence, session.catalog.rules.id), before.localGeneration())
            CloudRestorePreview(id, archive.state.pet.name, archive.state.engine?.day,
                archive.state.economy.availableBalance, archive.state.economy.savingsBalance)
        }
    }

    override fun dismissRestore(previewId: String) {
        if (restoreCandidate?.id == previewId) restoreCandidate = null
    }

    override suspend fun restore(previewId: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val candidate = checkNotNull(restoreCandidate?.takeIf { it.id == previewId }) { "Reload the cloud preview" }
            val identity = identity()
            val current = session.exportSnapshot()
            check(current.localGeneration() == candidate.generation) { "World changed since the preview" }
            // Replacing an old uncommitted intent is permitted only by a new explicit confirmation.
            store.pending(identity.profileId, RESTORE)?.let { store.clear(it.profileId, it.kind, it.requestId) }
            store.stage(PendingBackendRequestEntity(identity.profileId, RESTORE, candidate.id,
                BackendJson.encodeToString(RestoreIntent(candidate.response, candidate.generation))))
            restoreCandidate = null
            try {
                session.restoreSnapshot(candidate.archive, candidate.guard)
                recoverRestore(identity, metadata(identity, current), session.exportSnapshot())
                mutableState.value = CloudSyncState(message = "Облачная копия восстановлена.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failed(failure); throw failure }
        }
    }

    /** Recover a crash after world replacement but before transport bookkeeping, without restoring twice. */
    private suspend fun recoverRestore(identity: ParentIdentity, metadata: BackendSyncStateEntity,
        local: GameSnapshot): BackendSyncStateEntity {
        val pending = store.pending(identity.profileId, RESTORE) ?: return metadata
        val intent = BackendJson.decodeFromString<RestoreIntent>(pending.payload)
        if (local.localGeneration() == intent.previousGeneration) {
            // A rejected guard or a process death before the transaction did not replace the world.
            store.clear(identity.profileId, RESTORE, pending.requestId)
            return metadata
        }
        val remote = intent.response.archive()
        // A confirmed restore can commit immediately before an offline rewind. Its receipt
        // remains in the predecessor archive and still recovers the same server revision.
        val predecessors = local.predecessorSnapshots()
        val restored = (listOf(local) + predecessors).firstOrNull { candidate ->
            val marker = candidate.history.getOrNull(remote.history.size)
            candidate.runId == remote.runId && candidate.localGeneration() != intent.previousGeneration &&
                marker?.type == AuditType.RESTORED &&
                marker.after?.let(HistoryCodec::encodeState) == HistoryCodec.encodeState(remote.state) &&
                remote.isHistoryPrefixOf(candidate)
        }
        if (restored == null && predecessors.any { it.localGeneration() == intent.previousGeneration }) {
            // The intent never replaced the old world, which was subsequently archived.
            store.clear(identity.profileId, RESTORE, pending.requestId)
            return metadata
        }
        checkNotNull(restored) {
            "Восстановление было прервано. Снова откройте облачную копию в настройках."
        }
        return metadata.copy(serverRevision = intent.response.serverRevision, lastSnapshotChecksum = remote.checksum,
            gameRunId = restored.runId, localGeneration = restored.localGeneration(), lastAnalyticsSequence = 0,
            rewardFetchCursor = null, skillsPayload = null, lastSyncedAtEpochMs = null).also { store.replaceAfterRestore(it) }
    }

    private fun SnapshotDownloadResponse.archive(): GameSnapshot {
        check(schemaVersion == 1 && serverRevision >= 1 && currentContentFingerprint.isNotBlank()) { "Invalid snapshot envelope" }
        return HistoryCodec.decodeSnapshot(snapshotJson).also { check(it.runId == gameRunId) { "Snapshot run mismatch" } }
    }

    private fun failed(failure: Exception): CloudSyncResult {
        if (mutableState.value.skillsPhase == SkillSyncPhase.SYNCING) skillFailure(failure)
        val http = (failure as? HttpException)?.code()
        val retryable = failure is IOException || http == 408 || http == 429 || http != null && http >= 500
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
        val archive: GameSnapshot, val guard: RestoreGuard, val generation: String)
    @Serializable private data class RestoreIntent(val response: SnapshotDownloadResponse, val previousGeneration: String)
    private class CloudConflict(message: String) : IllegalStateException(message)
    private class SkillsAwaitingWorldTransition : IllegalStateException("Skill refresh awaits the current game generation")
    /** Adapt old frozen bodies at the transport boundary without changing their intent or receipt IDs. */
    private inline fun <reified T> PendingBackendRequestEntity.forDevice(deviceId: String): T {
        check(profileId == deviceId) { "Pending request belongs to another device" }
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
