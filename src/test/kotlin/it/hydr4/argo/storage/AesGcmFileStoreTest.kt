package it.hydr4.argo.storage

import it.hydr4.argo.exceptions.ProtocolException
import it.hydr4.argo.models.LoginData
import it.hydr4.argo.models.Token
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Encrypted filesystem persistence contract. */
class AesGcmFileStoreTest {
    @TempDir
    lateinit var tempDir: Path

    private fun store(dir: Path = tempDir): AesGcmFileStore = AesGcmFileStore(
        dir.resolve("tokens.bin"),
        AesGcmCredentialCipher("test-passphrase".toCharArray()),
    )

    private val sampleSnapshot: SessionSnapshot =
        SessionSnapshot(
            token = Token(
                accessToken = "at",
                refreshToken = "rt",
                expiresAt = Instant.parse("2026-08-26T08:00:00Z"),
                scope = "openid",
                tokenType = "Bearer",
            ),
            loginData = LoginData(codMin = "SS13325", xAuthToken = "xat", username = "user"),
        )

    @Test
    fun `save then load round-trips the full snapshot`() = kotlinx.coroutines.test.runTest {
        val store = store()
        store.save(sampleSnapshot)
        assertEquals(sampleSnapshot, store.load())
    }

    @Test
    fun `missing file loads as absent`() = kotlinx.coroutines.test.runTest {
        assertNull(store().load())
    }

    @Test
    fun `clear removes the persisted snapshot`() = kotlinx.coroutines.test.runTest {
        val store = store()
        store.save(sampleSnapshot)
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `garbage file is treated as absent rather than trusted`() = kotlinx.coroutines.test.runTest {
        val path = tempDir.resolve("tokens.bin")
        Files.writeString(path, "this is not encrypted material at all")
        assertNull(store().load())
    }

    @Test
    fun `wrong passphrase surfaces as absent`() = kotlinx.coroutines.test.runTest {
        val store = store()
        store.save(sampleSnapshot)
        val wrongKey = AesGcmFileStore(
            tempDir.resolve("tokens.bin"),
            AesGcmCredentialCipher("wrong-passphrase".toCharArray()),
        )
        assertNull(wrongKey.load())
    }

    @Test
    fun `valid JSON that fails schema validation raises protocol error`() = kotlinx.coroutines.test.runTest {
        val cipher = AesGcmCredentialCipher("test-passphrase".toCharArray())
        // Encrypted payload whose plaintext parses but violates the snapshot schema.
        Files.write(tempDir.resolve("tokens.bin"), cipher.encrypt("""{"token": 42}""".toByteArray()))
        assertFailsWith<ProtocolException> { store().load() }
    }
}
