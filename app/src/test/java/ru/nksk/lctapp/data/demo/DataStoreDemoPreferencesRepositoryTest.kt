package ru.nksk.lctapp.data.demo

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.nksk.lctapp.BuildConfig

class DataStoreDemoPreferencesRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun newInstallationDefaultsOffAndDebugOrJuryCanEnableIt() = runTest {
        val file = File(temporary.root, "new.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val repository = DataStoreDemoPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { file }))
        try {
            assertFalse(repository.read().demoModeEnabled)
            repository.setDemoModeEnabled(true)
            assertEquals(BuildConfig.DEBUG || BuildConfig.JURY_TOOLS, repository.read().demoModeEnabled)
            assertEquals(BuildConfig.DEBUG || BuildConfig.JURY_TOOLS, repository.observe().first().demoModeEnabled)
            repository.setDemoModeEnabled(false)
            assertFalse(repository.read().demoModeEnabled)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun reopenedLegacyEnabledPreferenceIsNeverEnabledForChildrenRelease() = runTest {
        val file = File(temporary.root, "legacy.preferences_pb")
        val oldScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val old = PreferenceDataStoreFactory.create(scope = oldScope, produceFile = { file })
        try { old.edit { it[booleanPreferencesKey("demo_mode_enabled")] = true } }
        finally { oldScope.coroutineContext.job.cancelAndJoin() }

        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val repository = DataStoreDemoPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { file }))
        try {
            assertEquals(BuildConfig.DEBUG || BuildConfig.JURY_TOOLS, repository.read().demoModeEnabled)
            assertEquals(BuildConfig.DEBUG || BuildConfig.JURY_TOOLS, repository.observe().first().demoModeEnabled)
            repository.setDemoModeEnabled(true)
            assertEquals(BuildConfig.DEBUG || BuildConfig.JURY_TOOLS, repository.read().demoModeEnabled)
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun irrelevantLegacyFileCannotBlockChildrenReleaseAndEnabledBuildReadErrorsRemainVisible() = runTest {
        val file = File(temporary.root, "corrupt.preferences_pb")
        val invalid = byteArrayOf(0xff.toByte())
        file.writeBytes(invalid)
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val repository = DataStoreDemoPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { file }))
        try {
            if (BuildConfig.DEBUG || BuildConfig.JURY_TOOLS) {
                try {
                    repository.read()
                    fail("A demo preference read failure must remain visible")
                } catch (_: CorruptionException) { /* The settings screen can offer a retry. */ }
            } else {
                assertFalse(repository.read().demoModeEnabled)
                repository.setDemoModeEnabled(true)
                assertFalse(repository.observe().first().demoModeEnabled)
            }
            assertArrayEquals(invalid, file.readBytes())
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }
}
