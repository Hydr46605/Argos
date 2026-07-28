package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Average statistics for one evaluation period, from `mediaPerPeriodo[<pkPeriodo>]`.
 *
 * Wire is a map keyed by period pk, so callers correlate through
 * [Periodo.pkPeriodo]. All numeric aggregates are nullable: empty periods have
 * been observed emitting zero-entries rather than omitting keys.
 *
 * @property overall Period-wide average.
 * @property bySubject Averages keyed by subject name.
 * @property byMonth Monthly averages keyed as `yyyy-MM`.
 */
@Serializable
public data class MediaPeriodo(
    @SerialName("mediaGenerale") public val overall: Double? = null,
    @SerialName("listaMaterie") public val bySubject: Map<String, MateriaMedia> = emptyMap(),
    @SerialName("mediaMese") public val byMonth: Map<String, Double> = emptyMap(),
)

/**
 * Average statistics for a single subject inside a period or globally.
 *
 * Every aggregate independently nullable/unstable upstream — absent when the
 * school disables oral/written separation, etc.
 */
@Serializable
public data class MateriaMedia(
    @SerialName("mediaMateria") public val average: Double? = null,
    @SerialName("mediaScritta") public val writtenAverage: Double? = null,
    @SerialName("mediaOrale") public val oralAverage: Double? = null,
    @SerialName("sumValori") public val valueSum: Double? = null,
    @SerialName("numValori") public val valueCount: Int? = null,
    @SerialName("numVoti") public val voteCount: Int? = null,
    @SerialName("sommaValutazioniOrale") public val oralSum: Double? = null,
    @SerialName("numValutazioniOrale") public val oralCount: Int? = null,
    @SerialName("sommaValutazioniScritto") public val writtenSum: Double? = null,
    @SerialName("numValutazioniScritto") public val writtenCount: Int? = null,
)
