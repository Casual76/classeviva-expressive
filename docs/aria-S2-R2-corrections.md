# Source2 DATA FOUNDATION: S2/R1 correction R2/3

2026-11-07. App-local correction after both independent reviews on the released 463-file snapshot (manifest SHA256 4CB75323E02C8E4E5C25BD8DB477C27BF16541FE37DA4C3280CB1EA86F0E61B1). No new review or device gate waiver.

## Changes

- P1 collection loss: strict validation returns the canonical payload actually normalized by REST. Single nonempty alternatives survive empty siblings; multiple nonempty alternatives fail before cache commit. Direct/nested data, all-empty alternatives, malformed siblings, documents/report kinds and material folder selection are covered through MockWebServer.
- P2 alias types: required-field validation and actual string/decimal normalizers share strict primitive admission. Earlier boolean/object/array aliases reject, null falls through, genuine textual grade labels remain supported. Legacy behavior remains outside strict scope.
- P2 legacy routing: actual AllTools registry selects scoped execution only for supplied keys absent from the original schema. Original schema definitions take precedence in augmentation. Communications native argument combinations compare exactly against native execution; scoped/legacy mixtures reject unsupported filters.

## Exact correction files

Paths relative to repository root; pre-existing unrelated changes are preserved.

- android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/ScopedSchoolRead.kt
- android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/NetworkParsers.kt
- android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/RestClient.kt
- android/core/core-network/src/test/kotlin/dev/antigravity/classevivaexpressive/core/network/client/RestClientNetworkTest.kt
- android/core/core-data/build.gradle (test dependencies only)
- android/core/core-data/src/test/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/ScopedSchoolRestBoundaryTest.kt
- android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/ToolTracing.kt
- android/core/core-assistant/src/test/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/SchoolDataToolsTest.kt
- docs/aria-data-access.md
- docs/aria-S2-R2-corrections.md
- docs/validation-S2-R2/ (persistent execution evidence)

Embedded engine remains 3.0.5 at a055257fc4b559bee54b9ce114f265bcd237adfd, with existing generic patches preserved. No engine sync/edit, README/screenshots edit, other-repo mutation, commit, push or publication. No sensitive configuration, local.properties, credential/auth stores, keystore or user data inspected.

## Validation

All commands serialized from android/, JAVA_HOME=C:/Program Files/Android/Android Studio/jbr (Java21), --no-daemon --max-workers=1 --console=plain. Stable logs under docs/validation-S2-R2; no Temp-only evidence.

- tests-01.log: network tests passed; new REST/cache fixture failed because its mocked ApiSessionManager omitted canUseRest. Fixture corrected without weakening assertions.
- tests-02.log: network/data passed; assistant test compilation caught missing required pubId/evtCode in the new Communication fixture. Fixture corrected.
- tests-03.log: BUILD SUCCESSFUL in 33s, exit 0. All three test tasks explicitly forced with per-task --rerun; these are fresh executions on final correction sources. Archived JUnit XML and timestamp/count summary are under validation-S2-R2/junit and test-counts.txt.

| Module | Executed tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| core-network | 137 | 0 | 0 | 0 |
| core-data | 181 | 0 | 0 | 0 |
| core-assistant | 34 | 0 | 0 | 0 |
| Total for this scoped run | 352 | 0 | 0 | 0 |

Six new regression methods: four HTTP collection/alias/material/document tests, one real REST-to-cache boundary, one native tool combination test. Existing assistant provider-bound eight-row/one-call aggregate test and 6000-character recoverability test both executed successfully. No current full seven-module suite is claimed.

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat --no-daemon --max-workers=1 --console=plain :core:core-network:testDebugUnitTest --rerun :core:core-data:testDebugUnitTest --rerun :core:core-assistant:testDebugUnitTest --rerun
.\gradlew.bat --no-daemon --max-workers=1 --console=plain --continue :app:assembleDebug :app:lintDebug :core:core-network:lintDebug :core:core-data:lintDebug :core:core-assistant:lintDebug
```

Assembly and all four requested scoped lint tasks passed, build-lint.log: BUILD SUCCESSFUL in 3m 2s, exit 0. App 84, network 0, data 15 and assistant 6 warnings; all have zero Fatal/Error issues. Reports and parsed counts are archived under validation-S2-R2/lint and lint-summary.txt. Full engine-ui lint was not invoked; its six previously documented errors are not suppressed or claimed fixed.

APK: android/app/build/outputs/apk/debug/app-debug.apk, 46,582,793 bytes, SHA256 6735DE345E46A279597A6DD2569BFED38543A8CC2535CD0FFC1F83D70F99A7A9. No installation. Source SHA256 list and APK metadata are retained in validation-S2-R2/source-sha256.txt and apk-sha256.txt. Scoped git diff --check passed.

The prior 701 suite and subsequent assistant 33 batching run remain separate historical evidence. They are not summed into the current correction run. Full CV lint has six previously documented unchanged engine-ui errors and is outside this correction; no suppressions or baseline edits.

## Limits and runner handoff

Only fake HTTP (MockWebServer) and simulated model/provider unit boundaries. REST-to-repository regression populates a known nonempty account/year snapshot, verifies failed malformed/ambiguous refresh cannot replace it, then rereads the original cached ID. DAO is mocked, not an on-device SQLite/process-death test. Real APIs, providers, device/Binder/signing/account drift and accessibility remain unverified. No devices, live APIs, provider calls, profile/background or first-delivery work. Pending user device-gate decision remains pending.

Sole compiler/Gradle runner RELEASED after reading final command completion (exit 0). Owned exec sessions 23757, 57743, 99357 and 10502 have all exited. Post-run Win32_Process inventory returned no java.exe processes; no owned active process remains and no process termination was needed. Fluidify may acquire the runner only through explicit root transfer.
