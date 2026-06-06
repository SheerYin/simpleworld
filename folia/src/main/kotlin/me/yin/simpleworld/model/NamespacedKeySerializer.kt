package me.yin.simpleworld.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.NamespacedKey

object NamespacedKeySerializer : KSerializer<NamespacedKey> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NamespacedKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NamespacedKey) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): NamespacedKey {
        val value = decoder.decodeString()
        return NamespacedKey.fromString(value)
            ?: throw SerializationException("Invalid NamespacedKey: $value")
    }
}
