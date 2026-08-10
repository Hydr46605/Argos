package it.hydr4.argo.models

import it.hydr4.argo.testing.Fixtures
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Wire-shape deserialization of the lenient timestamp fields. */
class VotoDeserializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `valid datetime and date wire values parse onto java time`() {
        val voto = json.decodeFromString(
            Voto.serializer(),
            """
            {
              "pk": "1",
              "datEvento": "2026-03-10 10:30:00.000",
              "datGiorno": "2026-03-10",
              "valore": 7.5,
              "desMateria": "MATEMATICA",
              "mese": 3
            }
            """.trimIndent(),
        )
        assertEquals(LocalDateTime.of(2026, 3, 10, 10, 30, 0), voto.datEvento)
        assertEquals(LocalDate.of(2026, 3, 10), voto.day)
        assertEquals(7.5, voto.value)
    }

    @Test
    fun `malformed optional datetime degrades to null instead of throwing`() {
        val voto = json.decodeFromString(
            Voto.serializer(),
            """{ "pk": "2", "datEvento": "not-a-timestamp", "datGiorno": "2026-03-10" }""",
        )
        assertNull(voto.datEvento)
        assertEquals(LocalDate.of(2026, 3, 10), voto.day)
    }

    @Test
    fun `json null datetime stays null`() {
        val voto = json.decodeFromString(Voto.serializer(), """{ "pk": "3", "datEvento": null }""")
        assertNull(voto.datEvento)
    }

    @Test
    fun `iso-separator datetime variant is accepted`() {
        val voto = json.decodeFromString(Voto.serializer(), """{ "pk": "4", "datEvento": "2026-03-10T10:30:00" }""")
        assertEquals(LocalDateTime.of(2026, 3, 10, 10, 30, 0), voto.datEvento)
    }

    @Test
    fun `serialized token round-trips through the ISO instant serializer`() {
        val token = Token(
            accessToken = "at",
            refreshToken = "rt",
            expiresAt = java.time.Instant.parse("2026-08-25T08:00:00Z"),
            scope = "openid",
            tokenType = "Bearer",
        )
        val encoded = json.encodeToString(Token.serializer(), token)
        assertEquals(token, json.decodeFromString(Token.serializer(), encoded))
        kotlin.test.assertTrue("2026-08-25T08:00:00Z" in encoded)
    }

    @Test
    fun `dashboard fixture votes decode against the full model`() {
        val root = json.parseToJsonElement(Fixtures.text("dashboard-full.json")).jsonObject
        val dati = (root["data"]!!.jsonObject["dati"] as JsonArray).first().jsonObject
        val votes = json.decodeFromJsonElement(ListSerializer(Voto.serializer()), dati["voti"]!!)
        assertEquals(2, votes.size)
        assertEquals("MATEMATICA", votes.first().subjectName)
        assertEquals(LocalDate.of(2026, 3, 10), votes.first().day)
        assertEquals(7.5, votes.first().value)
    }
}
