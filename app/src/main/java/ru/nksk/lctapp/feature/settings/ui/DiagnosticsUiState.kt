package ru.nksk.lctapp.feature.settings.ui

internal enum class DiagnosticsExportResult { SAVED, EXPORT_FAILED, PICKER_FAILED }

internal data class DiagnosticsUiState(
    val saving: Boolean = false,
    val result: DiagnosticsExportResult? = null,
)
