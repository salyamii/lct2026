package ru.nksk.lctapp.domain.demo

import kotlinx.coroutines.flow.Flow

/** A device setting. Enabling it alone never rewrites the saved game. */
data class DemoPreferences(val demoModeEnabled: Boolean = false)

interface DemoPreferencesRepository {
    /** Storage errors propagate instead of silently changing the selected rules. */
    fun observe(): Flow<DemoPreferences>
    suspend fun read(): DemoPreferences
    /** The same explicit value can be safely retried after a failed write. */
    suspend fun setDemoModeEnabled(enabled: Boolean)
}

/** Normal rules for domain callers that do not install a device preference source. */
object DisabledDemoPreferencesRepository : DemoPreferencesRepository {
    override fun observe() = kotlinx.coroutines.flow.flowOf(DemoPreferences())
    override suspend fun read() = DemoPreferences()
    override suspend fun setDemoModeEnabled(enabled: Boolean) {
        require(!enabled) { "This session does not support changing demo mode" }
    }
}
