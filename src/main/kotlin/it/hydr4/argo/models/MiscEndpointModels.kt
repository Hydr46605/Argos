package it.hydr4.argo.models

import it.hydr4.argo.annotations.Experimental
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/**
 * Internship pathways from `pcto`.
 *
 * The upstream array shape was never observed populated during reverse
 * engineering, so contents stay opaque ([JsonArray]) pending real-world records.
 */
@Experimental("pcto payload shape never observed populated; treat contents as opaque")
@Serializable
public data class PctoData(@SerialName("percorsi") public val pathways: JsonArray? = null)

/**
 * Recovery-course structures from `corsirecupero`; both collections were
 * consistently empty in recorded captures and remain opaque until stable shapes emerge.
 */
@Experimental("corsirecupero collections consistently empty in captures; shapes may drift")
@Serializable
public data class CorsiRecuperoData(
    @SerialName("corsiRecupero") public val courses: JsonArray? = null,
    @SerialName("periodi") public val periods: JsonArray? = null,
)

/**
 * Curriculum year entry from `curriculumalunno`.
 *
 * @property esito Year outcome; nullable when the year has no published esito yet
 *   (the upstream emits empty strings instead of omitting the key).
 */
@Serializable
public data class CurriculumEntry(
    @SerialName("pkScheda") public val schedaPk: String? = null,
    @SerialName("classe") public val classe: String? = null,
    @SerialName("anno") public val anno: Int? = null,
    @SerialName("esito") public val esito: Esito? = null,
    @SerialName("credito") public val credit: Double? = null,
    @SerialName("media") public val average: Double? = null,
    @SerialName("mostraInfo") public val showsInfo: Boolean = false,
    @SerialName("mostraCredito") public val showsCredit: Boolean = false,
    @SerialName("CVAbilitato") public val curriculumEnabled: Boolean = false,
    @SerialName("isSuperiore") public val isSuperiorSchool: Boolean = false,
    @SerialName("isInterruzioneFR") public val interruptedFR: Boolean = false,
    @SerialName("ordineScuola") public val schoolOrder: String? = null,
)

/** Published year outcome. */
@Serializable
public data class Esito(
    @SerialName("codEsito") public val code: String? = null,
    @SerialName("desDescrizione") public val description: String? = null,
    @SerialName("descrizione") public val shortDescription: String? = null,
    @SerialName("numColore") public val colorIndex: Int? = null,
    @SerialName("flgPositivo") public val positiveFlag: String? = null,
    @SerialName("tipoEsito") public val type: String? = null,
    @SerialName("particolarita") public val particularity: String? = null,
    @SerialName("icona") public val icon: String? = null,
)

/**
 * Telematic payment receipt locator returned by `ricevutatelematica`.
 *
 * Note: unlike other endpoints this one carries `success` on the top object but
 * no `data` wrapper.
 */
@Serializable
public data class RicevutaTelematica(
    @SerialName("fileName") public val fileName: String? = null,
    @SerialName("url") public val url: String? = null,
)

/** Attachment download link issued by `downloadallegatobacheca(alunno)` short-lived URLs. */
@Serializable
public data class AllegatoDownload(@SerialName("url") public val url: String)
