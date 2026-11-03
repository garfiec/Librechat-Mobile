package com.garfiec.librechat.core.model.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Decodes a config text that is either one string or an array of lines (upstream's
 * `z.string().or(z.array(z.string()))`), joining the lines with `\n` as web does. Anything else
 * decodes to null rather than failing the whole config.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object TextOrLinesSerializer : KSerializer<String?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TextOrLines", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        if (decoder is JsonDecoder) {
            return when (val element = decoder.decodeJsonElement()) {
                is JsonNull -> null
                is JsonPrimitive -> element.content.takeIf { element.isString }
                is JsonArray -> element
                    .mapNotNull { (it as? JsonPrimitive)?.takeIf { line -> line.isString }?.content }
                    .joinToString("\n")
                else -> null
            }
        }
        if (!decoder.decodeNotNullMark()) {
            decoder.decodeNull()
            return null
        }
        return decoder.decodeString()
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}
