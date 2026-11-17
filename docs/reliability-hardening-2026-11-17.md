# Affidabilità quotidiana — 17 novembre 2026

Interventi sul working tree di Classeviva Expressive, a partire da `c603e0dc`. Priorità alle funzioni già presenti: notifiche, consultazione quotidiana, sincronizzazione, dati locali e widget. Nessuna pubblicazione o modifica al Fluid Engine incorporato.

## Comportamento corretto

| Area | Risultato |
|---|---|
| Tap sulle notifiche | Risoluzione esplicita di destinazione e parametri: voto, evento, compito, comunicazione, nota e assenza conservano la propria identità. Il riepilogo delle note apre la scheda Note. Gli argomenti duplicati o malformati vengono rifiutati. |
| Avvio e ripristino | L’inbox conserva il tap durante login e caricamento delle preferenze; dopo il consumo non lo riapplica ricreando l’Activity. Un tap più recente non viene cancellato dal completamento di quello precedente. |
| Account e anno | Le nuove notifiche includono studente e anno d’origine. Un collegamento per un contesto diverso non apre l’ID nel contesto corrente e mostra subito una spiegazione chiudibile. Un successivo tap valido rimuove quell’avviso. I vecchi link senza scope restano compatibili. |
| Novità da notificare | Checkpoint persistente separato dalla cache consultata dall’interfaccia. Un refresh foreground o di manutenzione non consuma più le novità prima del worker. Prima lettura e baseline corrotta inizializzano lo storico senza annunciarlo integralmente. |
| Ripristino delle notifiche | Permesso Android negato, canale disattivato e post falliti conservano le novità. Lo spegnimento esplicito di una categoria nell’app riconosce i dati osservati mentre era spenta. |
| Compiti da fonti diverse | Il primo baseline della fonte dedicata è distinto dalla consegna. Letture dedicate/merged fuori fase, blocchi Android e successiva corruzione del checkpoint non devono perdere nuove assegnazioni o ripresentare quelle già riconosciute. |
| Voti notificati | Nuovi voti e rettifiche arrivati nello stesso aggiornamento sono entrambi rappresentati. Tutti i content intent reali, incluso l’orario live, conservano lo scope. Spegnere l’orario live ritira la notifica presente. |
| Cancellazione della sync | La cancellazione interrompe endpoint e fallback, libera il gate e ripristina lo stato visibile. Il refresh annullato non lascia un cooldown che impedisca di riprovare. |
| Freschezza della cache | La verifica annuale completa ha un marker persistente distinto dal timestamp dei contenuti e dal refresh FAST. Un aggiornamento della sola finestra temporale non rende fresco il resto dell’anno. |
| Errori parziali | Se tutte le fonti dei compiti falliscono, lo snapshot completo viene conservato; una prima lettura fallita non salva un falso elenco vuoto. Se una fonte riesce, aggiorna i propri dati conservando quelli della fonte indisponibile e segnalando il fallimento. Il fetch condiviso dei colloqui attribuisce gli errori a tutte le sezioni richieste. |
| Bacheca e note | L’ultimo tocco possiede il dettaglio. Risposte lente, dismiss e azioni concluse dopo il cambio selezione non riaprono o sostituiscono il contenuto recente. Invii duplicati bloccati e fallback al contenuto salvato accompagnato dall’errore del caricamento completo. |
| Agenda e orario | DatePicker in UTC; data ed elemento iniziali consumati una volta. L’orario aggiorna oggi e settimana a mezzanotte e al rientro, preservando la scelta manuale del giorno. |
| Medie e periodi | Home, statistiche, schermata Voti e assistente condividono la politica dei pesi. Il codice di un periodo riconosciuto prevale sulla data di pubblicazione. Pesi esclusi o non finiti non producono `NaN`. |
| Assenze | Una notifica apre anche una registrazione già giustificata o non giustificabile, senza riaprire il dialogo al refresh. Rimossa la limitazione silenziosa alle ultime venti registrazioni. Invio singolo e completamento che non chiude una selezione successiva. |
| Contatori e filtri widget | Il repository conserva tutti i voti non visti e gli impegni prima dell’applicazione dei filtri del widget. Un gruppo iniziale di compiti non nasconde una verifica successiva. |
| Ciclo Glance e account | Dati, preferenze, tema, calendario ed epoch sono osservati nella composizione attiva. Logout e login invalidano localmente il widget; una vecchia generazione non può ripubblicare dati, anche nel logout/relogin dello stesso studente. |
| Refresh widget | Il worker possiede lo stato di aggiornamento e lo libera anche su cancellazione. I tap con policy KEEP non possono riscrivere `refreshing=true` dopo il cleanup. Cambio data/ora/fuso e aggiornamento periodico locale ridisegnano il calendario senza richiedere rete. |

La media generale ora applicata è quella già adottata dalla schermata Voti: media pesata di ciascuna materia, seguita dalla media delle materie. Può quindi differire dalla precedente media semplice di tutti i singoli voti mostrata dalla home. I pesi percentuali seguono la normalizzazione già usata dall’assistente.

## Verifiche dell’orchestratore

| Controllo | Esito |
|---|---|
| Suite JVM dei moduli applicativi | 818 casi nei risultati validi; zero fallimenti, errori o skip. Include app, core e feature con test. I moduli senza test risultano `NO-SOURCE`; i test dell’engine non sono inclusi nel totale. |
| Build | APK debug QA, test Android dell’app e test Android widget compilati. Ultima modifica a MainApp ricompilata con unit test dell’app e lint dell’app. |
| Lint | Undici moduli toccati: zero errori/fatal; 121 warning nei report. Non equivale a un lint completo e indipendente del repository dell’engine. |
| Controlli repository | `git diff --check` superato. Engine doctor superato: engine 3.0.5, nessuna modifica locale al submodule. |
| Android, app | 22 test passati, zero fallimenti nel run finale. Include il grafo Navigation reale, inbox e Bundle, assenza già giustificata e vera MainActivity con Hilt/Room/Fluid: tap a freddo e a caldo, ricreazione senza replay, rifiuto di account/anno diversi e successivo tap valido che rimuove l’avviso. |
| Android, widget | `WidgetRefreshKeepRaceTest`: 1 test passato con WorkManager e action reali. Il worker è sostituito da un worker controllato e il flag da una variabile: questa prova non certifica un ridisegno completo delle RemoteViews. |

La QA usa l’emulatore dedicato `Classeviva_CIE_QA_35`, Android 15/API 35, e il pacchetto separato `dev.antigravity.classevivaexpressive.qa`. La prova con la vera Activity attraversa Hilt, Room, MainApp, navigazione Fluid e dettaglio voto con dati locali sintetici e sessione non abilitata alla rete REST. Non sono credenziali o dati di uno studente reale. L’entry point di preparazione è presente soltanto nella variante debug.

I risultati JVM comprendono task eseguiti nei batch di questa sessione e task poi riutilizzati da Gradle con gli stessi input. I conteggi provengono dagli XML finali; non sommano i tentativi falliti o le vecchie directory `docs/validation-*`.

Durante la verifica sono stati corretti quattro mock metadati non realistici e un test che assumeva di ricevere subito il risultato finale, ignorando lo stato intermedio di invio. La prima prova Activity usava un harness incompatibile con il consumo dell’intent. Il test della scala animazioni richiede l’accesso esclusivo a UiAutomation: con la sessione manuale Device sospesa passa. Questi tentativi sono conservati separatamente e non valgono come esiti verdi.

## Review indipendenti

Quattro analisi/implementazioni parallele, tre review indipendenti e due verifiche del delta hanno guidato i cicli. Il tetto di concorrenza effettivo era quattro agenti, orchestratore compreso.

| Ambito | Esito finale della review | Limite della prova del reviewer |
|---|---|---|
| Notifiche e sincronizzazione | 8/10 statico; chiusi i finding FAST/FULL e priming dedicato durante blocco Android, incluse le varianti con cache fuori fase. | Sorgenti, wiring e regressioni letti; nessun test o dispositivo eseguito dal reviewer. |
| Navigazione e percorsi quotidiani | 8,5/10 statico, nessun P1/P2 confermato nel delta esaminato. | Cold/warm/restore e comportamento visivo restavano NV al reviewer; le prove Android sono attribuite all’orchestratore. L’avviso immediato finale è stato aggiunto dopo la cattura UI. |
| Widget e dashboard | Correttezza dati, isolamento account e ciclo vita 8/10; robustezza e test 7/10. Chiusi closure Glance, race KEEP e invalidazione al logout. | Launcher, RemoteViews e processo terminato non qualificati dal reviewer. |

Le rubriche coprono ambiti diversi e non sono aggregate in un voto complessivo dell’app.

## Evidenze locali e ripetibilità

I log completi e gli script del runner sono in [artifacts/reliability-2026-11-17](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17>). La cartella è esclusa da Git, ma rimane nel workspace.

- [Suite JVM](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/unit-tests.log>) e [conteggi per modulo](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/unit-summary.json>).
- [Build e lint dei moduli](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/build-lint.log>), [ultimo controllo app](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/final-app-checks.log>) e [riepilogo lint](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/lint-summary.json>).
- [Prova Android finale app](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/instrumentation-app-final.log>) e [prova WorkManager widget](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/instrumentation-widget-01.log>).
- [Cattura diagnostica dell’avviso accodato](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/instrumentation-year-diagnostic.log>): conferma `loading=false`, studente e anno correnti, home visibile e avviso di accesso davanti alla risposta al tap.

I runner Gradle usano un solo slot condiviso, heap Gradle da 2 GiB, test da 768 MiB, `--no-daemon --no-watch-fs --no-parallel --max-workers=2` e rilascio nel `finally`. L’APK QA conservato è [classeviva-expressive-qa-debug.apk](<C:/VibeCoded Projects/classeviva-expressive/artifacts/reliability-2026-11-17/classeviva-expressive-qa-debug.apk>); non è una pubblicazione dello store. XML JUnit, report lint e identità dei file sono conservati nella stessa cartella di evidenza.

## Limiti ancora reali

Non sono stati verificati con un account scolastico reale il recapito dei nuovi dati, le differenze tra tenant e le operazioni di scrittura sul registro. CIE/NFC, notifica live su API 36, ricezione sotto restrizioni energetiche, riavvio e comportamento sui telefoni dei diversi produttori restano da collaudare. L’emulatore non è stato usato per conclusioni sulle prestazioni.

La consegna Android e il salvataggio Room del checkpoint non sono una transazione atomica: un crash tra post e salvataggio può provocare un reinvio. L’ID fisso della categoria sostituisce la notifica ancora presente. Inoltre, dati mai letti mentre una categoria era spenta non possono essere datati con certezza senza metadati di pubblicazione affidabili dal server.

Nei log dell’emulatore compare anche un warning di compilazione shader dell’engine 3.0.5 sul termine `flat`, con fallback; il materiale grafico non è stato qualificato su hardware reale in questa fase.
