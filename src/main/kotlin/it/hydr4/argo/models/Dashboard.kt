package it.hydr4.argo.models

import it.hydr4.argo.models.ModelTimeSerializers.IsoInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Aggregated register snapshot resolved from `dashboard/dashboard`, with every
 * collection already delta-merged (`operazione I/D` markers consumed during parsing).
 *
 * Collections are plain immutable lists — merging old snapshots happens in
 * [it.hydr4.argo.sync.DeltaLists] before this model is built, so consumers never
 * see protocol mechanics.
 *
 * @property fetchedAt Server time of the fetch (response `Date` header as [Instant]).
 * @property overallAverage `mediaGenerale`; nullable when no averages were published yet.
 * @property monthlyAverages Average per month keyed `yyyy-MM` ("mediaPerMese").
 * @property periodAverages Per-period statistics keyed by [Periodo.pkPeriodo].
 * @property subjectAverages Global per-subject statistics keyed by subject name.
 * @property subjects Subject catalog ("listaMaterie").
 * @property grades Delta-resolved grades.
 * @property bulletins Teacher bulletin entries ("bacheca").
 * @property studentBulletins Student-facing documents ("bachecaAlunno").
 * @property attendance Attendance rows ("appello").
 * @property lessons Daily-log lessons ("registro").
 * @property reminders Teacher reminders ("promemoria").
 * @property outOfClasses Out-of-class activities ("fuoriClasse").
 * @property bookings Parent-teacher reservations ("prenotazioniAlunni").
 * @property periods Evaluation periods ("listaPeriodi"); unstable upstream — may be absent.
 * @property options Session feature flags echoed back; empty before first login merge.
 * @property serverMessage Verbatim wire message when present ("msg" inside the dati row).
 * @property removeLocalData `true` when the server requests a local cache wipe.
 * @property reloadData `true` when the server asks for an immediate refetch ("ricaricaDati").
 */
@Serializable
public data class Dashboard(
    @Serializable(with = IsoInstant::class) public val fetchedAt: Instant? = null,
    @SerialName("mediaGenerale") public val overallAverage: Double? = null,
    @SerialName("mediaPerMese") public val monthlyAverages: Map<String, Double> = emptyMap(),
    @SerialName("mediaPerPeriodo") public val periodAverages: Map<String, MediaPeriodo> = emptyMap(),
    @SerialName("mediaMaterie") public val subjectAverages: Map<String, MateriaMedia> = emptyMap(),
    @SerialName("listaMaterie") public val subjects: List<Materia> = emptyList(),
    @SerialName("voti") public val grades: List<Voto> = emptyList(),
    @SerialName("bacheca") public val bulletins: List<BachecaEntry> = emptyList(),
    @SerialName("bachecaAlunno") public val studentBulletins: List<BachecaAlunnoEntry> = emptyList(),
    @SerialName("appello") public val attendance: List<AppelloEntry> = emptyList(),
    @SerialName("registro") public val lessons: List<RegistroEntry> = emptyList(),
    @SerialName("promemoria") public val reminders: List<PromemoriaEntry> = emptyList(),
    @SerialName("fuoriClasse") public val outOfClasses: List<FuoriClasseEntry> = emptyList(),
    @SerialName("prenotazioniAlunni") public val bookings: List<PrenotazioneAlunni> = emptyList(),
    @SerialName("listaPeriodi") public val periods: List<Periodo> = emptyList(),
    @SerialName("opzioni") public val options: List<LoginOption> = emptyList(),
    @SerialName("msg") public val serverMessage: String? = null,
    @SerialName("rimuoviDatiLocali") public val removeLocalData: Boolean = false,
    @SerialName("ricaricaDati") public val reloadData: Boolean = false,
) {
    /** Convenience accessor for grade-less new students where the average is absent. */
    public fun hasGrades(): Boolean = grades.isNotEmpty()
}
