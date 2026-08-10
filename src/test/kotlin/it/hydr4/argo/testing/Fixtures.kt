package it.hydr4.argo.testing

/**
 * Loads sanitized recorded payloads from `src/test/resources/fixtures/`.
 *
 * Fixtures are the single source of wire-shape truth for offline tests; they are
 * stripped of any real student data and carry only synthetic identifiers.
 */
public object Fixtures {
    /** Reads a fixture as UTF-8 text; a missing file is a test-setup failure. */
    public fun text(name: String): String = checkNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$name")) {
        "Missing fixture 'fixtures/$name' on the test classpath"
    }.readBytes().decodeToString()
}
