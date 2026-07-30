package it.hydr4.argo.repositories

import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.exceptions.ArgoApiException
import it.hydr4.argo.exceptions.ProtocolException
import it.hydr4.argo.models.Pagamento
import it.hydr4.argo.models.Ricevimenti
import it.hydr4.argo.models.RicevimentiWire
import it.hydr4.argo.models.RicevutaTelematica
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * School fees installments (`listatassealunni`) plus the telematic receipt lookup.
 */
public class FeesRepository(private val http: ArgoHttpClient) {
    /**
     * All fee rows for a scheda.
     *
     * @param schedaPk Enrollment identifier (`scheda.pk`).
     */
    public suspend fun listatasse(schedaPk: String?): FeesSheet {
        val shell =
            http.fetchEnvelope(
                Endpoints.LISTA_TASSE,
                buildJsonObject { put("pkScheda", schedaPk) },
            )
        val onlineFlag =
            (shell.envelope["isPagOnlineAttivo"] as? kotlinx.serialization.json.JsonPrimitive)?.let {
                it.contentOrNull == "true"
            } ?: false
        val payments =
            shell.envelope["data"] as? kotlinx.serialization.json.JsonArray
                ?: throw ProtocolException("listatasse lost its array payload")
        val rows = payments.map { http.json.decodeStrict(Pagamento.serializer(), it, Endpoints.LISTA_TASSE) }
        return FeesSheet(rows = rows, isOnlinePaymentActive = onlineFlag)
    }

    /**
     * Resolves the printable receipt for an IUV.
     *
     * Upstream answers `success: false` when no receipt exists yet — that maps to
     * `null`; transport and schema failures keep propagating.
     *
     * @return Receipt locator or `null` when the payment has no receipt yet.
     */
    @Suppress("SwallowedException")
    // success:false is the endpoint's normal "no receipt yet" answer, not an error worth surfacing.
    public suspend fun ricevutaTelematica(iuv: String): RicevutaTelematica? {
        val shell = try {
            http.fetchEnvelope(
                Endpoints.RICEVUTA_TELEMATICA,
                buildJsonObject { put("iuv", iuv) },
            )
        } catch (e: ArgoApiException) {
            return null // success:false == no receipt issued
        }
        return http.json.decodeStrict(RicevutaTelematica.serializer(), shell.envelope, Endpoints.RICEVUTA_TELEMATICA)
    }
}

/**
 * Fees payload normalized above the raw wire envelope.
 *
 * @property rows Fee installments.
 * @property isOnlinePaymentActive Whether the school enabled pago-online.
 */
public data class FeesSheet(public val rows: List<Pagamento>, public val isOnlinePaymentActive: Boolean)

/**
 * Parent-teacher meeting availability and bookings (`ricevimento`).
 */
public class MeetingsRepository(private val http: ArgoHttpClient) {
    /**
     * Flattened meeting sheet for the logged family.
     *
     * Upstream keys availability by teacher id — values are lists here because
     * every captured deployment used the array form; object form would surface
     * as a [ProtocolException] rather than silent data loss.
     */
    public suspend fun ricevimenti(): Ricevimenti {
        val shell = http.fetchEnvelope(Endpoints.RICEVIMENTO, buildJsonObject { })
        val data =
            shell.envelopeData()
                ?: throw ProtocolException("ricevimento payload lost 'data'")
        val wire = http.json.decodeStrict(RicevimentiWire.serializer(), data, Endpoints.RICEVIMENTO)
        return Ricevimenti(
            slots =
            wire.disponibilita
                .orEmpty()
                .values
                .flatten(),
            people = wire.genitoreOAlunno,
            accessType = wire.tipoAccesso,
            bookings = wire.prenotazioni,
        )
    }
}
