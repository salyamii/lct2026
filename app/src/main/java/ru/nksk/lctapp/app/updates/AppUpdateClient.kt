package ru.nksk.lctapp.app.updates

import kotlinx.coroutines.flow.Flow

/** Platform update operations, independent of the game and its saved state. */
internal interface AppUpdateClient {
    val events: Flow<UpdateStatus>
    suspend fun check(): UpdateStatus
    /** Uses fresh update info. True means the package was already downloaded. */
    suspend fun download(onPromptStarted: () -> Unit): Boolean
    suspend fun install()
}

internal enum class UpdateStatus { NONE, AVAILABLE, IN_PROGRESS, DOWNLOADED }
