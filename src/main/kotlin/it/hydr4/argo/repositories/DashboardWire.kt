package it.hydr4.argo.repositories

import it.hydr4.argo.api.EnvelopeShell
import it.hydr4.argo.auth.ArgoSession
import it.hydr4.argo.auth.ServerInstant
import it.hydr4.argo.exceptions.ProtocolException
import it.hydr4.argo.models.AppelloEntry
import it.hydr4.argo.models.BachecaAlunnoEntry
import it.hydr4.argo.models.BachecaEntry
import it.hydr4.argo.models.Dashboard
import it.hydr4.argo.models.FuoriClasseEntry
import it.hydr4.argo.models.LoginOption
import it.hydr4.argo.models.Materia
import it.hydr4.argo.models.MateriaMedia
import it.hydr4.argo.models.MediaPeriodo
import it.hydr4.argo.models.Periodo
import it.hydr4.argo.models.PrenotazioneAlunni
import it.hydr4.argo.models.Profile
import it.hydr4.argo.models.PromemoriaEntry
import it.hydr4.argo.models.RegistroEntry
import it.hydr4.argo.models.Voto
import it.hydr4.argo.sync.DeltaLists
import it.hydr4.argo.time.TimeFormats
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Single-responsibility decoder converting raw `dashboard/dashboard` payloads
 * into the typed [Dashboard], applying reference-faithful delta semantics:
 *
 * 1. `rimuoviDatiLocali == true` discards the previous snapshot before merging.
 * 2. Entries marked `"operazione": "D"` delete matching local records by identity.
 * 3. Everything else inserts fresh or updates the record sharing its identity.
 */
internal class DashboardWire(private val mapper: Json) {
    /** Builds the exact request body of the reference client for a fetch round. */
    fun buildRequestPayload(previous: Dashboard?, sinceOverride: java.time.Instant?, session: ArgoSession, profile: Profile?): JsonObject {
        val anchor: java.time.Instant =
            sinceOverride
                ?: previous?.fetchedAt
                ?: profile
                    ?.anno
                    ?.startsOn
                    ?.atStartOfDay(java.time.ZoneId.systemDefault())
                    ?.toInstant()
                ?: java.time.Instant.EPOCH
        val options = previous?.options ?: session.loginDataOrNull()?.options.orEmpty()
        return buildJsonObject {
            put("dataultimoaggiornamento", TimeFormats.formatWire(anchor))
            put("opzioni", encodeOptions(options))
        }
    }

    /** Decodes one envelope response into a fresh [Dashboard] snapshot. */
    fun assemble(shell: EnvelopeShell, previous: Dashboard?): Dashboard {
        val data =
            shell.envelope["data"] as? JsonObject
                ?: throw ProtocolException("dashboard envelope lost 'data'")
        val row =
            (data["dati"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: throw ProtocolException("dashboard payload lost its 'dati[0]' shape")

        val wipeLocal = row.flag("rimuoviDatiLocali") ?: false
        val base = if (wipeLocal) null else previous

        return Dashboard(
            fetchedAt = ServerInstant.fromHeader(shell.response.header("date")),
            overallAverage = row.decimal("mediaGenerale"),
            monthlyAverages = mapOfDoubles(row["mediaPerMese"]),
            periodAverages = mapped(row["mediaPerPeriodo"], MediaPeriodo.serializer()),
            subjectAverages = mapped(row["mediaMaterie"], MateriaMedia.serializer()),
            subjects = rowsOf(row["listaMaterie"], Materia.serializer()).orEmpty(),
            periods = rowsOf(row["listaPeriodi"], Periodo.serializer()).orEmpty(),
            grades = resolved(base?.grades.orEmpty(), row["voti"], Voto.serializer()) { it.pk },
            bulletins = resolved(base?.bulletins.orEmpty(), row["bacheca"], BachecaEntry.serializer()) { it.pk },
            studentBulletins = resolved(base?.studentBulletins.orEmpty(), row["bachecaAlunno"], BachecaAlunnoEntry.serializer()) { it.pk },
            attendance = resolved(base?.attendance.orEmpty(), row["appello"], AppelloEntry.serializer()) { it.pk },
            lessons = resolved(base?.lessons.orEmpty(), row["registro"], RegistroEntry.serializer()) { it.pk },
            reminders = resolved(base?.reminders.orEmpty(), row["promemoria"], PromemoriaEntry.serializer()) { it.pk },
            outOfClasses = resolved(base?.outOfClasses.orEmpty(), row["fuoriClasse"], FuoriClasseEntry.serializer()) { it.pk },
            bookings =
            resolved(base?.bookings.orEmpty(), row["prenotazioniAlunni"], PrenotazioneAlunni.serializer()) {
                it.pk ?: it.prenotazione?.pk
            },
            options = rowsOf(row["opzioni"], LoginOption.serializer()).orEmpty(),
            serverMessage = row.text("msg"),
            removeLocalData = wipeLocal,
            reloadData = row.flag("ricaricaDati") ?: false,
        )
    }

    // --- collection decoding ------------------------------------------------------

    private fun <T> rowsOf(element: kotlinx.serialization.json.JsonElement?, serializer: KSerializer<T>): List<T>? =
        (element as? JsonArray)?.mapNotNull { item ->
            runCatching { mapper.decodeFromJsonElement(serializer, item) }.getOrNull()
        }

    private fun <T> resolved(
        previous: List<T>,
        element: kotlinx.serialization.json.JsonElement?,
        serializer: kotlinx.serialization.KSerializer<T>,
        identityOf: (T) -> String?,
    ): List<T> where T : Any {
        val incoming = rowsOf(element, serializer).orEmpty().filterNotNull()
        return DeltaLists.apply(
            previous = previous,
            incoming = incoming,
            identityOf = identityOf,
            isTombstone = { isDeleteMarker(it) },
        )
    }

    private fun isDeleteMarker(candidate: Any): Boolean = when (candidate) {
        is Voto -> candidate.wireOperation == "D"
        is BachecaEntry -> candidate.wireOperation == "D"
        is BachecaAlunnoEntry -> candidate.wireOperation == "D"
        is AppelloEntry -> candidate.wireOperation == "D"
        is RegistroEntry -> candidate.wireOperation == "D"
        is PromemoriaEntry -> candidate.wireOperation == "D"
        is FuoriClasseEntry -> candidate.wireOperation == "D"
        is PrenotazioneAlunni -> candidate.wireOperation == "D"
        else -> false
    }

    private fun <K> mapped(element: kotlinx.serialization.json.JsonElement?, valueSerializer: KSerializer<K>): Map<String, K> =
        (element as? JsonObject)
            ?.mapNotNull { (key, value) ->
                runCatching { key to mapper.decodeFromJsonElement(valueSerializer, value) }.getOrNull()
            }?.toMap() ?: emptyMap()

    private fun mapOfDoubles(element: kotlinx.serialization.json.JsonElement?): Map<String, Double> = mapped(element, Double.serializer())

    // --- primitives -----------------------------------------------------------------

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    /**
     * Reads a decimal field tolerating both dot and comma separators.
     *
     * Some deployments emit locale-formatted averages ("7,25"); strict parsing
     * would silently drop the headline average, so the comma is normalized
     * before conversion. Non-numeric values stay null.
     */
    private fun JsonObject.decimal(name: String): Double? = (this[name] as? JsonPrimitive)?.let { value ->
        value.doubleOrNull ?: value.contentOrNull?.trim()?.replace(',', '.')?.toDoubleOrNull()
    }

    private fun JsonObject.flag(name: String): Boolean? = text(name)?.let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun encodeOptions(options: List<LoginOption>): String =
        options.joinToString(",", "{", "}") { "${JsonPrimitive(it.key)}:${JsonPrimitive(it.value)}" }
}

/** Reads the boolean `success` flag off an envelope shell for non-typed endpoints. */
internal fun EnvelopeShell.asBooleanSuccess(): Boolean = (envelope["success"] as? JsonPrimitive)?.contentOrNull == "true"
