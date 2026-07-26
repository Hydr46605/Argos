package it.hydr4.argo.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * RFC 7636 PKCE material generator.
 *
 * The reference client draws a 43-character alphanumeric verifier (minimum legal
 * length) and derives the S256 challenge as base64url(sha256(verifier)) with
 * padding stripped — exactly what this generator reproduces.
 */
public class PkceGenerator(
    private val random: SecureRandom = SecureRandom(),
    /** Fixed entropy source keeps tests deterministic; production callers never pass one. */
    internal val alphabet: String = DEFAULT_ALPHABET,
) {
    /**
     * Generates a fresh challenge pair.
     *
     * @param verifierLength Verifier size; Argo uses 43 chars.
     * @throws IllegalArgumentException when [verifierLength] is outside 43..128.
     */
    public fun generate(verifierLength: Int = 43): PkceChallenge {
        require(verifierLength in MIN_LENGTH..MAX_LENGTH) {
            "PKCE verifier length must be between $MIN_LENGTH and $MAX_LENGTH, was $verifierLength"
        }
        val verifier = generateVerifier(verifierLength)
        return PkceChallenge(verifier, deriveChallenge(verifier))
    }

    private fun generateVerifier(length: Int): String = buildString(length) {
        repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    public companion object {
        public const val MIN_LENGTH: Int = 43
        public const val MAX_LENGTH: Int = 128

        private const val DEFAULT_ALPHABET: String =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        /** Derives the base64url-unpadded SHA-256 challenge of a raw verifier. */
        public fun deriveChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64Url.encode(digest)
        }
    }
}

/** Internal base64url encoder (no padding, URL-safe alphabet). */
internal object Base64Url {
    fun encode(bytes: ByteArray): String = java.util.Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)
}
