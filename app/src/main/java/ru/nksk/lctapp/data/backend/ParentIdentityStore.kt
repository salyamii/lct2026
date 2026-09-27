package ru.nksk.lctapp.data.backend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class ParentIdentity(
    val profileId: String,
    val installationId: String,
    val credential: String,
    val registrationRequestId: String,
    val backendUrl: String? = null,
    val registration: RegisterProfileRequest? = null,
    val registered: Boolean = false,
) {
    override fun toString(): String = "ParentIdentity(profileId=$profileId, registered=$registered)"
}

internal interface ParentIdentityStore {
    suspend fun getOrCreate(): ParentIdentity
    suspend fun update(transform: (ParentIdentity) -> ParentIdentity): ParentIdentity
}

/** Small installation identity is separate from game snapshots and excluded from OS backup. */
@Singleton
internal class EncryptedParentIdentityStore @Inject constructor(@ApplicationContext context: Context) : ParentIdentityStore {
    private val preferences = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { File(context.noBackupFilesDir, "parent_link.preferences_pb") },
    )
    private val key = stringPreferencesKey("identity_v1")
    private val cipher = ParentIdentityCipher()

    override suspend fun getOrCreate(): ParentIdentity = withContext(Dispatchers.IO) {
        preferences.data.first()[key]?.let { return@withContext decode(it) }
        var result: ParentIdentity? = null
        preferences.edit { prefs ->
            result = prefs[key]?.let(::decode) ?: newIdentity().also {
                prefs[key] = cipher.encrypt(BackendJson.encodeToString(it))
            }
        }
        checkNotNull(result)
    }

    override suspend fun update(transform: (ParentIdentity) -> ParentIdentity): ParentIdentity = withContext(Dispatchers.IO) {
        var result: ParentIdentity? = null
        preferences.edit { prefs ->
            val current = decode(checkNotNull(prefs[key]) { "Parent profile has not been initialized" })
            result = transform(current).also { next ->
                check(next.profileId == current.profileId && next.installationId == current.installationId &&
                    next.credential == current.credential) { "A linking request cannot replace identity" }
                prefs[key] = cipher.encrypt(BackendJson.encodeToString(next))
            }
        }
        checkNotNull(result)
    }

    // Corrupt storage or a missing keystore key propagates. Never silently issue a different profile.
    private fun decode(value: String): ParentIdentity = BackendJson.decodeFromString<ParentIdentity>(cipher.decrypt(value)).also {
        UUID.fromString(it.profileId)
        UUID.fromString(it.installationId)
        UUID.fromString(it.registrationRequestId)
        require(it.credential.length == 43)
    }

    private fun newIdentity() = ParentIdentity(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        UUID.randomUUID().toString())
}

private class ParentIdentityCipher {
    private val alias = "lct.parent-link.identity.v1"
    private val aad = "lct.parent-link.identity.v1".toByteArray(Charsets.UTF_8)
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized private fun key(create: Boolean): SecretKey {
        (store().getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Parent profile encryption key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher.updateAAD(aad)
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}
