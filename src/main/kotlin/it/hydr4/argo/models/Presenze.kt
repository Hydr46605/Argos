package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Attendance row from `appello`.
 *
 * Justification fields are raw wire strings — the upstream mixes date-only and
 * empty-string sentinels.
 *
 * @property pk Attendance identifier; deletions reference it.
 * @property wireOperation Upstream delta marker; resolved by repositories before exposure.
 * @property eventType Absence code (`A` absence, `R` delay observed so far).
 * @property justifiable `true` while still awaiting justification.
 * @property justified State of justification.
 * @property note Administrative note shown with the row.
 */
@Serializable
public data class AppelloEntry(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("operazione") public val wireOperation: String? = null,
    @SerialName("codEvento") public val eventType: String? = null,
    @SerialName("daGiustificare") public val justifiable: Boolean = false,
    @SerialName("giustificata") public val justified: String? = null,
    @SerialName("commentoGiustificazione") public val justificationComment: String? = null,
    @SerialName("dataGiustificazione") public val justifiedAt: String? = null,
    @SerialName("nota") public val note: String? = null,
    @SerialName("descrizione") public val description: String? = null,
    @SerialName("docente") public val teacher: String? = null,
    @SerialName("data") public val occurredOn: String? = null,
    @SerialName("datEvento") public val eventAt: String? = null,
)

/**
 * Teacher reminder/note from `promemoria`.
 *
 * @property pk Reminder identifier; deletions reference it.
 * @property wireOperation Upstream delta marker; resolved by repositories before exposure.
 * @property annotations Reminder body text.
 * @property visibleToFamily Flag string (`S`/`N`); kept as-is — unstable semantics.
 */
@Serializable
public data class PromemoriaEntry(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("operazione") public val wireOperation: String? = null,
    @SerialName("desAnnotazioni") public val annotations: String? = null,
    @SerialName("flgVisibileFamiglia") public val visibleToFamily: String? = null,
    @SerialName("docente") public val teacher: String? = null,
    @SerialName("pkDocente") public val teacherPk: String? = null,
    @SerialName("datGiorno") public val day: String? = null,
    @SerialName("oraInizio") public val startsAt: String? = null,
    @SerialName("oraFine") public val endsAt: String? = null,
    @SerialName("datEvento") public val eventAt: String? = null,
)

/**
 * Out-of-class activity (outing, project day) from `fuoriClasse`.
 *
 * @property pk Entry identifier; deletions reference it.
 * @property wireOperation Upstream delta marker; resolved by repositories before exposure.
 * @property onlineFrequency `true` when delivered as online lesson.
 */
@Serializable
public data class FuoriClasseEntry(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("operazione") public val wireOperation: String? = null,
    @SerialName("descrizione") public val description: String? = null,
    @SerialName("nota") public val note: String? = null,
    @SerialName("docente") public val teacher: String? = null,
    @SerialName("data") public val occurredOn: String? = null,
    @SerialName("datEvento") public val eventAt: String? = null,
    @SerialName("frequenzaOnLine") public val onlineFrequency: Boolean = false,
)
