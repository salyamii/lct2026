package ru.nksk.lctapp.data.media

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class DataStoreMediaPreferencesRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun newDeviceDefaultsToSoundAndSavedMuteSurvivesDataStoreRecreation() = runTest {
        val file = File(temporary.root, "media.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val first = DataStoreMediaPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = firstScope, produceFile = { file }))
        try {
            assertTrue(first.read().soundEnabled)
            first.setSoundEnabled(false)
            assertFalse(first.observe().first().soundEnabled)
        } finally { firstScope.coroutineContext.job.cancelAndJoin() }

        val secondScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val reopened = DataStoreMediaPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = secondScope, produceFile = { file }))
        try {
            assertFalse(reopened.read().soundEnabled)
            reopened.setSoundEnabled(true)
            assertTrue(reopened.read().soundEnabled)
        } finally { secondScope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun corruptedPreferencesPropagateInsteadOfSilentlyTurningSoundOnOrReplacingTheFile() = runTest {
        val file = File(temporary.root, "corrupt.preferences_pb")
        val invalid = byteArrayOf(0xff.toByte())
        file.writeBytes(invalid)
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val repository = DataStoreMediaPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { file }))
        try {
            try {
                repository.read()
                fail("Corrupt preferences must not become default preferences")
            } catch (_: CorruptionException) { /* The caller can offer an explicit retry. */ }
            try {
                repository.setSoundEnabled(false)
                fail("A sound toggle must not overwrite unreadable preferences")
            } catch (_: CorruptionException) { /* Keep the existing file for recovery. */ }
            assertArrayEquals(invalid, file.readBytes())
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }
}
