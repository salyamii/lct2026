package ru.nksk.lctapp.data.tutorial

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreMenuTourPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun savedProgressSurvivesReopeningThePreferenceFile() = runTest {
        val file = File(temporary.root, "tour.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val first = DataStoreMenuTourPreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        try { assertNull(first.readStep()); first.saveStep(7) }
        finally { scope.coroutineContext.job.cancelAndJoin() }
        val reopenedScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val reopened = DataStoreMenuTourPreferences(PreferenceDataStoreFactory.create(scope = reopenedScope, produceFile = { file }))
        try { assertEquals(7, reopened.readStep()) }
        finally { reopenedScope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun corruptionIsReportedAndDoesNotResetProgress() = runTest {
        val file = File(temporary.root, "tour.preferences_pb")
        val bytes = byteArrayOf(0xff.toByte())
        file.writeBytes(bytes)
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val preferences = DataStoreMenuTourPreferences(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        try {
            try { preferences.readStep(); fail("Expected corruption") } catch (_: CorruptionException) { }
            assertArrayEquals(bytes, file.readBytes())
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }
}
