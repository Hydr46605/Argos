package it.hydr4.argo.storage

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Symmetric encryption used by local stores.
 *
 * Implementations must be authenticated (tamper-evident) and must fail closed:
 * a wrong key or corrupted ciphertext yields `null` from [decrypt], never garbage.
 */
public interface CredentialCipher {
    /** Encrypts [plaintext]; the result embeds any IV/nonce material needed. */
    public fun encrypt(plaintext: ByteArray): ByteArray

    /**
     * Decrypts [ciphertext] or returns `null` when authentication fails
     * (wrong key, truncation or bit-flipping).
     */
    public fun decrypt(ciphertext: ByteArray): ByteArray?
}

/**
 * AES-256/GCM with PBKDF2-HMAC-SHA256 key stretching.
 *
 * Wire layout: `[1B version][16B salt][12B iv][ciphertext+tag]`, letting stores
 * re-derive keys from a stable passphrase without external key files.
 *
 * @property passphrase Secret stretched into the data key; **never persisted**.
 */
public class AesGcmCredentialCipher(private val passphrase: CharArray, private val random: SecureRandom = SecureRandom()) :
    CredentialCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = derive(salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val sealed = cipher.doFinal(plaintext)
        return byteArrayOf(VERSION) + salt + iv + sealed
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray? = runCatching {
        require(ciphertext.size > SALT_BYTES + IV_BYTES + 1)
        check(ciphertext[0] == VERSION) { "unsupported store format" }
        val salt = ciphertext.copyOfRange(1, 1 + SALT_BYTES)
        val iv = ciphertext.copyOfRange(1 + SALT_BYTES, 1 + SALT_BYTES + IV_BYTES)
        val sealed = ciphertext.copyOfRange(1 + SALT_BYTES + IV_BYTES, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, derive(salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(sealed)
    }.getOrNull()

    private fun derive(salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw =
            try {
                factory.generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        val key = SecretKeySpec(raw, "AES")
        java.util.Arrays.fill(raw, 0.toByte())
        return key
    }

    private companion object {
        const val VERSION: Byte = 1
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val ITERATIONS = 210_000
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
