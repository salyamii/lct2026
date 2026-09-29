package ru.nksk.lctapp.data.demo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.nksk.lctapp.domain.demo.DemoPreferences
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class DemoSettingsStore

@Singleton
internal class DataStoreDemoPreferencesRepository @Inject constructor(
    @param:DemoSettingsStore private val preferences: DataStore<Preferences>,
) : DemoPreferencesRepository {
    override fun observe() = preferences.data.map { saved ->
        DemoPreferences(demoModeEnabled = saved[DemoModeEnabled] ?: false)
    }.distinctUntilChanged()

    override suspend fun read(): DemoPreferences = observe().first()

    override suspend fun setDemoModeEnabled(enabled: Boolean) {
        preferences.edit { it[DemoModeEnabled] = enabled }
    }

    private companion object { val DemoModeEnabled = booleanPreferencesKey("demo_mode_enabled") }
}
