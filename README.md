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

## Licenza

Classeviva Expressive e' distribuita sotto **GNU General Public License v3.0** (vedi [LICENSE](LICENSE)).

Il motivo e' il Fluid Engine, il design system condiviso (sottomodulo `android/engine`): dalla versione 3.0 contiene
codice di [LibrePods](https://github.com/kavishdevar/librepods) (GPL-3.0) per i pulsanti e il loro gesto
(`android/engine/LICENSES/LibrePods.md`), e un'app che lo distribuisce e' un'opera derivata, quindi GPL-3.0 a sua volta.

I sorgenti completi di ogni APK sono in questo repository, pubblico:
<https://github.com/Casual76/classeviva-expressive>. Chi riceve un APK ha diritto ai sorgenti della stessa versione
(il tag o il commit corrispondente al `versionName`) e puo' modificarli e ridistribuirli alle stesse condizioni.
Le librerie di terze parti (OkHttp, Retrofit, Room, Coil, JSoup, PDFBox-Android e le altre) sono Apache-2.0, MIT o BSD,
compatibili con la GPL-3.0; l'elenco delle opere incluse nell'APK e' nella pagina Impostazioni, sezione informazioni.
