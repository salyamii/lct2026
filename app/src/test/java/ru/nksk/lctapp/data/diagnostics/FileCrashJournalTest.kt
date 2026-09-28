package ru.nksk.lctapp.data.diagnostics

import java.io.File
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileCrashJournalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun savedCrashSurvivesJournalRecreation() {
        val directory = temporary.newFolder("reports")
        FileCrashJournal(directory, "test build").apply {
            updateContext("route=day chapter=5 age=SENIOR")
            recordCrash(Thread("crashed-worker"), IllegalStateException("original-crash-marker"))
        }

        val exported = StringWriter().also { FileCrashJournal(directory, "test build").writeReport(it) }.toString()

        assertTrue(exported.contains("original-crash-marker"))
        assertTrue(exported.contains("crashed-worker"))
        assertTrue(exported.contains("route=day chapter=5 age=SENIOR"))
        assertTrue(exported.contains(IllegalStateException::class.java.name))
    }

    @Test fun recordingRetainsAtMostFourReportsAndLeavesOtherFilesAlone() {
        val directory = temporary.newFolder("reports")
        val unrelated = File(directory, "unrelated.txt").apply { writeText("keep this file") }
        val journal = FileCrashJournal(directory, "test build")
        repeat(9) { index ->
            // No thread is started: this exercises only the JVM journal API.
            journal.recordCrash(Thread("worker-$index"), IllegalStateException("crash-$index"))
            assertTrue(reports(directory).size <= 4)
        }

        assertEquals(4, reports(directory).size)
        assertEquals("keep this file", unrelated.readText())
    }

    @Test(timeout = 5_000) fun everyStoredFileIsBoundedEvenWhenTheHeaderAndStackAreHuge() {
        val directory = temporary.newFolder("reports")
        val hugeHeader = "build context ".repeat(10_000)
        val exception = IllegalStateException("long message ".repeat(10_000)).apply {
            stackTrace = Array(200) { index ->
                StackTraceElement("LongClass".repeat(160), "longMethod".repeat(160), "Source.kt", index)
            }
        }
        FileCrashJournal(directory, "test build").recordCrash(Thread("large-stack"), exception)
        FileCrashJournal(directory, hugeHeader).recordCrash(Thread("large-header"), exception)

        assertEquals(2, reports(directory).size)
        reports(directory).forEach { file ->
            val stored = file.readText(Charsets.UTF_8)
            assertFalse(stored.isEmpty())
            assertTrue("The stored file itself must be bounded, not only its export", stored.length <= 64 * 1024)
        }
    }

    @Test(timeout = 5_000) fun deepCyclicAndSuppressedExceptionsFinishWithABoundedReport() {
        val directory = temporary.newFolder("reports")
        val root = IllegalStateException("root-cause-marker")
        val cycle = IllegalArgumentException("cycle-cause-marker")
        root.initCause(cycle)
        cycle.initCause(root)
        var deep: Throwable = IllegalStateException("deep-leaf")
        repeat(40) { index -> deep = IllegalStateException("depth-$index " + "detail ".repeat(400), deep) }
        root.addSuppressed(deep)
        root.addSuppressed(IllegalStateException("second-suppressed", cycle))
        repeat(30) { root.addSuppressed(IllegalStateException("extra-suppressed-$it")) }
        root.stackTrace = Array(180) { StackTraceElement("Example", "frame$it", "Source.kt", it) }

        FileCrashJournal(directory, "test build").recordCrash(Thread("complex-crash"), root)

        val stored = reports(directory).single().readText(Charsets.UTF_8)
        assertTrue(stored.contains("root-cause-marker"))
        assertTrue(stored.contains("cycle-cause-marker"))
        assertTrue(stored.length <= 64 * 1024)
    }

    @Test fun failureToReadThrowableDetailsKeepsTheMinimalCrashReport() {
        val directory = temporary.newFolder("reports")
        val hostile = object : RuntimeException("unavailable details") {
            override fun getStackTrace(): Array<StackTraceElement> = throw OutOfMemoryError("simulated detail failure")
        }
        FileCrashJournal(directory, "test build").apply {
            updateContext("route=summary")
            recordCrash(Thread("failed-details"), hostile)
        }

        val stored = reports(directory).single().readText(Charsets.UTF_8)
        assertTrue(stored.contains("test build"))
        assertTrue(stored.contains("failed-details"))
        assertTrue(stored.contains(hostile.javaClass.name))
        assertTrue(stored.contains("route=summary"))
        assertTrue(stored.length <= 64 * 1024)
    }

    @Test fun exportAlsoBoundsAnOversizedReportCreatedByAnEarlierVersion() {
        val directory = temporary.newFolder("reports")
        File(directory, "crash-1-1.txt").writeText("Ω".repeat(100_000) + "unbounded-tail-marker")

        val exported = StringWriter().also { FileCrashJournal(directory, "test build").writeReport(it) }.toString()

        assertTrue(exported.contains("crash-1-1.txt"))
        assertFalse(exported.contains("unbounded-tail-marker"))
        assertEquals(64 * 1024, exported.count { it == 'Ω' })
    }

    private fun reports(directory: File) = directory.listFiles()
        .orEmpty().filter { it.name.startsWith("crash-") && it.extension == "txt" }
}
