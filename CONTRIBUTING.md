# Contribuire ad Argos

Grazie per voler contribuire. Questo documento ti porta da zero a un run della CI locale a posto e spiega le convenzioni inderogabili del progetto.

## Prerequisiti

- JDK 17+ (Temurin consigliato).
- Non serve un Gradle installato, usa `./gradlew`.

## Setup

```sh
git clone https://github.com/Hydr46605/Argos.git
cd Argos
./gradlew help          # scarica le dipendenze una volta
```

## Gate CI locale (è quello che gira in CI)

```sh
./gradlew verifyAll     # spotlessCheck + detekt + test + jar
```

Prima di ogni release, esegui anche

```sh
./gradlew releaseCheck  # valida sezione changelog, dipendenze senza -SNAPSHOT, stampa la checklist
```

In `justfile` ci sono helper opzionali se hai [just](https://github.com/casey/just), ovvero `just verify`, `just format`, `just release`.

## Convenzioni di engineering

1. **I nomi dicono esattamente una cosa.** `TokenRepository`, `PkceGenerator`, `DashboardRepository`. Mai `Utils`, `Helper`, `Manager`, `BaseClient`, `Common`.
2. **File piccoli.** Se una classe supera le ~200 righe, spezzala lungo la sua singola responsabilità.
3. **API pubblica con KDoc.** Ogni dichiarazione pubblica documenta scopo, parametri, valore di ritorno ed eccezioni lanciate.
4. **Modalità API esplicita attiva.** Le dichiarazioni pubbliche richiedono visibilità e tipi espliciti.
5. **Invariante di sicurezza.** Nulla sui token (`access_token`, `refresh_token`, `x-auth-token`), password o cookie deve mai finire in log, messaggi di eccezione o output di esempio. I test impongono questa cosa; tienili verdi.
6. **Fixture sanificate.** Il JSON registrato va in `src/test/resources/fixtures/` con le credenziali sostituite da dummy evidenti (`sample-token-replaced`, etc.).

## Disciplina dei commit

Conventional Commits, scope atomici, niente rumore WIP. Tipi ammessi `feat fix docs style refactor test chore ci build perf revert`.

- `feat(auth): implement PKCE challenge generation`
- `fix(models): make desCommento nullable in Voto`
- `docs(readme): add stability warning`

## Versioni e release

Calendar Versioning, `YYYY.MM.MICRO` (es. `2026.08.0`). Per tagliare una release aggiorna `CHANGELOG.md` (sezioni Keep a Changelog), alza `version=` in `gradle.properties`, esegui `./gradlew verifyAll releaseCheck`, committa `chore(release): cut YYYY.MM.MICRO`, tagga `vYYYY.MM.MICRO` citando la sezione del changelog, e spingi branch e tag. La CI costruisce gli artefatti.

## Esempi

Gli esempi eseguibili vivono in un source set dedicato (`src/example/kotlin`) così non finiscono mai nel jar della libreria. `Quickstart` copre il login con credenziali, `AdvancedUsage` mostra ripristino duraturo, sincronizzazione con sonda, retry resilienti e introspezione versione a runtime.

Eseguiteli contro endpoint reali (solo con le tue credenziali).

```sh
./gradlew runExample -PexampleRun
```

Il flag `-PexampleRun` è un guard deliberato così nessuno lancia richieste di produzione per sbaglio.

## Test di integrazione live

Il source set `integrationTest` parla con gli endpoint **reali** di Argo. Si auto-salta senza credenziali, quindi CI e `verifyAll` non dipendono mai dai servizi live.

Per eseguirlo contro il tuo account

```sh
cp local-test.properties.example local-test.properties
# modifica con LE TUE credenziali (il file è gitignorato)
./gradlew integrationTest
```

Le variabili d'ambiente `ARGO_TEST_SCHOOL_CODE` / `ARGO_TEST_USERNAME` / `ARGO_TEST_PASSWORD` funzionano come alternativa. La suite stampa solo riepiloghi sanificati, mai token, password o codici scuola. Non committare mai `local-test.properties`.

## Pubblicazione

Pubblicato via [JitPack](https://jitpack.io) da ogni tag. Non servono configurazioni particolari, basta che il build passi (`./gradlew verifyAll`). Per usarlo come dipendenza in un altro progetto

```kotlin
repositories { maven { url = uri("https://jitpack.io") } }
dependencies { implementation("com.github.Hydr46605.Argos:TAG") }
```

## Branching

Il lavoro procede direttamente su branch a vita breve mergiati su `main` con storia il più possibile lineare. I force-push sono permessi solo sui tuoi feature branch, mai su `main`.