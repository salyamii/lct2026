package ru.nksk.lctapp.app.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import ru.nksk.lctapp.domain.backend.CloudSyncResult

internal const val ForegroundSyncIntervalMillis = 15_000L
internal const val HistorySyncDebounceMillis = 2_000L

/** Runs only in RESUMED. A slow pass keeps at most one follow-up, without parallel HTTP passes. */
internal suspend fun runForegroundBackendSync(
    networkReturns: Flow<Unit>,
    isConnected: () -> Boolean,
    synchronize: suspend () -> CloudSyncResult,
    scheduleBackground: () -> Unit,
) {
    val timer = flow {
        while (true) {
            emit(Unit)
            delay(ForegroundSyncIntervalMillis)
        }
    }
    val network = networkReturns.catch { cause ->
        if (cause is CancellationException || cause !is Exception) throw cause
        // A platform callback limit must not stop the foreground timer.
    }
    merge(timer, network).buffer(Channel.CONFLATED).collect {
        try {
            if (!isConnected() || synchronize() == CloudSyncResult.RETRY) scheduleBackground()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            // Persistent requests retain their exact keys/bodies; WorkManager owns durable retries.
            scheduleBackground()
        }
    }
}
