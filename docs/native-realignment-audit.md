# Native Realignment Audit

## Source of Truth

The shipping app is the native Android project under `android/`.
From this point onward the product baseline is:

- `cd android`
- `./gradlew testDebugUnitTest`
- `./gradlew :app:assembleDebug`

The Expo / TypeScript app remains in the repository only as legacy code until the cutover is fully archived.

## Active Code Paths

Modify these paths for product work:

- `android/app/src/main/kotlin`
- `android/app/src/main/AndroidManifest.xml`
- `android/core/*/src/main`
- `android/feature/*/src/main`
- `android/gradle/libs.versions.toml`

Primary native feature ownership:

- App shell and routing:
  - `android/app/src/main/kotlin/dev/antigravity/classevivaexpressive/MainApp.kt`
- App startup and notification channels:
  - `android/app/src/main/kotlin/dev/antigravity/classevivaexpressive/ClassevivaExpressiveApp.kt`
- Domain contracts:
  - `android/core/core-domain/src/main/kotlin/dev/antigravity/classevivaexpressive/core/domain/model/DomainModels.kt`
- Persistence and repositories:
  - `android/core/core-datastore/src/main/kotlin/dev/antigravity/classevivaexpressive/core/datastore/Stores.kt`
  - `android/core/core-database/src/main/kotlin/dev/antigravity/classevivaexpressive/core/database/database/SchoolDatabase.kt`
  - `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/Repositories.kt`
  - `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/sync/SchoolSyncCoordinator.kt`
- Network normalization:
  - `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/NetworkParsers.kt`
  - `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/RestClient.kt`
- Feature UI:
  - `android/feature/feature-dashboard`
  - `android/feature/feature-grades`
  - `android/feature/feature-agenda`
  - `android/feature/feature-communications`
  - `android/feature/feature-lessons`
  - `android/feature/feature-materials`
  - `android/feature/feature-settings`
  - `android/feature/feature-absences`
  - `android/feature/feature-documents`

## Frozen Legacy Paths

Do not use these paths as the source of truth for product fixes:

- `app/`
- `components/`
- `constants/`
- `hooks/`
- `lib/`
- `shared/`
- `server/`
- `drizzle/`
- `tests/`
- `app.config.ts`
- `babel.config.js`
- `metro.config.js`
- `global.css`
- `tailwind.config.js`
- `theme.config.ts`
- `expo-env.d.ts`
- `manifest.json`
- `package.json`
- `pnpm-lock.yaml`

Legacy native experiments to avoid for product work:

- `android/app/src/main/java/space/manus/classeviva/expressive/t20260318155100`

Temporary or generated paths that must not become dependencies:

- `.codex-temp/`
- `.expo/`
- `builds/`
- `node_modules/`
- `android/.gradle/`
- `android/**/build/`

## Prompt Mapping To Native Files

The original `Prompt.md` issues map to the native code like this:

- Login autofill and password semantics:
  - `android/app/src/main/kotlin/dev/antigravity/classevivaexpressive/MainApp.kt`
- Bottom navigation and screen transitions:
  - `android/app/src/main/kotlin/dev/antigravity/classevivaexpressive/MainApp.kt`
- Notification toggles, channels, test notification:
  - `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/notifications/NotificationSupport.kt`
  - `android/feature/feature-settings/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/settings/SettingsScreen.kt`
- Reduced home:
  - `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/Repositories.kt`
  - `android/feature/feature-dashboard/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/dashboard/DashboardScreen.kt`
- Unseen grades and goals:
  - `android/core/core-database/src/main/kotlin/dev/antigravity/classevivaexpressive/core/database/database/SchoolDatabase.kt`
  - `android/feature/feature-grades/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/grades/GradesScreen.kt`
- Agenda cleanup, colors and selected-day filtering:
  - `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/NetworkParsers.kt`
  - `android/feature/feature-agenda/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/agenda/AgendaScreen.kt`
  - `android/core/core-designsystem/src/main/kotlin/dev/antigravity/classevivaexpressive/core/designsystem/theme/NativeUi.kt`
- Noticeboard actions and attachments:
  - `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/NetworkParsers.kt`
  - `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/sync/SchoolSyncCoordinator.kt`
  - `android/feature/feature-communications/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/communications/CommunicationsScreen.kt`
- Materials and documents fallback:
  - `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/RestClient.kt`
  - `android/feature/feature-materials/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/materials/MaterialsScreen.kt`
  - `android/feature/feature-documents/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/documents/DocumentsScreen.kt`
- Absence vs late vs exit separation:
  - `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/NetworkParsers.kt`
  - `android/feature/feature-absences/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/absences/AbsencesScreen.kt`
- Lessons fallback and schedule reconstruction:
  - `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/Repositories.kt`
  - `android/feature/feature-lessons/src/main/kotlin/dev/antigravity/classevivaexpressive/feature/lessons/LessonsScreen.kt`

## Phase 2 Backend Plan

The new backend lives in `backend/python/`.
Its job is to become the future API boundary between the Android app and Classeviva upstream.

Phase 2 scope:

- Build a FastAPI service with routes for:
  - auth/session
  - dashboard
  - grades / periods / subjects
  - lessons / agenda
  - absences
  - noticeboard + actions + attachments
  - materials
  - documents
  - test notification
- Keep the Android app on direct upstream access until the Python API is production-ready.
- After cutover, switch `android/core/core-network` to a configurable backend base URL and archive the legacy Expo stack.

## Practical Rules

- Do not validate shipping behavior with `pnpm` or Expo commands.
- Do not make UI fixes first in `app/` or `components/`.
- If a change touches the product UX, there must be a native Android counterpart in `android/`.
- When in doubt, prefer adding tests in Kotlin over adding more logic to the legacy stack.
