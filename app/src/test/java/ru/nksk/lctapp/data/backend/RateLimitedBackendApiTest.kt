package ru.nksk.lctapp.data.backend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.backend.*

class RateLimitedBackendApiTest {
    @Test fun everyEndpointUsesTheSameGateAndRetryKeepsTheFrozenKeyAndBody() = runTest {
        val mediaType = "application/json".toMediaType()
        var now = 0L
        val sent = mutableListOf<Pair<String?, String>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { checkNotNull(request.body).writeTo(it) }.readUtf8()
            val first = synchronized(sent) { sent += request.header("Idempotency-Key") to body; sent.size == 1 }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (first) 429 else 200).message(if (first) "Rate limited" else "OK")
                .header("Retry-After", "60")
                .body((if (first) "{\"code\":\"RATE_LIMITED\"}" else BackendJson.encodeToString(
                    AnalyticsUploadResponse("batch", "run", 0, emptyList()))).toResponseBody(mediaType))
                .build()
        }.build()
        val delegate = Retrofit.Builder().baseUrl(Backend).client(client)
            .addConverterFactory(BackendJson.asConverterFactory(mediaType)).build().create(BackendApi::class.java)
        val preferences = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(data.value).also { data.value = it }
        }
        val api = RateLimitedBackendApi(delegate, BackendRateLimit(preferences) { now }, Backend)
        val analytics = analyticsUploadRequest("device", "batch", "run", 0, emptyList())
        try { api.uploadAnalytics("batch", analytics); fail("Expected 429") }
        catch (error: HttpException) { assertEquals(429, error.code()) }

        val blockedCalls: List<suspend () -> Unit> = listOf(
            { api.registerProfile("registration", RegisterProfileRequest("device", createInitialGameState().pet.registrationDto())); Unit },
            { api.uploadSnapshot("snapshot", SnapshotUploadRequest("device", "snapshot", null, "run", 0,
                "content", 5, "checksum", "frozen")); Unit },
            { api.downloadSnapshot(SnapshotDownloadRequest("device")); Unit },
            { api.uploadAnalytics("batch", analytics); Unit },
            { api.skills(SkillAssessmentsRequest("device", "run")); Unit },
            { api.parentRewards(PullParentRewardsRequest("device", "run", 0)); Unit },
            { api.acknowledgeParentRewards("ack", AckParentRewardsRequest("device", "run", listOf(
                ParentRewardReceiptDto("reward", "application", "history", 1, ParentRewardOutcome.APPLIED)))); Unit },
        )
        blockedCalls.forEach { call ->
            try { call(); fail("Every endpoint must honor the deadline") }
            catch (_: BackendRetryDelayedException) { }
        }
        assertEquals(1, synchronized(sent) { sent.size })
        now = 60_000L
        assertEquals("batch", api.uploadAnalytics("batch", analytics).batchId)
        assertEquals(2, sent.size)
        assertEquals(sent[0], sent[1])
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private companion object { const val Backend = "https://backend.example.test/" }
}
