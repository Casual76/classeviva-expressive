# Classeviva Gateway

Gateway HTTP controllato per le funzioni del registro che richiedono ancora flussi web o moduli tenant-specifici.

## Obiettivi

- Nessuna password persistita lato server.
- Cookie di sessione solo in memoria e solo per la singola richiesta.
- REST ufficiali riusate quando disponibili.
- Heuristics HTML limitate al backend, mai nel client Android.

## Avvio locale

```powershell
cd backend/gateway
python -m venv .venv
.venv\Scripts\activate
pip install -e .[test]
uvicorn app.main:app --reload --port 8088
```

Imposta poi `gatewayBaseUrl=http://10.0.2.2:8088/` o l’URL adatto nel file `android/gradle.properties`.
