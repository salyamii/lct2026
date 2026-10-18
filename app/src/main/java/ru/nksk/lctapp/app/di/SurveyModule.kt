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
import ru.nksk.lctapp.data.survey.DataStoreSurveyRepository
import ru.nksk.lctapp.data.survey.SurveyStore
import ru.nksk.lctapp.domain.survey.SurveyRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SurveyModule {
    @Binds @Singleton
    abstract fun repository(implementation: DataStoreSurveyRepository): SurveyRepository

    companion object {
        @Provides @Singleton @SurveyStore
        fun store(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = {
                File(context.noBackupFilesDir, "survey_6ad49a05852af395fcc7a0af.preferences_pb")
            })
    }
}
