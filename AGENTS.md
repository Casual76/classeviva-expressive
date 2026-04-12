# AGENTS.md

This file provides guidance to the Gemini CLI and specialized sub-agents when working with this repository.

## Specialized Agent Team

To handle the complexity of this modular Android project, use the following specialized sub-agents:

1.  **`@android-core-architect`**: Architect for modular structure, Gradle, and Clean Architecture.
2.  **`@cv-domain-specialist`**: Expert in Classeviva APIs, HTML parsing (JSoup), and school logic.
3.  **`@data-alignment-guard`**: Specialist in Room, DataStore, and offline-first synchronization.
4.  **`@quality-assurance-lead`**: Lead for unit/UI testing, linting, and overall stability.
5.  **`@ui-ux-polisher`**: Designer for Jetpack Compose, Material 3 Expressive, and accessibility.

## Commands

All commands run from `android/`:

```bash
./gradlew.bat --no-daemon :app:assembleDebug       # Build debug APK
./gradlew.bat --no-daemon :app:assembleRelease      # Build release APK
./gradlew.bat --no-daemon testDebugUnitTest         # Run all unit tests
./gradlew.bat --no-daemon :module:testDebugUnitTest # Run tests for a specific module
./gradlew.bat --no-daemon lintDebug                 # Run lint
```

## Architecture (100% Native Kotlin)

### Module Layers

```
app  →  feature-*  →  core-data  →  core-domain (pure Kotlin)
                   →  core-network
                   →  core-database
                   →  core-datastore
         (all UI)  →  core-designsystem
```

- **`core-domain`**: Models, repository interfaces, use cases — no Android dependencies.
- **`core-network`**: Retrofit/OkHttp API clients. All communication is direct to Classeviva REST APIs.
- **`core-data`**: Repository implementations, WorkManager sync scheduler (`SyncWorkScheduler`).
- **`core-database`**: Room DAOs and entities.
- **`core-datastore`**: DataStore preferences and encrypted settings.
- **`core-designsystem`**: Shared Material 3 Compose components, theme.
- **`feature-*`**: 11 feature modules, each owns its Screen composable and ViewModel.

### UI Pattern

MVVM with Jetpack Compose. ViewModels expose immutable state data classes (`*UiState`). Navigation is handled in `MainApp.kt`.

### Network Parsing

Some Classeviva REST responses include embedded HTML. `NetworkParsers` uses JSoup to convert these to domain models. Tests live in `core-network/src/test/`.

## Key Files

- `android/gradle/libs.versions.toml` — Single source of truth for dependencies.
- `android/app/src/main/kotlin/.../MainApp.kt` — Navigation & Auth flow.
- `android/core/core-domain/.../DomainModels.kt` — Canonical domain entities.
