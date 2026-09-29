package ru.nksk.lctapp.data.backend

import ru.nksk.lctapp.domain.backend.*

/** One suspend boundary for every HTTP endpoint; preserves callers' immutable bodies and keys. */
internal class RateLimitedBackendApi(
    private val delegate: BackendApi,
    private val limits: BackendRateLimit,
    private val backendUrl: String,
) : BackendApi {
    override suspend fun parentMaterials() =
        limits.execute(backendUrl) { delegate.parentMaterials() }

    override suspend fun registerProfile(requestId: String, body: RegisterProfileRequest) =
        limits.execute(backendUrl) { delegate.registerProfile(requestId, body) }

    override suspend fun uploadSnapshot(requestId: String, body: SnapshotUploadRequest) =
        limits.execute(backendUrl) { delegate.uploadSnapshot(requestId, body) }

    override suspend fun downloadSnapshot(body: SnapshotDownloadRequest) =
        limits.execute(backendUrl) { delegate.downloadSnapshot(body) }

    override suspend fun uploadAnalytics(requestId: String, body: AnalyticsUploadRequest) =
        limits.execute(backendUrl) { delegate.uploadAnalytics(requestId, body) }

    override suspend fun skills(body: SkillAssessmentsRequest) =
        limits.execute(backendUrl) { delegate.skills(body) }

    override suspend fun parentRewards(body: PullParentRewardsRequest) =
        limits.execute(backendUrl) { delegate.parentRewards(body) }

    override suspend fun acknowledgeParentRewards(requestId: String, body: AckParentRewardsRequest) =
        limits.execute(backendUrl) { delegate.acknowledgeParentRewards(requestId, body) }
}
