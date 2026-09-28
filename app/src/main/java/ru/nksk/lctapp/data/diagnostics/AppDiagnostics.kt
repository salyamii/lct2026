package ru.nksk.lctapp.data.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.Writer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess
import ru.nksk.lctapp.BuildConfig

/** Process-local diagnostic context. Never reads the game database from a crash handler. */
@Singleton
class AppDiagnostics @Inject constructor(@ApplicationContext context: Context) {
    private val journal = FileCrashJournal(
        directory = File(context.noBackupFilesDir, "diagnostics"),
        header = buildString {
            appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Process started at epoch ms: ${System.currentTimeMillis()}")
        },
    )
    private val fields = linkedMapOf<String, String>()
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        Thread.setDefaultUncaughtExceptionHandler(
            ReportingExceptionHandler(
                previous = Thread.getDefaultUncaughtExceptionHandler(),
                record = journal::recordCrash,
                terminate = {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                },
            ),
        )
        installed = true
    }

    /** Callers supply fixed labels/enums, never names, identifiers, routes or snapshot bodies. */
    @Synchronized
    fun updateContext(key: String, value: String) {
        val safeValue = value.take(120).replace('\n', ' ').replace('\r', ' ')
        if (fields[key] == safeValue) return
        if (key !in fields && fields.size >= 16) return
        fields[key.take(40)] = safeValue
        journal.updateContext(fields.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    internal fun writeReport(writer: Writer) = journal.writeReport(writer)
}
