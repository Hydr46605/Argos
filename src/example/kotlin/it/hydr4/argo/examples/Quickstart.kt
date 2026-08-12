package it.hydr4.argo.examples

import it.hydr4.argo.ArgoClient
import it.hydr4.argo.models.Credentials
import it.hydr4.argo.models.Dashboard
import kotlinx.coroutines.runBlocking

/**
 * End-to-end usage sample mirroring the README quickstart: credential login,
 * first dashboard fetch and a couple of repository calls.
 *
 * **Never commit real credentials.** Replace the placeholder school code,
 * username and password with values from the student's family register.
 */
public object Quickstart {

    /** Runs the whole sample and prints a summary. */
    @JvmStatic
    public fun main(args: Array<String>) {
        val client = ArgoClient.create()
        try {
            val credentials = Credentials(
                schoolCode = "SS00000", // ministerial school code shown in the portal URL
                username = "your-username",
                password = "your-password",
            )
            val dashboard: Dashboard = runBlocking { client.login(credentials) }
            println("Welcome! mediaGenerale = ${dashboard.overallAverage}")
            println("Grades recorded: ${dashboard.grades.size}")
            println("Latest bulletin: ${dashboard.bulletins.firstOrNull()?.category ?: "none"}")

            runBlocking {
                val profile = client.profiles.profilo()
                println("Student: ${profile.alunno.nominativo}")
                val today = client.schedule.orarioGiornaliero()
                println("Today's timetable slots: ${today.size}")
            }
        } finally {
            client.close()
        }
    }
}
