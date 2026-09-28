package ru.nksk.lctapp.data.backend

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import ru.nksk.lctapp.domain.backend.*

/** Device identity is part of each JSON body. Operation keys only deduplicate writes. */
internal interface BackendApi {
    @POST("api/pets")
    suspend fun registerProfile(@Header("Idempotency-Key") requestId: String,
        @Body body: RegisterProfileRequest): RegisterProfileResponse

    @PUT("v1/profiles/snapshot")
    suspend fun uploadSnapshot(@Header("Idempotency-Key") requestId: String,
        @Body body: SnapshotUploadRequest): SnapshotUploadResponse

    @POST("v1/profiles/snapshot/download")
    suspend fun downloadSnapshot(@Body body: SnapshotDownloadRequest): SnapshotDownloadResponse

    @POST("v1/profiles/analytics")
    suspend fun uploadAnalytics(@Header("Idempotency-Key") requestId: String,
        @Body body: AnalyticsUploadRequest): AnalyticsUploadResponse

    @POST("v1/profiles/skills/query")
    suspend fun skills(@Body body: SkillAssessmentsRequest): SkillAssessmentsResponse

    @POST("v1/profiles/rewards/pull")
    suspend fun parentRewards(@Body body: PullParentRewardsRequest): ParentRewardsResponse

    @POST("v1/profiles/rewards/ack")
    suspend fun acknowledgeParentRewards(@Header("Idempotency-Key") requestId: String,
        @Body body: AckParentRewardsRequest): AckParentRewardsResponse

}
