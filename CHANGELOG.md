# Changelog

Tutte le modifiche rilevanti ad Argos sono documentate qui, seguendo [Keep a Changelog](https://keepachangelog.com/it/1.1.0/) e [Calendar Versioning](https://calver.org/) (`YYYY.MM.MICRO`).

## [Unreleased]

### Added

- Nothing yet.

## [2026.08.5] 2026-08-27

### Added

- **In-flight request hardening.** Une richiesta non viene mai inviata con un bearer localmente noto come scaduto (la rotazione precede l'invio). Un refresh che fallisce in modo transitorio fa riprovare l'intera chiamata, un rifiuto terminale fallisce subito cancellando la sessione, e una richiesta già inviata che il server rifiuta con HTTP 401 fa scattare una sola rotazione forzata più un re-invio. Un secondo 401 o un grant morto propagano senza andare in loop.
- `ArgoClientConfig.retryPolicy` espone la policy di retry del transport ai consumatori. Si regolano `maxAttempts`, `baseDelayMillis` / `maxDelayMillis` e il predicato `retryOn` tramite lo stesso oggetto di config con cui si crea il client.
- `dashboardChanges` ora ha backoff con jitter. I giri che falliscono raddoppiano il tempo di attesa (più jitter uniforme, così molti client non si sincronizzano mai a branco) fino a `maxBackoff`, e un singolo giro sano ripristina l'`interval` base. Un server malato viene sondato sempre più di rado, ma il recupero viene comunque rilevato subito.
- `SessionContext.forceRefresh()`, l'hook del transport per la rotazione "401 una volta", implementato da `ArgoSession` tramite il protocollo di refresh reale.

### Changed

- `ArgoHttpClient` ora legge la sua policy di retry da `config.retryPolicy` invece che da un default nel costruttore. Un unico punto controlla il comportamento del transport (il parametro del costruttore del 2026.08.4 viene rimosso a favore della config).

### Fixed

- Lo `dashboardChanges().collect { }` del quickstart del README andava in loop infinito dentro il `main` di esempio e non arrivava mai a `client.close()`. Ora campiona il primo cambiamento con una deadline così l'esempio termina. È stata aggiornata anche la coordinata di installazione `2026.08.0` ormai vecchia nel README.

### Security

- La rotazione "401 una volta" è strettamente limitata (una rotazione, un re-invio) e non rimanda mai dopo un fallimento di rotazione terminale. Una sessione morta non può quindi essere martellata in scambi di credenziali ripetuti.

## [2026.08.4] 2026-08-27

### Added

- `RetryPolicy` cablato nel transport autenticato. `fetch` e `fetchEnvelope` ritentano il rumore transitorio (errori di rete e 5xx del server) con il backoff esponenziale della policy. I rifiuti applicativi (`success:false`, 4xx) e i fallimenti di autenticazione propagano subito, perché un rifiuto dato non è rumore. Ogni tentativo ricalcola header freschi, così una rotazione scattata a metà retry non viene mai riusata stantia.
- `RefreshRejectedException` (`invalid_grant` / `invalid_client` / `unauthorized_client`) che distingue un grant provatamente morto da qualsiasi altro rifiuto di refresh.

### Changed

- I fallimenti di refresh ora sono classificati invece di essere appiattiti in un unico errore. I rifiuti terminali del grant cancellano il materiale di sessione locale e portano la macchina a stati su `Failed("refresh")`, mentre i rifiuti transitori (`temporarily_unavailable`, 5xx HTTP, errori di rete) emergono come `ArgoApiException` / `NetworkException` e lasciano la sessione viva per un tentativo successivo.
- Il body di un refresh viene parsato prima di giudicare lo stato HTTP, così un rifiuto terminale risposto con HTTP 400 viene comunque classificato correttamente (prima il controllo dello stato nascondeva `invalid_grant` dietro un errore HTTP generico).
- I fallimenti di trasporto del refresh ora lanciano `NetworkException` invece di `AuthenticationException`. Un blip di rete non è un motivo per chiedere di riaccedere, e il flow `dashboardChanges` sopravvive tramite il suo sink di errore.

### Fixed

- Un `invalid_grant` terminale a metà volo lasciava una sessione zombie. Il token morto restava nello store, `isAuthenticated()` continuava a dire vero e un `restorePersistedSession` successivo poteva resuscitare il cadavere. Ora il grant viene cancellato da memoria e disco prima che il rifiuto emerga.

### Security

- Il contenuto di `error_description` non viene mai rimandato, incluso nel nuovo percorso di rifiuto. I rifiuti terminali cancellano le credenziali morte invece di lasciarle a riposo.

## [2026.08.3] 2026-08-27

### Added

- `ArgoClient.dashboardChanges()`. Un `Flow<Dashboard>` freddo costruito sul `WhatPoller`. Emette solo quando un giro di sync trova un cambiamento reale (baselined sullo snapshot corrente alla sottoscrizione, così un primo giro pulito non produce mai una notifica spuria), deduplica snapshot strutturalmente identici, riporta gli errori transitori tramite un sink `onError` tenendo il polling vivo e termina su `AuthenticationException` perché i chiamanti rifacciano il login invece di martellare una sessione morta.
- Copertura di regressione sulla concorrenza. I chiamanti paralleli che condividono un token scaduto ora collassano su un unico giro di refresh (test a 8 thread che asserisce esattamente una POST `auth/refresh-token`).
- Verifica live contro gli endpoint reali di Argo (via `local-test.properties`). L'harness ha fatto login con credenziali reali e ha restituito una `Dashboard` popolata (`mediaGenerale`, 81 voti, 13 materie, orario e ricevimenti).

### Changed

- **Single-flight token refresh.** `ArgoSession.headersWithFreshBearer` ora esegue la rotazione sotto una `Mutex`. I chiamanti concorrenti aspettano il vincitore e riusano il suo token ruotato invece di sparare refresh POST duplicati con lo stesso bearer quasi scaduto (che il server rifiuterebbe).
- `SsoCodeBroker` segue la catena di redirect SSO come il client di riferimento (max 3 hop, cookie jar tra gli hop). Prima il round-trip di autorizzazione si fermava al primo 302.
- `ArgoClient.create` ora monta un `InMemoryCookieJar` di default, perché il dance SSO lega la sessione di login ai cookie. Il precedente default `NO_COOKIES` poteva rompere l'hop della challenge sui server reali.
- Il costruttore di comodo `OkHttpEngine` ora disabilita il seguire i redirect per rispettare il contratto documentato. I redirect sono guidati dalla richiesta (flag `followRedirects`), così gli hop authorize/SSO restano ispezionabili.
- `FakeEngine` segue fedelmente i redirect http(s) come OkHttp, così la suite offline esercita la stessa logica degli hop del transport live (era proprio quella divergenza a far passare il bug dei redirect).
- `DashboardWire` accetta le virgole decimali nei campi numerici (alcune installazioni emettono decimali localizzati), mantenendo comunque un parsing rigoroso sulla forma.

### Fixed

- `Nonces.alphanumeric` ora usa `SecureRandom` invece di `kotlin.random.Random.Default`. `state` e `nonce` sono materiale anti-CSRF e non devono essere prevedibili.
- `InMemoryCookieJar` è thread-safe (un poller concorrente più un fetch esplicito non provocano più una `ConcurrentModificationException`).

### Security

- Generazione di nonce/state indurita, vedi Fixed. La suite live ha confermato che nessun token o credenziale compare nel suo output sanificato.

## [2026.08.2] 2026-08-27

### Added

- Layer di registrazione DX. Annotation `@ArgoEndpoint` / `@RequiresAuthentication` (ritenzione runtime) e il package `it.hydr4.argo.registry`. I consumatori possono registrare endpoint custom contro la superficie API non documentata e per-installazione di Argo e chiamarli tramite lo stesso transport tipizzato e autenticato dei repository integrati (`client.registry.registerAnnotated(...)`). Le chiamate che richiedono autenticazione falliscono subito, prima di qualsiasi traffico di rete.
- Harness di test di integrazione live. Il `local-test.properties` gitignorato (template in `local-test.properties.example`) o le env var `ARGO_TEST_*` guidano `LiveSmokeTest`, che fa login contro gli endpoint reali e stampa solo riepiloghi sanificati. La suite si auto-salta senza credenziali, così CI e `verifyAll` non dipendono mai dai servizi live (`./gradlew integrationTest`).
- Consumer rules R8/ProGuard (`gradle/proguard/consumer-rules.pro`) con keep rules per kotlinx.serialization e registry, pubblicate per i consumatori Android. Il README documenta il percorso di integrazione Android (keep rules più `TokenStore` sostituibile sopra `EncryptedSharedPreferences`).

### Changed

- `ArgoClient` espone `registry` legato al suo transport e alla sua sessione. Esempi e CONTRIBUTING documentano la registrazione di endpoint custom e la suite live.

### Fixed

- Nothing yet.

### Security

- Il layer di registrazione riusa le garanzie di no-leak esistenti. I body delle richieste sono serializzati dal transport, gli header restano redatti e il guard di autenticazione non rimanda mai credenziali.

## [2026.08.1] 2026-08-27

### Added

- Package `it.hydr4.argo.util` con `RetryPolicy` (backoff esponenziale per il transport instabile), `ArgoVersion` (introspezione dell'artefatto a runtime) e `Redactor` (fonte unica per la renderizzazione sicura delle credenziali).
- Package `it.hydr4.argo.annotations` con i marcatori di stabilità `@Beta` / `@Experimental` sulla wire surface meno stabile (`pcto`, `corsirecupero`).
- Source set `example` dedicato. Gli esempi eseguibili non finiscono più nel jar, e `AdvancedUsage` copre ripristino, sync con sonda di cambiamento, retry e introspezione versione (`./gradlew runExample -PexampleRun`).
- Preparazione Maven Central. Repository di staging OSSRH, firma PGP protetta e un task `publishToMavenCentral` che valida le credenziali (nessuna pubblicazione per ora).
- Nuove fixture sanificate e test dei repository per orario, scrutinio, tasse, ricevimenti, storico bacheca, link allegati, pcto, corsi di recupero, curriculum e dettagli profilo (115 test totali).
- `justfile` che replica i passi della CI (`just verify`, `just format`, `just release`).

### Changed

- La sonda `dashboard/what` ora espone `forceLogin` come `PollDecision.SessionInvalid`. `ArgoClient.synchronize` solleva un errore di autenticazione invece di leggere una sessione rifiutata come "pulita".
- I lookup di ricevute e link allegati trattano `success:false` secondo i loro contratti documentati (`null` / `ProtocolException`) invece di far trapelare `ArgoApiException` dal layer di trasporto.
- `LoginLinkBuilder.build` non è più sospendente, e `logout` usa la tabella degli endpoint.

### Fixed

- Chiusi altri vettori di leak in `toString`. `AuthHeaders`, `ArgoHttpResponse` e `EnvelopeShell` ora rendono redatto (header e body non vengono mai riportati).
- Le richieste non autenticate non inviano più un header `Authorization: Bearer ` vuoto.

### Security

- Garanzia di no-leak estesa alla renderizzazione di risposte ed envelope. La suite di test asserisce la redazione per ogni tipo che contiene credenziali.

## [2026.08.0] 2026-08-27

### Added

- Flusso PKCE completo (`auth.portaleargo.it`) con challenge Hydra, post del form SSO e scambio family-login, guidato da una macchina a stati `AuthState` esplicita.
- Refresh trasparente del bearer tramite il protocollo custom `auth/refresh-token`, ancorato agli header `Date` del server (`ServerInstant`).
- Modello `Dashboard` tipizzato che espone `mediaGenerale`, `mediaPerMese`, `mediaPerPeriodo`, `voti`, `bacheca`, `bachecaAlunno`, `appello`, `registro`, `promemoria`, `fuoriClasse` e `prenotazioniAlunni` come collezioni immutabili.
- Sincronizzazione delta che rispecchia il client di riferimento. `WhatPoller` sonda `dashboard/what` e `DeltaLists` fonde i marker di operazione I/D negli snapshot locali.
- Repository per tutti i dodici endpoint documentati dietro un'unica radice di composizione `ArgoClient`.
- Persistenza sessione criptata (`AesGcmFileStore` + `AesGcmCredentialCipher`, AES-256-GCM con PBKDF2) dietro l'interfaccia `TokenStore` sostituibile.
- Gerarchia di eccezioni tipizzata e log-safe (`ArgoException` sealed root) con garanzia di no-leak di token e password.
- Suite di test offline deterministica (71 test) su un motore HTTP finto con payload fixture sanificati.
- Modelli kotlinx.serialization rigorosi con tipi `java.time` e nullabilità documentata per i campi upstream instabili.
- Build Gradle con version catalog, modalità API esplicita, gate detekt e Spotless/ktlint, task `verifyAll` e `releaseCheck`.
- Daemon JVM fissato a JDK 17 tramite Daemon JVM criteria per la parità di toolchain.

### Changed

- Nothing yet (prima release).

### Deprecated

- Nothing yet.

### Removed

- Nothing yet.

### Fixed

- Nothing yet.

### Security

- I body delle richieste non incorporano mai access token e i valori degli header sono redatti da `ArgoHttpRequest.toString`.
- I messaggi di rifiuto SSO/refresh non riportano mai il contenuto di `error_description` (potenziale PII).

[Unreleased]: https://github.com/Hydr46605/Argos/compare/v2026.08.5...HEAD
[2026.08.5]: https://github.com/Hydr46605/Argos/releases/tag/v2026.08.5
[2026.08.4]: https://github.com/Hydr46605/Argos/releases/tag/v2026.08.4
[2026.08.3]: https://github.com/Hydr46605/Argos/releases/tag/v2026.08.3
[2026.08.2]: https://github.com/Hydr46605/Argos/releases/tag/v2026.08.2
[2026.08.1]: https://github.com/Hydr46605/Argos/releases/tag/v2026.08.1
[2026.08.0]: https://github.com/Hydr46605/Argos/releases/tag/v2026.08.0