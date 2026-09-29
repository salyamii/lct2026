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
import ru.nksk.lctapp.data.tutorial.DataStoreMenuTourPreferences
import ru.nksk.lctapp.data.tutorial.MenuTourStore
import ru.nksk.lctapp.domain.tutorial.MenuTourPreferences

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MenuTourModule {
    @Binds @Singleton
    abstract fun preferences(implementation: DataStoreMenuTourPreferences): MenuTourPreferences

    companion object {
        @Provides @Singleton @MenuTourStore
        fun store(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = {
                File(context.noBackupFilesDir, "menu_tour.preferences_pb")
            })
    }
}
