# Source2 S1/R0 corrections R1/3

2026-11-07. App-local corrections in classeviva-expressive only. No commit, push, publication, device/emulator or live provider/source calls. README, screenshots and unrelated edits preserved. No sensitive configuration, credential files, keystores, persisted auth stores or user data opened. Engine base remains 3.0.5 / a055257fc4b559bee54b9ce114f265bcd237adfd; this correction does not edit engine files or generic contracts. Existing selective engine patches from root remain in place.

## Findings and behavioral evidence

- F1: Strict reads validate per-section semantic fields before legacy normalizers, reject every malformed consumed sibling/error envelope, and check material teacher/folder/content nesting plus documents/reports. Explicit empty arrays are valid. MockWebServer regressions use the real REST client for every section, impossible dates, agenda:[{}], malformed siblings, nested materials and combined document collections. Valid material/document fixtures pass. A repository regression verifies refresh failure performs no cache upsert and retains readable cached data. Legacy agenda normalization remains permissive outside strict context.
- F2: Model-facing page size is one (limite remains an upper bound). All count/numericCount/weightedAverage values appear in prose. JSON records are complete or recoverable in 1,500-character chunks using dettaglio_offset/dettaglio_hash, same query/input cursor, and aggiorna=false. A changed record hash rejects continuation. nextCursor is withheld until the final chunk. A genuine AiOrchestrator + simulated ChatProvider test inspects actual provider-bound Message.ToolResult objects across eight pages, proving records six through eight and weightedAverage are accessible. A 6,000-character record is reconstructed exactly across bounded outputs without early cursor advancement. No generic engine changes required.
- F3: The dispatch gate covers every newly advertised argument absent from each original schema, plus the original explicit scoped switches. A real AllTools.registry regression tests every added argument alone for all nine affected tools, without injecting anno/limite to trigger routing. Native-only argument calls retain the legacy path. Unsupported mixed arguments reject explicitly.
- F4: Query admission rejects date ranges before, after or overlapping the acquired school year, for cache and refresh calls, before any cache/network access; explicit empty archives cannot certify an out-of-year request. In-year empty refresh remains covered by the existing repository test.
- I1 confirmed and fixed: The original data generation is carried through action/executor/repository commit and checked before completion/readback claims. A deterministic suspended operation changes password to CIE alias before repository admission; the authentication epoch stays unchanged, the data lease changes, and no DAO upsert occurs. A real tool/registry test verifies an action with preserved auth epoch and revoked data generation cannot claim completion. Method-only changes now revoke data leases too. Existing CIE, deduplication/conflict and readback tests remain passing.

## Validation

All Gradle commands run serially from android/, with --no-daemon --max-workers=1 --console=plain. No unrelated process or daemon termination. One module-local :core:core-assistant:clean was used after incremental compilation reported the existing findCommunication helper unresolved; a clean module compilation passed without source workarounds.

Final unit suite (tests-04.log): BUILD SUCCESSFUL in 52s. Counts from the seven modules' JUnit XML, one final suite only; do not add earlier runs:

| Module | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| core-domain | 53 | 0 | 0 | 0 |
| core-network | 133 | 0 | 0 | 0 |
| core-data | 180 | 0 | 0 | 0 |
| core-datastore | 33 | 0 | 0 | 0 |
| core-assistant | 33 | 0 | 0 | 0 |
| engine-ai | 244 | 0 | 0 | 0 |
| engine-ai-bridge | 25 | 0 | 0 | 0 |
| Total | 701 | 0 | 0 | 0 |

Ten app-local regression methods added. Engine counts include root's existing selective bridge/evidence changes; earlier 687 and selected 298 results predate this final suite. Initial tests-01.log failed the new CIE fixture validation (fixture corrected to include coherent CIE fields); tests-02.log failed incremental compilation of existing helper references; tests-03.log passed, then tests-04.log validated the latest semantic checks and valid nested fixtures. No assertions weakened.

Unit command: :core:core-domain:testDebugUnitTest :core:core-network:testDebugUnitTest :core:core-data:testDebugUnitTest :core:core-datastore:testDebugUnitTest :core:core-assistant:testDebugUnitTest :engine-ai:testDebugUnitTest :engine-ai-bridge:testDebugUnitTest.

Assembly/lint command (with --continue): :app:assembleDebug :app:lintDebug :core:core-domain:lintDebug :core:core-network:lintDebug :core:core-data:lintDebug :core:core-datastore:lintDebug :core:core-assistant:lintDebug :engine-ai:lintDebug :engine-ai-bridge:lintDebug.

Assembly and all eight requested scoped lint tasks passed (build-lint.log, BUILD SUCCESSFUL in 4m 2s). APK: android/app/build/outputs/apk/debug/app-debug.apk, 46,582,773 bytes. Scoped reports have zero Fatal/Error issues. Warning counts:

```text
app: {'Fatal': 0, 'Error': 0, 'Warning': 84, 'Information': 0}
core/core-domain: {'Fatal': 0, 'Error': 0, 'Warning': 1, 'Information': 0}
core/core-network: {'Fatal': 0, 'Error': 0, 'Warning': 0, 'Information': 0}
core/core-data: {'Fatal': 0, 'Error': 0, 'Warning': 15, 'Information': 0}
core/core-datastore: {'Fatal': 0, 'Error': 0, 'Warning': 1, 'Information': 0}
core/core-assistant: {'Fatal': 0, 'Error': 0, 'Warning': 6, 'Information': 0}
engine/engine-ai: {'Fatal': 0, 'Error': 0, 'Warning': 1, 'Information': 0}
engine/engine-ai-bridge: {'Fatal': 0, 'Error': 0, 'Warning': 2, 'Information': 0}
```

Logs: C:/Users/casua/AppData/Local/Temp/aria-classeviva-S1-R1/ (tests-01.log through tests-04.log, test-counts.txt, build-lint.log). JUnit XML/HTML and lint reports remain under each named module's build directory.

The full lint task is not rerun in this round: six existing unchanged engine-ui errors remain documented in aria-data-access.md (five RestrictedApi, one RememberReturnType). No suppression or baseline edits.

## Exact files changed by this correction

- `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/ScopedSchoolRead.kt`
- `android/core/core-network/src/test/kotlin/dev/antigravity/classevivaexpressive/core/network/client/RestClientNetworkTest.kt`
- `android/core/core-domain/src/main/kotlin/dev/antigravity/classevivaexpressive/core/domain/model/SchoolDataAccess.kt`
- `android/core/core-domain/src/main/kotlin/dev/antigravity/classevivaexpressive/core/domain/model/DomainModels.kt`
- `android/core/core-datastore/src/main/kotlin/dev/antigravity/classevivaexpressive/core/datastore/AccountSessionGuard.kt`
- `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/Repositories.kt`
- `android/core/core-data/src/test/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/ScopedSchoolDataAccessTest.kt`
- `android/core/core-data/src/test/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/ScopedCustomEventTest.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/actions/AssistantActions.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/runtime/AssistantActionExecutor.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/AppTools.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/SchoolDataTools.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/ToolTracing.kt`
- `android/core/core-assistant/src/test/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/SchoolDataToolsTest.kt`
- `android/core/core-assistant/src/test/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/SchoolDataProviderTest.kt`
- `docs/aria-data-access.md`
- `docs/aria-S1-corrections.md`

## Runtime limits and handoff

No real school responses, signed Binder v1/v2, provider runtime, device/emulator, SQLite/process-death or cross-app integration executed. HTTP tests use MockWebServer; provider tests use a simulated ChatProvider receiving real orchestrator requests; event persistence uses the existing in-memory DAO fixture. Historical endpoint restrictions, first explicit scoped refresh, account isolation and additive storage/history behavior remain as documented. Profile/background and first-delivery work are outside this correction.

Sole Gradle runner RELEASED after successful assembly/scoped lint. All owned Gradle commands have exited; no owned active processes remain. Fluidify may receive the runner through root transfer.


Root integration refinement after the 701-test run: registro_dati now honors limite (default20) instead of forcing one record per call. The existing 1500-character hash-checked chunks apply to the complete requested page; nextCursor remains withheld until every chunk is accessible. Short records can share one model tool call. Provider-bound regression now asserts all eight compact records and aggregate in one call. This narrow source/test change is NOT covered by the preceding701 result; scoped rerun/build waits for Fluidify to release the sole runner.

Root batching refinement validation: assistant33 unit tests pass, core-assistant lint and app assembleDebug pass. BUILD SUCCESSFUL4m50s; log C:/Users/casua/AppData/Local/Temp/aria-cv-batching-final.log. This fresh scoped run covers the batching/wording change; the other668 tests remain the earlier unchanged-module run, not newly rerun or added. No credentials/configuration inspected, no runtime calls. Runner transferred internally to PampAI root validation.
