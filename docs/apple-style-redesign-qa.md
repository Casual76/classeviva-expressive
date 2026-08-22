# Apple-style redesign QA

This checklist is the release gate for the current visual and motion refinement. A successful build
alone is not sufficient: every checked item must have automated or device evidence.

## Motion and navigation

- [x] Top-level switch left-to-right: no readable double exposure; selection indicator follows.
- [x] Top-level switch right-to-left: no readable double exposure; selection indicator follows.
- [x] Push to a detail: destination remains opaque for the full transition.
- [x] Predictive back at 25%, 50%, and 75%: pages remain spatially separated, never alpha-blended.
- [x] Cancelled predictive back settles without a jump, flash, or stale glass frame.
- [x] Completed predictive back restores the parent and its scroll state.
- [x] Bottom pill hides on forward scroll and returns on reverse scroll or at the top.
- [x] Reselecting the active tab reveals the pill and scrolls the page to the top.
- [x] Animator scale 0x, 0.5x, 1x, and 2x remains usable.

## Chrome and scrolling

- [x] At rest, the large title is sharp and there is no compact-title flash on first layout.
- [x] The top material starts after a visible dead zone and reaches full strength gradually.
- [x] Fast scroll at 60/120 Hz has no noisy trail, stale snapshot, or duplicated text.
- [x] The compact title appears only after the large title is out of its way.
- [x] Pull-to-refresh and top chrome do not fight for vertical motion.
- [x] The last list item clears the pill in portrait and landscape.

## Colour and personality

- [x] Brand, Dynamic, Blu, Arancio, Indaco, and Verde visibly change the UI beyond the tab bar.
- [x] Dynamic Color works from a single user action on Android 12+.
- [x] Forced Light, Dark, and AMOLED do not inherit the system theme by mistake.
- [x] Text/container role pairs meet readable contrast in every palette.
- [x] Home, Voti, Agenda, Bacheca, Altro, and Orario have identifiable hierarchy and accents.
- [x] Colour never turns success/warning/error semantics into decoration-only ambiguity.

## School year and in-app notices

- [x] June-August offers the newest useful pair only: upcoming and current school years.
- [x] September-May offers current and previous only.
- [x] An unavailable year falls back exactly once and never walks two years into the archive.
- [x] Notification and maintenance workers never change the user's visible selection.
- [x] Manual selection refreshes authoritative content.
- [x] Automatic fallback shows one top banner naming requested and selected years.
- [x] The fallback banner survives a late UI collector and is not replayed after acknowledgement.
- [x] Notice queue deduplicates IDs, respects FIFO order, auto-dismisses, and remains dismissible.

## Accessibility and layouts

- [x] Touch targets are at least 48 dp, including accent choices and banner close.
- [ ] Accent choices expose label, role, and selected state to accessibility services.
- [ ] Banner is announced as a polite live region without stealing focus.
- [x] Font scale 1.0x, 1.3x, and 2.0x does not clip the pill, banner, titles, or controls.
- [x] Portrait phone, landscape phone, and width >= 600 dp preserve navigation hierarchy.

## Evidence

- Device gate: Samsung SM-S931B, Android API 36, serial `RFCY61FZDHW`; final release was
  installed in place with `adb install -r` and the existing session/data was preserved.
- Exact-binary smoke: after a forced stop and cold start of the installed release, the Home
  remained authenticated, retained the Indaco palette and 2025/26 data, and did not replay the
  acknowledged fallback notice. Evidence: `artifacts/qa/post/home-final-binary.{png,xml}`.
- Bounded 120 Hz render flow (Home scroll/reverse, tab switches, hierarchy open/back): 455 frames,
  22 frame-deadline misses (4.84%), 8 ms median, 12 ms p90, 17 ms p95, 53 ms p99, and zero slow
  bitmap uploads. Raw evidence: `artifacts/qa/post/gfxinfo-premium-final.txt`; separate warm-path
  captures are `gfxinfo-{scroll,tabs,hierarchy}-warm.txt`.
- Automated gates: `testDebugUnitTest`, `lintDebug`, `:app:compileDebugAndroidTestKotlin`,
  `:app:compileDebugKotlin`, and `:app:assembleRelease` all completed successfully. The focused
  DataStore persistence suite also passed (6 tests, 0 failures/errors).
- Motion/chrome captures: `artifacts/qa/post/tab-home-to-grades-premium-2x-{20,100,220}ms.png`,
  `predictive-premium-{25,50,75}.png`, `predictive-cancel-{mid,settled}-premium-final.png`,
  `settings-predictive-{cancel-mid,cancel-settled}-premium-final.png`,
  `home-{top,scrolled-forward,scrolled-reverse}-premium-final.png`,
  `home-reselect-top-premium-final.png`, `pull-to-refresh-premium-final.mp4`, and
  `home-scrolled-forward-60hz-premium-final.png`. Normal runs covered 120 Hz and animator 1x;
  dedicated captures cover animator 0x, 0.5x, and 2x.
- Theme/personality captures: `artifacts/qa/post/appearance-{brand,dynamic-dot,blue,orange,indigo,green}-premium-final.png`,
  `appearance-dynamic-isolated-5s.png`, and
  `appearance-theme-{light,dark,amoled,system-restored}-premium-final.png`. Palette role and
  semantic tone contrast is covered by `AppThemeColorSchemeTest` and `FluidContrastTest`.
- School-year/notice evidence: `artifacts/qa/post/year-fallback-{1s,3s}-premium-final.png`,
  `year-fallback-no-replay.xml`, and `year-fallback-font-2.0-premium-final.{png,xml}`. Policy,
  one-step fallback, worker isolation, authoritative manual refresh, durable acknowledgement,
  FIFO/deduplication, active-time timeout, and exit handshakes are covered by the domain,
  DataStore, sync, settings, app, and notification-host unit suites.
- Layout matrix: `artifacts/qa/post/home-font-{1.3,2.0}-premium-final.{png,xml}`,
  `settings-root-font-2.0-premium-final.png`, `settings-account-font-2.0.xml`,
  `home-landscape-600dp-premium-final.{png,xml}`, and
  `home-landscape-bottom-premium-final.png`. Device XML confirms the 48 dp banner close target.
- Release artifact: `android/app/build/outputs/apk/release/app-release.apk`; SHA-256
  `CD177CA4BE7B38370E1F6ED2A63AEABBFE1EB5997B8D487027180FB7B78BDEED`; signer
  `CN=Pampa, O=PampaStore, C=IT`; RSA 2048; APK Signature Scheme v2 verified.
- Remaining accessibility limit: the accent semantics and polite live-region behavior are
  implemented and their Compose UI tests compile, but those instrumented assertions were not
  executed on this signed-device session. They remain unchecked until a runtime accessibility
  test (ideally including TalkBack announcement behavior) is completed.
