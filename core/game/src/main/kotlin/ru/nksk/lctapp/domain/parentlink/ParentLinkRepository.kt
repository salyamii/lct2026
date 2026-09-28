package ru.nksk.lctapp.domain.parentlink

/** Compatibility name: profileId is the saved deviceId sent by the backend transport. */
data class ParentLinkProfile(val profileId: String, val backendConfigured: Boolean)

/** The QR text is exactly the persisted device identifier, with no URL or expiry. */
data class ParentLinkCode(val qrPayload: String)

class ParentLinkUnavailableException : IllegalStateException("Parent linking backend is not configured")

interface ParentLinkRepository {
    suspend fun profile(): ParentLinkProfile
    /** Local operation: available offline, never regenerates the saved profile identity. */
    suspend fun createCode(): ParentLinkCode
    /** Separate network action. Retries preserve the registration body and request ID. */
    suspend fun registerProfile()
}
