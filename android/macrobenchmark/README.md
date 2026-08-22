# Motion macrobenchmarks

This module measures the Hyper Premium motion warm paths with AndroidX Macrobenchmark 1.4.1:

- Home scrolling;
- top-level tab switching;
- grade row to detail and back;
- Lessons weekly timeline;
- Communications section scrolling;
- navigation at animator scale `0x` (functional smoke test).

The target is the non-debuggable `benchmark` app variant. It uses release signing when the local
keystore is configured, preserving the authenticated installation on physical QA devices, and
falls back to debug signing only in keystore-less environments. Every measured iteration emits
`FrameTimingMetric` percentiles and a Perfetto trace. Run on an authenticated physical device at
60 Hz and 120 Hz; emulator measurements are not release evidence.

## Personal-device runner

Do **not** use `connectedBenchmarkAndroidTest` after logging in on a personal phone: Gradle's test
deployment can replace/reinstall the target outside the controlled flow. Build the two APKs without
a device, then use the repository runner. It only uses `adb install -r`, never uninstall or
`pm clear`, and invokes instrumentation directly with benchmark compilation management disabled.

```powershell
cd android
.\gradlew.bat --no-daemon :app:assembleBenchmark :macrobenchmark:assembleBenchmark
cd ..
.\scripts\run-motion-device-qa.ps1 -Serial RFCY61FZDHW
```

By default the runner collects separate 60 Hz and 120 Hz runs at animator scale 1x, then executes
the 0x functional smoke. A broader animator matrix can be requested explicitly:

```powershell
.\scripts\run-motion-device-qa.ps1 `
  -Serial RFCY61FZDHW `
  -RefreshRates 60,120 `
  -AnimatorScales 0,0.5,1,2
```

Results are written under `artifacts/motion-device-qa/<timestamp>/`, with one directory per refresh
rate/animator combination, instrumentation output, JSON/Perfetto artifacts, display snapshots and
package metadata. Where SurfaceFlinger exposes a usable frame period, the runner records the
observed rate and rejects a 60/120 Hz mismatch instead of mislabelling the results. The script
requires an already installed target and detects authenticated Home through the accessibility
hierarchy **before** the first APK replacement. Its ADB wrapper rejects `uninstall`, `pm clear` and
package-uninstall commands at runtime. It then verifies the
instrumentation package, compares the target package's `firstInstallTime` before/after, and
restores the exact original refresh-rate and animator settings in a
`finally` block. If restoration fails, the command fails loudly with every setting that needs
manual attention.

Release gates are evaluated from the generated JSON and Perfetto traces:

- janky frames at or below 5%;
- 60 Hz: p95 frame duration at or below 16.7 ms;
- 120 Hz: p90 at or below 8.33 ms and p95 at or below 12.5 ms;
- no slow bitmap upload slices;
- no readable double-page frame during navigation.

The connected device must retain a valid authenticated session and contain at least one numeric
grade. Tests fail fast when those prerequisites are missing instead of reporting misleading data.
