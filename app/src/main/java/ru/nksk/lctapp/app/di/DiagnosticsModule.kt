package ru.nksk.lctapp.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.nksk.lctapp.data.diagnostics.AndroidDiagnosticLogRepository
import ru.nksk.lctapp.domain.diagnostics.DiagnosticLogRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    abstract fun diagnosticLogRepository(repository: AndroidDiagnosticLogRepository): DiagnosticLogRepository
}
