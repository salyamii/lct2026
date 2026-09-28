package ru.nksk.lctapp.data.backend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BackendRateLimitTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun deadlineIsReadFromDiskAfterTheOriginalDataStoreIsClosed() = runTest {
        val file = temporary.newFolder().resolve("retry.preferences_pb")
        val firstJob = SupervisorJob()
        val first = PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file }
        try {
            try { BackendRateLimit(first) { 1_000L }.execute(Backend) { throw limited("60") } }
            catch (_: HttpException) { }
        } finally { firstJob.cancelAndJoin() }

        val secondJob = SupervisorJob()
        val second = PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO)) { file }
        try {
            var calls = 0
            try { BackendRateLimit(second) { 2_000L }.execute(Backend) { calls++ }; fail("Persisted deadline") }
            catch (error: BackendRetryDelayedException) { assertEquals(61_000L, error.retryAtEpochMillis) }
            assertEquals(0, calls)
        } finally { secondJob.cancelAndJoin() }
    }

    @Test fun secondsDeadlineSurvivesRecreationAndPreservesTheOriginalRequest() = runTest {
        val preferences = MemoryPreferences()
        var now = 1_000L
        var calls = 0
        val requests = mutableListOf<Pair<String, String>>()
        val original = "same-key" to "same-frozen-body"
        suspend fun send(): String {
            calls++
            requests += original
            if (calls == 1) throw limited("3600")
            return "accepted"
        }
        val gate = BackendRateLimit(preferences) { now }
        try { gate.execute(Backend, ::send); fail("Expected 429") } catch (error: HttpException) {
            assertEquals(429, error.code())
        }
        val recreated = BackendRateLimit(preferences) { now }
        now = 3_600_999L
        try { recreated.execute(Backend, ::send); fail("No HTTP before deadline") }
        catch (error: BackendRetryDelayedException) { assertEquals(3_601_000L, error.retryAtEpochMillis) }
        assertEquals(1, calls)
        now = 3_601_000L
        assertEquals("accepted", recreated.execute(Backend, ::send))
        assertEquals(listOf(original, original), requests)
    }

    @Test fun queuedManualRequestCannotPassAfterAnotherCallReceives429() = runTest {
        val gate = BackendRateLimit(MemoryPreferences()) { 1_000L }
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val first = async {
            try { gate.execute(Backend) { entered.complete(Unit); response.await(); throw limited("60") } }
            catch (_: HttpException) { }
        }
        entered.await()
        var secondCalls = 0
        val second = async {
            try { gate.execute(Backend) { secondCalls++ }; fail("Must wait for server deadline") }
            catch (_: BackendRetryDelayedException) { }
        }
        runCurrent()
        response.complete(Unit)
        first.await(); second.await()
        assertEquals(0, secondCalls)
    }

    @Test fun httpDateUsesServerDateToAllowForDeviceClockSkew() {
        assertEquals(61_000L, retryAfterDeadline("Tue, 29 Sep 2026 10:01:00 GMT",
            "Tue, 29 Sep 2026 10:00:00 GMT", 1_000L))
        assertEquals(1_000L, retryAfterDeadline("Tue, 29 Sep 2026 09:59:00 GMT",
            "Tue, 29 Sep 2026 10:00:00 GMT", 1_000L))
    }

    @Test fun httpDateWithoutServerDateUsesItsAbsoluteDeadline() {
        assertEquals(1_790_676_060_000L,
            retryAfterDeadline("Tue, 29 Sep 2026 10:01:00 GMT", null, 1_000L))
    }

    @Test fun invalidHeaderFallsBackWithoutOverflowingOrTreatingNegativeSecondsAsADeadline() {
        for (value in listOf(null, "", "-1", "1.5", "tomorrow", "Tue, 99 Sep 2026 10:00:00 GMT")) {
            assertEquals(31_000L, retryAfterDeadline(value, null, 1_000L))
        }
        assertEquals(1_000L, retryAfterDeadline("0", null, 1_000L))
        assertEquals(Long.MAX_VALUE, retryAfterDeadline("999999999999999999999999", null, 1_000L))
        assertEquals(Long.MAX_VALUE, retryAfterDeadline("60", null, Long.MAX_VALUE - 10))
    }

    @Test fun differentBackendDoesNotInheritTheDeadline() = runTest {
        val gate = BackendRateLimit(MemoryPreferences()) { 1_000L }
        try { gate.execute(Backend) { throw limited("60") } } catch (_: HttpException) { }
        assertEquals("other", gate.execute("https://other.example.test/") { "other" })
    }

    @Test fun storageFailureFailsClosedBeforeAnyHttpCall() = runTest {
        val preferences = object : DataStore<Preferences> {
            override val data = kotlinx.coroutines.flow.flow<Preferences> { throw IOException("disk") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("disk")
        }
        var calls = 0
        try { BackendRateLimit(preferences) { 0L }.execute(Backend) { calls++ }; fail("Storage error") }
        catch (_: IOException) { }
        assertEquals(0, calls)
    }

    @Test fun cancellationAndOtherHttpErrorsDoNotCreateRateLimitState() = runTest {
        val preferences = MemoryPreferences()
        val gate = BackendRateLimit(preferences) { 0L }
        try { gate.execute(Backend) { throw CancellationException("cancel") }; fail("Cancellation") }
        catch (_: CancellationException) { }
        try { gate.execute(Backend) { throw httpFailure(409, "60") }; fail("Conflict") }
        catch (error: HttpException) { assertEquals(409, error.code()) }
        assertEquals(emptyPreferences(), preferences.data.value)
        assertEquals("ok", gate.execute(Backend) { "ok" })
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(data.value).also { data.value = it } }
    }

    private fun limited(retryAfter: String) = httpFailure(429, retryAfter)
    private fun httpFailure(code: Int, retryAfter: String): HttpException {
        val raw = okhttp3.Response.Builder().request(okhttp3.Request.Builder().url(Backend).build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(code).message("Error")
            .headers(Headers.headersOf("Retry-After", retryAfter)).build()
        return HttpException(Response.error<Unit>("{}".toResponseBody("application/json".toMediaType()), raw))
    }

    private companion object { const val Backend = "https://backend.example.test/" }
}
