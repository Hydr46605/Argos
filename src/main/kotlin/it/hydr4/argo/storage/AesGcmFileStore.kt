package it.hydr4.argo.storage

import it.hydr4.argo.exceptions.ProtocolException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

/**
 * Durable [TokenStore] writing the serialized snapshot encrypted via AES-GCM.
 *
 * Durability contract: temp-file + atomic move, so a crash mid-write can never
 * leave a half-written token file behind. On Windows POSIX permission tightening
 * degrades silently to default ACLs.
 *
 * **Security:** payloads at rest are ciphertext only; a tampered or unparseable
 * store yields `null` from [load] and clears itself on next save.
 */
public class AesGcmFileStore(private val filePath: Path, cipher: CredentialCipher) : TokenStore {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val wrapped: CredentialCipher = cipher
    private val mutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun load(): SessionSnapshot? {
        val bytes =
            if (Files.notExists(filePath)) {
                null
            } else {
                try {
                    Files.readAllBytes(filePath)
                } catch (e: java.io.IOException) {
                    throw ProtocolException("Session store unreadable at ${filePath.fileName}", e)
                }
            }
        val plaintext = bytes?.let(wrapped::decrypt)
        if (plaintext == null) return null // absent, or tamper/corruption → treated as absent
        return try {
            json.decodeFromString(SessionSnapshot.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw ProtocolException("Persisted session snapshot failed schema validation", e)
        } catch (e: IllegalArgumentException) {
            throw ProtocolException("Persisted session snapshot is not valid JSON", e)
        }
    }

    override suspend fun save(snapshot: SessionSnapshot) {
        mutex.lock()
        try {
            val encoded = json.encodeToString(SessionSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
            Files.createDirectories(filePath.toAbsolutePath().parent)
            val tmp = filePath.resolveSibling(filePath.fileName.toString() + ".tmp")
            Files.write(tmp, wrapped.encrypt(encoded))
            tightenPermissions(tmp)
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun clear() {
        mutex.lock()
        try {
            Files.deleteIfExists(filePath)
        } finally {
            mutex.unlock()
        }
    }

    /** Best-effort owner-only readability; POSIX-only, ignored elsewhere. */
    private fun tightenPermissions(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }

    public companion object {
        /** Default store directory name under the user home. */
        public const val DEFAULT_DIRECTORY_NAME: String = ".argos"
    }
}
