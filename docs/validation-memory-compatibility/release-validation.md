# ClasseViva 7.17.0-beta.2: release validation

The beta APK is ready for ROOT's publication workflow. The source already implements
the required PampAI memory proof contract; this audit found no missing production wiring
and introduced no functional proof changes. Only `android/app/build.gradle` version metadata
was changed, to **7.17.0-beta.2 / 2026110801**. Existing scoped school-data, source-proof,
CIE, README, screenshots and embedded engine changes were preserved.

## Artifact

| Field | Verified value |
|---|---|
| Publication candidate | `android/build/release-beta-7.17.0-beta.2/classeviva-expressive-7.17.0-beta.2.apk` |
| Original Gradle output | `android/app/build/outputs/apk/release/app-release.apk` |
| SHA-256 of both copies | `90d26155cd6e83438d217b20e29251ba40f0158a0bb6ba7d5a9bdc91832acbb6` |
| Size | 34,606,010 bytes |
| Application ID | `dev.antigravity.classevivaexpressive` |
| Version / build code | `7.17.0-beta.2` / `2026110801` |
| Debuggable | false |
| Signature | one signer; APK Signature Scheme v2 verified |
| Signing certificate SHA-256 | `825738500ce9605429ffba3c4019228f992f2c37d1b70a2230a7ce2c25637e43` |

The public APK supplied by ROOT at
`C:/Users/casua/AppData/Local/PampAI-qa/stable-fixes/phone-cv.apk` was read only for
signature/version checks. It declares 7.16.1 / 2026110501 and verifies with the same
certificate. The candidate also matches ROOT's expected PampAI/Store certificate.
This establishes signing/version compatibility; no install or cross-app Binder call ran.

The actual compiled APK manifest exports
`dev.antigravity.classevivaexpressive.core.assistant.pampai.RegistroToolHostProvider`
at `dev.antigravity.classevivaexpressive.ai.tools`. Its discovery metadata is
`dev.antigravity.fluidengine.ai.TOOL_HOST`, integer type **0x10**, value **1**.
The provider has no obsolete manifest permission; its signature gate was traced in
the engine's final `call` implementation during the source audit.

All eleven checked DEX markers are present, including `source_capabilities`,
`source_lease`, `source_validate`, `source_read`, `aria.source-proof`, authorization
identity/capacity markers, and the registry, provider, CV adapter and `RegistroDatiTool`
class descriptors. `apk-validation.json` names the containing DEX files. Packaged
presence and source wiring do not establish device execution.

## Executed checks

ROOT transferred sole compiler ownership after Fluidify r5's release. Both invocations
used Android Studio JBR, `--no-daemon --no-watch-fs --max-workers=1 --console=plain`.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat --no-daemon --no-watch-fs --max-workers=1 --console=plain :core:core-assistant:testDebugUnitTest --rerun
.\gradlew.bat --no-daemon --no-watch-fs --max-workers=1 --console=plain --continue :app:assembleRelease :app:lintRelease :core:core-assistant:lintRelease
```

Both commands were run from `android/` and exited **0**. The first completed in 22 seconds;
the second completed in 5 minutes 16 seconds. The task-local `--rerun` forced fresh test
execution while reusing dependency compilation outputs (1 task executed, 155 up-to-date).
No temporary init hook or test-output deletion was needed.

| Check | Result |
|---|---|
| Fresh assistant tests | **62 passed**, 0 failures, 0 errors, 0 skipped |
| Included proof regressions | **28 passed**: 26 registry + 2 real CV adapter/registry |
| Included scoped school regressions | **9 passed**: 8 school tools + 1 provider boundary |
| Signed release assembly | passed |
| App release lint | 0 fatal/errors, **86 warnings** |
| Assistant release lint | 0 fatal/errors, **6 warnings** |
| APK signature / version / manifest / DEX | passed |
| Audited input hashes before and after build | **414/414 unchanged** |

The proof and school counts are subsets of 62, not additional suites. No other unit suites
were rerun in this packaging pass. The broader suites in `../validation-source-proof/`
and `../validation-S2-R2/` are historical evidence, attributed separately in the audit
handoff. No warning/error suppression or lint baseline changes were added. The existing
six full `engine-ui` lint errors were outside this task and its full lint report was not
rerun; successful app/assistant lint is not a claim that full-repository lint is clean.

Public APK checks used the installed Android SDK build-tools 36.0.0:

```powershell
& '<Android SDK>/build-tools/36.0.0/apksigner.bat' verify --verbose --print-certs '<candidate APK>'
& '<Android SDK>/build-tools/36.0.0/apksigner.bat' verify --verbose --print-certs '<public phone CV APK>'
& '<Android SDK>/build-tools/36.0.0/aapt.exe' dump badging '<candidate APK>'
& '<Android SDK>/build-tools/36.0.0/aapt.exe' dump badging '<public phone CV APK>'
& '<Android SDK>/build-tools/36.0.0/aapt.exe' dump xmltree '<candidate APK>' AndroidManifest.xml
```

## Source closure and release ownership

`source-freeze.json` has SHA-256
`b26c4bde70a49a8a72ff886e5604566c0ef44597e4582fa1ffa916e26a03ceb7` and captures the
414 audited production, regression, build and prebuild handoff inputs. Its compiler-status
field describes the prebuild freeze; `runner-release.json` records the completed handoff.
The initial source audit and unchanged API are in `../cv-memory-integration-handoff.md`.
The final report and generated verification files are intentionally outside that earlier
prebuild freeze.

Local and remote default branch HEAD were both
`7f0891ed18c295125528737c5aee03e3c76a0841` at source audit. No remote drift or stale CIE
source replacement was needed. Existing engine HEAD remains the previously pinned
3.0.5 / `a055257fc4b559bee54b9ce114f265bcd237adfd`; no engine source was changed by this audit.

Release notes are prepared at `../releases/7.17.0-beta.2.md`. They retain the published
CIE P2 offline-data-loss disclosure, real CieID NV and HTTP/HTTPS versus WebRTC UDP limit.
The current app store manifest still advertises stable 7.16.1 and beta.1; ROOT owns its
update and the final probe, source closure, commit/push, dry-run, publication and remote
artifact verification. No publication, commit or push was performed by this worker.

All owned Gradle sessions and APK verification commands are terminal. The runner was
explicitly released to ROOT and ROOT acknowledged reacquisition. The two remaining Java
Gradle/Kotlin processes observed at release began at 09:28 UTC, hours before these
invocations, and were preserved. No further CV compiler or verification process is planned.

## Runtime limits

Device installation, signed Binder discovery/calls, real authenticated school cache and
refresh reads, account/CIE transitions during reads, process death and the complete PampAI
memory flow remain **NV**. No school API, real account or device operation was used here.
The source proof remains a point-in-time local authorization check, with observed-state
revocation, exact scope and a short monotonic TTL. It does not detect unseen upstream
revocation or replace PampAI's independent atomic local revoke/forget fence.
