package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parent-teacher availability/booking row from `prenotazioniAlunni`.
 *
 * Identifiers are nested ([Prenotazione.pk]) on inserts while deletion markers
 * carry a top-level `pk`; delta resolution handles both (see
 * `it.hydr4.argo.sync.DeltaLists`).
 *
 * @property pk Reservation identity when present.
 * @property wireOperation Upstream delta marker; resolved by repositories before exposure.
 * @property eventAt Event timestamp of the delta record.
 * @property prenotazione The reservation itself; nullable on availability-only rows.
 * @property disponibilita Availability window; nullable on pure-reservation rows.
 */
@Serializable
public data class PrenotazioneAlunni(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("operazione") public val wireOperation: String? = null,
    @SerialName("datEvento") public val eventAt: String? = null,
    @SerialName("prenotazione") public val prenotazione: Prenotazione? = null,
    @SerialName("disponibilita") public val disponibilita: Disponibilita? = null,
)

/** Concrete reservation made by a family for a meeting slot. */
@Serializable
public data class Prenotazione(
    @SerialName("pk") public val pk: String,
    @SerialName("orarioPrenotazione") public val bookedForRaw: String? = null,
    @SerialName("datPrenotazione") public val bookedOnRaw: String? = null,
    @SerialName("genitore") public val parentLabel: String? = null,
    @SerialName("desTelefonoGenitore") public val parentPhone: String? = null,
    @SerialName("desEMailGenitore") public val parentEmail: String? = null,
    @SerialName("numMax") public val maxReservations: Int? = null,
    @SerialName("numPrenotazioni") public val reservationCount: Int? = null,
    /** Cancelled-flag strings kept raw (`S`/`N`); unstable upstream. */
    @SerialName("flgAnnullato") public val cancelledFlag: String? = null,
)

/** Availability window a teacher opened for meetings. */
@Serializable
public data class Disponibilita(
    @SerialName("pk") public val pk: String,
    @SerialName("datDisponibilita") public val dayRaw: String? = null,
    @SerialName("ora_Inizio") public val startsRaw: String? = null,
    @SerialName("ora_Fine") public val endsRaw: String? = null,
    @SerialName("desNota") public val note: String? = null,
    @SerialName("desLuogoRicevimento") public val location: String? = null,
    @SerialName("desUrl") public val url: String? = null,
)
