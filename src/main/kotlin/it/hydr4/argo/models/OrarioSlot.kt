package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One timetable slot from `orario-giorno`.
 *
 * The wire response is a map keyed by hour label whose values are lists of
 * slots — repositories flatten it into a single ordered list.
 *
 * All descriptive fields are individually optional: same-subject rows collapse
 * differently depending on how teachers split hours.
 */
@Serializable
public data class OrarioSlot(
    @SerialName("numOra") public val hourNumber: Int? = null,
    @SerialName("mostra") public val shown: Boolean = true,
    @SerialName("materia") public val subject: String? = null,
    @SerialName("docente") public val teacher: String? = null,
    @SerialName("desCognome") public val teacherSurname: String? = null,
    @SerialName("desNome") public val teacherName: String? = null,
    @SerialName("desDenominazione") public val classGroup: String? = null,
    @SerialName("desSezione") public val section: String? = null,
    @SerialName("desEmail") public val teacherEmail: String? = null,
    @SerialName("pk") public val pk: String? = null,
    @SerialName("scuAnagrafePK") public val teacherRegistryPk: String? = null,
    /** Raw room/time annotation; inconsistent format across schools. */
    @SerialName("ora") public val roomTimeRaw: String? = null,
)
