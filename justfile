# Argos development recipes — replicating the CI steps locally (requires `just`).
set shell := ["bash", "-c"]

# Full local CI gate: formatting, static analysis, tests and jar assembly.
verify:
    ./gradlew verifyAll

# Auto-format sources with ktlint via Spotless.
format:
    ./gradlew spotlessApply

# Release pre-flight: changelog + snapshot validation, prints the checklist.
release:
    ./gradlew releaseCheck

# Run the quickstart example (needs -e exampleRun=1 and real credentials in the sample).
example:
    ./gradlew runExample -PexampleRun
