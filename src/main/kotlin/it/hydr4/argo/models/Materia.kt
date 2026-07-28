package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subject entry from `dashboard/dashboard` → `listaMaterie`.
 *
 * @property materia Subject name as displayed in the register.
 * @property abbreviation Shortened display name; unstable, may be omitted.
 * @property codeType Wire type code (`S` written, `O` oral variants observed).
 * @property countsTowardAverage `true` when the subject participates in averages.
 * @property isScrutinized `true` when the subject receives a scrutinio vote.
 */
@Serializable
public data class Materia(
    @SerialName("materia") public val materia: String,
    @SerialName("abbreviazione") public val abbreviation: String? = null,
    @SerialName("codTipo") public val codeType: String? = null,
    @SerialName("faMedia") public val countsTowardAverage: Boolean = true,
    @SerialName("scrut") public val isScrutinized: Boolean = false,
)
