package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Counts from the same accepted history as the server status, never local mastery thresholds. */
@Serializable
data class SkillObservationCounts(
    val observedEpisodes: Int,
    val completedEpisodes: Int,
    val supportedEpisodes: Int,
    val difficultyEpisodes: Int,
    val neutralEpisodes: Int,
    val pendingEpisodes: Int,
    val incompleteEpisodes: Int,
    val supportedWithoutGameHints: Int,
    val assistedEpisodes: Int,
) {
    init {
        require(listOf(observedEpisodes, completedEpisodes, supportedEpisodes, difficultyEpisodes,
            neutralEpisodes, pendingEpisodes, incompleteEpisodes, supportedWithoutGameHints,
            assistedEpisodes).all { it >= 0 })
    }
}

/** Old servers may omit counts; a malformed optional block must not hide a valid status. */
object OptionalObservationCountsSerializer : KSerializer<SkillObservationCounts?> {
    private val delegate = SkillObservationCounts.serializer().nullable
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): SkillObservationCounts? {
        if (decoder !is JsonDecoder) return decoder.decodeSerializableValue(delegate)
        val element = decoder.decodeJsonElement() as? JsonObject ?: return null
        val fields = listOf("observedEpisodes", "completedEpisodes", "supportedEpisodes",
            "difficultyEpisodes", "neutralEpisodes", "pendingEpisodes", "incompleteEpisodes",
            "supportedWithoutGameHints", "assistedEpisodes")
        if (fields.any { field ->
            val value = element[field] as? JsonPrimitive
            value == null || value.isString || value.intOrNull?.let { it >= 0 } != true
        }) return null
        return try {
            Json(decoder.json) { ignoreUnknownKeys = true }.decodeFromJsonElement(delegate, element)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: SkillObservationCounts?) =
        encoder.encodeSerializableValue(delegate, value)
}
