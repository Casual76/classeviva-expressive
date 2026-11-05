# Diagnostica WebRTC separata

Questa cartella non appartiene ad alcun source set Gradle: il test non viene compilato o distribuito con l'app o con la suite strumentata ordinaria. Usa soltanto una pagina sintetica e UDP loopback, senza credenziali, SDP, indirizzi o payload nei log.

Nel collaudo del 5 novembre 2026 il controllo senza guard JavaScript ha ricevuto STUN locale anche con proxy HTTP installato e permessi multimediali negati. Il candidato `addDocumentStartJavaScript("*")` ha invece lasciato recuperabili i costruttori RTC negli iframe iniziali letti sincronicamente, anche annidati, e nei casi `document.open`/`about:blank`. Il guard è stato rimosso: non costituisce una protezione affidabile. Il run corrispondente aveva 425 unit test passati e 27 test strumentati, di cui 26 passati e il test di confinamento RTC fallito; evidenza nel report locale `android/build/reports/cie-phase2-correction2-checks.log`.

L'utente ha scelto di mantenere il perimetro HTTP/HTTPS del piano. Il proxy CIE controlla quel traffico e i relativi redirect; non garantisce il confinamento di ogni protocollo della WebView. Il contenuto dei provider autorizzati rimane nel modello di fiducia.

Per ripetere esplicitamente la sola diagnostica in un checkout QA:

1. Copiare `CieWebRtcUdpDiagnostic.kt` in `app/src/androidTest/kotlin/dev/antigravity/classevivaexpressive/cie/`.
2. Da `android/`, con JBR 21, selezionare il solo dispositivo QA con `ANDROID_SERIAL` e lanciare in PowerShell `.\gradlew.bat --no-daemon --max-workers=2 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.antigravity.classevivaexpressive.cie.CieWebRtcUdpDiagnostic`.
3. Rimuovere la copia dal source set dopo la prova; l'originale resta qui escluso dalla build.

Un controllo senza datagramma ricevuto è marcato `INCONCLUSIVE`: non dimostra che RTC sia confinato. Questo test diagnostico non è un criterio di accettazione del perimetro HTTP/HTTPS e non è un collaudo CIE reale.
