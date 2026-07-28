package it.hydr4.argo.models

import it.hydr4.argo.models.ModelTimeSerializers.WireDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Evaluation period ("quadrimestre", "trimestre", ...) from `listaPeriodi`.
 *
 * Dates are individually unstable upstream: some deployments fill only one of
 * the two wire aliases for each bound, so both remain nullable with a KDoc note.
 *
 * @property pkPeriodo Canonical period key referenced by [Voto.pkPeriodo].
 * @property codPeriodo Alternative period code; unstable.
 * @property description Human-readable period label.
 * @property startsOn Period start (from `dataInizio`); nullable when the alias
 *   field `datInizio` variant is used instead by a deployment.
 * @property endsOn Period end (from `dataFine`); nullable when the alias
 *   field `datFine` variant is used instead by a deployment.
 * @property hasUniqueVote `true` when the period collapses to a single overall vote.
 * @property averageScrutinio Scrutinio-computed average when present.
 * @property isScrutinioAverage Whether [averageScrutinio] should be displayed.
 * @property isFinalScrutinio `true` for the year-end scrutinio period.
 */
@Serializable
public data class Periodo(
    @SerialName("pkPeriodo") public val pkPeriodo: String,
    @SerialName("codPeriodo") public val codPeriodo: String? = null,
    @SerialName("descrizione") public val description: String,
    @Serializable(with = WireDate::class) @SerialName("dataInizio") public val startsOn: LocalDate? = null,
    @Serializable(with = WireDate::class) @SerialName("dataFine") public val endsOn: LocalDate? = null,
    @SerialName("votoUnico") public val hasUniqueVote: Boolean = false,
    @SerialName("mediaScrutinio") public val averageScrutinio: Double? = null,
    @SerialName("isMediaScrutinio") public val isScrutinioAverage: Boolean = false,
    @SerialName("isScrutinioFinale") public val isFinalScrutinio: Boolean = false,
)
