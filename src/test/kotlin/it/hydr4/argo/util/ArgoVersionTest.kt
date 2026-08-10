package it.hydr4.argo.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Version introspection on an unpacked test classpath (no manifest). */
class ArgoVersionTest {
    @Test
    fun `unpacked classpath reports the development fallback`() {
        // Unit tests run from directories, not the packaged jar manifest.
        assertTrue(ArgoVersion.current.isNotBlank())
        assertFalse(ArgoVersion.isRelease)
    }
}
