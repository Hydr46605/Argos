package it.hydr4.argo.models

import it.hydr4.argo.models.ModelTimeSerializers.WireDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * The authenticated profile returned by the `profilo` endpoint.
 *
 * Unstable/optional fields are nullable by contract: several Argo deployments
 * omit sections depending on school configuration.
 *
 * @property anno School year context; start/end dates feed delta calculations.
 * @property genitore Parent (family account) identity.
 * @property alunno Student identity.
 * @property scheda Enrollment "scheda" — the per-school-year enrollment whose
 *   [Scheda.pk] is the `pkScheda` required by most student-scoped endpoints.
 * @property isPasswordResetPending Server-side password reset flag.
 * @property lastPasswordChange Unstable: timestamp string, format varies by deployment.
 * @property isFirstAccess First-login flag; unstable, may be omitted.
 * @property isHistoricalProfile `true` when viewing a past school year.
 * @property isSpid SPID-authenticated account flag; unstable, may be omitted.
 */
@Serializable
public data class Profile(
    @SerialName("anno") public val anno: SchoolYear,
    @SerialName("genitore") public val genitore: FamilyContact,
    @SerialName("alunno") public val alunno: StudentIdentity,
    @SerialName("scheda") public val scheda: Scheda,
    @SerialName("resetPassword") public val isPasswordResetPending: Boolean = false,
    @SerialName("ultimoCambioPwd") public val lastPasswordChange: String? = null,
    @SerialName("primoAccesso") public val isFirstAccess: Boolean? = null,
    @SerialName("profiloStorico") public val isHistoricalProfile: Boolean? = null,
    @SerialName("profiloDisabilitato") public val isDisabled: Boolean? = null,
    @SerialName("isSpid") public val isSpid: Boolean? = null,
)

/**
 * School year span.
 *
 * @property anno Label such as `2025/2026`.
 * @property startsOn First day of the school year.
 * @property endsOn Last day of the school year.
 */
@Serializable
public data class SchoolYear(
    @SerialName("anno") public val anno: String,
    @Serializable(with = WireDate::class) @SerialName("dataInizio") public val startsOn: LocalDate,
    @Serializable(with = WireDate::class) @SerialName("dataFine") public val endsOn: LocalDate,
)

/**
 * Parent identity within the family register.
 *
 * @property nominativo Full name as registered by the school office.
 */
@Serializable
public data class FamilyContact(
    @SerialName("pk") public val pk: String,
    @SerialName("nominativo") public val nominativo: String,
    @SerialName("desEMail") public val email: String,
)

/**
 * Student identity inside a family account.
 *
 * Unstable upstream: some deployments report empty strings for missing fields
 * rather than omitting them, so fields stay non-null with emptiness meaning "unknown".
 *
 * @property nome First name; may be empty on incomplete imports.
 * @property cognome Surname; may be empty on incomplete imports.
 * @property isLastYearClass `true` in the final year of the school cycle.
 * @property email Nullable when no email is registered for the student.
 */
@Serializable
public data class StudentIdentity(
    @SerialName("pk") public val pk: String,
    @SerialName("nominativo") public val nominativo: String,
    @SerialName("nome") public val nome: String,
    @SerialName("cognome") public val cognome: String,
    @SerialName("isUltimaClasse") public val isLastYearClass: Boolean = false,
    @SerialName("maggiorenne") public val isAdult: Boolean = false,
    @SerialName("desEmail") public val email: String? = null,
)

/**
 * Enrollment record ("scheda") grouping class/course/school for one year.
 *
 * @property pk Enrollment identifier used as `pkScheda` across endpoints.
 */
@Serializable
public data class Scheda(
    @SerialName("pk") public val pk: String,
    @SerialName("classe") public val classe: Classe,
    @SerialName("corso") public val corso: Corso,
    @SerialName("sede") public val sede: Sede,
    @SerialName("scuola") public val scuola: Scuola,
)

/** Class group (e.g. `3ªA`). */
@Serializable
public data class Classe(
    @SerialName("pk") public val pk: String,
    @SerialName("desDenominazione") public val denomination: String,
    @SerialName("desSezione") public val section: String,
)

/** Study course. */
@Serializable
public data class Corso(@SerialName("pk") public val pk: String, @SerialName("descrizione") public val description: String)

/** Campus/building. */
@Serializable
public data class Sede(@SerialName("pk") public val pk: String, @SerialName("descrizione") public val description: String)

/** School institution of the enrollment. */
@Serializable
public data class Scuola(
    @SerialName("pk") public val pk: String,
    @SerialName("descrizione") public val description: String,
    @SerialName("desOrdine") public val orderCode: String,
)
