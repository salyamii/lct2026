package ru.nksk.lctapp.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.nksk.lctapp.data.media.DataStoreMediaPreferencesRepository
import ru.nksk.lctapp.data.media.MediaSettingsStore
import ru.nksk.lctapp.domain.media.MediaPreferencesRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MediaComputationDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaModule {
    @Binds @Singleton
    abstract fun preferences(repository: DataStoreMediaPreferencesRepository): MediaPreferencesRepository

    companion object {
        @Provides @MediaComputationDispatcher
        fun computationDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides @Singleton @MediaSettingsStore
        fun preferenceStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.noBackupFilesDir, "media.preferences_pb") },
            )
    }
}
