package ru.nksk.lctapp.data.backend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.backend.registrationDto

class ParentIdentityStoreTest {
    private val currentKey = stringPreferencesKey("identity_v2")
    private val legacyKey = stringPreferencesKey("identity_v1")

    @Test fun newIdentityUsesAndroidIdAndKeepsItAcrossRecreation() = runTest {
        val prefs = MemoryPreferences()
        var reads = 0
        val first = DeviceParentIdentityStore(prefs, { reads++; "0123456789abcdef" }, { error("No legacy record") })
        val identity = first.getOrCreate()
        val recreated = DeviceParentIdentityStore(prefs, { error("Do not replace a saved ID") }, { error("No legacy record") })

        assertEquals("0123456789abcdef", identity.deviceId)
        assertEquals(identity.deviceId, identity.profileId)
        assertEquals(identity, recreated.getOrCreate())
        assertEquals(1, reads)
        val stored = checkNotNull(prefs.value[currentKey])
        assertFalse(stored.contains("credential"))
        assertFalse(stored.contains("installationId"))
        assertNull(prefs.value[legacyKey])
    }

    @Test fun concurrentCreationSharesOnePersistedIdentityAndRetryKey() = runTest {
        val prefs = MemoryPreferences()
        var reads = 0
        val store = DeviceParentIdentityStore(prefs, { reads++; "0123456789abcdef" }, { error("No legacy record") })
        val first = async { store.getOrCreate() }
        val second = async { store.getOrCreate() }
        assertEquals(first.await(), second.await())
        assertEquals(1, reads)
    }

    @Test fun missingAndroidIdIsAnErrorAndNeverGeneratesAReplacement() = runTest {
        for (unavailable in listOf(null, "", "   ")) {
            val prefs = MemoryPreferences()
            val store = DeviceParentIdentityStore(prefs, { unavailable }, { error("No legacy record") })
            try { store.getOrCreate(); fail("Missing device ID must fail") } catch (_: IllegalStateException) { }
            assertEquals(emptyPreferences(), prefs.value)
        }
    }

    @Test fun migrationPreservesUuidAndFrozenPetAndRemovesEncryptedSecretAtomically() = runTest {
        val prefs = MemoryPreferences(preferencesOf(legacyKey to "encrypted-old-record"))
        val frozenPet = createInitialGameState().pet.copy(name = "Первое имя").registrationDto()
        var decryptions = 0
        val store = DeviceParentIdentityStore(prefs, { error("Legacy UUID must win over Android ID") }, {
            assertEquals("encrypted-old-record", it)
            decryptions++
            legacyJson(BackendJson.encodeToString(frozenPet))
        })

        val migrated = store.getOrCreate()
        assertEquals(LegacyProfile, migrated.deviceId)
        assertEquals(frozenPet, migrated.registration!!.pet)
        assertEquals(LegacyProfile, migrated.registration.deviceId)
        assertEquals("https://backend.example.test/", migrated.backendUrl)
        assertNotEquals(LegacyRequest, migrated.registrationRequestId)
        assertFalse(migrated.registered)
        assertNull(prefs.value[legacyKey])
        val stored = checkNotNull(prefs.value[currentKey])
        assertFalse(stored.contains("credential"))
        assertFalse(stored.contains("old-secret"))
        assertFalse(stored.contains("installationId"))

        store.update { it.copy(registered = true) }
        val recreated = DeviceParentIdentityStore(prefs, { error("Saved ID exists") }, { error("Already migrated") })
        assertEquals(migrated.copy(registered = true), recreated.getOrCreate())
        assertEquals(1, decryptions)
    }

    @Test fun failedLegacyDecryptionPreservesRecordAndDoesNotReadAnotherId() = runTest {
        val original = preferencesOf(legacyKey to "encrypted-old-record")
        val prefs = MemoryPreferences(original)
        val store = DeviceParentIdentityStore(prefs, { error("Never issue a replacement") }, {
            throw IllegalStateException("Legacy key is unavailable")
        })
        try { store.getOrCreate(); fail("Unavailable legacy identity must fail") } catch (_: IllegalStateException) { }
        assertEquals(original, prefs.value)
        assertNull(prefs.value[currentKey])
    }

    @Test fun failedUpdateDoesNotPartiallyCommitLegacyMigration() = runTest {
        val original = preferencesOf(legacyKey to "encrypted-old-record")
        val prefs = MemoryPreferences(original)
        val store = DeviceParentIdentityStore(prefs, { error("Saved ID exists") }, {
            legacyJson(BackendJson.encodeToString(createInitialGameState().pet.registrationDto()))
        })
        try {
            store.update { it.copy(deviceId = "replacement-device") }
            fail("Identity cannot be replaced")
        } catch (_: IllegalStateException) { }
        assertEquals(original, prefs.value)
    }

    @Test fun migrationRotatesTransportKeyDeterministicallyAndRejectsWrongFrozenProfile() {
        val json = legacyJson(BackendJson.encodeToString(createInitialGameState().pet.registrationDto()))
        assertEquals(migrateLegacyParentIdentity(json), migrateLegacyParentIdentity(json))
        val wrong = json.replace("\"profileId\": \"$LegacyProfile\", \"installationId\"",
            "\"profileId\": \"other-profile\", \"installationId\"")
        try { migrateLegacyParentIdentity(wrong); fail("Frozen registration belongs to another device") }
        catch (_: IllegalArgumentException) { }
    }

    private fun legacyJson(pet: String) = """
        {
          "profileId": "$LegacyProfile",
          "installationId": "f0cf614d-df3a-42f4-bfee-c222b275ba02",
          "credential": "old-secret",
          "registrationRequestId": "$LegacyRequest",
          "backendUrl": "https://backend.example.test/",
          "registered": true,
          "registration": {
            "profileId": "$LegacyProfile", "installationId": "f0cf614d-df3a-42f4-bfee-c222b275ba02",
            "pet": $pet,
            "schemaVersion": 1
          }
        }
    """.trimIndent()

    private companion object {
        const val LegacyProfile = "a1a81f35-ae8b-44dd-925d-659d88c6cd45"
        const val LegacyRequest = "ca6e9018-fbe4-4b24-91cc-757c333c85b4"
    }
}

private class MemoryPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val writes = Mutex()
    val value get() = state.value
    override val data = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = writes.withLock {
        transform(state.value).also { state.value = it }
    }
}
