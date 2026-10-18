package ru.nksk.lctapp.app.presentation

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.backend.CloudSyncResult

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundBackendSyncTest {
    @Test fun startsImmediatelyPollsEveryFifteenSecondsAndStopsWhenTheScreenPauses() = runTest {
        var calls = 0
        val work = launch { runForegroundBackendSync(emptyFlow(), { true },
            { calls++; CloudSyncResult.SUCCESS }, { error("No retry expected") }) }
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, calls)
        work.cancelAndJoin()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test fun offlinePassesUseDurableWorkAndNetworkReturnDoesNotWaitForTheTimer() = runTest {
        val network = MutableSharedFlow<Unit>()
        var online = false
        var calls = 0
        var deferred = 0
        val work = launch { runForegroundBackendSync(network, { online },
            { calls++; CloudSyncResult.SUCCESS }, { deferred++ }) }
        try {
            runCurrent()
            assertEquals(0, calls)
            assertEquals(1, deferred)
            online = true
            network.emit(Unit)
            runCurrent()
            assertEquals(1, calls)
            assertEquals(1, deferred)
        } finally { work.cancelAndJoin() }
    }

    @Test fun slowExchangeCoalescesTimerAndNetworkSignalsIntoOneFollowUp() = runTest {
        val network = MutableSharedFlow<Unit>()
        val firstReply = CompletableDeferred<Unit>()
        var calls = 0
        var active = 0
        var maxActive = 0
        val work = launch { runForegroundBackendSync(network, { true }, {
            calls++
            active++
            maxActive = maxOf(maxActive, active)
            try { if (calls == 1) firstReply.await(); CloudSyncResult.SUCCESS }
            finally { active-- }
        }, { error("No retry expected") }) }
        try {
            runCurrent()
            repeat(20) { network.emit(Unit) }
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, calls)
            firstReply.complete(Unit)
            runCurrent()
            assertEquals(2, calls)
            assertEquals(1, maxActive)
        } finally { work.cancelAndJoin() }
    }

    @Test fun retryableResultsAndFailuresQueueDurableRetriesWithoutKillingTheTimer() = runTest {
        var calls = 0
        var retries = 0
        val work = launch { runForegroundBackendSync(emptyFlow(), { true }, {
            when (++calls) {
                1 -> CloudSyncResult.RETRY
                2 -> throw IOException("Backend unavailable")
                3 -> CloudSyncResult.NEEDS_ATTENTION
                else -> CloudSyncResult.SUCCESS
            }
        }, { retries++ }) }
        try {
            runCurrent()
            assertEquals(1, retries)
            repeat(3) { advanceTimeBy(15_000); runCurrent() }
            assertEquals(4, calls)
            assertEquals(2, retries)
        } finally { work.cancelAndJoin() }
    }

    @Test fun platformCallbackFailureKeepsTheTimerAndCancellationPropagatesToTheExchange() = runTest {
        var calls = 0
        var cancelled = false
        val reply = CompletableDeferred<Unit>()
        val work = launch { runForegroundBackendSync(flow { throw SecurityException("No network callback") },
            { true }, {
                calls++
                try { reply.await(); CloudSyncResult.SUCCESS }
                finally { cancelled = true }
            }, { error("Cancellation must not become another retry") }) }
        runCurrent()
        assertEquals(1, calls)
        work.cancelAndJoin()
        assertTrue(cancelled)
    }
}
