package it.hydr4.argo.auth

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Derives absolute expiry instants from HTTP `Date` headers.
 *
 * Argo returns relative `expires_in` values; the reference client anchors them on
 * the server-issued response date so client clock skew never shifts validity.
 */
public object ServerInstant {
    private val RFC1123 = DateTimeFormatter.RFC_1123_DATE_TIME

    /**
     * Parses a RFC 1123 date header, falling back to [fallback] when absent/unparseable.
     */
    public fun fromHeader(headerValue: String?, fallback: Instant = Instant.now()): Instant = headerValue?.let {
        runCatching { Instant.from(RFC1123.parse(it.trim())) }.getOrNull()
    } ?: fallback
}
