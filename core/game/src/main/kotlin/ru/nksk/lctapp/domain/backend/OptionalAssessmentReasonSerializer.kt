package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** Optional display text must not invalidate a server assessment. */
object OptionalAssessmentReasonSerializer : KSerializer<String?> {
    private val delegate = String.serializer().nullable
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): String? {
        val text = if (decoder is JsonDecoder) {
            val value = decoder.decodeJsonElement() as? JsonPrimitive
            value?.takeIf { it.isString }?.content
        } else decoder.decodeSerializableValue(delegate)
        return text?.takeIf { it.isNotBlank() && it.length <= 600 && '\u0000' !in it }
    }
    override fun serialize(encoder: Encoder, value: String?) = encoder.encodeSerializableValue(delegate, value)
}
