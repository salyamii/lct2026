package ru.nksk.lctapp.data.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.nksk.lctapp.domain.media.MediaPreferences
import ru.nksk.lctapp.domain.media.MediaPreferencesRepository
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MediaSettingsStore

@Singleton
internal class DataStoreMediaPreferencesRepository @Inject constructor(
    @param:MediaSettingsStore private val preferences: DataStore<Preferences>,
) : MediaPreferencesRepository {
    override fun observe() = preferences.data.map { saved ->
        // Only an absent preference has a default. Corruption and I/O errors remain visible.
        MediaPreferences(soundEnabled = saved[SoundEnabled] ?: true)
    }.distinctUntilChanged()

    override suspend fun read(): MediaPreferences = observe().first()

    override suspend fun setSoundEnabled(enabled: Boolean) {
        preferences.edit { it[SoundEnabled] = enabled }
    }

    private companion object { val SoundEnabled = booleanPreferencesKey("sound_enabled") }
}
