package it.hydr4.argo.repositories

import it.hydr4.argo.api.EnvelopeShell
import it.hydr4.argo.exceptions.DeserializationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Extracts the typed-standard `data` object when present. */
internal fun EnvelopeShell.envelopeData(): JsonObject? = envelope["data"] as? JsonObject

/**
 * Strict decode mapping schema drift onto [DeserializationException] so callers
 * never see raw kotlinx types.
 */
internal fun <T> Json.decodeStrict(serializer: KSerializer<T>, element: JsonElement, endpoint: String): T = try {
    decodeFromJsonElement(serializer, element)
} catch (e: SerializationException) {
    throw DeserializationException(endpoint, e)
} catch (e: IllegalArgumentException) {
    throw DeserializationException(endpoint, e)
}
