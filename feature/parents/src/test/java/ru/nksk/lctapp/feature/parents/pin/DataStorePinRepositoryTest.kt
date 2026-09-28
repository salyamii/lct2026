package ru.nksk.lctapp.feature.parents.pin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStorePinRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `creation persists salted verifier and refuses an existing PIN`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(temporaryFolder.root, "pin.preferences_pb")
        }
        val repository = DataStorePinRepository(store, { 100_000L }, PinHasher())
        assertEquals(PinStatus.NotConfigured, repository.readStatus())
        assertEquals(CreatePinResult.Created, repository.createPin("1234"))
        assertEquals(CreatePinResult.AlreadyConfigured, repository.createPin("9876"))
        assertEquals(VerifyPinResult.Wrong, repository.verifyPin("9876"))
        assertEquals(VerifyPinResult.Accepted, repository.verifyPin("1234"))
        val values = store.data.first().asMap().values
        assertFalse(values.contains("1234"))
        assertTrue(values.filterIsInstance<String>().single().startsWith("1|pbkdf2-sha1|"))
    }

    @Test fun `reopened repository retains failed attempt throttle`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(temporaryFolder.root, "pin.preferences_pb")
        }
        var now = 100_000L
        val repository = DataStorePinRepository(store, { now }, PinHasher())
        repository.createPin("1234")
        repeat(4) { assertEquals(VerifyPinResult.Wrong, repository.verifyPin("4321")) }
        assertEquals(VerifyPinResult.Throttled(30_000), repository.verifyPin("4321"))
        val reopened = DataStorePinRepository(store, { now }, PinHasher())
        assertEquals(PinStatus.Configured(30_000), reopened.readStatus())
        assertEquals(VerifyPinResult.Throttled(30_000), reopened.verifyPin("1234"))
        now += 30_001
        assertEquals(VerifyPinResult.Accepted, reopened.verifyPin("1234"))
    }

    @Test fun `malformed stored verifier fails closed instead of offering setup`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(temporaryFolder.root, "pin.preferences_pb")
        }
        store.edit { it[stringPreferencesKey("pin_record")] = "unreadable" }
        val repository = DataStorePinRepository(store, { 100_000L }, PinHasher())
        assertThrowsIo { repository.readStatus() }
        assertThrowsIo { repository.createPin("1234") }
        assertThrowsIo { repository.verifyPin("1234") }
    }

    private suspend fun assertThrowsIo(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected an IOException")
        } catch (_: IOException) {
            // Corrupt storage must remain visible to the gate.
        }
    }
}
