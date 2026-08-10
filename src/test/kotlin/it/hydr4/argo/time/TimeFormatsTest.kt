package it.hydr4.argo.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Wire timestamp dialect coverage for the canonical `java.time` converters. */
class TimeFormatsTest {
    @Test
    fun `date parses the plain wire format`() {
        assertEquals(LocalDate.of(2026, 8, 25), TimeFormats.parseDate("2026-08-25"))
    }

    @Test
    fun `datetime parses both dialects and the ISO separator variant`() {
        assertEquals(LocalDateTime.of(2026, 3, 10, 10, 30, 0), TimeFormats.parseDateTime("2026-03-10 10:30:00"))
        assertEquals(LocalDateTime.of(2026, 3, 10, 10, 30, 0, 500_000_000), TimeFormats.parseDateTime("2026-03-10 10:30:00.500"))
        assertEquals(LocalDateTime.of(2026, 3, 10, 10, 30, 0), TimeFormats.parseDateTime("2026-03-10T10:30:00"))
    }

    @Test
    fun `datetime parsing rejects unknown shapes`() {
        assertFailsWith<java.time.format.DateTimeParseException> { TimeFormats.parseDateTime("gibberish") }
        assertFailsWith<java.time.format.DateTimeParseException> { TimeFormats.parseDate("25/08/2026") }
    }

    @Test
    fun `lenient probes return null instead of throwing`() {
        assertNull(TimeFormats.tryParseDateTime("not a date"))
        assertNull(TimeFormats.tryParseDate(null))
        assertEquals(LocalDate.of(2026, 1, 2), TimeFormats.tryParseDate("2026-01-02"))
    }

    @Test
    fun `formatWire emits the millisecond dialect used by headers and bodies`() {
        val instant = Instant.parse("2026-08-25T08:00:00Z")
        val formatted = TimeFormats.formatWire(instant, ZoneOffset.UTC)
        assertEquals("2026-08-25 08:00:00.000", formatted)
    }

    @Test
    fun `formatWire round-trips through parseDateTime`() {
        val instant = Instant.parse("2026-03-10T10:30:00Z")
        assertEquals(
            LocalDateTime.ofInstant(instant, ZoneOffset.UTC),
            TimeFormats.parseDateTime(TimeFormats.formatWire(instant, ZoneOffset.UTC)),
        )
    }
}
