package it.hydr4.argo.models

import it.hydr4.argo.time.TimeFormats
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Serializers adapting Argo's timestamp dialects onto `java.time` types so the
 * public model surface stays free of wire-format strings.
 *
 * Malformed **optional** values are surfaced by making the corresponding model
 * property nullable at declaration sites rather than silently swallowed here;
 * parsing failures throw and are translated into [it.hydr4.argo.exceptions.DeserializationException]
 * by the transport layer.
 */
public object ModelTimeSerializers {
    /** `java.time.LocalDate` against Argo's `yyyy-MM-dd` wire format. */
    public object WireDate : KSerializer<LocalDate> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(
                "it.hydr4.argo.WireDate",
                PrimitiveKind.STRING,
            )

        override fun deserialize(decoder: Decoder): LocalDate {
            val raw = decoder.decodeString()
            return TimeFormats.parseDate(raw)
        }

        override fun serialize(encoder: Encoder, value: LocalDate) {
            encoder.encodeString(value.toString())
        }
    }

    /** `java.time.LocalDateTime` against Argo's space-separated datetime dialects. */
    public object WireDateTime : KSerializer<LocalDateTime> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(
                "it.hydr4.argo.WireDateTime",
                PrimitiveKind.STRING,
            )

        override fun deserialize(decoder: Decoder): LocalDateTime = TimeFormats.parseDateTime(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: LocalDateTime) {
            encoder.encodeString(TimeFormats.formatWire(value))
        }
    }

    /**
     * Tolerant variant accepting either dialect or an already-ISO string;
     * used for fields whose exact upstream emission was inconsistent between schools.
     */
    public object LenientDateTime : KSerializer<LocalDateTime?> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(
                "it.hydr4.argo.LenientDateTime",
                PrimitiveKind.STRING,
            )

        override fun deserialize(decoder: Decoder): LocalDateTime? {
            val element = (decoder as JsonDecoder).decodeJsonElement()
            if (element !is JsonPrimitive || !element.isString) {
                return null
            }
            return TimeFormats.tryParseDateTime(element.content)
        }

        override fun serialize(encoder: Encoder, value: LocalDateTime?) {
            if (value != null) encoder.encodeString(TimeFormats.formatWire(value))
        }
    }

    /** `java.time.Instant` serialized as ISO-8601 text; used by persisted tokens. */
    public object IsoInstant : KSerializer<Instant> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(
                "it.hydr4.argo.IsoInstant",
                PrimitiveKind.STRING,
            )

        override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: Instant) {
            encoder.encodeString(value.toString())
        }
    }
}
