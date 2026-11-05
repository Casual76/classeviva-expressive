# Accesso CIE: stato di implementazione

## Beta Pampa Store 7.17.0-beta.1

Successivamente alla consegna con P2 aperto, l'utente ha richiesto esplicitamente una release beta sul Pampa Store. Versione: **7.17.0-beta.1 / 2026110502**, con Fluid Engine 3.0.5. La pubblicazione riguarda soltanto il canale beta; la stabile 7.16.1 rimane invariata. Il P2 e il collaudo CieID reale mancante sono dichiarati nelle note `docs/releases/7.17.0-beta.1.md`; nessuna correzione aggiuntiva è stata applicata.

- APK release firmato, non debuggabile, stesso package e stessa firma della stabile 7.16.1. Build `:app:assembleRelease`, lint `:app:lintRelease` e 30 test unitari dell'app superati sul nuovo numero di versione. Log `android/build/reports/cie-beta-7.17.0-beta.1-build.log`.
- APK: 34.556.863 byte, SHA-256 `a53482be0bba95e502a854d164d07cc38eb9821b04e28079d3d4d2b1a4ff7b83`; certificato SHA-256 `825738500ce9605429ffba3c4019228f992f2c37d1b70a2230a7ce2c25637e43`.
- Tag previsto: `beta-classeviva-expressive-v7.17.0-beta.1`. La verifica della pubblicazione e dei manifest remoti viene conservata sotto `android/build/release-beta-7.17.0-beta.1/`.

## Stato alla consegna

**Consegna richiesta esplicitamente dall'utente con un difetto P2 aperto.** Non proseguire con ulteriori correzioni senza una nuova richiesta. Sono esauriti i tre giri di correzione concordati; il giro aggiuntivo proposto è stato rifiutato.

- Implementazione CIE, riautenticazione, isolamento account e credito Leone presenti nel checkout; nessun rilascio CIE effettuato. App 7.16.1, Fluid Engine 3.0.5 e manifest di manutenzione preservati.
- Verifiche finali riuscite: **431 unit test debug**, 47 domain anche release, **24 test strumentati** su emulatore API 35; zero errori/fallimenti/skipped. Build debug e AndroidTest e lint dell'app/moduli interessati riusciti. Log `android/build/reports/cie-phase2-correction3-checks.log`.
- Snapshot consegnato: HEAD `865dfcbf294dd61217f5bbb158dec6f217bd974f`, 183 file, SHA-256 `5992a90f604782ce41cbfd89330d93b76e1685d149c4ca80b2510bd46cd550dd`. Nessuna modifica ai sorgenti dopo le due review finali.
- APK debug: `android/app/build/outputs/apk/debug/app-debug.apk`, 46.373.784 byte, SHA-256 `9215c461db01f70207655ddad40f477d2aae6d108e30fe4903c04739039cda14`.
- **P2 aperto:** dopo errore iniziale di lettura/decrittazione/migrazione dell'archivio di accesso, lo snapshot risulta nullo e lo stato è `STORAGE_UNAVAILABLE`. La UI consente comunque un nuovo login. Se REST/CIE riesce ma il successivo commit continua a fallire, `finishAuthentication` può cancellare orari personalizzati, eventi e punteggi prima del CAS durevole. La sessione non viene pubblicata, ma quei dati offline sono già persi. Riferimenti: `SessionStore.kt:125–138`, `MainApp.kt:379–384`, `SchoolSyncCoordinator.kt:290–307,369–371`. Scenario confermato staticamente dalla review, non ancora riprodotto da un test dedicato. Non corretto su esplicita richiesta di fermarsi.
- **Gate CIE reale aperto:** nessun telefono con CieID e identità studente disponibile. Andata/ritorno CieID, caricamento autenticato, rinnovo reale e cambio account non sono stati collaudati su telefono. Checklist in `docs/cie-device-qa.md`.
- Perimetro HTTP/HTTPS confermato dall'utente. WebRTC UDP resta il limite documentato; diagnostica fuori dalla build in `android/qa-diagnostics/cie-webrtc`.
- Emulatore QA `emulator-5582` arrestato. L'istanza preesistente `emulator-5580` non è stata modificata. La rimozione dei file dell'AVD temporanea è stata rifiutata dalla revisione automatica con «blocked by policy»: directory `android/build/cie-qa-avd` e relativa registrazione AVD conservate. Report e APK conservati.

## Configurazione e perimetro

- Workflow richiesto: 2 implementatori e 2 reviewer indipendenti per fase, GPT-6.1 Sol, reasoning xhigh; massimo 3 figli simultanei e 3 giri di correzione per fase.
- CIE affiancata alle credenziali; un solo profilo studente. Genitori, multifiglio e supporto SPID dichiarato esclusi.
- Preservare gli aggiornamenti concorrenti: Fluid Engine 3.0.5 (`a055257fc4b559bee54b9ce114f265bcd237adfd`) e app 7.16.1. Nessun bump, commit o rilascio richiesto.
- Credito: Leone. Testo: «Metodo [omissis] nella fossa dei leoni: è entrato fra le API di Spaggiari ed è uscito con il token.»

## Criteri obbligatori

- Protocollo login/rinnovo, storage cifrato atomico e durevole, migrazione legacy e gestione separata REST/portale.
- Nessuna perdita della cache su errore o scadenza, nessuna commistione tra account, nessun effetto tardivo dopo logout.
- Callback, WebView e handoff CieID controllati; nessun segreto o dato personale nei log.
- Login password preservato, UI e crediti coerenti, test mirati e build/lint.
- La prova CIE reale sullo stesso telefono resta un gate distinto e obbligatorio: non sostituibile con mock, build o test emulatori.

## Fasi

1. Contratti, storage e rete: APPROVATA da entrambe le review finali (`phase1_security_recheck3`, `phase1_data_recheck3`), nessun difetto concreto residuo. 3/3 giri di correzione consumati, 204 test riusciti e build app/AndroidTest riuscita.
2. Integrazione dati, Android e collaudo trasversale: implementazione e tre giri di correzione conclusi. P2 Mainblocking chiuso; ultima review con un P2 residuo sullo storage illeggibile e la cancellazione preventiva dei dati offline. Utente ha richiesto di fermarsi e consegnare con il difetto aperto. Il gate CIE reale resta distinto e aperto.

## Base ed evidenze

- Head iniziale: `ae3479e3d4e68be547895a844b004ccb53540426`.
- Modifiche preesistenti: `android/app/build.gradle`, submodule `android/engine`, `android/engine.properties`.
- ZIP esaminato in memoria, senza esecuzione del contenuto. Esempi Android dichiarati non testati dall'autore.
- ADB senza dispositivi durante la pianificazione; verificare nuovamente per il collaudo.
- Il proprietario ha confermato di non avere un dispositivo disponibile ora: il gate CIE reale resterà aperto, con checklist in `cie-device-qa.md`.
- Emulatore preesistente `emulator-5580` (Pampa_Tablet API 35, vecchia app 7.13.0) da non modificare. Il tentativo di clone read-only è stato rifiutato dal runtime e si è chiuso. Creata invece AVD dedicata `Classeviva_CIE_QA_35`, API 35 Pixel 7, disco in `android/build/cie-qa-avd`; avviata sulla porta 5582 senza finestra/snapshot. Log `cie-emulator.*.log`. Arrestare solo questa istanza alla fine; pannello Devices T3 disabilitato, si usa ADB locale.
- Verifica automatica domain/datastore riuscita: 46 test domain + 22 datastore, zero fallimenti/errori/skipped; log `android/build/reports/cie-domain-storage-tests.log` e `cie-storage-final-tests.log`.
- Verifica rete riuscita: 116 test, zero fallimenti/errori/skipped; 19 nuovi casi CIE. Totale dei tre moduli: 184 test. Log `android/build/reports/cie-network-tests.log`.
- Snapshot sorgenti esaminato dalla prima review: SHA-256 `e8470ed5a96c66b4e7551bb54544ac403648ea71a47b8cfee5c1ef5fe521aa96` dei percorsi e contenuti ordinati sotto `src` dei tre moduli.
- Un test pre-review ha riprodotto la perdita dei tag account nei network interceptor di OkHttp. Il collegamento dell'account è ora applicato prima di BridgeInterceptor e il test del 401 tardivo passa senza indebolimento.
- Commit di release creato dall'altro lavoro: `a411109` (7.16.1 / engine 3.0.5); i file CIE restano separati e non committati. Build/pubblicazione della release proseguono in checkout isolato.
- Release di manutenzione completata dall'altro lavoro; fast-forward del solo `manifest.json` integrato sul main fino a `865dfcb`. Hash dei sorgenti CIE invariato durante le due review. Compilazione core-data con i contratti fase 1 riuscita (`cie-phase1-integration-compile.log`).
- Nessuna prova CIE live eseguita.

## Contratti di integrazione della fase 1

- Domain: `UserSession.authMethod` (default `PASSWORD`) e `ident`; `AuthenticationState` separa REST e portale; `AuthRepository.loginWithCie(pa, portalCookieHeader)`.
- Fase 2: `authenticationEpoch(): Long`; `loginWithCie(pa, portalCookieHeader = "", expectedEpoch: Long? = null)`. L'epoch si acquisisce prima della WebView e viene verificato prima dello scambio e del commit.
- Datastore: `StoredAuthSnapshot`, `CieCredentials`, `PortalSessionSnapshot`; `SessionStore.authenticationState`; commit/CAS dello snapshot prima dell'emissione.
- `accountGuard.capture()` produce una lease dell'account, invariata sul rinnovo del token. `withCurrentAccount(lease) { ... }` protegge commit sospendibili ed effetti finali; `clearAndRun { ... }` invalida e pulisce al logout.
- La barriera non è rientrante: nessuna mutazione dello store o rete dentro `withCurrentAccount`; nessuna mutazione dello store dentro il cleanup di `clearAndRun`.
- Rete: `ClassevivaRestClient.loginWithCie(pa)` restituisce un candidato non pubblicato; `PortalClient.importCieCookies(header, expectedIdent)` verifica l'identità, senza invalidare REST se il portale non è disponibile.
- Snapshot dopo correzione 1, inclusi i src di core-data: SHA-256 `38c75a5fe6edf482bf845b1feb5ef0ef441901521397aec2666fe0952c3e8a04`. Verifiche: 47 domain + 27 datastore + 121 network + 5 nuovi test bootstrap core-data = 200 test riusciti.
- Snapshot dopo correzione 2: SHA-256 `3b6d0f1628042e4ab38d6116eb9d0e67abefa6dbc3cd28d54dc8ec7692686e76` sugli stessi quattro moduli. Rete 123 test, totale verificato 202; log `cie-phase1-portal-generation-tests.log`. Il solo ritocco successivo al run corregge tre messaggi italiani, senza variazioni di comportamento.
- Snapshot dopo correzione 3: SHA-256 `03a44a10b0a706ebde374930571809adb2ff09160fc19b0251c38d5feb45925a` sugli stessi quattro moduli. Rete 125, totale verificato 204; log `cie-phase1-revalidation-tests.log`. I due nuovi casi usano clock iniettato e latch per WHOAMI rifiutato e riuscito dopo una rotazione più recente.
- Build baseline dell'app e dell'APK strumentato riuscita (`cie-phase1-app-baseline-build.log`). Test UI esistenti avviati sul solo emulatore QA tramite `:app:connectedDebugAndroidTest --serial emulator-5582`, log `cie-ui-baseline-tests.log`; ancora senza UI CIE, servono a distinguere la base dalle modifiche successive.
- Il primo connected test baseline si è fermato per `ExecutionException: UnsupportedOperationException` nell'infrastruttura, senza esito dei test; diagnosi con stacktrace in corso. Non è una prova UI superata.
- Diagnosi stacktrace: bug AGP nel filtro `--serial`, `ImmutableCollection.remove` in `DeviceProviderInstrumentTestTask.getFilteredDevices`. Per il finale usare soltanto `ANDROID_SERIAL=emulator-5582` senza `--serial`, oppure ADB esplicito. Test baseline eseguiti via ADB sugli APK già compilati: 11 test, 6 fallimenti da analizzare, log `cie-ui-baseline-adb.log`. La UI CIE non è ancora inclusa in quegli APK.

## Rilievi delle review iniziali

- P1: conservare la lease per l'intera operazione del portale, non ricatturarla a ogni GET/POST/retry. Una risposta vecchia non deve invalidare il portale del nuovo account né inviare un'azione di A con cookie B.
- P2: cookie importati/ruotati non devono restare attivi in memoria dopo un errore di persistenza.
- P2: il backend Android aggiorna la cache SharedPreferences prima dell'esito del commit; simulare separatamente cache/disco e gestire rollback/indisponibilità senza perdere legacy o pubblicare candidati non durevoli.
- P2: il login password dello stesso studente deve riportare esplicitamente READY con snapshot/CAS completo, senza cancellare account preesistente su fallimento.
- P2: redigere anche i DTO condivisi del login e dei cookie; completare il test di vero commit concorrente durante il cleanup logout.
- Dopo il giro 1, quei rilievi risultano risolti nelle due nuove review. Residuo P2: importare nuovi cookie per lo stesso studente non cambia l'epoch dell'account; una risposta della precedente sessione portale non deve invalidare la sostituzione né continuare con un POST. Il giro 2 vincola anche la versione dei cookie all'operazione, avanzandola soltanto per i suoi aggiornamenti autorizzati.
- Osservazione non bloccante: `clearAndRun` libera il gate se il cleanup termina con errore/cancellazione; il vecchio epoch è comunque invalidato e il cleaner è vincolato allo studente. Nella fase 2 rendere il cleanup logout resistente alla cancellazione senza creare un blocco indefinito su errori permanenti del filesystem.

## Vincoli per la fase Android

- Esempio ufficiale verificato: `italia/cieid-android-sdk`, `sampleapp/redirection/RedirectionActivity.kt`; CieID package `it.ipzs.cieid`, Activity `it.ipzs.cieid.BaseActivity`, risultato extra `URL` (ed eventualmente codice `ERROR`). Validare sempre l'URL restituito.
- Evitare token/cookie negli extra ActivityResult: il registry Android può conservarli nello stato. Preferire scambio immediato nell'Activity CIE tramite repository e restituire al chiamante soltanto un esito non sensibile.
- Legare il tentativo WebView alla generazione account iniziale, non soltanto al momento del successivo scambio HTTP; un logout durante il flusso web deve invalidare il tentativo.

## Integrazione e verifiche della fase 2

- Rete: la lease dell'operazione viene trasmessa nel contesto coroutine e marcata nella CallFactory prima di passare ai thread OkHttp. Il portale eredita la stessa lease; una chiamata avviata in ritardo non può adottare il nuovo account. Tre regressioni aggiunte; rete 128 test riusciti.
- Store: separata la generazione dell'identità dall'epoch di ammissione delle operazioni. La scadenza del vecchio token non invalida un accesso CIE già avviato per la stessa identità; logout e cambio account lo invalidano. Cleanup con snapshot precedente acquisito atomicamente sotto barriera. Datastore 30 test riusciti.
- Dati: bootstrap CIE, CAS durevole, isolamento di sync, worker, notifiche, scheduler, backup, download e file privati. Il rinnovo trasparente conserva le operazioni valide. Errori e scadenze conservano i dati offline e la cache colloqui.
- Logout: cleanup non cancellabile, con tentativo di ogni passo e segnalazione di errori permanenti. Eventi, timetable manuali/template e StudentScore globali sono ripuliti soltanto al logout esplicito o al primo accesso senza un proprietario legacy noto. Aggiunti solo due metodi DAO, senza modifica schema/versione. Conferma UI aggiornata.
- Android: scambio CIE dentro l'Activity privata; ActivityResult contiene soltanto un esito enum e il tentativo è legato all'epoch iniziale. Nessun token/cookie/URL del fornitore nei Bundle. CieID usa il contratto ufficiale; WebView con filtri anche per POST, file, popup, download e TLS. Credito Leone inserito nelle Informazioni.
- Un piccolo errore di concatenazione nel nuovo test IME è stato rilevato dalla compilazione integrata e corretto prima delle review; nessuna modifica al comportamento applicativo.
- Verifica integrata riuscita: 401 test unitari nella variante debug (47 domain, 30 datastore, 128 rete, 149 dati, 21 app, 26 settings), più i 47 domain anche release. Zero fallimenti/errori/skipped. `:app:assembleDebug`, `:app:assembleDebugAndroidTest` e `:app:lintDebug` riusciti. Log `android/build/reports/cie-phase2-integrated-checks.log`.
- Lint app: zero errori; 82 warning e 1 hint, incluse segnalazioni di dipendenze e file preesistenti. Le sei segnalazioni note di engine-ui non sono state corrette nei sorgenti condivisi.
- Smoke reale dell'APK debug sull'emulatore QA: pulsante CIE, Activity privata, selettore scuole del Ministero, rotazione e chiusura accessibile osservati. Nessuna credenziale inserita. La prima pagina può impiegare tempo per rendere il contenuto; non è stato necessario ampliare le origini ammesse. XML in `cie-ui-final-*.xml`.
- Il primo run strumentato ha superato 16/19 test; i tre fallimenti erano selettori obsoleti dei tab di navigazione, corretti sulle semantics reali senza ridurre le asserzioni. Nuovo `:app:connectedDebugAndroidTest` con `ANDROID_SERIAL=emulator-5582`, senza l'opzione AGP difettosa `--serial`: **19/19 riusciti**, zero errori/fallimenti/skipped. Anche `:app:assembleDebugAndroidTest` e `:app:lintDebug` ripetuti con esito positivo. Log `cie-phase2-connected-final.log`.
- Lint specifico dei sei moduli modificati riuscito con zero errori (`cie-phase2-module-lint.log`).
- Review iniziale fase 2 completata: `phase2_data_review` senza difetti concreti; `phase2_android_review` con due rilievi P2. Entrambi Sol xhigh, indipendenti dagli autori, sullo snapshot di 176 file SHA-256 `90c3622d9bd91d007520a7e28cea12a38e9358c2acacaf29c9dc74acd7c41a4e`.
- Giro di correzione fase 2 **1/3**. Rilievi confermati: la WebView non offre callback preventive per ogni URL dei redirect (in particolare subresource e POST); un errore del cleanup logout può uscire dallo scope UI e terminare l'app.
- Correzione redirect affidata all'autore Android: confine di rete WebView tramite proxy CONNECT locale con whitelist, TLS end-to-end, nessun fallback diretto e nessun bypass implicito loopback. Nessuna decrittazione o registrazione di contenuti. Setup supportato da AndroidX WebKit già presente; fail closed se indisponibile. Nuovi test richiesti.
- Correzione logout integrata da root: operazione posseduta dal MainViewModel che sopravvive alla destinazione Settings, avvisi app-wide sanitizzati per commit fallito o cleanup incompleto; login e CIE bloccati durante il logout. Fallback SettingsViewModel intercetta comunque l'errore. Nuovo test settings riuscito, test app da eseguire insieme al confine di rete aggiornato.
- Correzioni integrate e verificate: **409 test unitari debug** (47 domain, 30 datastore, 128 rete, 149 dati, 28 app, 27 settings), più i 47 domain release. Nuovo run con app/settings unit, build debug, APK strumentato, lint app/settings e **23/23 test strumentati** riuscito; zero fallimenti/errori/skipped. Log `cie-phase2-correction1-checks.log`. I quattro nuovi test WebView usano server locali e verificano override, assenza di fallback diretto, feature assente, redirect subresource e POST 307. I quattro JVM proxy verificano dinieghi, inoltro opaco e chiusura delle connessioni. I quattro test logout verificano commit fallito, cleanup incompleto, notifica dopo smontaggio della pagina e busy.
- Snapshot aggiornato: 180 file, SHA-256 `64e313b6d5973812b322c2f8d589c4687f06ed47fc28954551f41a553b4864a5`. Nuove review indipendenti in corso: `phase2_android_recheck1`, `phase2_data_recheck1`, Sol xhigh. Nessuna scrittura ai sorgenti durante le review.
- Target strumentato: AVD dedicata API 35, Android System WebView `124.0.6367.219`. Nessun dispositivo personale o identità reale usati.
- Review dopo correzione 1 concluse, hash invariato: i due P2 iniziali sono chiusi. Nuovo P2 concreto sul backup SAF: il repository proteggeva il payload, mentre la UI eseguiva lettura/scrittura file fuori dall'ownership iniziale. Una write bloccante poteva finire dopo il logout; una read vecchia poteva importare sotto il nuovo account. Correzione 2 affidata all'autore dati: callback I/O repository con lease prima di ogni await e write finale dentro barriera.
- Il reviewer Android ha segnalato inoltre WebRTC data channel/ICE fuori dal proxy HTTP senza permission fotocamera/microfono. Il probe sul WebView API 35 lo ha **confermato con UDP STUN sintetico locale**. Il candidato di blocco a inizio documento è stato provato e **respinto**: iframe inizialmente vuoti e document.open mantengono un costruttore recuperabile. Run sperimentale: 425 unit riusciti; 27 strumentati, 26 riusciti e 1 fallito, zero skipped. Log `cie-phase2-correction2-checks.log`; non rappresenta una protezione RTC riuscita.
- **Decisione esplicita dell'utente:** mantenere il perimetro del piano HTTP/HTTPS, documentando il limite WebRTC. Il candidato JS aggirabile e il requisito DOCUMENT_START_SCRIPT vengono rimossi; nessuna architettura VPN o promessa di confinamento di tutti i protocolli. Il fence riguarda HTTP/HTTPS e relativi redirect. I provider autorizzati restano nel modello di fiducia. La diagnostica RTC viene conservata separatamente dalla suite prodotto.
- Nello stesso giro rendere osservabile l'eventuale cleanup browser incompleto, senza annullare una sessione REST già salvata. Non è lecito garantire una pulizia riuscita se una delle API di storage fallisce.
- Fix SAF esteso anche all'apertura del file picker: epoch catturata al tap, pending solo in RAM e consumato al ritorno. Pending assente o annullamento non aprono stream; repository verifica l'epoch sullo stesso snapshot atomico. Coperti cambio account, logout/rientro dello stesso ID, letture e scritture bloccanti e cancellazione. Totale unit ora **425** (47 domain, 30 datastore, 128 rete, 158 dati, 29 app, 33 settings), tutti riusciti nel run precedente alla rimozione del candidato RTC; 47 domain release riusciti.
- Contratto Android ora `CieLoginResult(outcome, browserCleanupComplete)`: soltanto enum e boolean negli extra. Il nominale Success/Cancelled/TimedOut rimane distinto e un avviso app-wide segnala il cleanup incerto, senza alterare REST già salvato. Test di cleanup WebView guasto e Success con cleanup incompleto aggiunti.
- Snapshot prodotto dopo decisione HTTP/HTTPS: **182 file**, SHA-256 `ba1b6a2dc725950d061ba34337f5fac89d5ce31750df6c395ba361523b3026ab`. Diagnostica RTC opt-in fuori source set in `android/qa-diagnostics/cie-webrtc`; non viene distribuita né conteggiata nella suite prodotto. Sorgenti congelati; run finale previsto con 24 test strumentati.
- Probe pubblico temporaneo delle sole metriche DOM riuscito (nessun HTML, cookie, URL completo o credenziale registrato): gateway `eid.istruzione.it`, documento completo, 115 nodi, 1 form, 9 controlli, risorse dallo stesso host. Nessuna necessità osservata di ampliare le origini. Eseguito sull'APK intermedio con fence HTTP/HTTPS invariato; log `cie-public-probe-adb.log`. Il test diagnostico temporaneo sotto `build/` è escluso dalla build standard.
- Run finale standard **riuscito**, log `cie-phase2-correction2-final.log`: 425 unit debug e 47 domain release; 24/24 strumentati, zero fallimenti/errori/skipped; build debug e AndroidTest, lint app/domain/data/settings riusciti. Gli altri moduli interessati mantengono il lint già riuscito senza ulteriori modifiche. DEX dell'APK test verificato senza diagnostiche RTC o probe pubblico temporaneo.
- Review dopo correzione 2 concluse: `phase2_android_recheck2`, `phase2_data_recheck2`, Sol xhigh, hash iniziale/finale invariato sui 182 file `ba1b6a2dc725950d061ba34337f5fac89d5ce31750df6c395ba361523b3026ab`. P2 SAF/picker chiuso per isolamento; unico residuo concorde: cattura snapshot `runBlocking` sul Main durante la barriera tenuta da una write SAF lenta, con possibile ANR su logout o secondo backup. Nessun altro difetto concreto HTTP/HTTPS o dati rilevato.
- Correzione finale 3/3: pubblicazione unica volatile di snapshot durevole + lease + epoch, lettura non bloccante in produzione e rivalidazione invariata sui commit. La scrittura SAF mantiene la barriera; nessuna cattura d'identità ritardata da un dispatch. Autore dati cura publisher e test di concorrenza, autore UI aggiunge test del percorso MainViewModel → repository logout con heartbeat Main e gate trattenuto. Nuove review indipendenti richieste dopo verifiche.
- Giro 3 verificato: 431 unit debug (47 domain, 33 datastore, 128 rete, 160 dati, 30 app, 33 settings), più 47 domain release; 24/24 connected, build e lint riusciti. Sei nuove regressioni su publisher, commit bloccato/fallito e heartbeat Main; integrazione MainViewModel → repository → storage riuscita in 2,08 s. Lint app zero errori, 83 warning e 1 hint; il controllo runtime `PROXY_OVERRIDE` precede la chiamata segnalata da `RequiresFeature` dentro la lambda. Nessuna modifica all'engine.
- Due review finali nuove, `phase2_security_recheck3` e `phase2_data_recheck3`, Sol xhigh, stesso hash iniziale/finale `5992a90f604782ce41cbfd89330d93b76e1685d149c4ca80b2510bd46cd550dd`. Entrambe chiudono Mainblocking; quella dati non rileva ulteriori blocchi, quella sicurezza rileva il P2 storage/cancellazione descritto in apertura. Il difetto concreto non è stato ignorato per maggioranza.
- Dopo esaurimento dei tre giri, proposto un giro mirato aggiuntivo con due nuove review. Risposta esplicita dell'utente: «Ferma qui e consegna con il difetto aperto». Nessuna ulteriore correzione avviata.
- APK debug CIE locale non pubblicato: `android/app/build/outputs/apk/debug/app-debug.apk`, 46.373.907 byte, SHA-256 `ebda473a19c6b8c75d549739695fb14c6ce8a0652e4c7e5104739bf81adfa04e`. Versione app/engine/manifest di manutenzione preservati senza differenze rispetto a HEAD.
- Il collaudo con identità studente e CieID su telefono reale resta aperto; le verifiche simulate e dell'emulatore non lo sostituiscono.
