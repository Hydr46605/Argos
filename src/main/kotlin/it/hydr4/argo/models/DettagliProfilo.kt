package it.hydr4.argo.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Extended personal data from the `dettaglioprofilo` endpoint.
 *
 * All three sections are modeled nullable: deployments have been observed to
 * omit individual sections (the TypeScript reference typed them partially as `any`).
 *
 * @property utente Account-level metadata.
 * @property genitore Parent extended data.
 * @property alunno Student extended data.
 */
@Serializable
public data class ProfileDetails(
    @SerialName("utente") public val utente: UtenteInfo? = null,
    @SerialName("genitore") public val genitore: ParentDetails? = null,
    @SerialName("alunno") public val alunno: StudentDetails? = null,
)

/** Account metadata fragment. */
@Serializable
public data class UtenteInfo(@SerialName("flgUtente") public val flag: String)

/**
 * Parent extended data.
 *
 * Birth date is kept as raw wire text: the upstream format has been observed
 * inconsistent between schools (`yyyy-MM-dd` vs `dd/MM/yyyy`).
 */
@Serializable
public data class ParentDetails(
    @SerialName("desCognome") public val cognome: String? = null,
    @SerialName("desNome") public val nome: String? = null,
    @SerialName("desEMail") public val email: String? = null,
    @SerialName("desCellulare") public val mobile: String? = null,
    @SerialName("desTelefono") public val phone: String? = null,
    @SerialName("datNascita") public val birthDateRaw: String? = null,
    @SerialName("flgSesso") public val genderFlag: String? = null,
)

/**
 * Student extended data. Contact/registry fields are individually optional:
 * instability comes from municipal registry imports.
 */
@Serializable
public data class StudentDetails(
    @SerialName("cognome") public val cognome: String? = null,
    @SerialName("nome") public val nome: String? = null,
    @SerialName("nominativo") public val nominativo: String? = null,
    @SerialName("desCf") public val fiscalCode: String? = null,
    @SerialName("sesso") public val gender: String? = null,
    @SerialName("cittadinanza") public val citizenship: String? = null,
    @SerialName("datNascita") public val birthDateRaw: String? = null,
    @SerialName("desComuneNascita") public val birthTown: String? = null,
    @SerialName("desComuneResidenza") public val residenceTown: String? = null,
    @SerialName("desVia") public val street: String? = null,
    @SerialName("desCap") public val postalCode: String? = null,
    @SerialName("desCapResidenza") public val residencePostalCode: String? = null,
    @SerialName("desIndirizzoRecapito") public val deliveryAddress: String? = null,
    @SerialName("desComuneRecapito") public val deliveryTown: String? = null,
    @SerialName("desEMail") public val email: String? = null,
    @SerialName("desTelefono") public val phone: String? = null,
    @SerialName("desCellulare") public val mobile: String? = null,
)
