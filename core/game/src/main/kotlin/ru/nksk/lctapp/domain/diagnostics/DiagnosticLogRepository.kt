package ru.nksk.lctapp.domain.diagnostics

/** Writes local diagnostics to a document explicitly selected by the user. */
interface DiagnosticLogRepository {
    /** [destination] is the opaque document URI returned by the platform's save dialog. */
    suspend fun exportTo(destination: String)
}
