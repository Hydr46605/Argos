package it.hydr4.argo.util

/**
 * Runtime access to the library version baked into the jar manifest.
 *
 * Falls back to `development` when running from an unpacked classpath (unit
 * tests, IDE run configurations) where no manifest is available.
 */
public object ArgoVersion {
    /** Version string from the jar manifest, or `development` on unpacked classpaths. */
    public val current: String =
        ArgoVersion::class.java.`package`?.implementationVersion ?: DEVELOPMENT_FALLBACK

    /** `true` when the running artifact is a released build. */
    public val isRelease: Boolean get() = current != DEVELOPMENT_FALLBACK

    private const val DEVELOPMENT_FALLBACK = "development"
}
