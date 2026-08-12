package it.hydr4.argo.examples

import it.hydr4.argo.ArgoClient
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.sync.PollDecision
import it.hydr4.argo.util.ArgoVersion
import it.hydr4.argo.util.RetryPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Advanced usage sample: durable restore, change-probe driven synchronization,
 * retried repository calls and runtime version introspection.
 *
 * **Never commit real credentials.** Run with `./gradlew runExample -PexampleRun`
 * after putting your own values in [credentials] — the task refuses to start
 * without the `exampleRun` flag on purpose.
 */
public object AdvancedUsage {

    @JvmStatic
    public fun main(args: Array<String>) {
        val client = ArgoClient.create() // production endpoints + encrypted local store
        try {
            runBlocking {
                val restored = client.restorePersistedSession()
                if (restored) {
                    println("Restored a persisted session (library ${ArgoVersion.current}).")
                } else {
                    println("No persisted session; logging in (library ${ArgoVersion.current}).")
                    client.login(
                        Credentials(
                            schoolCode = "SS00000",
                            username = "your-username",
                            password = "your-password",
                        ),
                    )
                }

                // Change-probe driven loop: skip the expensive fetch when nothing changed.
                repeat(3) { round ->
                    val (snapshot, decision) = client.synchronize()
                    println("Round $round -> $decision (grades=${snapshot?.grades?.size})")
                    when (decision) {
                        is PollDecision.Clean -> Unit // nothing changed, reuse snapshot
                        is PollDecision.FetchDashboard -> Unit // full fetch already happened
                        is PollDecision.SchedaChanged -> println("Scheda drifted; profile refreshed.")
                        is PollDecision.SessionInvalid -> error("Session rejected; re-login required.")
                    }
                    delay(5_000)
                }

                // Transport is flaky and undocumented: wrap repository calls in a retry policy.
                val resilient = RetryPolicy(maxAttempts = 3)
                val profile = resilient.retry { client.profiles.profilo() }
                val today = resilient.retry { client.schedule.orarioGiornaliero() }
                println("Student: ${profile.alunno.nominativo}")
                println("Timetable slots today: ${today.size}")

                resilient.retry {
                    val fees = client.fees.listatasse(profile.scheda.pk)
                    println("Fees: ${fees.rows.size} rows, online payment ${if (fees.isOnlinePaymentActive) "enabled" else "disabled"}")
                }
            }
        } finally {
            client.close()
        }
    }
}
