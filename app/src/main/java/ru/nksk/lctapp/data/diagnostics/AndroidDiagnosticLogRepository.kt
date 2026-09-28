package ru.nksk.lctapp.data.diagnostics

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.domain.diagnostics.DiagnosticLogRepository

@Singleton
class AndroidDiagnosticLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnostics: AppDiagnostics,
) : DiagnosticLogRepository {
    override suspend fun exportTo(destination: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(destination)
        require(uri.scheme == "content") { "A document URI is required" }
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("The selected document cannot be opened")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            diagnostics.writeReport(writer)
            writeSystemExitReport(context, writer)
        }
    }
}
