package it.hydr4.argo.models

import it.hydr4.argo.models.ModelTimeSerializers.WireDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Lesson row from `registro` (daily didactics log).
 *
 * @property pk Lesson identifier; deletions reference it.
 * @property wireOperation Upstream delta marker; resolved by repositories before exposure.
 * @property day Lesson day.
 * @property hourSlot Hour number within the schedule.
 * @property subject Subject display name.
 * @property subjectPk Subject key when present.
 * @property teacher Teacher display name.
 * @property teacherPk Teacher identifier (useful for meetings).
 * @property isSigned Whether the register row was signed by the teacher.
 * @property activity Performed activity description; nullable when unused.
 * @property homework Homework items attached to the lesson.
 * @property attachmentUrl Remote document link when provided.
 */
@Serializable
public data class RegistroEntry(
    @SerialName("pk") public val pk: String? = null,
    @SerialName("operazione") public val wireOperation: String? = null,
    @Serializable(with = WireDate::class) @SerialName("datGiorno") public val day: LocalDate? = null,
    @SerialName("ora") public val hourSlot: Int? = null,
    @SerialName("materia") public val subject: String? = null,
    @SerialName("pkMateria") public val subjectPk: String? = null,
    @SerialName("docente") public val teacher: String? = null,
    @SerialName("pkDocente") public val teacherPk: String? = null,
    @SerialName("isFirmato") public val isSigned: Boolean = false,
    @SerialName("attivita") public val activity: String? = null,
    @SerialName("compiti") public val homework: List<Compito> = emptyList(),
    @SerialName("desUrl") public val attachmentUrl: String? = null,
)

/** A single homework assignment inside a [RegistroEntry]. */
@Serializable
public data class Compito(@SerialName("compito") public val task: String, @SerialName("dataConsegna") public val dueRaw: String? = null)
