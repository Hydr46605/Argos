package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Teacher/school bulletin entry from `bacheca` (dashboard or `storicobacheca`).
 *
 * Confirmation/expiration datetimes are raw wire strings: upstream emits a mix
 * of date-only and full datetime values depending on the notice kind.
 *
 * @property pk Bulletin identifier; deletions reference it.
 * @property wireOperation Upstream delta marker; resolved by repositories before exposure.
 * @property category Notice category label.
 * @property message Body text (HTML fragments observed).
 * @property eventAt Event timestamp.
 * @property publishedOn Publication datetime ("data").
 * @property author Author display name.
 * @property requiresPv `true` when the family must confirm "presa visione".
 * @property isPvConfirmed Whether "presa visione" was confirmed.
 * @property pvConfirmedAt Raw confirmation timestamp; unstable format.
 * @property requiresAdhesion `true` when the notice asks for an adhesion choice.
 * @property isAdhesionConfirmed Whether adhesion was confirmed.
 * @property adhesionConfirmedAt Raw adhesion confirmation timestamp; unstable format.
 * @property attachmentUrl Direct URL when the school publishes remote links.
 * @property expiresAt Raw expiry; nullable on bulletins that never expire.
 * @property adhesionDeadline Raw adhesion deadline; nullable when not applicable.
 */
@Serializable
public data class BachecaEntry(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("operazione") public val wireOperation: String? = null,
    @SerialName("categoria") public val category: String? = null,
    @SerialName("messaggio") public val message: String? = null,
    @SerialName("datEvento") public val eventAt: String? = null,
    @SerialName("data") public val publishedOn: String? = null,
    @SerialName("autore") public val author: String? = null,
    @SerialName("pvRichiesta") public val requiresPv: Boolean = false,
    @SerialName("isPresaVisione") public val isPvConfirmed: Boolean = false,
    @SerialName("dataConfermaPresaVisione") public val pvConfirmedAt: String? = null,
    @SerialName("adRichiesta") public val requiresAdhesion: Boolean = false,
    @SerialName("isPresaAdesioneConfermata") public val isAdhesionConfirmed: Boolean = false,
    @SerialName("dataConfermaAdesione") public val adhesionConfirmedAt: String? = null,
    @SerialName("url") public val attachmentUrl: String? = null,
    @SerialName("dataScadenza") public val expiresAt: String? = null,
    @SerialName("dataScadAdesione") public val adhesionDeadline: String? = null,
    @SerialName("listaAllegati") public val attachments: List<Allegato> = emptyList(),
)

/** Downloadable attachment attached to a [BachecaEntry]. */
@Serializable
public data class Allegato(
    @SerialName("pk") public val pk: String,
    @SerialName("nomeFile") public val fileName: String,
    @SerialName("path") public val path: String? = null,
    @SerialName("descrizioneFile") public val description: String? = null,
    @SerialName("url") public val url: String? = null,
)

/**
 * Student-facing bulletin entry from `bachecaAlunno` (report card documents etc.).
 *
 * @property pk Entry identifier.
 * @property wireOperation Upstream delta marker; resolved by repositories before exposure.
 * @property fileName Attached report/document name.
 */
@Serializable
public data class BachecaAlunnoEntry(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("operazione") public val wireOperation: String? = null,
    @SerialName("nomeFile") public val fileName: String? = null,
    @SerialName("datEvento") public val eventAt: String? = null,
    @SerialName("messaggio") public val message: String? = null,
    @SerialName("data") public val publishedOn: String? = null,
    @SerialName("flgDownloadGenitore") public val parentDownloadFlag: String? = null,
    @SerialName("isPresaVisione") public val isPvConfirmed: Boolean = false,
)
