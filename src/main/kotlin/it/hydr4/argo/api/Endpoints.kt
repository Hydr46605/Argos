package it.hydr4.argo.api

/**
 * REST path table. Paths are relative to [ArgoConstants.REST_BASE_URL].
 * Every repository function maps onto exactly one entry here.
 */
public object Endpoints {
    /** Family-login metadata incl. `x-auth-token`. */
    public const val LOGIN: String = "login"

    /** Bearer refresh endpoint (custom, not RFC grant). */
    public const val REFRESH_TOKEN: String = "auth/refresh-token"

    /** Change-probe preceding dashboard fetches. */
    public const val DASHBOARD_WHAT: String = "dashboard/what"

    /** Full dashboard document. */
    public const val DASHBOARD: String = "dashboard/dashboard"

    /** Confirms last synchronization timestamp. */
    public const val DASHBOARD_UPDATE_DATE: String = "dashboard/aggiornadata"

    /** Profile summary including scheda pk. */
    public const val PROFILE: String = "profilo"

    /** Extended personal data. */
    public const val PROFILE_DETAILS: String = "dettaglioprofilo"

    /** Daily timetable for one day. */
    public const val ORARIO_GIORNO: String = "orario-giorno"

    /** Published scrutinio votes. */
    public const val VOTI_SCRUTINIO: String = "votiscrutinio"

    /** Teacher bulletin history. */
    public const val STORICO_BACHECA: String = "storicobacheca"

    /** Student bulletin history. */
    public const val STORICO_BACHECA_ALUNNO: String = "storicobachecaalunno"

    /** Fees installments for a scheda. */
    public const val LISTA_TASSE: String = "listatassealunni"

    /** Meeting availability and bookings. */
    public const val RICEVIMENTO: String = "ricevimento"

    /** Internship pathways. */
    public const val PCTO: String = "pcto"

    /** Recovery courses. */
    public const val CORSI_RECUPERO: String = "corsirecupero"

    /** Curriculum year entries. */
    public const val CURRICULUM_ALUNNO: String = "curriculumalunno"

    /** Signed URL for a bulletin attachment. */
    public const val DOWNLOAD_ALLEGATO: String = "downloadallegatobacheca"

    /** Signed URL for a student-board attachment. */
    public const val DOWNLOAD_ALLEGATO_ALUNNO: String = "downloadallegatobachecaalunno"

    /** Telematic receipt locator by IUV. */
    public const val RICEVUTA_TELEMATICA: String = "ricevutatelematica"

    /** Telemetry hook mirroring the reference client; not called unless opted-in. */
    public const val LOG_TOKEN: String = "logtoken"

    /** Profile removal executed at logout. */
    public const val RIMUOVI_PROFILO: String = "rimuoviprofilo"
}

/** Internal entropy source producing reference-compatible alphanumeric identifiers. */
public object Nonces {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private val random = java.security.SecureRandom()

    /**
     * Returns a cryptographically random alphanumeric string of [length] characters.
     *
     * The authorize-link `state` and OpenID `nonce` are anti-CSRF/anti-replay
     * markers and must never come from a weak PRNG.
     */
    public fun alphanumeric(length: Int): String = buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
