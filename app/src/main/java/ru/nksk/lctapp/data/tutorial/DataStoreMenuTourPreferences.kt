package ru.nksk.lctapp.data.tutorial

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import ru.nksk.lctapp.domain.tutorial.MenuTourPreferences
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class MenuTourStore

internal class DataStoreMenuTourPreferences @Inject constructor(
    @param:MenuTourStore private val store: DataStore<Preferences>,
) : MenuTourPreferences {
    override suspend fun readStep(): Int? {
        val preferences = store.data.first()
        return preferences[Step] ?: preferences[LegacyStep]?.let { legacy ->
            // v2 splits the first hint; preserve every later hint and completion.
            if (legacy <= 0) 0 else legacy.coerceAtMost(11) + 1
        }
    }
    override suspend fun saveStep(step: Int) { store.edit { it[Step] = step } }
    private companion object {
        val Step = intPreferencesKey("menu_tour_v2_step")
        val LegacyStep = intPreferencesKey("menu_tour_v1_step")
    }
}
