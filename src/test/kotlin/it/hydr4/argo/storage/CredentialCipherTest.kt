package it.hydr4.argo.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** Authenticated-encryption contract of the local cipher. */
class CredentialCipherTest {
    @Test
    fun `roundtrip restores the plaintext`() {
        val cipher = AesGcmCredentialCipher("correct horse battery".toCharArray())
        val plaintext = "{\"token\":\"value\"}".toByteArray()
        assertContentEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)))
    }

    @Test
    fun `every encryption emits fresh salt and iv`() {
        val cipher = AesGcmCredentialCipher("pass".toCharArray())
        val plaintext = "same payload".toByteArray()
        assertContentEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)))
        val first = cipher.encrypt(plaintext)
        val second = cipher.encrypt(plaintext)
        kotlin.test.assertTrue(!first.contentEquals(second), "encryptions of identical input must differ")
    }

    @Test
    fun `bit flip in ciphertext fails authentication`() {
        val cipher = AesGcmCredentialCipher("pass".toCharArray())
        val sealed = cipher.encrypt("attack at dawn".toByteArray())
        val tampered = sealed.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()
        assertNull(cipher.decrypt(tampered))
    }

    @Test
    fun `wrong passphrase yields null`() {
        val sealed = AesGcmCredentialCipher("right".toCharArray()).encrypt("data".toByteArray())
        assertNull(AesGcmCredentialCipher("wrong".toCharArray()).decrypt(sealed))
    }

    @Test
    fun `truncated payload yields null`() {
        val cipher = AesGcmCredentialCipher("pass".toCharArray())
        val sealed = cipher.encrypt("data".toByteArray())
        assertNull(cipher.decrypt(sealed.copyOf(10)))
        assertNull(cipher.decrypt(byteArrayOf(9, 9, 9)))
    }
}
