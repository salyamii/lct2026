package ru.nksk.lctapp.feature.settings.ui

import ru.nksk.lctapp.domain.backend.CloudRestorePreview
import ru.nksk.lctapp.domain.backend.CloudLocalUploadPreview
import ru.nksk.lctapp.domain.backend.CloudSyncPhase
import ru.nksk.lctapp.domain.backend.CloudSyncState

internal enum class CloudSettingsOperation { SYNC, DOWNLOAD, RESTORE, PREPARE_UPLOAD, UPLOAD_LOCAL }

internal data class CloudSettingsUiState(
    val sync: CloudSyncState = CloudSyncState(),
    val operation: CloudSettingsOperation? = null,
    val restorePreview: CloudRestorePreview? = null,
    val localUploadPreview: CloudLocalUploadPreview? = null,
    val feedback: String? = null,
    val lastSyncedLabel: String? = null,
) {
    val busy: Boolean get() = operation != null || sync.phase == CloudSyncPhase.SYNCING
}
