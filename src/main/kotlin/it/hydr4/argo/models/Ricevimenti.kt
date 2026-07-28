package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Normalized payload of the `ricevimento` endpoint.
 *
 * Upstream instability note: `disponibilita` is a map keyed by teacher id whose
 * values may be a list **or** an object depending on deployment; repositories
 * therefore decode [RicevimentiWire] and hand callers this flattened form.
 *
 * @property slots Flattened availability windows per teacher.
 * @property people Parent/student identity rows allowed to book.
 * @property accessType Access mode label; unstable semantics.
 * @property bookings Already-made bookings for this family.
 */
public data class Ricevimenti(
    public val slots: List<RicevimentoSlot> = emptyList(),
    public val people: List<PersonaReferente> = emptyList(),
    public val accessType: String? = null,
    public val bookings: List<RicevimentoBooking> = emptyList(),
)

/** Wire carrier preserving Argo's map-keyed availability shape. */
@Serializable
public data class RicevimentiWire(
    @SerialName("disponibilita") public val disponibilita: Map<String, List<RicevimentoSlot>>? = null,
    @SerialName("genitoreOAlunno") public val genitoreOAlunno: List<PersonaReferente> = emptyList(),
    @SerialName("tipoAccesso") public val tipoAccesso: String? = null,
    @SerialName("prenotazioni") public val prenotazioni: List<RicevimentoBooking> = emptyList(),
)

/**
 * A single availability window.
 *
 * Time fields remain raw strings (`oraInizio` formats vary between HH:mm and
 * full datetimes across schools).
 */
@Serializable
public data class RicevimentoSlot(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("desNota") public val note: String? = null,
    @SerialName("datInizioPrenotazione") public val bookingWindowStart: String? = null,
    @SerialName("datScadenza") public val bookingDeadline: String? = null,
    @SerialName("datDisponibilita") public val dayRaw: String? = null,
    @SerialName("oraInizio") public val startsRaw: String? = null,
    @SerialName("oraFine") public val endsRaw: String? = null,
    @SerialName("oraInizioPrenotazione") public val bookingOpenAt: String? = null,
    @SerialName("desLuogoRicevimento") public val location: String? = null,
    @SerialName("desUrl") public val url: String? = null,
    @SerialName("numMax") public val maxBookings: Int? = null,
    @SerialName("numPrenotazioni") public val bookingCount: Int? = null,
    @SerialName("numPrenotazioniAnnullate") public val cancelledCount: Int? = null,
    @SerialName("flgAttivo") public val activeFlag: String? = null,
    @SerialName("unaTantum") public val onceOnlyFlag: String? = null,
    @SerialName("flgMostraEmail") public val showEmailFlag: String? = null,
    @SerialName("indisponibilita") public val unavailabilityNote: String? = null,
    @SerialName("docente") public val docente: ReferenteDocente? = null,
)

/** Teacher reference used across ricevimento payloads. */
@Serializable
public data class ReferenteDocente(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("desCognome") public val cognome: String? = null,
    @SerialName("desNome") public val nome: String? = null,
    @SerialName("desEMail") public val email: String? = null,
    /** Alias spelling emitted by some endpoints; nullable elsewhere. */
    @SerialName("desEmail") public val emailAlt: String? = null,
) {
    /** Email resolved across both spellings Argo uses. */
    public fun resolvedEmail(): String? = email ?: emailAlt
}

/** Person row that may book meetings (parent or adult student). */
@Serializable
public data class PersonaReferente(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("nominativo") public val nominativo: String? = null,
    @SerialName("desEMail") public val email: String? = null,
    @SerialName("telefono") public val phone: String? = null,
)

/** Existing booking made through the portal. */
@Serializable
public data class RicevimentoBooking(
    @SerialName("prenotazione") public val prenotazione: Prenotazione? = null,
    @SerialName("disponibilita") public val disponibilita: Disponibilita? = null,
    @SerialName("docente") public val docente: ReferenteDocente? = null,
)
