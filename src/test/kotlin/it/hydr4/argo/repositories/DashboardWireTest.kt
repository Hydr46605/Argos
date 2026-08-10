package it.hydr4.argo.repositories

import io.mockk.mockk
import it.hydr4.argo.api.EnvelopeShell
import it.hydr4.argo.auth.ArgoSession
import it.hydr4.argo.models.Dashboard
import it.hydr4.argo.models.Voto
import it.hydr4.argo.testing.FakeEngine
import it.hydr4.argo.testing.Fixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dashboard payload decoding, delta resolution and wipe semantics. */
class DashboardWireTest {

    private val mapper: Json = Json { ignoreUnknownKeys = true }
    private val wire = DashboardWire(mapper)

    private fun envelope(fixture: String): EnvelopeShell = EnvelopeShell(
        mapper.parseToJsonElement(Fixtures.text(fixture)).jsonObject,
        FakeEngine.json(Fixtures.text(fixture)),
    )

    @Test
    fun `full fixture populates averages and register collections`() {
        val dashboard = wire.assemble(envelope("dashboard-full.json"), previous = null)

        assertEquals(7.25, dashboard.overallAverage)
        assertEquals(7.5, dashboard.monthlyAverages["2026-03"])
        assertEquals(7.0, dashboard.monthlyAverages["2026-04"])
        assertEquals(7.25, dashboard.periodAverages["12345"]?.overall)
        assertEquals(7.5, dashboard.periodAverages["12345"]?.byMonth?.get("2026-03"))
        assertEquals(2, dashboard.subjectAverages.size)

        assertEquals(listOf("MATEMATICA", "ITALIANO"), dashboard.subjects.map { it.materia })
        assertEquals(2, dashboard.periods.size)
        assertEquals("Primo periodo", dashboard.periods.first().description)
        assertEquals(LocalDate.of(2025, 9, 11), dashboard.periods.first().startsOn)

        assertEquals(2, dashboard.grades.size)
        val grade = dashboard.grades.first()
        assertEquals("MATEMATICA", grade.subjectName)
        assertEquals(7.5, grade.value)
        assertEquals(LocalDate.of(2026, 3, 10), grade.day)
        assertNull(grade.comment, "absent optional comment stays null")

        assertEquals(1, dashboard.bulletins.size)
        assertEquals("CIRCOLARI", dashboard.bulletins.first().category)
        assertEquals(1, dashboard.attendance.size)
        assertEquals(1, dashboard.lessons.size)
        assertEquals("Equazioni di secondo grado", dashboard.lessons.first().activity)
        assertEquals(1, dashboard.lessons.first().homework.size)
        assertTrue(dashboard.lessons.first().isSigned)
        assertEquals(1, dashboard.options.size)
        assertEquals("opz-vot_annuale", dashboard.options.first().key)
        assertEquals("ok", dashboard.serverMessage)
        assertFalse(dashboard.removeLocalData)
        assertFalse(dashboard.reloadData)
        assertEquals(FakeEngine.SERVER_INSTANT, dashboard.fetchedAt)
    }

    @Test
    fun `delta fixture deletes tombstones and merges inserts`() {
        val previous =
            Dashboard(
                grades = listOf(
                    Voto(pk = "101", subjectName = "MATEMATICA", value = 7.5),
                    Voto(pk = "102", subjectName = "MATEMATICA", value = 7.0),
                ),
                bulletins = listOf(),
            )
        val merged = wire.assemble(envelope("dashboard-delta.json"), previous)

        val ids = merged.grades.map { it.pk }
        assertEquals(listOf("102", "103"), ids, "101 tombstoned, 103 inserted")
        assertEquals(6.5, merged.grades.last().value)
        assertEquals(7.0, merged.overallAverage)
    }

    @Test
    fun `removeLocalData discards the previous snapshot entirely`() {
        val previous = Dashboard(grades = listOf(Voto(pk = "101", subjectName = "OLD", value = 1.0)))
        val wipePayload = Fixtures.text("dashboard-full.json")
            .replace("\"rimuoviDatiLocali\": \"false\"", "\"rimuoviDatiLocali\": \"true\"")
        val shell = EnvelopeShell(
            mapper.parseToJsonElement(wipePayload).jsonObject,
            FakeEngine.json(wipePayload),
        )
        val merged = wire.assemble(shell, previous)
        assertEquals(listOf("101", "102"), merged.grades.map { it.pk }, "previous grades must be wiped, not merged")
        assertTrue(merged.removeLocalData)
    }

    @Test
    fun `empty fixture yields an empty but valid dashboard`() {
        val dashboard = wire.assemble(envelope("dashboard-empty.json"), previous = null)
        assertNull(dashboard.overallAverage)
        assertTrue(dashboard.grades.isEmpty())
        assertTrue(dashboard.subjects.isEmpty())
        assertFalse(dashboard.hasGrades())
    }

    @Test
    fun `request payload anchors on the school year when no snapshot exists`() {
        val session = mockk<ArgoSession>(relaxed = true)
        val body = wire.buildRequestPayload(previous = null, sinceOverride = null, session = session, profile = null)
        assertTrue(body.containsKey("dataultimoaggiornamento"))
        assertTrue(body.containsKey("opzioni"))
    }

    @Test
    fun `request payload carries the previous fetch anchor and persisted options`() {
        val session = mockk<ArgoSession>(relaxed = true)
        val previous = Dashboard(fetchedAt = FakeEngine.SERVER_INSTANT.minusSeconds(60))
        val body = wire.buildRequestPayload(previous = previous, sinceOverride = null, session = session, profile = null)
        val anchor = body["dataultimoaggiornamento"]!!.toString().trim('"')
        assertTrue(anchor.isNotBlank(), "anchor must be present once a snapshot exists")
        assertEquals("{}", (body["opzioni"] as kotlinx.serialization.json.JsonPrimitive).content)
    }
}
