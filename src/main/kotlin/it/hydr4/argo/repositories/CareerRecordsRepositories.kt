package it.hydr4.argo.repositories

import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.models.CorsiRecuperoData
import it.hydr4.argo.models.CurriculumEntry
import it.hydr4.argo.models.PctoData
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Internship pathways (`pcto`).
 */
public class PctoRepository(private val http: ArgoHttpClient) {
    /** Decoded pcto container for [schedaPk]; contents opaque until stable upstream shapes emerge. */
    public suspend fun pcto(schedaPk: String): PctoData = http.fetch(
        Endpoints.PCTO,
        body = buildJsonObject { put("pkScheda", schedaPk) },
        dataSerializer = PctoData.serializer(),
    )
}

/**
 * Recovery courses (`corsirecupero`).
 */
public class RecoveryCoursesRepository(private val http: ArgoHttpClient) {
    /** Decoded recovery-course container; collections stay opaque pending stable shapes. */
    public suspend fun corsiRecupero(schedaPk: String): CorsiRecuperoData = http.fetch(
        Endpoints.CORSI_RECUPERO,
        body = buildJsonObject { put("pkScheda", schedaPk) },
        dataSerializer = CorsiRecuperoData.serializer(),
    )
}

/**
 * Curriculum year entries (`curriculumalunno`).
 */
public class CurriculumRepository(private val http: ArgoHttpClient) {
    /**
     * All curriculum years recorded for [schedaPk], ordered as upstream returns them.
     */
    public suspend fun curriculum(schedaPk: String): List<CurriculumEntry> {
        val shell =
            http.fetchEnvelope(
                Endpoints.CURRICULUM_ALUNNO,
                buildJsonObject { put("pkScheda", schedaPk) },
            )
        val array =
            shell.envelopeData()?.get("curriculum")
                as? kotlinx.serialization.json.JsonArray ?: emptyList()
        return array.map { http.json.decodeStrict(CurriculumEntry.serializer(), it, Endpoints.CURRICULUM_ALUNNO) }
    }
}
