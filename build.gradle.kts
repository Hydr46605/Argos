import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    `maven-publish`
}

// group comes from gradle.properties (= it.hydr4); never hardcode it here.

// Repositories are centralized in settings.gradle.kts (FAIL_ON_PROJECT_REPOS).

kotlin {
    jvmToolchain(17)
    // Library discipline: public API surface must be explicit and intentional.
    explicitApi()
}

// Runnable examples live in a dedicated source set so they never ship in the jar.
// Custom source sets must pull the main resolvable classpaths explicitly: the
// auto-wired <name>Implementation only sees implementation deps, not api ones.
sourceSets {
    create("example") {
        compileClasspath += sourceSets.main.get().output + configurations.getByName("compileClasspath")
        runtimeClasspath += sourceSets.main.get().output + configurations.getByName("runtimeClasspath")
        kotlin.srcDir("src/example/kotlin")
    }
    // Live smoke tests against real endpoints; they self-skip when credentials
    // are absent, so CI and verifyAll stay green without them.
    create("integrationTest") {
        compileClasspath +=
            sourceSets.main.get().output + configurations.getByName("compileClasspath") +
            sourceSets.test.get().output + configurations.getByName("testCompileClasspath")
        runtimeClasspath +=
            sourceSets.main.get().output + configurations.getByName("runtimeClasspath") +
            sourceSets.test.get().output + configurations.getByName("testRuntimeClasspath")
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    // Annotation-driven registry reads class metadata at runtime; kotlin-reflect
    // is runtime-only so consumers never see it on their compile classpath.
    implementation(kotlin("reflect"))

    testImplementation(libs.mockk)
    testImplementation(libs.mockk.jvm)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Examples compile against the published surface plus the coroutine runtime.
    add("exampleImplementation", libs.kotlinx.coroutines.core)

    // Integration tests reuse the unit-test harness (fake engine helpers, fixtures).
    add("integrationTestImplementation", kotlin("test-junit5"))
    add("integrationTestImplementation", libs.kotlinx.coroutines.test)
    add("integrationTestImplementation", libs.mockk)
    add("integrationTestImplementation", libs.mockk.jvm)
}

tasks.register<Test>("integrationTest") {
    description = "Runs live smoke tests against real endpoints; skips without local-test.properties."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
    testLogging {
        events("failed", "skipped", "passed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<JavaExec>("runExample") {
    group = "examples"
    description = "Runs the Quickstart sample (requires real Argo credentials)."
    classpath = sourceSets["example"].runtimeClasspath
    mainClass.set("it.hydr4.argo.examples.Quickstart")
}

tasks.named("runExample") {
    // Prevent accidental runs against production endpoints without explicit intent.
    onlyIf { project.hasProperty("exampleRun") }
}

spotless {
    kotlin {
        // ktlint's filename rule fights the conventional `package-info.kt` name used for
        // package documentation; detekt's MatchingDeclarationName already enforces file naming.
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf("ktlint_standard_filename" to "disabled"))
        targetExclude("${layout.buildDirectory.get()}/**")
    }
    kotlinGradle {
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<Detekt>().configureEach {
    reports {
        sarif.required = true
        html.required = true
        txt.required = false
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val spotlessCheckTask = tasks.named("spotlessCheck")

tasks.named("check") {
    dependsOn(spotlessCheckTask)
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Local CI gate: formatting, static analysis, tests and jar assembly."
    dependsOn(spotlessCheckTask, "detekt", "test", "jar")
}

tasks.register("releaseCheck") {
    group = "release"
    description = "Validates the changelog entry, absence of snapshot dependencies and prints the release checklist."
    // Files and version are captured at configuration time so the task stays configuration-cache safe.
    val version = project.version.toString()
    val catalogFile = layout.projectDirectory.file("gradle/libs.versions.toml").asFile
    val changelogFile = layout.projectDirectory.file("CHANGELOG.md").asFile
    doLast {
        require(!version.endsWith("-SNAPSHOT")) { "Cannot release a -SNAPSHOT version: $version" }
        // Comments may mention the policy; only declaration lines matter.
        val catalog = catalogFile.readLines().filterNot { it.trimStart().startsWith("#") }.joinToString("\n")
        require("-SNAPSHOT" !in catalog) { "Dependency catalog contains -SNAPSHOT versions" }
        val changelog = changelogFile.readText()
        require("## [$version]" in changelog || "## [$version." in changelog) {
            "CHANGELOG.md must contain a '## [$version]' section before releasing"
        }
        println(
            """Ready-to-push checklist for $version:
          |  [x] Changelog section present      -> CHANGELOG.md
          |  [x] No -SNAPSHOT dependencies      -> gradle/libs.versions.toml
          |  [x] Local verification green       -> ./gradlew verifyAll
          |  [ ] Commit changelog bump: 'chore(release): cut $version'
          |  [ ] Tag: git tag -a v$version -m "<refers to CHANGELOG [$version]>"
          |  [ ] Push: git push origin main --follow-tags
            """.trimMargin(),
        )
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
