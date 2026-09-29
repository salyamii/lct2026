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
    override suspend fun readStep(): Int? = store.data.first()[Step]
    override suspend fun saveStep(step: Int) { store.edit { it[Step] = step } }
    private companion object { val Step = intPreferencesKey("menu_tour_v1_step") }
}
