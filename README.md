# Argos

Argos è una libreria Kotlin/JVM tipizzata e idiomatica per parlare con le API REST del registro elettronico Argo ScuolaNext (famiglia DidUp).

## Perché esiste

Il registro elettronico usato in molte scuole italiane espone API non documentate. Niente SDK ufficiale, solo un protocollo osservabile dal client web. Argos lo porta in Kotlin partendo dal lavoro di reverse-engineering di [DTrombett/portaleargo-api](https://github.com/DTrombett/portaleargo-api), che ha ricostruito il protocollo wire originale (il flusso di accesso OAuth2/PKCE, gli header `x-auth-token`/`x-cod-min`/`x-date-exp-auth` e gli envelope `{success, msg, data}`). Argos riusa quella conoscenza del protocollo dietro un'interfaccia Kotlin completamente tipizzata e basata su `suspend`, senza duplicare client web o dipendenze della reference TypeScript.

Lo scopo è dare a sviluppatori (e studenti curiosi) un modo semplice, sicuro e locale per costruire i propri strumenti (widget, dashboard, notifiche). Niente server intermedi, niente raccolta dati, la libreria parla direttamente con Argo da dentro la tua applicazione.

> **Avviso di stabilità.** Questa libreria dipende da **API non documentate** che Argo può modificare o rompere in qualsiasi momento senza preavviso. Tratta ogni release come potenzialmente fragile, fissa la versione e aspettati il drift.

## Conformità

Argos è pensato per **uso personale, locale e non commerciale**. Sei responsabile di rispettare i Termini di Servizio di Argo e le normative privacy applicabili (es. GDPR) quando usi la libreria. Il progetto non si assume alcuna responsabilità per un uso improprio.

## Funzionalità

- **Flusso OAuth2/PKCE completo.** Link di autorizzazione, domanda, form SSO, scambio del codice, login famiglia. Esposto come macchina a stati esplicita (`Idle -> ChallengeRequested -> CodeExchanged -> Authenticated -> TokenRefreshed`).
- **Refresh del bearer trasparente.** Rinnovo tramite l'endpoint custom `auth/refresh-token`, ancorato agli header `Date` del server così lo skew dell'orologio del client non sposta mai la validità. Single-flight, le chiamate concorrenti condividono un'unica rotazione, e un rifiuto terminale (`invalid_grant` / `invalid_client` / `unauthorized_client`) cancella la sessione morta invece di lasciare uno zombie.
- **`Dashboard` tipizzata** con `mediaGenerale`, `mediaPerMese`, `mediaPerPeriodo`, `voti`, `bacheca`, `appello`, `registro`, `promemoria`, `fuoriClasse` e `prenotazioniAlunni`, delta-mergiata contro le sonde `dashboard/what`.
- **Repository per ogni endpoint documentato.** Profilo, orario, voti di scrutinio, storico bacheca, tasse, ricevimenti, PCTO, corsi di recupero, curriculum.
- **Persistenza sessione criptata** (AES-256-GCM, key stretching PBKDF2) dietro una `TokenStore` sostituibile.
- **`EndpointRegistry` annotato** per endpoint custom su route non documentate.
- **`dashboardChanges()`.** Un `Flow<Dashboard>` freddo costruito sulla sonda dei cambiamenti. Emette solo quando un giro di sync trova una differenza reale, sopravvive agli errori transitori, termina su fallimento di autenticazione e applica backoff con jitter.
- **Suite di test offline deterministica** con motore HTTP finto e fixture sanificate, più una suite live opzionale (`./gradlew integrationTest`) verificata contro gli endpoint reali.
- **Retry sul rumore transitorio.** Cablati nel transport. Errori di rete e 5xx serverici ritentati con backoff esponenziale (`RetryPolicy`, regolabile via `ArgoClientConfig`), mentre i rifiuti applicativi e i fallimenti di auth propagano subito. Un 401 lato server ruota il bearer una volta e reinvia invece di fallire su una sessione stantia.

## Requisiti

- JDK 17+
- Kotlin 2.2+ (per chi consuma la libreria)

## Installazione

Pubblicato via [JitPack](https://jitpack.io/#Hydr46605/Argos) da ogni tag. Per usarlo, aggiungi il repository JitPack e la dipendenza.

```kotlin
// build.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") }
}
dependencies {
    implementation("com.github.Hydr46605.Argos:2026.08.5")
}
```

## Quickstart

```kotlin
import it.hydr4.argo.ArgoClient
import it.hydr4.argo.models.Credentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

fun main() {
    val client = ArgoClient.create() // endpoint di produzione, store locale criptato
    try {
        runBlocking {
            val dashboard = client.login(
                Credentials(
                    schoolCode = "SS13325", // codice scuola dall'URL del portale
                    username = "your-username",
                    password = "your-password",
                ),
            )
            println("mediaGenerale = ${dashboard.overallAverage}")
            dashboard.grades.forEach { println("${it.subjectName}: ${it.value}") }

            // Sync guidata dalla sonda dei cambiamenti, salta il fetch costoso se
            // in realtà non è cambiato nulla.
            val (snapshot, decision) = client.synchronize()
            println("sync decision = $decision")

            // Notifiche di cambiamento, un flow freddo che emette una dashboard
            // aggiornata solo quando upstream è davvero cambiato (backoff con
            // jitter sugli errori; il fallimento di auth lo termina). Non finisce
            // mai da solo; qui campioniamo il primo cambiamento con un timeout
            // solo per permettere all'esempio di terminare.
            val firstChange = withTimeoutOrNull(Duration.ofSeconds(30)) {
                client.dashboardChanges().first()
            }
            println("first detected change = ${firstChange?.overallAverage}")
        }
    } finally {
        client.close()
    }
}
```

Gli esempi eseguibili vivono in un source set dedicato (`src/example/kotlin`) per non finire mai nel jar. [`Quickstart`](src/example/kotlin/it/hydr4/argo/examples/Quickstart.kt) copre il login con credenziali, [`AdvancedUsage`](src/example/kotlin/it/hydr4/argo/examples/AdvancedUsage.kt) mostra ripristino duraturo, sync con sonda, retry e introspezione versione. Si eseguono con `./gradlew runExample -PexampleRun` (servono credenziali reali).

## Mappa degli endpoint

| Repository | Funzione | Endpoint |
|---|---|---|
| `DashboardRepository` | `fetch` / `probeChanges` / `acknowledgeSync` | `dashboard/dashboard`, `dashboard/what`, `dashboard/aggiornadata` |
| `ProfileRepository` | `profilo` / `dettagliProfilo` | `profilo`, `dettaglioprofilo` |
| `ScheduleRepository` | `orarioGiornaliero` | `orario-giorno` |
| `ScrutinioRepository` | `votiScrutinio` | `votiscrutinio` |
| `BulletinRepository` | `storicoBacheca` / `storicoBachecaAlunno` / download | `storicobacheca`, `storicobachecaalunno`, `downloadallegatobacheca*` |
| `FeesRepository` | `listatasse` / `ricevutaTelematica` | `listatassealunni`, `ricevutatelematica` |
| `MeetingsRepository` | `ricevimenti` | `ricevimento` |
| `PctoRepository` | `pcto` | `pcto` |
| `RecoveryCoursesRepository` | `corsiRecupero` | `corsirecupero` |
| `CurriculumRepository` | `curriculum` | `curriculumalunno` |

## Utility per lo sviluppatore

`it.hydr4.argo.util.RetryPolicy` per il retry con backoff esponenziale, `it.hydr4.argo.util.ArgoVersion` per l'introspezione della versione, `it.hydr4.argo.util.Redactor` come luogo unico che decide come renderizzano i campi sensibili nei `toString`. Il package `it.hydr4.argo.annotations` offre i marcatori di stabilità `@Beta` / `@Experimental` sulla wire surface meno stabile, più `@ArgoEndpoint` / `@RequiresAuthentication` per il layer di registrazione. In `it.hydr4.argo.registry` vive l'`EndpointRegistry` per endpoint custom guidati da annotazioni.

## Integrazione Android

Il client è JVM-first e gira su Android 21+ così com'è (OkHttp, kotlinx serialization e coroutine hanno tutte varianti compatibili Android). Due cose da sapere.

1. **Regole di keep.** Le build di release devono mantenere i descriptor dei modelli kotlinx.serialization e le annotation del registry. Applica [`gradle/proguard/consumer-rules.pro`](gradle/proguard/consumer-rules.pro) nel tuo release build, le stesse regole che un AAR porterebbe in automatico.
2. **Storage.** Lo store di default è quello JVM criptato su file. Su Android implementa `TokenStore` sopra `EncryptedSharedPreferences` (androidx.security-crypto) e passala a `ArgoClient.create(config, engine, storage)`; anche `ArgoHttpEngine` è sostituibile se vuoi una postura TLS/rete custom.

Deliberatamente **non** spediamo un AAR o un modulo di cache Room nel core. Packaging Android e caching sono responsabilità della piattaforma consumatrice e le interfacce di cui sopra le coprono già. Un modulo `argo-android` dedicato può essere aggiunto in seguito senza toccare questo codebase.

## Target di piattaforma (KMP)

Kotlin Multiplatform **non** è adottato in questa release e la decisione è rivista qui di proposito piuttosto che data per scontata.

- **Android consuma già l'artefatto JVM.** Il core (OkHttp, kotlinx serialization, coroutine) è compatibile Android, quindi KMP non aggiunge valore per il consumatore primario.
- **I target iOS/native non sono verificabili da questo toolchain.** Richiedono macOS/Xcode per essere validati, quindi un target native verrebbe spedito senza essere mai compilato e testato.
- **Le surface solo-JVM andrebbero prima rifatte.** Lo store criptato usa `javax.crypto` e il registry si appoggia a `kotlin-reflect`; entrambi richiederebbero split `expect/actual` senza consumatori che li motivino.

Quando compariranno consumatori iOS, il percorso di migrazione è concreto. `expect/actual` per `TokenStore` e il motore HTTP, `kotlinx-datetime` al posto di `java.time`, e gestione delle annotation senza kotlin-reflect. Fino ad allora l'artefatto JVM è la superficie giusta (una decisione documentata, non un accidente del build).

## Sicurezza

- Token e password non compaiono mai in `toString`, messaggi di eccezione, log o output di esempio. Verificato dalla suite `NoLeakTest`.
- `Credentials` e `Token` vengono renderizzati redatti; `ArgoHttpRequest.toString` depura gli header sensibili.
- Gli snapshot persistiti sono criptati a riposo con AES-256-GCM autenticato; chiavi sbagliate o manomissioni falliscono in chiusura.
- Il percorso di errore del refresh del token omette deliberatamente il contenuto di `error_description`, che upstream può popolare con dati utente.
- Un rifiuto terminale del refresh (`invalid_grant` e simili) cancella il materiale di sessione locale e porta la macchina a stati su `Failed`; un grant morto non viene mai ripristinato, resuscitato o ritentato in silenzio.

## Sviluppo

```bash
./gradlew verifyAll          # format + detekt + test + jar (il gate CI locale)
./gradlew spotlessApply      # auto-format
./gradlew releaseCheck       # validazione changelog/snapshot + checklist release
```

Vedi [CONTRIBUTING.md](CONTRIBUTING.md) per il setup locale completo e [SECURITY.md](SECURITY.md) per la politica di segnalazione delle vulnerabilità.

## Pubblicazione

Pubblicato su [JitPack](https://jitpack.io/#Hydr46605/Argos) da ogni tag. Vedi la sezione installazione per le coordinate.

## Licenza

MIT, vedi [LICENSE](LICENSE).

## Disclaimer

Argos non è affiliato a, sponsorizzato da, o collegato a Argo Software / DidUp. Tutti i nomi di prodotto e i marchi appartengono ai rispettivi proprietari. Usa a tuo rischio e secondo i Termini di Servizio di Argo.

---

*Nota di trasparenza. Questo progetto è stato sviluppato in parte con l'ausilio di strumenti di intelligenza artificiale e coding agentico. Ogni riga è stata però revisionata e curata da un essere umano prima di essere rilasciata.*

---

## English (optional)

Argos is an unofficial Kotlin/JVM client for the undocumented Argo ScuolaNext / DidUp school-register APIs, based on the DTrumbett/portaleargo-api protocol reference. It wraps the OAuth2/PKCE login, token refresh and typed dashboard/schedule/bulletin endpoints in a suspend-based, secure, local-only library. Expect the wire protocol to break at any time; treat it as best-effort.