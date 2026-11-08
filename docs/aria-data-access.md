# Aria school data access

Implementation source delivery and serialized build/unit-test validation, 2026-11-07. No device/provider execution.

## Public APIs and behavior

- `core-domain/SchoolDataAccess.read(SchoolDataQuery): SchoolDataPage`: immutable explicit account/student/year scope, exact subject/period, ISO date filters, text search, stable date-descending/ID ordering, snapshot-bound cursors, full matching-record aggregates. Limit 1..100, hard source bound 20,000 rows. Cursor replays are deterministic; changed query, account, year, snapshot or contents rejects the cursor. `SchoolDataUnavailable.code` distinguishes missing authentication, unavailable scope, missing cache, refresh failure and invalid cache.
- `core-data/ScopedSchoolDataAccess`: a single section per explicit technical refresh. Authenticated username and student must match. Account leases fence network operations, cache commits and results. Dedicated Room snapshot keys include account, student, year and section; legacy student-only caches are deliberately not relabeled as account-attributed evidence. Refresh never calls SchoolYearStore, selection APIs, activities or the coordinator's global refresh/fallback.
- `core-assistant/registro_dati`: all eight sections (grades, agenda, lessons, communications, absences, notes, materials, documents). Typed records preserve source IDs, with aggregates, coverage, sync time, retrieval mode and actual nextCursor; model-facing delivery uses one record per page, includes all aggregate values, and exposes long JSON records in recoverable 1,500-character fragments using dettaglio_offset/dettaglio_hash. The record cursor is withheld until its final fragment. limite is an upper bound; model delivery currently caps it at one. Known source emptiness differs from unavailable data. Pagination counts available matching records; it does not assert source archive completeness.
- Existing primary list tools and `voti_media` accept additive scoped arguments (`anno`, `account`, `studente`, `cursore`, `limite`, `aggiorna`). Supplying any advertised argument absent from that tool's original schema uses the scoped path; shared keys such as communications limite retain native semantics in original-only combinations; original-only filters retain legacy semantics. Unsupported legacy-only filters are rejected instead of silently discarded. Calls with only legacy arguments preserve their existing semantics and carry UNCERTAIN legacy evidence; use `registro_dati` for trustworthy coverage and pagination.
- `withStrictSchoolRead`: opt-in response-shape validation on the REST paths used by scoped retrieval. HTTP/error/malformed objects cannot become known empty lists. Legacy parsing stays compatible; strict attendance reads do not convert a second 404 into an empty result.
- `AgendaRepository.addScopedCustomEvent(event, expectedAuthenticationEpoch, expectedDataEpoch)`: concrete repository fences the write with the original authentication and data generations, including password/CIE alias or method changes that intentionally preserve the authentication epoch. `agenda_aggiungi_evento` derives event identity from authenticated account/student plus bridge operationId. The Room upsert checks matching content under the account gate; repeated identical IDs reconcile, conflicting content fails. Confirmation gates remain unchanged. Successful creation is COMPLETED only after matching readback, otherwise UNCERTAIN; cancelled/unavailable actions are ERROR. No automatic exactly-once claim, no school server event creation.

## Explicit limitations

REST grades/materials/documents/notes/communications endpoints have no historical-year parameter. Historical refresh for these sections returns historical_refresh_unavailable. A historical dedicated snapshot can be read if previously captured for that account/year. Date-ranged agenda/lessons/attendance support historical refresh without changing selected year. This adds no impersonation or arbitrary-student access.

Non-date-ranged endpoint coverage remains unknown even after refresh. Cache auth validity is unknown; only a successful explicit refresh provides request-level validity. Five-minute freshness is a local staleness policy, not evidence of live authentication. New dedicated cache starts empty; explicit refresh is required before first retrieval. Existing UI snapshots are not imported because they cannot prove account provenance or full sync coverage. The source REST endpoints return collections in one response; client response paging is finite and does not invent remote cursors. The 20,000-row bound reports an error rather than silently truncating.

No schema migration: reuse SnapshotCacheDao for scoped data and CustomEventDao for durable local identity. Remote writes other than custom events retain existing confirmation/effect behavior and are marked UNCERTAIN when evidence is unavailable; they do not gain deduplication guarantees. A deleted custom event retried under the same operation ID can be recreated; this is entity-ID reconciliation, not a permanent operation tombstone ledger.

## Engine additive sync

Embedded HEAD remains a055257fc4b559bee54b9ce114f265bcd237adfd (3.0.5). Before edits it was clean. For existing selected files, embedded contents were compared against PampAI engine HEAD baseline; no divergences in the selected contracts/bridge files. Copied only:

- engine-ai tools: Tools.kt, ToolResults.kt, ToolArgumentValidator.kt; test ToolContractsTest.kt.
- engine-ai-bridge: BridgeProtocol.kt, AiToolClient.kt, AiToolHostProvider.kt, RemoteTools.kt, TypedResultCodec.kt; tests RemoteToolTest.kt and TypedProtocolTest.kt.

No model/provider/orchestrator files, version files, upstream repo changes, commits or publication. Public definitions and protocol bytes match canonical PampAI files at copy time. No-negotiation remains v1; typed v2 requires explicit capabilities. TracedTool now forwards the three-argument execution hook, preserving the bridge operation identity. Signature and confirmation checks remain in the canonical host.

## Root verification, serialized from android/

```powershell
.\gradlew.bat --no-daemon --max-workers=1 --console=plain :core:core-domain:testDebugUnitTest :core:core-network:testDebugUnitTest :core:core-data:testDebugUnitTest :core:core-assistant:testDebugUnitTest :engine-ai:testDebugUnitTest :engine-ai-bridge:testDebugUnitTest
.\gradlew.bat --no-daemon --max-workers=1 --console=plain :app:assembleDebug lintDebug
```

New tests cover stable/repeated/expired cursors, filters/aggregate/page boundaries and empty results; historical explicit date scope, absent/error/auth/account isolation, stale metadata, logout during request/no commit; strict source shape validation; malformed tools/no dispatch and event identity/readback including TracedTool execution forwarding. Existing bridge tests cover negotiated/legacy compatibility. Root device integration and independent review remain required.

## Validation corrections

- `AccountSessionGuard` now includes authenticated username when advancing account epochs; a username switch within the same authentication method also advances the authentication epoch. Previously switching accounts for the same student left pending cache commits and event writes admitted. Password/CIE reauthentication for the same student preserves the existing browser authentication epoch guarantee while invalidating cache leases when the login alias changes. Tests reject old-account effects and verify separate account/year cache keys without importing legacy snapshots.
- Strict notes reads accept only arrays of objects under known NTTE/NTCL/NTWN/NTST category keys. Arbitrary error arrays and partially malformed category responses cannot be certified as an empty archive. Legacy reads remain opt-in and retain their parser behavior.
- If a custom-event write is accepted but readback throws, its typed result remains UNCERTAIN with the operation ID; cancellation still propagates. Tests also cover readback unavailable and durable repository reconciliation using a persisted DAO fixture across repository recreation, conflicting retries, revoked epochs and editable legacy events.
- Cursors reject changed contents even when the snapshot timestamp stays unchanged.

These corrections are app-local. No generic engine contract corrections or canonical propagation patch were needed; embedded engine HEAD remains a055257fc4b559bee54b9ce114f265bcd237adfd (3.0.5).

## Actual unit-test validation (2026-11-07)

All runs serialized from `android/`, using `--no-daemon --max-workers=1 --console=plain`. The six requested modules passed initially (644 tests). After the corrections and additional regressions, the final suite also included `:core:core-datastore:testDebugUnitTest`:

| Module | Tests | Failures/errors/skipped |
| --- | ---: | --- |
| core-domain | 53 | 0/0/0 |
| core-network | 130 | 0/0/0 |
| core-data | 177 | 0/0/0 |
| core-assistant | 29 | 0/0/0 |
| engine-ai | 242 | 0/0/0 |
| engine-ai-bridge | 23 | 0/0/0 |
| core-datastore | 33 | 0/0/0 |
| **Total** | **687** | **0/0/0** |

Final suite succeeded in 50s. Ten additional test methods were added during validation, plus readback-error assertions in the existing event test. An intermediate run had one failing existing CIE epoch assertion; corrected the implementation to preserve that invariant, without weakening assertions. No cache cleanup, compiler workaround or daemon shutdown was necessary.

Logs: `C:/Users/casua/AppData/Local/Temp/aria-classeviva-validation/tests-01.log` (initial success), `tests-02.log` (intermediate CIE failure), `tests-03.log` (final success), and `test-counts.txt` (counts extracted from JUnit XML). Original XML/HTML results remain in each module's `build/test-results/testDebugUnitTest` and `build/reports/tests/testDebugUnitTest`.

## Actual assembly and lint validation (2026-11-07)

Final command: `.\gradlew.bat --no-daemon --max-workers=1 --console=plain --continue :app:assembleDebug lintDebug`.

- `:app:assembleDebug` passed on the corrected sources. APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 45,702,691 bytes. No install or device/provider calls.
- `:app:lintDebug`, all six requested modules' lint tasks, and `:core:core-datastore:lintDebug` passed with zero errors. App report has 84 warnings; scoped modules have warnings but no errors.
- Overall command exits 1 because `:engine-ui:lintDebug` reports **6 existing errors** in unchanged engine 3.0.5 sources: RestrictedApi in FluidNavHost.kt at 136/175/185/213 and FluidPageNavigator.kt at 29, plus RememberReturnType in FluidTextReveal.kt at 82. Its report also has 17 warnings and 18 hints. All remaining modules completed under `--continue` with zero errors. No suppressions/baseline changes or engine UI patches were made.
- First assembly/lint log: `C:/Users/casua/AppData/Local/Temp/aria-classeviva-validation/build-lint-01.log` (assembly passed, lint stopped at engine-ui; 6m5s). Final log: `build-lint-02.log` (assembly passed, all lint tasks collected; 3m16s). Parsed module/error list: `lint-summary.txt` in the same directory.
- Full offending report: `android/engine/engine-ui/build/reports/lint-results-debug.html` (also .xml/.txt). App report: `android/app/build/reports/lint-results-debug.html`. No further Gradle runners remain active from this validation; ownership is released.

Remaining limitations: historical refresh is still unavailable for endpoints lacking an explicit year; dedicated cache requires an explicit first refresh; source completeness is unknown where documented above. Persisted-event tests use a DAO fixture across repository recreation, not an Android/SQLite process-death test. Live source responses, signed cross-app device execution and independent review remain unverified. Profile/background remain unimplemented. No commits, pushes, publication or unrelated README/docs/screenshots edits.

## Exact app file manifest

- `android/core/core-assistant/build.gradle`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/actions/AssistantActions.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/runtime/AssistantActionExecutor.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/runtime/AssistantContextFactory.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/AllTools.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/AppTools.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/AssistantToolContext.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/SchoolDataTools.kt`
- `android/core/core-assistant/src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/ToolTracing.kt`
- `android/core/core-assistant/src/test/kotlin/dev/antigravity/classevivaexpressive/core/assistant/tools/SchoolDataToolsTest.kt`
- `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/Repositories.kt`
- `android/core/core-data/src/main/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/ScopedSchoolDataAccess.kt`
- `android/core/core-data/src/test/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/ScopedSchoolDataAccessTest.kt`
- `android/core/core-data/src/test/kotlin/dev/antigravity/classevivaexpressive/core/data/repository/ScopedCustomEventTest.kt`
- `android/core/core-database/src/main/kotlin/dev/antigravity/classevivaexpressive/core/database/database/SchoolDatabase.kt`
- `android/core/core-datastore/src/main/kotlin/dev/antigravity/classevivaexpressive/core/datastore/AccountSessionGuard.kt`
- `android/core/core-domain/src/main/kotlin/dev/antigravity/classevivaexpressive/core/domain/model/DomainModels.kt`
- `android/core/core-domain/src/main/kotlin/dev/antigravity/classevivaexpressive/core/domain/model/SchoolDataAccess.kt`
- `android/core/core-domain/src/test/kotlin/dev/antigravity/classevivaexpressive/core/domain/model/SchoolDataPagingTest.kt`
- `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/RestClient.kt`
- `android/core/core-network/src/main/kotlin/dev/antigravity/classevivaexpressive/core/network/client/ScopedSchoolRead.kt`
- `android/core/core-network/src/test/kotlin/dev/antigravity/classevivaexpressive/core/network/client/ScopedSchoolReadTest.kt`
- `docs/aria-data-access.md`


## Source2 S1/R0 correction R1/3

Strict response validation now checks mandatory source identity, date and content fields per section before permissive normalization. Every consumed sibling branch is validated, including material teachers/folders/contents and document/report collections. Known-key error envelopes, boolean content, missing fields and impossible dates fail the refresh without a cache replacement. Explicit empty arrays remain valid; legacy callers retain permissive behavior.

All date filters must fall inside the requested school year (September 1 through August 31); before/after/overlapping ranges fail with range_outside_school_year even when the acquired archive is empty. Coverage cannot certify dates outside the fetched year.

Newly advertised filters are dispatched when used alone through the real AllTools registry. Existing explicit scoped switches still dispatch as before. Unsupported arguments are rejected. Long-record detail continuation uses the same filters and input cursor, aggiorna=false, and the returned offset/hash; a changed record fails continuation. Reset dettaglio_offset to zero before advancing to nextCursor. Full aggregate count/numericCount/weightedAverage values are in model-facing text on every page. This is an app-local adaptation to the existing 4,000-character engine budget; no generic engine API changed.

Original custom-event data generation travels from tool admission through AssistantActionExecutor into the checked repository commit, and is checked again before returning a completed claim. Method-only transitions also revoke data leases. Authentication epoch behavior for CIE remains intact; durable ID reconciliation and content readback are retained.

Exact correction manifest, final validation counts/logs and runtime limits: [aria-S1-corrections.md](aria-S1-corrections.md). Earlier validation tables above describe the prior source delivery and must not be added to this correction run.


Root integration refinement after the 701-test run: registro_dati now honors limite (default20) instead of forcing one record per call. The existing 1500-character hash-checked chunks apply to the complete requested page; nextCursor remains withheld until every chunk is accessible. Short records can share one model tool call. Provider-bound regression now asserts all eight compact records and aggregate in one call. This narrow source/test change is NOT covered by the preceding701 result; scoped rerun/build waits for Fluidify to release the sole runner.

Root batching refinement validation: assistant33 unit tests pass, core-assistant lint and app assembleDebug pass. BUILD SUCCESSFUL4m50s; log C:/Users/casua/AppData/Local/Temp/aria-cv-batching-final.log. This fresh scoped run covers the batching/wording change; the other668 tests remain the earlier unchanged-module run, not newly rerun or added. No credentials/configuration inspected, no runtime calls. Runner transferred internally to PampAI root validation.


## Source2 S2/R1 correction R2/3

Strict REST reads now normalize the payload returned by `validatedSchoolPayload`, sharing collection admission and extraction. Direct/nested `data` and a single nonempty recognized alternative are preserved even beside empty arrays. Every recognized branch is validated before selection; two nonempty alternative branches reject with `source_payload_invalid`, including duplicate-looking rows, because no union/dedup identity contract is assumed. All empty recognized alternatives remain valid. Error envelopes and unknown-only envelopes reject; unrecognized metadata siblings are not collections. Existing documents plus schoolReports combination retains document kinds; mixing those with a nonempty alternative envelope is rejected. Materials use the same selection rule for teacher envelopes and folder contents; direct teacher/outer-envelope mixtures are rejected.

Under `withStrictSchoolRead`, the actual string and decimal normalizers share primitive admission with required-field checks. JSON null/missing and blank strings retain existing fallback rules. Objects, arrays, booleans and nonfinite numeric primitives encountered before the selected alias reject instead of being skipped or converted to text. Required dates/content also retain semantic checks. Nonnumeric grade labels remain valid text. Legacy callers outside this scope retain their prior parsing.

Tool routing compares supplied keys against each original schema. Original-only combinations (including communications testo+limite, dates and unread filter) retain native filtering and limits. Newly added scoped fields route to registro_dati; mixed legacy-only filters reject explicitly. Original property definitions win when augmenting the advertised schema. Existing scope/year/account fences, default20 batching, aggregate visibility, 1500-character fragments and cursor withholding are unchanged.

Current correction manifest, persistent logs, fresh executed counts and build/lint status are recorded in [aria-S2-R2-corrections.md](aria-S2-R2-corrections.md). The earlier701 suite and later assistant33 batching run are historical separate evidence, not a current combined total. The old batching Temp log is missing; this correction does not recreate it.
