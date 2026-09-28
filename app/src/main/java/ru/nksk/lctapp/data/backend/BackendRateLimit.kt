package ru.nksk.lctapp.data.backend

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** Device transport preference, outside world snapshots and OS backup. */
@Singleton
internal class BackendRateLimit internal constructor(
    private val preferences: DataStore<Preferences>,
    private val nowMillis: () -> Long,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        PreferenceDataStoreFactory.create {
            File(context.noBackupFilesDir, "backend_retry.preferences_pb")
        },
        System::currentTimeMillis,
    )

    private val requests = Mutex()
    private val observedDeadlines = mutableMapOf<String, Long>()

    /** All calls share this gate, including registration and manual skill refresh. No sleeping worker. */
    suspend fun <T> execute(backendUrl: String, request: suspend () -> T): T = requests.withLock {
        val key = longPreferencesKey("retry_after_" + MessageDigest.getInstance("SHA-256")
            .digest(backendUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
        val deadline = maxOf(preferences.data.first()[key] ?: 0L, observedDeadlines[backendUrl] ?: 0L)
        if (deadline > nowMillis()) throw BackendRetryDelayedException(deadline)
        try {
            request()
        } catch (failure: HttpException) {
            if (failure.code() == 429) {
                val headers = failure.response()?.headers()
                val retryAt = retryAfterDeadline(headers?.get("Retry-After"), headers?.get("Date"), nowMillis())
                // Even a failed disk write must not allow another call in this process through.
                observedDeadlines[backendUrl] = retryAt
                // Once received, keep this small server deadline durable even if the caller leaves.
                withContext(NonCancellable) {
                    preferences.edit { saved -> saved[key] = maxOf(saved[key] ?: 0L, retryAt) }
                }
                currentCoroutineContext().ensureActive()
            }
            throw failure
        }
    }
}

internal class BackendRetryDelayedException(val retryAtEpochMillis: Long) :
    IOException("Backend retry is deferred until $retryAtEpochMillis")

/** RFC 9110: delay-seconds or HTTP-date; server Date avoids relying on a synchronized device clock. */
internal fun retryAfterDeadline(value: String?, serverDate: String?, nowMillis: Long): Long {
    val header = value?.trim().orEmpty()
    if (header.isNotEmpty() && header.all { it in '0'..'9' }) {
        val seconds = header.toLongOrNull() ?: return Long.MAX_VALUE
        return addDelay(nowMillis, if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L)
    }
    val date = parseHttpDate(header)
    if (date != null) {
        val reference = serverDate?.let(::parseHttpDate)
        return if (reference == null) maxOf(nowMillis, date)
        else addDelay(nowMillis, (date - reference).coerceAtLeast(0L))
    }
    // A malformed/missing header falls back to the existing initial worker backoff.
    return addDelay(nowMillis, 30_000L)
}

private fun addDelay(now: Long, delay: Long): Long =
    if (now > Long.MAX_VALUE - delay) Long.MAX_VALUE else now + delay

private fun parseHttpDate(value: String): Long? {
    // java.time would require API 26/desugaring; these formats are also supported on minSdk 24.
    for (pattern in listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEEE, dd-MMM-yy HH:mm:ss zzz", "EEE MMM d HH:mm:ss yyyy")) {
        val position = ParsePosition(0)
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }
        val date = format.parse(value, position)
        if (date != null && position.index == value.length) return date.time
    }
    return null
}
