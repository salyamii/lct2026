package ru.nksk.lctapp.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.Writer
import kotlinx.coroutines.CancellationException

/** Android keeps a limited history; unavailable exit traces do not prevent local log export. */
internal fun writeSystemExitReport(context: Context, writer: Writer) {
    writer.write("\n===== Android process exit history =====\n")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        writer.write("Системная история завершений доступна начиная с Android 11.\n")
        return
    }
    val exits = try {
        context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, 0, 6).orEmpty()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        writer.write("Системная история недоступна: ${error.javaClass.simpleName}\n")
        return
    }
    if (exits.isEmpty()) writer.write("Android пока не сохранил сведения о завершении процесса.\n")
    var tracesRemaining = 2
    for (exit in exits) {
        writer.write("\nTimestamp: ${exit.timestamp}\nReason: ${exitReason(exit.reason)} (${exit.reason})\n")
        writer.write("Status: ${exit.status}; importance: ${exit.importance}\n")
        writer.write("Last sampled memory KB: PSS=${exit.pss}; RSS=${exit.rss}\n")
        writer.write("Description: ${exit.description.orEmpty().take(1500)}\n")
        // Native tombstones can be protobuf data. Only ANR traces are copied as readable text.
        if (exit.reason == ApplicationExitInfo.REASON_ANR && tracesRemaining-- > 0) {
            val trace = readAnrTrace(exit)
            writer.write(trace ?: "ANR trace unavailable\n")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun readAnrTrace(exit: ApplicationExitInfo): String? = try {
    exit.traceInputStream?.bufferedReader(Charsets.UTF_8)?.use { input ->
        val buffer = CharArray(32 * 1024)
        var count = 0
        while (count < buffer.size) {
            val size = input.read(buffer, count, buffer.size - count)
            if (size < 0) break
            count += size
        }
        buildString {
            append("ANR trace:\n")
            append(buffer, 0, count)
            if (input.read() != -1) append("\n[Trace truncated]")
            append('\n')
        }
    }
} catch (error: Exception) {
    if (error is CancellationException) throw error
    null
}

@RequiresApi(Build.VERSION_CODES.R)
private fun exitReason(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_ANR -> "ANR"
    ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
    ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
    ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
    ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
    ApplicationExitInfo.REASON_OTHER -> "OTHER"
    else -> "UNKNOWN"
}
