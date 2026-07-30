package it.hydr4.argo.repositories

import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.Endpoints
import it.hydr4.argo.exceptions.ArgoApiException
import it.hydr4.argo.exceptions.ProtocolException
import it.hydr4.argo.models.AllegatoDownload
import it.hydr4.argo.models.BachecaAlunnoEntry
import it.hydr4.argo.models.BachecaEntry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Historical bulletin boards: teachers' (`storicobacheca`) and student-facing
 * (`storicobachecaalunno`) plus signed attachment download links.
 */
public class BulletinRepository(private val http: ArgoHttpClient) {
    /**
     * Full teacher bulletin history for [schedaPk].
     *
     * @param schedaPk Enrollment identifier; callers usually pass
     *   `client.profiles.currentOrNull()?.scheda?.pk`.
     */
    public suspend fun storicoBacheca(schedaPk: String): List<BachecaEntry> = fetchList(
        Endpoints.STORICO_BACHECA,
        buildJsonObject { put("pkScheda", schedaPk) },
        BachecaEntry.serializer(),
        dataKey = "bacheca",
    )

    /** Student board history for [schedaPk] (report cards, documents). */
    public suspend fun storicoBachecaAlunno(schedaPk: String): List<BachecaAlunnoEntry> = fetchList(
        Endpoints.STORICO_BACHECA_ALUNNO,
        buildJsonObject { put("pkScheda", schedaPk) },
        BachecaAlunnoEntry.serializer(),
        dataKey = "bachecaAlunno",
    )

    /** Time-limited URL of a teacher-board attachment. */
    public suspend fun linkAllegato(uid: String): String = downloadLink(Endpoints.DOWNLOAD_ALLEGATO, buildJsonObject { put("uid", uid) })

    /** Time-limited URL of a student-board attachment. */
    public suspend fun linkAllegatoStudente(uid: String, schedaPk: String): String = downloadLink(
        Endpoints.DOWNLOAD_ALLEGATO_ALUNNO,
        buildJsonObject {
            put("uid", uid)
            put("pkScheda", schedaPk)
        },
    )

    private suspend fun <T : Any> fetchList(
        path: String,
        body: kotlinx.serialization.json.JsonObject,
        serializer: kotlinx.serialization.KSerializer<T>,
        dataKey: String,
    ): List<T> {
        val shell = http.fetchEnvelope(path, body)
        val array = shell.envelopeData()?.get(dataKey) as? kotlinx.serialization.json.JsonArray
        return array.orEmpty().map { http.json.decodeStrict(serializer, it, path) }
    }

    private suspend fun downloadLink(path: String, body: kotlinx.serialization.json.JsonObject): String {
        val shell = try {
            http.fetchEnvelope(path, body)
        } catch (e: ArgoApiException) {
            // This endpoint answers success:false with only msg (e.g. expired link).
            throw ProtocolException("$path did not yield a download link${e.httpStatus?.let { " (HTTP $it)" } ?: ""}", e)
        }
        val download = http.json.decodeStrict(AllegatoDownload.serializer(), shell.envelope, path)
        return download.url
    }
}
