# Classeviva Expressive

Percorso attivo per bugfix e release APK:

- L'app Android reale vive in [android](/C:/Antigravity%20projects/classeviva-expressive/android) ed e costruita con Kotlin + Jetpack Compose.
- Le chiamate a Classeviva usate dall'APK passano oggi dai client Kotlin in [android/core/core-network](/C:/Antigravity%20projects/classeviva-expressive/android/core/core-network).
- La root Expo/React Native resta nel repository come materiale legacy e non e il percorso da usare per i fix Android correnti.
- [backend/python](/C:/Antigravity%20projects/classeviva-expressive/backend/python) e uno scaffold separato: non e parte del flusso che produce l'APK attuale.

Se devi correggere bug del prodotto Android, parti sempre da [android](/C:/Antigravity%20projects/classeviva-expressive/android).
