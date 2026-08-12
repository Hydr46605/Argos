package it.hydr4.argo.integration

import it.hydr4.argo.ArgoClient
import it.hydr4.argo.models.Credentials
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Live smoke test against the real Argo endpoints.
 *
 * Credentials come from the gitignored `local-test.properties` at the repo root
 * (see `local-test.properties.example`) or the `ARGO_TEST_*` environment
 * variables. Without either, the suite **skips** instead of failing, so CI and
 * `verifyAll` never depend on live services.
 *
 * **Security:** values are read only into the process; the test prints sanitized
 * summaries (averages and counts), never tokens, passwords or school codes.
 */
class LiveSmokeTest {

    @Test
    fun `credential login lands on a populated dashboard against the real API`() {
        val credentials = loadCredentials()
        assumeTrue(credentials != null, "Live test skipped: no credentials in local-test.properties or ARGO_TEST_* env")

        val client = ArgoClient.create()
        try {
            val dashboard = kotlinx.coroutines.runBlocking { client.login(credentials!!) }

            println(
                "[live] login ok: mediaGenerale=${dashboard.overallAverage}, voti=${dashboard.grades.size}, materie=${dashboard.subjects.size}",
            )
            checkNotNull(dashboard.fetchedAt) { "dashboard must carry a server timestamp" }

            kotlinx.coroutines.runBlocking {
                val profile = client.profiles.profilo()
                println("[live] profilo ok: ${profile.alunno.nominativo} (${profile.scheda.classe.denomination})")
                val schedule = client.schedule.orarioGiornaliero()
                println("[live] orario ok: ${schedule.size} slots today")
                val meetings = client.meetings.ricevimenti()
                println("[live] ricevimento ok: ${meetings.slots.size} availability windows")
                client.logout()
            }
        } finally {
            client.close()
        }
    }

    /** Reads `local-test.properties` then falls back to `ARGO_TEST_*` env vars. */
    private fun loadCredentials(): Credentials? {
        val propsFile = Path.of(System.getProperty("user.dir"), "local-test.properties")
        if (Files.exists(propsFile)) {
            val props = Properties().apply { Files.newInputStream(propsFile).use { load(it) } }
            val school = props.getProperty("argo.test.schoolCode")?.trim()
            val username = props.getProperty("argo.test.username")?.trim()
            val password = props.getProperty("argo.test.password")
            if (!school.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()) {
                return Credentials(schoolCode = school, username = username, password = password)
            }
        }
        val school = System.getenv("ARGO_TEST_SCHOOL_CODE")
        val username = System.getenv("ARGO_TEST_USERNAME")
        val password = System.getenv("ARGO_TEST_PASSWORD")
        return if (!school.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()) {
            Credentials(schoolCode = school, username = username, password = password)
        } else {
            null
        }
    }
}
