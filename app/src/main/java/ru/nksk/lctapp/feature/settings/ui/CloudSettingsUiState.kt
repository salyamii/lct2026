package ru.nksk.lctapp.feature.settings.ui

import ru.nksk.lctapp.domain.backend.CloudRestorePreview
import ru.nksk.lctapp.domain.backend.CloudSyncPhase
import ru.nksk.lctapp.domain.backend.CloudSyncState

internal enum class CloudSettingsOperation { SYNC, DOWNLOAD, RESTORE }

internal data class CloudSettingsUiState(
    val sync: CloudSyncState = CloudSyncState(),
    val operation: CloudSettingsOperation? = null,
    val restorePreview: CloudRestorePreview? = null,
    val feedback: String? = null,
    val lastSyncedLabel: String? = null,
) {
    val busy: Boolean get() = operation != null || sync.phase == CloudSyncPhase.SYNCING
}
