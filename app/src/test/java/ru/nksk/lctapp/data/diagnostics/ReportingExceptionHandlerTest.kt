package ru.nksk.lctapp.data.diagnostics

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ReportingExceptionHandlerTest {
    @Test fun forwardsTheOriginalCrashThenTerminates() {
        val thread = Thread("original-thread")
        val original = IllegalStateException("original")
        val calls = mutableListOf<String>()
        val handler = ReportingExceptionHandler(
            previous = Thread.UncaughtExceptionHandler { receivedThread, receivedError ->
                assertSame(thread, receivedThread)
                assertSame(original, receivedError)
                calls += "previous"
            },
            record = { receivedThread, receivedError ->
                assertSame(thread, receivedThread)
                assertSame(original, receivedError)
                calls += "record"
            },
            terminate = { calls += "terminate" },
        )

        handler.uncaughtException(thread, original)

        assertEquals(listOf("record", "previous", "terminate"), calls)
    }

    @Test fun writeFailureCannotReplaceTheOriginalCrashOrSkipTermination() {
        val original = IllegalStateException("original")
        var forwarded: Throwable? = null
        var terminations = 0
        val handler = ReportingExceptionHandler(
            previous = Thread.UncaughtExceptionHandler { _, error -> forwarded = error },
            record = { _, _ -> throw IOException("disk is unavailable") },
            terminate = { terminations++ },
        )

        handler.uncaughtException(Thread("worker"), original)

        assertSame(original, forwarded)
        assertEquals(1, terminations)
    }

    @Test fun terminatesEvenIfBothRecordingAndPreviousHandlerFail() {
        val original = IllegalStateException("original")
        val delegationFailure = IllegalArgumentException("previous handler failed")
        var terminations = 0
        val handler = ReportingExceptionHandler(
            previous = Thread.UncaughtExceptionHandler { _, error ->
                assertSame(original, error)
                throw delegationFailure
            },
            record = { _, _ -> throw OutOfMemoryError("simulated logging failure") },
            terminate = { terminations++ },
        )

        try {
            handler.uncaughtException(Thread("worker"), original)
            fail("The previous handler's failure should propagate after termination")
        } catch (error: IllegalArgumentException) {
            assertSame(delegationFailure, error)
        }
        assertEquals(1, terminations)
    }

    @Test fun missingPreviousHandlerStillTerminatesAfterWriteFailure() {
        var terminations = 0
        val handler = ReportingExceptionHandler(null,
            record = { _, _ -> throw IOException("disk is unavailable") },
            terminate = { terminations++ })

        handler.uncaughtException(Thread("worker"), IllegalStateException("original"))

        assertEquals(1, terminations)
    }

    @Test fun repeatedEntryRecordsOnlyOnceAndStillDelegatesBothOriginalErrors() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val recorded = mutableListOf<Throwable>()
        val forwarded = mutableListOf<Throwable>()
        var terminations = 0
        val handler = ReportingExceptionHandler(
            Thread.UncaughtExceptionHandler { _, error -> forwarded += error },
            record = { _, error -> recorded += error }, terminate = { terminations++ })

        handler.uncaughtException(Thread("first"), first)
        handler.uncaughtException(Thread("second"), second)

        assertEquals(1, recorded.size)
        assertSame(first, recorded.single())
        assertEquals(2, forwarded.size)
        assertSame(first, forwarded[0])
        assertSame(second, forwarded[1])
        assertEquals(2, terminations)
    }

    @Test(timeout = 2_000) fun reentrantCrashDoesNotEnterRecordingAgain() {
        val original = IllegalStateException("original")
        val nested = IllegalArgumentException("nested")
        val forwarded = mutableListOf<Throwable>()
        var recordings = 0
        var terminations = 0
        lateinit var handler: ReportingExceptionHandler
        handler = ReportingExceptionHandler(
            Thread.UncaughtExceptionHandler { _, error -> forwarded += error },
            record = { thread, _ ->
                recordings++
                handler.uncaughtException(thread, nested)
            }, terminate = { terminations++ })

        handler.uncaughtException(Thread("worker"), original)

        assertEquals(1, recordings)
        assertEquals(2, forwarded.size)
        assertSame(nested, forwarded[0])
        assertSame(original, forwarded[1])
        assertEquals(2, terminations)
    }
}
