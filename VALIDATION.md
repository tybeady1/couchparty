# Validation

Verified on October 17, 2026:

- Gradle 8.9 / Android Gradle Plugin 8.7.3: `assembleDebug` succeeds with JDK 17 and SDK 34.
- `lintDebug` succeeds with zero errors. Remaining warnings concern English-only strings, manifest SDK overrides for the alternative SDK build, backup configuration, and chart draw allocations.
- Direct Android SDK build succeeds and APK signature verification passes.
- `ShotTest`: canonical CSV values, imperial/metric conversions, and rejection of nonfinite/out-of-range measurements pass.
- `BallTrackerTest`: synthetic pixel frames verify known speed and angle, proportional calibration changes, and rejection of stationary objects, insufficient observations and excessive frame gaps.
- `LiveShotDetectorTest`: stationary lock, automatic putt/swing triggers, speed and angle, exactly one reading per impact, replacement/re-arming, jitter rejection, lost balls, frame gaps, lighting changes, all four ball profiles, perforated shape lock, and unchanged speed across profiles pass.

Emulator retry after enabling virtualization:

- Windows reports firmware virtualization enabled and a running hypervisor; WHPX is operational.
- The API 34 emulator completed its cold boot (`sys.boot_completed=1`).
- `Fairway-debug.apk` installed successfully and `MainActivity` launched with status `ok`.
- The main screen rendered correctly in a captured screenshot (`build/fairway-emulator.png`); the app remained running and the crash log was empty.

Live-camera update:

- Installed the updated direct-build APK over the existing app without clearing data.
- Camera permission prompt and grant work; the in-app color camera preview renders the emulator's synthetic scene, with analyzed frame rate displayed.
- Normal, Range, Foam and Wiffle options appear; choosing Foam persisted across APK update/activity restart.
- Calibration gate prevents detection before confirmation. Applying a valid width changes the instruction to selecting the stationary ball.
- Pause/resume detection works while maintaining preview. Backgrounding and returning to the app reopens the camera.
- Putting setup illustration was visually inspected in the emulator (`build/phone-setup-emulator.png`). The guide includes a second swing-placement diagram; standalone vector artwork is `docs/phone-setup.svg`.
- No app crashes were recorded during these checks. Updated build, lint and all three model test suites pass.

Not verified:

- Full UI interactions, practice-session restart persistence, document-picker export, and imported Android video decoding. Live preview and permission/lifecycle flows now have emulator verification; physical-phone testing is still pending.
- Real golf-shot detection and measurement accuracy. Synthetic frame tests do not establish performance with actual camera footage.

## Device acceptance checks

1. Install the debug APK on Android 8.0 or newer.
2. Open Live launch monitor, choose Putting and the actual ball type, follow the overhead placement graphic, and enter the measured image width.
3. Tap a stationary ball, wait for READY, and putt. Verify the tracked points follow the ball, one reading appears, and replacing the ball re-arms the monitor. Repeat with all ball types and check the saved reading labels.
4. Try practice swings without moving the ball, camera movement, low light, and a ball disappearing too quickly. Confirm no fabricated reading appears. Also test Analyze a saved clip with original high-frame-rate video and inspect the tracked overlay.
5. Add demo and manual practice shots, restart the app, save a session, and confirm history and source labels persist.
6. Export a session through the system file picker and verify canonical unit headers and manual/demo source columns.
7. Check camera permission denial/revocation, background/resume, camera interruption, the landscape live screen, and large font settings on the target phone. Compare measured putt speed against a known-distance timed reference before relying on results.

## 3D games and simulator adapters (2026-10-17)
Direct SDK build and Gradle assembleDebug/lintDebug passed (0 lint errors, 23 warnings). Model tests cover range round limits, ring scores, duplicate-shot rejection, rail/obstacle collisions, cup capture, three-hole scorecards and water penalties. Simulator loopback tests cover packet units, explicit assumptions, response framing, acknowledgement/rejection, timeout and cancellation. API34 emulator rendered Kenney meshes and accepted a mini-golf putt. Third-party simulator gameplay and physical camera-to-game tracking remain unverified. See integrations/README.md for sourced compatibility evidence.

Phone tap investigation: provided video shows the putting scene without app UI. Local replay at 360x640/30fps, with targets selected at 3s and 7s, reached READY after about 0.63s in each case. This does not reproduce OnePlus 15 touch input. Updated touch selection to respond on touch-down, added confirmation, calibration prompt and center-selection fallback. Emulator tap opens calibration; model tests and direct/Gradle builds and lint passed. Physical-device outcome remains unverified.

Updater (1.1.0/build 2): model tests, local TLS download/redirect/hash/error tests, direct SDK build and Gradle assemble/lint pass. Emulator upgrade from build 1 preserves install; Settings shows version and updater screen. Direct and Gradle APK signer SHA-256 match. Published v1.1.0 to tybeady1/couchparty. Public feed and APK fetched without authentication; SHA-256 matched. Installed release APK checked the live GitHub feed in the emulator and reported up to date. Physical-phone download/installer flow remains to be verified.

1.2.0: putt result screen inspected in API34 emulator using injected sample metrics (not a measured physical putt). Model tests and direct/Gradle builds with lint pass. Result includes camera-relative heading, trace, speed, observed segment distance and explicit unmeasured rollout. Real-camera transition and physical-phone behavior still need field verification.

1.3.0: calibration model tests cover quadratic speed scaling, odd/even median, invalid/nonfinite input, observed-distance bound and missing calibration. Emulator UI with synthetic 1.25 m/s test input saved a measured 2.5 m sample and showed 2.50 m estimated rollout with a calibration/self-fit label. This is a UI/math check, not physical validation. Direct/Gradle builds and lint passed.
