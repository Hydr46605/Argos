package it.hydr4.argo.auth

import it.hydr4.argo.api.ArgoConstants
import it.hydr4.argo.exceptions.AuthenticationException
import it.hydr4.argo.exceptions.DeserializationException
import it.hydr4.argo.exceptions.RefreshRejectedException
import it.hydr4.argo.time.TimeFormats
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Pure protocol mappings for Argo's custom `auth/refresh-token` contract.
 *
 * The reference client posts eleven specific JSON keys and expects either a
 * token payload or an `{error, error_description}` body. Everything here is
 * side-effect free so the exact byte shape is directly unit-testable against
 * recorded fixtures.
 */
public object RefreshProtocol {
    /**
     * Builds the refresh request body exactly like the reference client.
     *
     * @param refreshToken Existing `refresh_token`.
     * @param oldBearer Bearer being replaced (`old-bearer`).
     * @param currentExpiry Absolute expiry of [oldBearer] (`exp-bearer`).
     * @param scope Space-separated scopes of the old token.
     * @param username Session username when login data exists already.
     * @param now Wall clock anchor for `ts-app`; injectable for tests.
     */
    public fun buildBody(
        refreshToken: String,
        oldBearer: String,
        currentExpiry: Instant,
        scope: String,
        username: String?,
        now: Instant = Instant.now(),
    ): JsonObject = buildJsonObject {
        put("r-token", refreshToken)
        put("client-id", ArgoConstants.CLIENT_ID)
        // Scopes travel bracket-comma-joined upstream: "[openid, offline, ...]".
        put(
            "scopes",
            "[" + scope.split(' ').filter { it.isNotBlank() }.joinToString(", ") + "]",
        )
        put("old-bearer", oldBearer)
        put("primo-accesso", "false")
        put("ripeti-login", "false")
        put("exp-bearer", TimeFormats.formatWire(currentExpiry))
        put("ts-app", TimeFormats.formatWire(now))
        put("proc", ArgoConstants.PROC_TAG)
        username?.let { put("username", it) }
    }

    /**
     * Parses the refresh response onto a fresh [Token].
     *
     * OAuth error codes are classified: codes that prove the grant dead
     * ([TERMINAL_REJECTIONS]) throw [RefreshRejectedException] so the session
     * layer can wipe local material; any other rejection stays a plain
     * [AuthenticationException] and the session is kept for a later retry.
     *
     * @throws RefreshRejectedException when the grant is provably dead.
     * @throws AuthenticationException when upstream rejects the grant otherwise.
     * @throws DeserializationException when the schema no longer matches.
     */
    public fun parseResponse(bodyText: String, dateHeader: String?): it.hydr4.argo.models.Token {
        val json = Json { ignoreUnknownKeys = true }
        val element =
            try {
                json.parseToJsonElement(bodyText)
            } catch (e: IllegalArgumentException) {
                throw DeserializationException(REFRESH_PATH_LABEL, e)
            }
        val obj =
            element as? JsonObject
                ?: throw DeserializationException(REFRESH_PATH_LABEL, null)
        val rawError = obj["error"] as? JsonPrimitive
        if (!rawError?.content.isNullOrBlank()) {
            val code = rawError.content
            // error_description content intentionally omitted from messages (possible PII).
            if (code in TERMINAL_REJECTIONS) {
                throw RefreshRejectedException(
                    "Refresh token rejected upstream ($code): the grant is no longer valid; re-authentication required",
                )
            }
            throw AuthenticationException("Token refresh rejected: $code")
        }
        return try {
            json
                .decodeFromJsonElement(TokenWire.serializer(), obj)
                .toModel(ServerInstant.fromHeader(dateHeader))
        } catch (e: SerializationException) {
            throw DeserializationException(REFRESH_PATH_LABEL, e)
        } catch (e: IllegalArgumentException) {
            throw DeserializationException(REFRESH_PATH_LABEL, e)
        }
    }

    /**
     * OAuth codes that mean the refresh grant itself is dead — no retry or
     * restore can resurrect it. Anything else is treated as a transient-ish
     * rejection so a busy server never logs the user out.
     */
    public val TERMINAL_REJECTIONS: Set<String> = setOf("invalid_grant", "invalid_client", "unauthorized_client")

    private const val REFRESH_PATH_LABEL = "auth/refresh-token"
}
