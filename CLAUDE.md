# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All Gradle commands run from `android/`:

```bash
./gradlew.bat --no-daemon :app:assembleDebug       # Build debug APK
./gradlew.bat --no-daemon :app:assembleRelease      # Build release APK
./gradlew.bat --no-daemon testDebugUnitTest         # Run all unit tests
./gradlew.bat --no-daemon :module:testDebugUnitTest # Run tests for a specific module
./gradlew.bat --no-daemon lintDebug                 # Run lint
```

All Python (gateway) commands run from `backend/gateway/`:

```bash
python -m venv .venv && .venv\Scripts\activate
pip install -e .[test]
uvicorn app.main:app --reload --port 8088
pytest
```

## Architecture

### Module Layers

```
app  →  feature-*  →  core-data  →  core-domain (pure Kotlin)
                   →  core-network
                   →  core-database
                   →  core-datastore
         (all UI)  →  core-designsystem
```

- **`core-domain`**: Models, repository interfaces, use cases — no Android dependencies.
- **`core-network`**: Retrofit/OkHttp API clients. Two clients exist: `ClassevivaApiService` (official Classeviva REST) and `GatewayClient` (tenant-specific flows via the controlled FastAPI gateway).
- **`core-data`**: Repository implementations, WorkManager sync scheduler (`SyncWorkScheduler`).
- **`core-database`**: Room DAOs and entities.
- **`core-datastore`**: DataStore preferences and encrypted settings.
- **`core-designsystem`**: Shared Material 3 Compose components, theme (supports System/Light/Dark/AMOLED + brand/dynamic/custom color).
- **`feature-*`**: 11 feature modules, each owns its Screen composable and ViewModel.

### Feature Capability System

Classeviva capabilities are year-scoped and tagged with a `FeatureCapabilityMode`:
- `DIRECT_REST` — covered by official Classeviva REST API.
- `GATEWAY` — routes through the controlled FastAPI gateway (justifications, noticeboard reply/upload, meetings, deliverable homework).
- `TENANT_OPTIONAL` / `UNSUPPORTED` — handled accordingly.

When adding or modifying a feature, check the domain's `RegistroFeature` enum and the relevant `FeatureCapabilityMode` before choosing the data path.

### UI Pattern

MVVM with Jetpack Compose. ViewModels expose immutable state data classes (`*UiState`) combined from multiple flows using `combine()` + `SharingStarted.WhileSubscribed()`. Screens collect state via `collectAsStateWithLifecycle()`.

Navigation is handled in `MainApp.kt` using a typed NavHost with 5 top-level destinations (Home, Grades, Agenda, Communications, More). Secondary features live under a `MoreHubScreen`.

### Network Parsing

Some Classeviva REST responses include embedded HTML. `NetworkParsers` uses JSoup to convert these to domain models. Tests for this live in `core-network/src/test/` using OkHttp `MockWebServer`.

## Key Files

- `android/gradle/libs.versions.toml` — single source of truth for all dependency versions.
- `android/app/src/main/kotlin/.../MainApp.kt` — root Compose UI, navigation, auth flow.
- `android/app/src/main/kotlin/.../MainViewModel.kt` — session restoration, settings observation, sync scheduling.
- `android/core/core-domain/.../DomainModels.kt` — canonical domain entities.
- `backend/gateway/app/main.py` — FastAPI gateway entry point.

## Notes

- `minSdk 26` (Android 8.0), `compileSdk`/`targetSdk 36`.
- Signing config is read from `android/keystore.properties` (git-ignored).
- Parallel builds and build cache are enabled in `gradle.properties`.
