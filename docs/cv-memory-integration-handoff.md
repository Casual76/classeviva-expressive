# ClasseViva memory integration handoff

Source audit date: 2026-11-08. Release ownership: ROOT. No commit, push, publication,
device operation, live school request or account selection is part of this handoff.

The source already implements the PampAI source proof contract. The missing delivery
step is a new signed APK containing the existing scoped school-data and proof changes.
The installed 7.16.1 APK is older than these changes; its missing proof strings do not
show a missing implementation in the current checkout.

## Source and release baseline

- Local HEAD and the remote default branch `main` both resolve to
  `7f0891ed18c295125528737c5aee03e3c76a0841` (`git ls-remote --symref origin HEAD`
  and `git ls-remote origin refs/heads/main`, checked during this audit).
- Before this packaging change, `android/app/build.gradle` declared
  `7.17.0-beta.1` / `2026110502`; it now declares the accepted beta.2 version below.
- The app manifest advertises stable 7.16.1 and CIE beta 7.17.0-beta.1. The remote
  beta tag resolves to `0379a19dfce4faf29293e74089525d576e297275`.
- The current checkout includes the published CIE implementation. Retain it and all
  existing uncommitted scoped school-data/proof work. No reset, stash, rebase, checkout
  replacement or engine synchronization is needed.
- ROOT accepted the compatible update **7.17.0-beta.2 / 2026110801**, same application ID and
  existing release signing configuration. This exceeds both known installed stable
  and published beta build codes. Only the local build version is changed; ROOT publishes
  the release and updates the store manifest.
- Keep the existing CIE P2/offline-data-loss disclosure and real CieID runtime NV.
  See `cie-implementation-status.md` and `releases/7.17.0-beta.1.md`. Memory packaging
  does not close that CIE issue or justify promoting this CIE beta to stable.

## Production wiring and unchanged API

The authoritative API is `PampAI/docs/aria-source-proof-contract.md`. No API changes
were needed in this audit.

`RegistroToolHostProvider` wraps the Hilt `AllTools.registry()` with
`registroSourceProofRegistry`, reads the existing singleton `SessionStore` publication,
and sets `sourceProofCaller` only from `RemoteCall.callingPackage`. The merged module
manifest exports `${applicationId}.ai.tools` with integer `TOOL_HOST = 1`. The engine
provider's final `call` checks Binder caller signatures before every dispatch, including
negotiation and catalog. Original action confirmation and operation identity gates remain.

The external registry adds `source_capabilities`, `source_lease`, `source_validate`, and
`source_read`. Proof v1 travels in `ToolResultDetails.data` only through negotiated typed
bridge v2; the global bridge protocol remains unchanged. Only `registro_dati` is allowed.
Legacy direct tools remain callable and do not provide a source authorization proof.

Capabilities and leases bind signed caller, process incarnation, account/student,
explicit school year/section, exact logical arguments and limits. Continuation controls
do not broaden the original scope. There is no automatic account/student/year selection
in proof admission. The proof wrapper checks state before and after data production and
checks returned account/student/year/section against the lease. Uncertain/error results
do not receive proof. Source state includes `dataEpoch`, session identity/auth method and
reference identity of the already-published atomic `AuthSnapshotLease`, with READY admission.
No credential field or credential-derived hash is used.

The response-only random authorization UUID is stable per caller for unchanged source
state across handle expiry/renewal. State/publication/process changes revoke old handles
and rotate identity. The 60-second lifetime uses a monotonic clock; lease validation never
renews it. Capacity is bounded and new caller authorization fails closed at saturation.

`AssistantContextFactory` supplies the injected `SchoolDataAccess`, bound to
`ScopedSchoolDataAccess`. Its source reads and commits use account work leases, dedicated
account/student/year/section cache keys and strict REST validation. Historical reads do
not change the selected UI year; yearless endpoints cannot fabricate historical refresh.
The existing page/detail hash and cursor restrictions, full-match per-page aggregates,
bounded output and unavailable/unknown coverage remain intact.

## Evidence and validation ownership

The proof implementations still byte-match `validation-source-proof/source-artifact-manifest.json`:

| File under `android/core/core-assistant/` | SHA-256 |
|---|---|
| `src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/pampai/SourceProofRegistry.kt` | `766fe30ad5939f4cfeead9ba783fc040b193ad6086689e2821eedb8c1237c72a` |
| `src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/pampai/RegistroSourceProof.kt` | `26b5a7bfb5c22005b363ba62fe74b3ae09bec9f49f14bf33dacbc9eba0965d53` |
| `src/main/kotlin/dev/antigravity/classevivaexpressive/core/assistant/pampai/RegistroToolHostProvider.kt` | `d7dc69ae1e75ef5db5f1990d08cff2368a5dda53874c5f7d17650da4f7523290` |
| `src/main/AndroidManifest.xml` | `58557f179e258b5b3d6c6e99e7c0182ba39ec31675e19646608d95e01468333e` |

The archived JUnit XML was parsed again: 26 `SourceProofRegistryTest` and 2
`RegistroSourceProofTest` cases, zero failures/errors/skips. This is inspection of an
earlier run, not a fresh test execution. The archived debug APK also still matches its
manifest hash. Its debug signing is not a substitute for the required release-signed APK.

`validation-memory-compatibility/source-freeze.json` records the audited production,
regression and build inputs. The initial source audit launched no Gradle/Java/compiler
process. Any new validation requires ROOT's explicit transfer of the sole compiler runner.
All invocations must finish and runner ownership return to ROOT before its PampAI build.

After transfer, from `android/`, using Android Studio JBR and existing signing settings:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat --no-daemon --no-watch-fs --max-workers=1 --console=plain :core:core-assistant:testDebugUnitTest :core:core-domain:testDebugUnitTest :core:core-data:testDebugUnitTest :core:core-datastore:testDebugUnitTest :core:core-network:testDebugUnitTest :engine-ai-bridge:testDebugUnitTest
.\gradlew.bat --no-daemon --no-watch-fs --max-workers=1 --console=plain :app:assembleRelease :app:lintRelease :core:core-assistant:lintRelease
```

Force the actual test tasks to execute when fresh execution is required; do not present
UP-TO-DATE task results as newly executed tests. Do not read signing secrets. Verify the
produced APK with `apksigner verify --print-certs` and `aapt dump badging`, compare its
public signing certificate with the published stable/beta APK, and verify packaged DEX
contains all four proof tools and `aria.source-proof`. Record exact APK SHA-256, package,
version/code, debuggable status, signer and manifest metadata type. ROOT owns publisher
probe/dry-run/commit/push/publication and remote verification.

## Acceptance limits

Signed cross-app Binder discovery, real caller signature handling, authenticated cached
and refreshed school reads, account/CIE changes during reads, process death and the full
PampAI memory flow remain **NV** until separately exercised on authorized targets. No live
server revocation guarantee, distributed lock or complete upstream school archive claim
follows from a source-local lease. PampAI must still revalidate before committing or using
evidence and enforce its independent atomic local forget/revoke fence.
