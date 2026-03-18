# Classeviva Expressive - Design Plan

## Overview
Un'app mobile moderna in stile Material 3 Expressive che si integra con il registro elettronico Classeviva per mostrare voti, agenda, assenze, comunicazioni e altre informazioni scolastiche.

## Design Principles
- **Material 3 Expressive**: Colori vivaci, forme arrotondate, animazioni fluide
- **Mobile Portrait (9:16)**: Ottimizzato per uso con una sola mano
- **Accessibilità**: Contrasti adeguati, touch target di almeno 44x44pt
- **Performance**: Caricamento veloce, lazy loading per liste lunghe

## Screen List

### 1. **Login Screen**
- Campo username/codice studente
- Campo password
- Pulsante "Accedi"
- Opzione "Ricorda credenziali" (con secure storage)
- Link "Aiuto" per il recupero credenziali
- Indicatore di caricamento durante l'autenticazione

### 2. **Home Screen (Dashboard)**
- Greeting personalizzato con nome studente
- Card con media voti attuali
- Prossimi compiti/verifiche in evidenza
- Comunicazioni recenti (collapsible)
- Assenze/ritardi attuali
- Pulsante di refresh pull-to-refresh

### 3. **Voti Screen**
- Lista filtrata per materia
- Card per ogni voto con:
  - Materia
  - Voto/valutazione
  - Data
  - Tipo (compito, interrogazione, etc.)
  - Peso (se disponibile)
- Media per materia
- Grafico trend voti (opzionale)

### 4. **Agenda Screen**
- Calendario mensile
- Lista eventi del giorno selezionato
- Compiti da consegnare
- Verifiche programmate
- Lezioni
- Filtri per tipo di evento

### 5. **Assenze Screen**
- Contatore assenze totali
- Contatore ritardi
- Lista dettagliata con date
- Giustificazioni (se richieste)
- Stato giustificazione (in sospeso, accettata, rifiutata)

### 6. **Comunicazioni Screen**
- Lista comunicazioni scuola
- Filtri per mittente (dirigenza, docenti, etc.)
- Marcatura come letta/non letta
- Dettaglio comunicazione con allegati

### 7. **Profilo Screen**
- Dati personali (nome, classe, sezione)
- Istituto scolastico
- Anno scolastico
- Opzioni logout
- Impostazioni app (tema, notifiche)

## Primary Content and Functionality

### Home Screen
- **Greeting**: "Ciao, [Nome]!"
- **Media Voti**: Card con media generale e medie per materia
- **Prossimi Compiti**: Lista dei 3 compiti più prossimi
- **Comunicazioni**: Ultime 2-3 comunicazioni non lette
- **Assenze**: Contatore assenze/ritardi
- **Refresh**: Pull-to-refresh per aggiornare dati

### Voti Screen
- **Filtri**: Per materia, per tipo, per periodo
- **Statistiche**: Media, min, max per materia
- **Dettagli**: Tap su voto per vedere descrizione completa
- **Ordinamento**: Per data, per materia, per voto

### Agenda Screen
- **Calendario**: Visualizzazione mese con indicatori eventi
- **Giorno**: Tap su data per vedere dettagli
- **Tipi evento**: Colori diversi per compiti, verifiche, lezioni
- **Notifiche**: Reminder per compiti in scadenza

### Assenze Screen
- **Statistiche**: Totale assenze, ritardi, ore
- **Giustificazioni**: Stato e data di giustificazione
- **Dettagli**: Data, ora, materia

## Key User Flows

### Flow 1: Login e Accesso
1. Utente apre app
2. Inserisce username/password Classeviva
3. Tap "Accedi"
4. API autentica credenziali
5. Token salvato in secure storage
6. Reindirizzamento a Home Screen
7. Caricamento dati iniziali

### Flow 2: Visualizzazione Voti
1. Da Home, tap su "Voti"
2. Caricamento lista voti
3. Visualizzazione voti con media
4. Tap su voto per dettagli
5. Opzione per tornare indietro

### Flow 3: Consultazione Agenda
1. Da Home, tap su "Agenda"
2. Visualizzazione calendario mese corrente
3. Tap su data per vedere eventi
4. Visualizzazione compiti/verifiche/lezioni
5. Notifiche per compiti in scadenza

### Flow 4: Verifica Assenze
1. Da Home, tap su "Assenze"
2. Visualizzazione contatori
3. Scroll lista assenze
4. Tap su assenza per dettagli
5. Opzione giustificazione (se disponibile)

### Flow 5: Logout
1. Da Profilo, tap "Logout"
2. Conferma logout
3. Cancellazione token
4. Reindirizzamento a Login

## Color Choices (Material 3 Expressive)

### Primary Colors
- **Primary**: `#6750A4` (Viola)
- **Secondary**: `#625B71` (Viola scuro)
- **Tertiary**: `#7D5260` (Rosa)

### Semantic Colors
- **Success**: `#2E8B57` (Verde)
- **Warning**: `#FF9800` (Arancione)
- **Error**: `#DC3545` (Rosso)
- **Info**: `#0288D1` (Blu)

### Neutral Colors
- **Background**: `#FFFBFE` (Bianco freddo)
- **Surface**: `#FFFBFE` (Bianco freddo)
- **Surface Variant**: `#E8DEF8` (Viola chiaro)
- **Outline**: `#79747E` (Grigio)
- **On Background**: `#1C1B1F` (Nero)

### Dark Mode
- **Background**: `#1C1B1F` (Nero)
- **Surface**: `#1C1B1F` (Nero)
- **Surface Variant**: `#49454E` (Grigio scuro)
- **On Background**: `#E6E1E6` (Bianco)

## Component Design

### Cards
- Rounded corners: 12dp
- Elevation: 1dp
- Padding: 16dp
- Border: 1px outline color

### Buttons
- Primary: Filled button with primary color
- Secondary: Outlined button with secondary color
- Tertiary: Text button
- Rounded corners: 100dp (pill-shaped)
- Min height: 40dp

### Input Fields
- Rounded corners: 8dp
- Padding: 12dp
- Border: 1px outline color
- Focus state: Primary color border

### Typography
- **Display Large**: 57sp, weight 400
- **Headline Large**: 32sp, weight 400
- **Title Large**: 22sp, weight 500
- **Body Large**: 16sp, weight 400
- **Label Medium**: 12sp, weight 500

## Navigation Structure

```
Tab Bar Navigation:
├── Home (Dashboard)
├── Voti
├── Agenda
├── Assenze
└── Profilo
```

## Accessibility Considerations
- Minimum touch target: 44x44pt
- Color contrast ratio: 4.5:1 for normal text
- Icon labels for all buttons
- Haptic feedback for interactions
- Support for dynamic text sizing
