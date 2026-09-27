package ru.nksk.lctapp.domain.media

import kotlinx.coroutines.flow.Flow

/** Device preferences are independent of the saved game and its backup/restore contract. */
data class MediaPreferences(val soundEnabled: Boolean = true)

interface MediaPreferencesRepository {
    /** Emits persisted values. Read failures propagate; they must not be replaced with defaults. */
    fun observe(): Flow<MediaPreferences>
    suspend fun read(): MediaPreferences
    /** Returns only after persistence succeeds; assigning the same value is safe to retry. */
    suspend fun setSoundEnabled(enabled: Boolean)
}
