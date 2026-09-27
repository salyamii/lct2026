package ru.nksk.lctapp.data.backend

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Path
import ru.nksk.lctapp.domain.backend.*

/** Proposed v1 transport contract. A public profileId never authorizes these calls. */
internal interface BackendApi {
    @POST("v1/profiles")
    suspend fun registerProfile(@Header("Authorization") authorization: String,
        @Header("Idempotency-Key") requestId: String, @Body body: RegisterProfileRequest): RegisterProfileResponse

    @PUT("v1/profiles/{profileId}/snapshot")
    suspend fun uploadSnapshot(@Path("profileId") profileId: String,
        @Header("Authorization") authorization: String, @Header("Idempotency-Key") requestId: String,
        @Body body: SnapshotUploadRequest): SnapshotUploadResponse

    @GET("v1/profiles/{profileId}/snapshot")
    suspend fun downloadSnapshot(@Path("profileId") profileId: String,
        @Header("Authorization") authorization: String): SnapshotDownloadResponse

    @POST("v1/profiles/{profileId}/analytics")
    suspend fun uploadAnalytics(@Path("profileId") profileId: String,
        @Header("Authorization") authorization: String, @Header("Idempotency-Key") requestId: String,
        @Body body: AnalyticsUploadRequest): AnalyticsUploadResponse

    @GET("v1/profiles/{profileId}/skills")
    suspend fun skills(@Path("profileId") profileId: String,
        @Header("Authorization") authorization: String, @Query("gameRunId") gameRunId: String): SkillAssessmentsResponse

    @GET("v1/profiles/{profileId}/rewards")
    suspend fun parentRewards(@Path("profileId") profileId: String,
        @Header("Authorization") authorization: String, @Query("gameRunId") gameRunId: String,
        @Query("afterSequence") afterSequence: Long, @Query("limit") limit: Int = 50): ParentRewardsResponse

    @POST("v1/profiles/{profileId}/rewards/ack")
    suspend fun acknowledgeParentRewards(@Path("profileId") profileId: String,
        @Header("Authorization") authorization: String, @Header("Idempotency-Key") requestId: String,
        @Body body: AckParentRewardsRequest): AckParentRewardsResponse

}
