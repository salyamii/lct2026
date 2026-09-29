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
import ru.nksk.lctapp.data.demo.DataStoreDemoPreferencesRepository
import ru.nksk.lctapp.data.demo.DemoSettingsStore
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DemoModule {
    @Binds @Singleton
    abstract fun preferences(repository: DataStoreDemoPreferencesRepository): DemoPreferencesRepository

    companion object {
        @Provides @Singleton @DemoSettingsStore
        fun preferenceStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.noBackupFilesDir, "demo.preferences_pb") },
            )
    }
}
