package it.hydr4.argo.exceptions

import it.hydr4.argo.models.Credentials

/**
 * Root of every failure surfaced by the library.
 *
 * @property detail Safe-for-logs description; constructors deliberately accept
 *   only redactable fragments (paths, status codes, server messages).
 */
public sealed class ArgoException(public val detail: String, cause: Throwable? = null) : Exception(detail, cause)

/**
 * The Argo REST service answered `success: false`, or an unexpected HTTP status.
 *
 * @property endpoint API path that failed (e.g. `dashboard/dashboard`).
 * @property httpStatus HTTP status code when known.
 */
public class ArgoApiException(public val endpoint: String, public val httpStatus: Int?, message: String?) :
    ArgoException("API call to '$endpoint' failed${httpStatus?.let { " (HTTP $it)" } ?: ""}: ${message ?: "no message"}")

/**
 * Credential or token-level failure during authentication or refresh.
 */
public open class AuthenticationException(detail: String, cause: Throwable? = null) : ArgoException(detail, cause)

/** Thrown when [Credentials] are incomplete — before any network traffic happens. */
public class MissingCredentialsException :
    AuthenticationException(
        "Missing credentials: schoolCode, username and password are all required for login",
    )

/**
 * The refresh grant was rejected in a way that proves the session dead
 * (`invalid_grant`, `invalid_client`, `unauthorized_client`).
 *
 * Local session material is wiped before this is thrown, so callers should
 * prompt for re-authentication rather than retry or restore.
 */
public class RefreshRejectedException(detail: String) : AuthenticationException(detail)

/**
 * Network-level failure (DNS, socket, timeouts) or non-JSON body from a JSON
 * endpoint; the cause carries the transport-specific details.
 *
 * Never include request bodies in [detail] — they can embed tokens.
 */
public class NetworkException(cause: Throwable) : ArgoException("Network failure while calling Argo: ${cause.javaClass.simpleName}", cause)

/**
 * Unexpected protocol response (malformed envelope shape, invalid PKCE payload,
 * broken redirect chain).
 */
public class ProtocolException(detail: String, cause: Throwable? = null) : ArgoException(detail, cause)

/**
 * Strict deserialization failure against a known schema.
 *
 * Wire snippets are never embedded into [detail] to avoid leaking user data from
 * payloads into logs.
 */
public class DeserializationException(endpoint: String, cause: Throwable?) :
    ArgoException("Failed to decode response of '$endpoint' against the expected schema", cause)
