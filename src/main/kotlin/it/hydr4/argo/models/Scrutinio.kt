package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Outcome of `votiscrutinio`: per-entry scrutinio records holding periods.
 *
 * The wire nests scrutinio entries inside an array; families usually receive one
 * entry containing every period.
 */
@Serializable
public data class ScrutinioEntry(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("periodi") public val periodi: List<ScrutinioPeriodo> = emptyList(),
)

/**
 * A scrutinio period: final/published grades grouped by subject name strings.
 *
 * Subject detail lives in [subjects] as raw labels because the endpoint does not
 * resolve subject keys.
 *
 * @property description Period label (e.g. "Primo periodo").
 * @property subjects Ordered subject-name list.
 * @property isFinal `true` for the year-end scrutinio.
 * @property isJudicialType `true` when the period contains giudizi instead of numeric votes.
 */
@Serializable
public data class ScrutinioPeriodo(
    @SerialName("desDescrizione") public val description: String? = null,
    @SerialName("materie") public val subjects: List<String> = emptyList(),
    @SerialName("suddivisione") public val subdivision: String? = null,
    @SerialName("votiGiudizi") public val isJudicialType: Boolean = false,
    @SerialName("scrutinioFinale") public val isFinal: Boolean = false,
)
