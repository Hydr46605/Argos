package it.hydr4.argo.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Canonical converters between Argo wire timestamps and `java.time` types.
 *
 * The Argo servers emit two datetime dialects — `yyyy-MM-dd HH:mm:ss` and
 * `yyyy-MM-dd HH:mm:ss.SSS` — and occasionally accept an ISO-`T` variant.
 * Parsing is strict about the known shapes and rejects anything else so that
 * schema drift surfaces loudly during testing rather than silently corrupting
 * domain timestamps.
 */
public object TimeFormats {
    private val WIRE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val WIRE_DATETIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val WIRE_DATETIME_MILLIS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * Header/wire formatting used by Argo requests such as `x-date-exp-auth`,
     * `exp-bearer`, `ts-app` and `dataultimoaggiornamento`.
     */
    public fun formatWire(dateTime: LocalDateTime): String = WIRE_DATETIME_MILLIS.format(dateTime)

    /** Formats a wire datetime preserving the local-zone semantics of the reference client. */
    public fun formatWire(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        formatWire(LocalDateTime.ofInstant(instant, zone))

    /** Parses `yyyy-MM-dd` or throws [IllegalArgumentException]. */
    public fun parseDate(raw: String): LocalDate = LocalDate.parse(raw, WIRE_DATE)

    /**
     * Parses the datetime dialects above, including the ISO `T` separator variant.
     *
     * @throws IllegalArgumentException when none of the known shapes match.
     */
    public fun parseDateTime(raw: String): LocalDateTime {
        val normalized = raw.trim().replace('T', ' ')
        return runCatching {
            LocalDateTime.parse(normalized, WIRE_DATETIME_MILLIS)
        }.getOrElse {
            LocalDateTime.parse(normalized, WIRE_DATETIME)
        }
    }

    /** Lenient probe used by tests and diagnostics; returns `null` instead of throwing. */
    public fun tryParseDate(raw: String?): LocalDate? = raw?.let {
        runCatching { parseDate(it) }.getOrNull()
    }

    /** Lenient probe returning `null` for unparseable input, mirroring optional wire fields. */
    public fun tryParseDateTime(raw: String?): LocalDateTime? = raw?.let {
        runCatching { parseDateTime(it) }.getOrNull()
    }
}
