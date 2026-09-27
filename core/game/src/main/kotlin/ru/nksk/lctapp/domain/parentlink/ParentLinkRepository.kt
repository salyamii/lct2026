package ru.nksk.lctapp.domain.parentlink

/** Public identity is a locator, never a credential or permission to read a child's data. */
data class ParentLinkProfile(val profileId: String, val backendConfigured: Boolean)

/** The QR text is exactly the persisted profile UUID, with no URL, token or expiry. */
data class ParentLinkCode(val qrPayload: String)

class ParentLinkUnavailableException : IllegalStateException("Parent linking backend is not configured")

interface ParentLinkRepository {
    suspend fun profile(): ParentLinkProfile
    /** Local operation: available offline, never regenerates the saved profile identity. */
    suspend fun createCode(): ParentLinkCode
    /** Separate network action. Retries preserve the registration body and request ID. */
    suspend fun registerProfile()
}
