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
- **`core-network`**: Retrofit/OkHttp clients. Three entry points live side by side: `ClassevivaApiService` (official REST, via `RestClient`), `PortalClient` (scraped web-portal flows), and `GatewayClient` (controlled FastAPI gateway). `ApiSessionManager` owns token lifecycle; `NetworkModule` wires the shared OkHttp/Retrofit graph.
- **`core-data`**: Repository implementations, WorkManager sync scheduler (`SyncWorkScheduler`).
- **`core-database`**: Room DAOs and entities.
- **`core-datastore`**: DataStore preferences and encrypted settings.
- **`core-designsystem`**: the app's own thin layer on top of the **Fluid Engine** (`android/engine`, a git submodule pinned to a tag). It re-exports `:engine-ui` as `api`, so feature modules get the whole design system without changing their build files, and holds only what is ClasseViva's: the brand accent and `ClassevivaExpressiveTheme`, `GradePill` (which knows what a passing grade is), `FeatureIdentity` (which maps the app's seven sections onto the engine's tone/motif ring), and the bridge from the domain `SyncStatus` to the engine's.

  **The components themselves live in the engine, not here.** Adding one that is generic means adding it to `android/engine` and cutting a release — see `android/engine/ADOZIONE.md` and the `fluid-engine` skill. Never edit files under `android/engine/` without committing them in the engine repo: they vanish at the next update.
- **`feature-*`**: 7 feature modules — `dashboard`, `grades`, `agenda`, `lessons`, `communications`, `absences`, `settings` — each owns its Screen composable and ViewModel. Secondary screens (materials, documents, notes, schoolbooks, meetings, …) are hosted inside the `feature-*` modules above and reached from `MoreHubScreen`.

All Kotlin sources sit under the package root `dev.antigravity.classevivaexpressive`.

### Feature Capability System

Classeviva capabilities are year-scoped and tagged with a `FeatureCapabilityMode` (see [DomainModels.kt](android/core/core-domain/src/main/kotlin/dev/antigravity/classevivaexpressive/core/domain/model/DomainModels.kt)):
- `DIRECT_REST` — covered by the official Classeviva REST API (`ClassevivaApiService` / `RestClient`).
- `DIRECT_PORTAL` — scraped from the web portal via `PortalClient` when no stable REST exists.
- `GATEWAY` — routes through the controlled FastAPI gateway (justifications, noticeboard reply/join/upload, meetings, deliverable homework).
- `TENANT_OPTIONAL` / `UNSUPPORTED` — handled accordingly.

`RegistroFeature` enumerates every capability the app can expose. When adding or modifying a feature, pick the correct `RegistroFeature` entry and capability mode before choosing the data path — repositories in `core-data` branch on this to decide which client to hit.

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
- Signing config is read from `android/keystore.properties` (git-ignored). The keystore is
  `C:\VibeCoded Projects\pampa.jks`, alias `pampa` — the same key for every app.
- The engine is a submodule: a fresh clone needs `git submodule update --init` before it builds.
- `android/engine.properties` records which engine version this app is pinned to;
  `engine/tools/engine-doctor.ps1 -AppRoot .` checks that everything agrees.
- Parallel builds and build cache are enabled in `gradle.properties`.
