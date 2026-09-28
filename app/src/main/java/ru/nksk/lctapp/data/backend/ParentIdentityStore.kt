package ru.nksk.lctapp.data.backend

import android.content.Context
import android.provider.Settings
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import ru.nksk.lctapp.domain.backend.RegisterProfileRequest
import ru.nksk.lctapp.domain.backend.RegisteredPetDto
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class ParentIdentity(
    val deviceId: String,
    val registrationRequestId: String,
    val backendUrl: String? = null,
    val registration: RegisterProfileRequest? = null,
    val registered: Boolean = false,
) {
    /** Existing Room checkpoints and reward receipts keep their identifier unchanged. */
    val profileId: String get() = deviceId
    override fun toString(): String = "ParentIdentity(deviceId=$deviceId, registered=$registered)"
}

internal interface ParentIdentityStore {
    suspend fun getOrCreate(): ParentIdentity
    suspend fun update(transform: (ParentIdentity) -> ParentIdentity): ParentIdentity
}

/** Device ID and registration retry state; no client credential is created or stored. */
@Singleton
internal class DeviceParentIdentityStore internal constructor(
    private val preferences: DataStore<Preferences>,
    private val readDeviceId: () -> String?,
    private val decryptLegacy: (String) -> String,
) : ParentIdentityStore {
    @Inject constructor(@ApplicationContext context: Context) : this(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(context.noBackupFilesDir, "parent_link.preferences_pb") },
        ),
        { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) },
        LegacyParentIdentityCipher()::decrypt,
    )

    private val key = stringPreferencesKey("identity_v2")
    private val legacyKey = stringPreferencesKey("identity_v1")

    override suspend fun getOrCreate(): ParentIdentity = withContext(Dispatchers.IO) {
        val saved = preferences.data.first()
        if (saved[legacyKey] == null) saved[key]?.let { return@withContext decode(it) }
        var result: ParentIdentity? = null
        preferences.edit { prefs ->
            result = readSaved(prefs) ?: ParentIdentity(
                deviceId = checkNotNull(readDeviceId()?.takeIf(String::isNotBlank)) { "Android device ID is unavailable" },
                registrationRequestId = UUID.randomUUID().toString(),
            )
            write(prefs, checkNotNull(result))
        }
        checkNotNull(result)
    }

    override suspend fun update(transform: (ParentIdentity) -> ParentIdentity): ParentIdentity = withContext(Dispatchers.IO) {
        var result: ParentIdentity? = null
        preferences.edit { prefs ->
            val current = checkNotNull(readSaved(prefs)) { "Device identity has not been initialized" }
            result = transform(current).also { next ->
                check(next.deviceId == current.deviceId) { "A linking request cannot replace identity" }
                write(prefs, next)
            }
        }
        checkNotNull(result)
    }

    private fun readSaved(prefs: Preferences): ParentIdentity? = prefs[key]?.let(::decode)
        ?: prefs[legacyKey]?.let { migrateLegacyParentIdentity(decryptLegacy(it)) }

    private fun write(prefs: MutablePreferences, value: ParentIdentity) {
        validate(value)
        prefs[key] = BackendJson.encodeToString(value)
        // Migration and removal are one DataStore transaction. No decrypted secret is copied.
        prefs.remove(legacyKey)
    }

    // Corrupt storage or a missing legacy key propagates. Never silently switch the saved device ID.
    private fun decode(value: String): ParentIdentity = BackendJson.decodeFromString<ParentIdentity>(value).also(::validate)

    private fun validate(it: ParentIdentity) {
        require(it.deviceId.isNotBlank())
        UUID.fromString(it.registrationRequestId)
        require(it.registration == null || it.registration.deviceId == it.deviceId)
    }
}

/** Only public fields are decoded from the old encrypted record; old credentials are discarded. */
@Serializable
private data class LegacyParentIdentity(
    val profileId: String,
    val registrationRequestId: String,
    val backendUrl: String? = null,
    val registration: LegacyProfileRegistration? = null,
)

@Serializable
private data class LegacyProfileRegistration(
    val profileId: String,
    val pet: RegisteredPetDto,
    val schemaVersion: Int = 1,
)

internal fun migrateLegacyParentIdentity(json: String): ParentIdentity {
    val legacy = BackendJson.decodeFromString<LegacyParentIdentity>(json)
    UUID.fromString(legacy.profileId)
    UUID.fromString(legacy.registrationRequestId)
    require(legacy.registration == null || legacy.registration.profileId == legacy.profileId)
    return ParentIdentity(
        deviceId = legacy.profileId,
        // The transport body changed. Never retry its old idempotency key with different JSON.
        registrationRequestId = UUID.nameUUIDFromBytes(
            "device-body-contract-v2:${legacy.registrationRequestId}".toByteArray(Charsets.UTF_8)).toString(),
        backendUrl = legacy.backendUrl,
        registration = legacy.registration?.let { RegisterProfileRequest(legacy.profileId, it.pet, it.schemaVersion) },
        registered = false,
    )
}

/** Compatibility reader only. New installations never create a keystore key or encrypt an identity. */
private class LegacyParentIdentityCipher {
    private val alias = "lct.parent-link.identity.v1"
    private val aad = "lct.parent-link.identity.v1".toByteArray(Charsets.UTF_8)
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = checkNotNull(store().getKey(alias, null) as? SecretKey) { "Legacy profile encryption key is unavailable" }
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}
