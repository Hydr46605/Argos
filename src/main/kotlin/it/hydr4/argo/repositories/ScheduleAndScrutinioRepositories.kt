package it.hydr4.argo.repositories

import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.models.OrarioSlot
import it.hydr4.argo.models.ScrutinioEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * Daily timetable (`orario-giorno`).
 */
public class ScheduleRepository(private val http: ArgoHttpClient) {
    /**
     * Timetable slots for [date], defaulting to today.
     *
     * The upstream nests slots under hour labels; that grouping is flattened,
     * order preserved per hour.
     */
    public suspend fun orarioGiornaliero(date: LocalDate = LocalDate.now()): List<OrarioSlot> {
        val shell =
            http.fetchEnvelope(
                Endpoints.ORARIO_GIORNO,
                buildJsonObject { put("datGiorno", date.toString() + " 00:00:00.000") },
            )
        val data =
            shell.envelopeData()
                ?: throw it.hydr4.argo.exceptions
                    .ProtocolException("orario payload lost 'data'")
        val grouped = data["dati"] as? JsonObject ?: JsonObject(emptyMap())
        return grouped.values
            .asSequence()
            .filterIsInstance<kotlinx.serialization.json.JsonArray>()
            .flatMap { slots -> slots.asSequence() }
            .map { http.json.decodeStrict(OrarioSlot.serializer(), it, Endpoints.ORARIO_GIORNO) }
            .toList()
    }
}

/**
 * Published scrutinio votes (`votiscrutinio`).
 */
public class ScrutinioRepository(private val http: ArgoHttpClient) {
    /**
     * Periods of the scrutinio record; the reference client reads the first
     * record's periods, treating later ones as legacy scheda copies.
     */
    public suspend fun votiScrutinio(): List<it.hydr4.argo.models.ScrutinioPeriodo> {
        val shell = http.fetchEnvelope(Endpoints.VOTI_SCRUTINIO, buildJsonObject { })
        val records =
            shell
                .envelopeData()
                ?.get("votiScrutinio")
                ?.jsonArray
                .orEmpty()
        val first = records.firstOrNull() ?: return emptyList()
        val entry = http.json.decodeStrict(ScrutinioEntry.serializer(), first, Endpoints.VOTI_SCRUTINIO)
        return entry.periodi
    }
}
