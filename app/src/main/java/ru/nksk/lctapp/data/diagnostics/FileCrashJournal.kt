package ru.nksk.lctapp.data.diagnostics

import java.io.File
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.concurrent.atomic.AtomicBoolean

/** Small independent files: crash handling must never wait for Room, coroutines or a UI lock. */
internal class FileCrashJournal(private val directory: File, private val header: String) {
    @Volatile private var context = "Context not yet available"
    @Volatile private var breadcrumbs: List<String> = emptyList()

    @Synchronized fun updateContext(value: String) {
        if (context == value) return
        context = value.take(2048)
        breadcrumbs = (breadcrumbs + "${System.currentTimeMillis()} $context").takeLast(20)
    }

    fun recordCrash(thread: Thread, error: Throwable) {
        directory.mkdirs()
        val file = File.createTempFile("crash-${System.currentTimeMillis()}-", ".txt", directory)
        file.outputStream().use { output ->
            val writer = LimitedReportWriter(OutputStreamWriter(output, Charsets.UTF_8), MAX_REPORT_CHARS)
            writer.write(header)
            writer.write("\nCrash at epoch ms: ${System.currentTimeMillis()}\nThread: ${thread.name.take(100)}\n")
            writer.write("Exception: ${error.javaClass.name}\n$context\n")
            // Persist a minimal report before asking Throwable for potentially expensive stack data.
            writer.flush()
            try {
                val runtime = Runtime.getRuntime()
                writer.write("Heap bytes: used=${runtime.totalMemory() - runtime.freeMemory()} max=${runtime.maxMemory()}\n")
                writer.write("\nRecent context:\n")
                breadcrumbs.forEach { writer.write(it); writer.write("\n") }
                writeThrowable(writer, error, "", 0)
            } catch (_: Throwable) {
                // OOM or a hostile Throwable must not replace the original crash or recurse.
                writer.write("\n[Additional crash details unavailable]\n")
            }
            writer.flush()
            output.fd.sync()
        }
        prune()
    }

    fun writeReport(writer: Writer) {
        writer.write("Лапки и монеты - диагностический журнал\n$header\n")
        writer.write("Exported at epoch ms: ${System.currentTimeMillis()}\nCurrent context: $context\n")
        val files = reports()
        if (files.isEmpty()) writer.write("\nСохранённых отчётов о падении пока нет.\n")
        files.forEach { file ->
            writer.write("\n===== ${file.name} =====\n")
            file.reader(Charsets.UTF_8).use { input ->
                val buffer = CharArray(4096)
                var remaining = MAX_REPORT_CHARS
                while (remaining > 0) {
                    val size = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (size < 0) break
                    writer.write(buffer, 0, size)
                    remaining -= size
                }
                if (input.read() != -1) writer.write("\n[Report truncated]\n")
            }
        }
    }

    private fun reports() = directory.listFiles { file -> file.name.startsWith("crash-") && file.extension == "txt" }
        .orEmpty().sortedByDescending { it.name }.take(MAX_REPORTS)

    private fun prune() {
        directory.listFiles { file -> file.name.startsWith("crash-") && file.extension == "txt" }
            .orEmpty().sortedByDescending { it.name }.drop(MAX_REPORTS).forEach { it.delete() }
    }

    private fun writeThrowable(writer: LimitedReportWriter, error: Throwable, prefix: String, depth: Int) {
        if (writer.remaining == 0) return
        if (depth >= 6) { writer.write("[Further causes omitted]\n"); return }
        writer.write("$prefix${error.javaClass.name}: ${error.message.orEmpty().take(1500)}\n")
        val frames = error.stackTrace
        frames.take(80).forEach { writer.write("    at $it\n") }
        if (frames.size > 80) writer.write("    [Further frames omitted]\n")
        error.suppressed.take(2).forEach { writeThrowable(writer, it, "Suppressed: ", depth + 1) }
        error.cause?.takeUnless { it === error }?.let { writeThrowable(writer, it, "Caused by: ", depth + 1) }
    }

    companion object {
        const val MAX_REPORTS = 4
        const val MAX_REPORT_CHARS = 64 * 1024
    }
}

/** Caps the stored file too, rather than merely shortening it when the user exports it. */
private class LimitedReportWriter(private val target: Writer, limit: Int) : Writer() {
    var remaining = limit
        private set

    override fun write(buffer: CharArray, offset: Int, length: Int) {
        val count = minOf(length, remaining)
        if (count == 0) return
        target.write(buffer, offset, count)
        remaining -= count
    }

    override fun write(value: String, offset: Int, length: Int) {
        val count = minOf(length, remaining)
        if (count == 0) return
        target.write(value, offset, count)
        remaining -= count
    }

    override fun flush() = target.flush()
    override fun close() = target.close()
}

/** Always delegate to Android's existing handler, including when saving the report fails. */
internal class ReportingExceptionHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val record: (Thread, Throwable) -> Unit,
    private val terminate: () -> Unit,
) : Thread.UncaughtExceptionHandler {
    private val reporting = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            if (reporting.compareAndSet(false, true)) try { record(thread, error) } catch (_: Throwable) { }
        } finally {
            try { previous?.uncaughtException(thread, error) } finally { terminate() }
        }
    }
}
