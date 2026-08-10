package it.hydr4.argo.repositories

import it.hydr4.argo.api.ArgoClientConfig
import it.hydr4.argo.api.ArgoHttpClient
import it.hydr4.argo.api.AuthHeaders
import it.hydr4.argo.api.SessionContext
import it.hydr4.argo.exceptions.ProtocolException
import it.hydr4.argo.testing.FakeEngine
import it.hydr4.argo.testing.Fixtures
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wire-shape coverage for the thin endpoint repositories, all over the same
 * authenticated fake transport.
 */
class EndpointRepositoriesTest {

    private val session =
        object : SessionContext {
            override suspend fun headersWithFreshBearer(): AuthHeaders =
                AuthHeaders("bearer-x", "xauth-x", "SS13325", Instant.parse("2026-08-25T09:00:00Z"))
            override suspend fun currentHeaders(): AuthHeaders? = headersWithFreshBearer()
            override suspend fun forceRefresh(): AuthHeaders = headersWithFreshBearer()
        }

    private fun client(engine: FakeEngine): ArgoHttpClient = ArgoHttpClient(engine, ArgoClientConfig(), session)

    @Test
    fun `orarioGiornaliero flattens hour-grouped slots`() = runTest {
        val engine = FakeEngine("orario-giorno" to { FakeEngine.json(Fixtures.text("orario-giorno.json")) })
        val repository = ScheduleRepository(client(engine))
        val slots = repository.orarioGiornaliero(LocalDate.of(2026, 3, 11))
        assertEquals(2, slots.size)
        assertEquals("MATEMATICA", slots[0].subject)
        assertEquals("ITALIANO", slots[1].subject)
        assertEquals(1, slots[0].hourNumber)
        val body = engine.requests.single().body.orEmpty()
        assertTrue("datGiorno" in body)
    }

    @Test
    fun `votiScrutinio reads the first record's periods`() = runTest {
        val engine = FakeEngine("votiscrutinio" to { FakeEngine.json(Fixtures.text("voti-scrutinio.json")) })
        val repository = ScrutinioRepository(client(engine))
        val periods = repository.votiScrutinio()
        assertEquals(1, periods.size)
        assertEquals("Primo periodo", periods.first().description)
        assertEquals(listOf("MATEMATICA", "ITALIANO"), periods.first().subjects)
    }

    @Test
    fun `listatasse decodes rows and the online-payment flag`() = runTest {
        val engine = FakeEngine("listatassealunni" to { FakeEngine.json(Fixtures.text("listatasse.json")) })
        val repository = FeesRepository(client(engine))
        val sheet = repository.listatasse("9876")
        assertEquals(1, sheet.rows.size)
        assertTrue(sheet.isOnlinePaymentActive)
        assertEquals("Rata 1", sheet.rows.first().installment)
        assertEquals("IUV-0001", sheet.rows.first().iuv)
        assertTrue(sheet.rows.first().hasReceipt)
    }

    @Test
    fun `ricevutaTelematica returns null when the payment has no receipt`() = runTest {
        val engine = FakeEngine("ricevutatelematica" to { FakeEngine.json("""{"success":false,"msg":"nessuna ricevuta"}""") })
        val repository = FeesRepository(client(engine))
        assertNull(repository.ricevutaTelematica("IUV-9999"))
    }

    @Test
    fun `ricevimenti flattens teacher-keyed availability`() = runTest {
        val engine = FakeEngine("ricevimento" to { FakeEngine.json(Fixtures.text("ricevimento.json")) })
        val repository = MeetingsRepository(client(engine))
        val meetings = repository.ricevimenti()
        assertEquals(1, meetings.slots.size)
        assertEquals("R1", meetings.slots.first().pk)
        assertEquals("16:00", meetings.slots.first().startsRaw)
        assertEquals(1, meetings.people.size)
        assertEquals("F", meetings.accessType)
        assertEquals("9001", meetings.slots.first().docente?.pk)
    }

    @Test
    fun `storicoBacheca decodes the teacher bulletin history`() = runTest {
        val engine = FakeEngine("storicobacheca" to { FakeEngine.json(Fixtures.text("storicobacheca.json")) })
        val repository = BulletinRepository(client(engine))
        val entries = repository.storicoBacheca("9876")
        assertEquals(2, entries.size)
        assertEquals("CIRCOLARI", entries.first().category)
        assertEquals(1, entries.first().attachments.size)
        assertEquals("autorizzazione.pdf", entries.first().attachments.first().fileName)
    }

    @Test
    fun `storicoBachecaAlunno decodes the student board`() = runTest {
        val engine = FakeEngine("storicobachecaalunno" to { FakeEngine.json(Fixtures.text("storicobachecaalunno.json")) })
        val repository = BulletinRepository(client(engine))
        val entries = repository.storicoBachecaAlunno("9876")
        assertEquals(1, entries.size)
        assertEquals("pagella_primo_periodo.pdf", entries.first().fileName)
        assertTrue(entries.first().isPvConfirmed)
    }

    @Test
    fun `attachment links resolve the signed url`() = runTest {
        val engine = FakeEngine("downloadallegatobacheca" to { FakeEngine.json(Fixtures.text("downloadallegatobacheca.json")) })
        val repository = BulletinRepository(client(engine))
        assertEquals(
            "https://www.portaleargo.it/files/2026/03/signed-link-abc123.pdf",
            repository.linkAllegato("uid-1"),
        )
    }

    @Test
    fun `attachment link failure without url is a protocol error`() = runTest {
        val engine = FakeEngine("downloadallegatobacheca" to { FakeEngine.json("""{"success":false,"msg":"expired"}""") })
        val repository = BulletinRepository(client(engine))
        assertFailsWith<ProtocolException> { repository.linkAllegato("uid-1") }
    }

    @Test
    fun `pcto and recovery courses decode their containers`() = runTest {
        val engine =
            FakeEngine(
                "pcto" to { FakeEngine.json(Fixtures.text("pcto.json")) },
                "corsirecupero" to { FakeEngine.json(Fixtures.text("corsirecupero.json")) },
            )
        val http = client(engine)
        val pcto = PctoRepository(http).pcto("9876")
        val courses = RecoveryCoursesRepository(http).corsiRecupero("9876")
        assertEquals(0, pcto.pathways?.size)
        assertEquals(0, courses.courses?.size)
    }

    @Test
    fun `curriculum decodes year entries`() = runTest {
        val engine = FakeEngine("curriculumalunno" to { FakeEngine.json(Fixtures.text("curriculumalunno.json")) })
        val repository = CurriculumRepository(client(engine))
        val entries = repository.curriculum("9876")
        assertEquals(2, entries.size)
        assertEquals(2024, entries.first().anno)
        assertEquals("Ammesso", entries.first().esito?.description)
        assertEquals(8.0, entries.first().credit)
        assertEquals("2A INFO", entries.first().classe)
    }

    @Test
    fun `dettagliProfilo decodes the three optional sections`() = runTest {
        val engine = FakeEngine("dettaglioprofilo" to { FakeEngine.json(Fixtures.text("dettaglioprofilo.json")) })
        val repository = ProfileRepository(client(engine), mockSession())
        val details = repository.dettagliProfilo()
        assertNotNull(details.utente)
        assertEquals("ROSSI", details.genitore?.cognome)
        assertEquals("LUCA", details.alunno?.nome)
        assertEquals("RSSLCA05T01H000X", details.alunno?.fiscalCode)
    }

    private fun mockSession(): it.hydr4.argo.auth.ArgoSession = io.mockk.mockk(relaxed = true)
}
