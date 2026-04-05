# Classeviva Expressive

Repository Android nativo dell'app Classeviva Expressive.

## Stack supportato

- Android client: Kotlin, Jetpack Compose, Retrofit + OkHttp, Coroutines, Room, DataStore, EncryptedSharedPreferences
- Gateway backend: FastAPI, httpx, BeautifulSoup

## Struttura

- Il prodotto supportato vive in [android](/C:/Antigravity%20projects/classeviva-expressive/android).
- Il layer rete ufficiale vive in [android/core/core-network](/C:/Antigravity%20projects/classeviva-expressive/android/core/core-network).
- I repository e il coordinatore di sync vivono in [android/core/core-data](/C:/Antigravity%20projects/classeviva-expressive/android/core/core-data).
- Il gateway controllato per i flussi tenant-specifici vive in [backend/gateway](/C:/Antigravity%20projects/classeviva-expressive/backend/gateway).

## Stato migrazione

- Il legacy Expo/React Native e stato rimosso dal repository attivo.
- L'app usa le REST ufficiali di Classeviva per autenticazione, card, voti, assenze, agenda, lezioni, bacheca, note, materiali, documenti, periodi, materie e libri.
- I flussi senza copertura REST stabile nel client Android passano dal gateway controllato: giustificazioni, reply/join/upload in bacheca, compiti consegnabili e colloqui.
- Le capability del registro sono year-scoped e distinguono tra `direct_rest`, `gateway`, `tenant_optional` e `unsupported`.

## Comandi utili

Eseguire dalla cartella [android](/C:/Antigravity%20projects/classeviva-expressive/android):

```powershell
./gradlew.bat --no-daemon :app:assembleDebug
./gradlew.bat --no-daemon testDebugUnitTest
```

Eseguire dalla cartella [backend/gateway](/C:/Antigravity%20projects/classeviva-expressive/backend/gateway):

```powershell
python -m venv .venv
.venv\Scripts\activate
pip install -e .[test]
uvicorn app.main:app --reload --port 8088
pytest
```
