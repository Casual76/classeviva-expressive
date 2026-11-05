# Collaudo CIE su Android reale

Questo collaudo richiede un dispositivo reale con CieID e un'identità collegata a un profilo studente. Non è stato eseguito: il proprietario non ha attualmente un telefono disponibile.

Non raccogliere PIN, password, cookie o token nei report. L'autenticazione viene completata personalmente dal titolare nelle schermate del fornitore dell'identità e in CieID.

## Percorso principale

1. Installare l'APK debug verificato e aprire «Entra con la CIE» accanto alle credenziali.
2. Completare la scelta della scuola e l'autenticazione. Verificare passaggio a CieID e ritorno alla stessa pagina nell'app.
3. Verificare il profilo studente, il caricamento dei dati REST e almeno una funzione del portale. Non assumere che il successo REST dimostri la validità dei cookie del portale.
4. Chiudere e riaprire l'app: account e dati devono restare disponibili.
5. Alla scadenza reale della sessione REST, avviare un aggiornamento. Il rinnovo deve completarsi senza una nuova autenticazione CIE. Un rinnovo di prova anticipato non sostituisce questa verifica.

## Interruzioni e isolamento

- Ruotare lo schermo e passare in background durante il login; la pagina deve restare utilizzabile.
- Terminare il processo durante il login e riaprire: il tentativo precedente non deve essere riutilizzato.
- Annullare, riaprire e verificare che non rimanga materiale del tentativo annullato.
- Provare CieID assente: l'app deve offrire un messaggio e un'azione esplicita per installarla.
- Interrompere la rete durante sincronizzazione/rinnovo: i dati offline devono restare visibili.
- Verificare scadenza del solo portale: le funzioni REST devono continuare e i colloqui già salvati non devono sparire.
- Eseguire logout da A e accesso a B mentre una sincronizzazione è in corso: nessun risultato, file, notifica o lavoro successivo deve appartenere ad A.
- Durante una riautenticazione, selezionare un'identità diversa: non deve sostituire l'account corrente.
- Verificare nelle Informazioni il credito «Leone» e la frase con «[omissis]».

## Evidenze da conservare

Registrare soltanto versione app/engine, modello e versione Android, versione CieID, caso eseguito, risultato e orario. Eventuali schermate devono escludere documenti, credenziali, token e dati scolastici personali. Distinguere sempre il risultato del dispositivo dalle prove automatiche.
