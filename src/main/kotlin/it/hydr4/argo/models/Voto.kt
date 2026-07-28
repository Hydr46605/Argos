package it.hydr4.argo.models

import it.hydr4.argo.models.ModelTimeSerializers.LenientDateTime
import it.hydr4.argo.models.ModelTimeSerializers.WireDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A grade event from `dashboard/dashboard` → `voti` (post delta-resolution).
 *
 * The raw list additionally carries an `operazione` marker ("I"/"D") and a
 * top-level `pk`; those are protocol mechanics consumed during parsing and are
 * intentionally not re-exposed on this model.
 *
 * Unstable upstream: comment/test-description fields have been observed absent
 * on some schools, hence the nullability of [comment] and [testDescription].
 *
 * @property pk Server identifier for this grade; deletions reference it.
 * @property wireOperation Upstream delta marker (`"I"`/`"D"`); repositories resolve it
 *   before this model ever reaches callers.
 * @property date Event timestamp ("datEvento"); kept lenient — format varies slightly.
 * @property day Grade day ("datGiorno").
 */
@Serializable
public data class Voto(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("operazione") public val wireOperation: String? = null,
    @Serializable(with = LenientDateTime::class) @SerialName("datEvento") public val datEvento: LocalDateTime? = null,
    @Serializable(with = WireDate::class) @SerialName("datGiorno") public val day: LocalDate? = null,
    @SerialName("pkPeriodo") public val pkPeriodo: String? = null,
    @SerialName("codCodice") public val gradeCode: String? = null,
    @SerialName("valore") public val value: Double? = null,
    @SerialName("codVotoPratico") public val practicalCode: String? = null,
    @SerialName("docente") public val teacher: String? = null,
    @SerialName("pkDocente") public val teacherPk: String? = null,
    @SerialName("pkMateria") public val subjectPk: String? = null,
    @SerialName("desMateria") public val subjectName: String? = null,
    @SerialName("materiaLight") public val materiaLight: MateriaLight? = null,
    @SerialName("tipoValutazione") public val evaluationType: String? = null,
    @SerialName("prgVoto") public val progressionIndex: Int? = null,
    @SerialName("descrizioneProva") public val testDescription: String? = null,
    /** Comment shown under the grade; observed absent on some deployments. */
    @SerialName("desCommento") public val comment: String? = null,
    @SerialName("descrizioneVoto") public val descriptionVote: String? = null,
    @SerialName("codTipo") public val typeCode: String? = null,
    @SerialName("mese") public val month: Int? = null,
    @SerialName("numMedia") public val runningAverage: Double? = null,
    /** `"N"` excludes the grade from averages, per reference behavior. */
    @SerialName("faMenoMedia") public val excludedFromAverage: String? = null,
)

/**
 * Denormalized subject descriptor embedded inside each [Voto].
 *
 * Secondary administrative codes that were consistently empty across recorded
 * fixtures (`codAggrInvalsi`, `tipoOnGrid`, …) are intentionally not modeled;
 * unknown keys are ignored at parse time.
 */
@Serializable
public data class MateriaLight(
    @SerialName("scuMateriaPK") public val key: SubjectKey? = null,
    @SerialName("codMateria") public val code: String? = null,
    @SerialName("desDescrizione") public val description: String? = null,
    @SerialName("desDescrAbbrev") public val abbreviatedDescription: String? = null,
    @SerialName("icona") public val icon: String? = null,
    @SerialName("flgConcorreMedia") public val countsTowardAverageFlag: String? = null,
    @SerialName("prgMateria") public val progression: Int? = null,
    @SerialName("idmateria") public val idMateria: String? = null,
    @SerialName("codEDescrizioneMateria") public val codeAndDescription: String? = null,
)

/** Composite primary key of a subject within a school/year. */
@Serializable
public data class SubjectKey(
    @SerialName("codMin") public val codMin: String,
    @SerialName("prgScuola") public val schoolProgression: Int,
    @SerialName("numAnno") public val yearNumber: Int,
    @SerialName("prgMateria") public val subjectProgression: Int,
)
