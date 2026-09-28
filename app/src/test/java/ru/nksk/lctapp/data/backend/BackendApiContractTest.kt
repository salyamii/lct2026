package ru.nksk.lctapp.data.backend

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.backend.*

class BackendApiContractTest {
    @Test fun everyCallUsesTheDeviceIdInJsonWithoutIdentityInHeadersUrlOrQuery() = runTest {
        val mediaType = "application/json".toMediaType()
        val captured = mutableListOf<Pair<Request, String>>()
        val responses = mapOf(
            "/api/pets" to BackendJson.encodeToString(RegisterProfileResponse(DeviceId)),
            "/v1/profiles/snapshot" to BackendJson.encodeToString(SnapshotUploadResponse("upload", "run", 1, "checksum")),
            "/v1/profiles/snapshot/download" to BackendJson.encodeToString(SnapshotDownloadResponse("run", 1, "content", "snapshot bytes")),
            "/v1/profiles/analytics" to BackendJson.encodeToString(AnalyticsUploadResponse("batch", "run", 0, emptyList())),
            "/v1/profiles/skills/query" to BackendJson.encodeToString(SkillAssessmentsResponse("run", 0,
                SkillId.entries.map { SkillAssessmentDto(it, SkillStatus.NO_DATA, "test-policy") })),
            "/v1/profiles/rewards/pull" to BackendJson.encodeToString(ParentRewardsResponse(DeviceId, "run", emptyList(), 0, false)),
            "/v1/profiles/rewards/ack" to BackendJson.encodeToString(AckParentRewardsResponse("run", listOf("application"))),
        )
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { checkNotNull(request.body).writeTo(it) }.readUtf8()
            synchronized(captured) { captured += request to body }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(checkNotNull(responses[request.url.encodedPath]).toResponseBody(mediaType)).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://backend.example.test/").client(client)
            .addConverterFactory(BackendJson.asConverterFactory(mediaType)).build().create(BackendApi::class.java)
        val receipt = ParentRewardReceiptDto("reward", "application", "history", 1, ParentRewardOutcome.APPLIED)

        api.registerProfile("registration", RegisterProfileRequest(DeviceId, createInitialGameState().pet.registrationDto()))
        api.uploadSnapshot("upload", SnapshotUploadRequest(DeviceId, "upload", null, "run", 0, "content", 4,
            "checksum", "snapshot bytes"))
        api.downloadSnapshot(SnapshotDownloadRequest(DeviceId))
        api.uploadAnalytics("batch", analyticsUploadRequest(DeviceId, "batch", "run", 0, emptyList()))
        api.skills(SkillAssessmentsRequest(DeviceId, "run"))
        api.parentRewards(PullParentRewardsRequest(DeviceId, "run", 0))
        api.acknowledgeParentRewards("ack", AckParentRewardsRequest(DeviceId, "run", listOf(receipt)))

        assertEquals(listOf("POST", "PUT", "POST", "POST", "POST", "POST", "POST"), captured.map { it.first.method })
        assertEquals(responses.keys.toList(), captured.map { it.first.url.encodedPath })
        assertEquals(listOf("registration", "upload", null, "batch", null, null, "ack"),
            captured.map { it.first.header("Idempotency-Key") })
        captured.forEach { (request, payload) ->
            assertNull(request.header("Authorization"))
            assertNull(request.url.query)
            assertFalse(request.url.toString().contains(DeviceId))
            val json = BackendJson.parseToJsonElement(payload).jsonObject
            assertEquals(DeviceId, json.getValue("deviceId").jsonPrimitive.content)
            assertFalse(json.containsKey("installationId"))
            assertFalse(json.containsKey("credential"))
            assertFalse(json.containsKey("profileId"))
        }
    }

    private companion object { const val DeviceId = "71967c658e774f27" }
}
