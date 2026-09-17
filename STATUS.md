# Build and verification status — 17 September 2026

This replaces `PARTIAL_STATUS.md`. The previous handoff was source-complete but had never gone through a
full Android build. That build has now been completed in this environment on Windows, reusing the Unity
Android SDK/JDK as instructed. This is a debug-verified beta, not a device-field-tested release — see
"Still unverified" below before distributing to teachers.

## What changed since the partial handoff
- Fixed a real Room/SQLite bug: `Session` and `Student` used a blanket `@Upsert`, which resolves conflicts
  by primary key only. Inserting a brand-new row (fresh UUID) that collided with an existing row only on
  the `UNIQUE(groupId,date)` or `UNIQUE(groupId,rollKey)` index would silently update zero rows instead of
  failing — so "one class per group per date" and "unique roll per group" were application-level
  conventions, not real database backstops. Split into explicit `@Insert`/`@Update` DAO methods
  (`data/Database.kt`, `data/Repository.kt`) so the constraints are enforced in persistence, matching the
  requirement. Caught by a new Room/SQLite integration test, not by inspection.
- Fixed `Backups.restore()`: it took a redundant snapshot *after* a successful restore, and if that
  incidental snapshot write failed, the whole restore was reported to the caller as failed ("your data is
  unchanged") even though the restore itself had already succeeded. Removed the redundant post-restore
  snapshot; the mandatory pre-restore snapshot (the one that actually matters for safety) is still a hard
  precondition.
- Added the missing correction path the previous handoff's own audit called for: there was no way to add a
  student to an already-saved register if the teacher forgot to enter them beforehand. Rosters are (by
  design) frozen at first save. Added `Repository.addOmittedStudent()` plus a "Add missed student" action
  in the class history screen — requires a reason, writes one new audited mark, never touches any other
  student's data, and refuses if the student is already on the register.
- Fixed `XlsxWriter`/`RegisterExport`: Present/Absent/Leave counts and the attendance percentage were
  written as text, not numbers (roll numbers were correctly text; the totals should not have been).
  Numeric totals are now genuine numeric cells with number formats; "N/A" (zero-denominator case) still
  falls back to text, which is correct since it is not a number.
- Fixed the light-mode launch flash: the pre-Compose Android theme (`values/styles.xml`) hardcoded a dark
  window background regardless of theme. Split into `values/styles.xml` (light) and
  `values-night/styles.xml` (dark) so the native splash frame matches the system theme before Compose
  takes over.
- Corrected `buildToolsVersion` from the documented `35.0.0` to `36.0.0`: the actual Unity 6000.5.8f1
  Android SDK bundles `build-tools;36.0.0` alongside `platforms;android-36`, not 35.0.0. Compatible with
  AGP 8.13 for a compileSdk 36 project. `scripts/build.ps1` and `scripts/make-update.ps1` updated to match.
- Fixed a `local.properties`/lint interaction: Android Lint's `PropertyEscape` check rejects an unescaped
  drive-letter colon in `sdk.dir=D:/...` on Windows, even though it loads correctly. `scripts/build.ps1`
  now writes the colon escaped (`D\:/...`), matching what Android Studio itself generates.
- Added a monochrome adaptive-icon layer (Android 13+ themed icons) and switched two `Int`-backed Compose
  states to `mutableIntStateOf` per Lint's own suggestions; left the two `SharedPreferences.edit()` calls
  in `AppSecurity.kt` as explicit `commit()` calls rather than switching to the KTX `edit { }` helper,
  because that helper discards the boolean success result we rely on to detect a failed PIN write.
- Discovered and worked around a real, reproducible Kotlin/K2 compiler defect on this specific toolchain
  (Kotlin 2.2.21 + AGP 8.13.2 on this JDK 17/Windows combination): constructing `Updater(app)` through a
  wildcard import (`import design.techbytes.rollora.update.*`) failed to resolve with a nonsensical
  "Cannot infer type for type parameter 'T'" / `CoroutineDispatcher.invoke` error, even though the same
  wildcard-import pattern worked fine for other classes elsewhere in the same codebase (e.g. `Backups` in
  `RolloraApp.kt`). Replacing the wildcard with explicit imports (`import
  design.techbytes.rollora.update.Updater`, `.Release`, `.readBytesLimited`) in `ui/AppViewModel.kt` fixed
  it. This looks narrow to that one import combination; if a future edit reintroduces a wildcard import of
  the `update` package in a file with several other wildcard imports and the build fails with a similar
  "unresolved reference" pointing at a constructor call that plainly exists, try explicit imports first.
- Added Robolectric + `androidx.test:core` (test-only) so Room/SQLite persistence can be exercised as a
  real integration test on the JVM: unique-constraint enforcement, draft→final→correction round trips, the
  new omission-correction path, and restore atomicity (an invalid snapshot leaves existing data untouched;
  a valid one fully replaces it). See `docs/DEPENDENCIES.md` for why `@Config(sdk = [34])` was pinned
  (Robolectric 4.16 needs JDK 21 to shadow API 36; this keeps the test suite on the same JDK 17 as the
  main build).

## Second round of changes (same day, after the initial debug-verified build)
- **Added a guided in-app tutorial with on-device text-to-speech.** A 12-step walkthrough (setup, PIN,
  class groups, students, attendance, corrections, calendar, statistics, Excel export, backups, updates)
  offers itself automatically the first time a teacher reaches the workspace, and can be replayed any time
  from Settings → Help & tutorial. Narration uses Android's built-in `android.speech.tts.TextToSpeech` —
  no network call, no cloud TTS service, consistent with the rest of the app's offline-first design. A
  mute/replay toggle is shown only when a TTS engine is actually available on the device; the toggle state
  and "seen" flag are stored in a small SharedPreferences file (not Room), so they are intentionally not
  part of portable backups. New file `ui/Tutorial.kt`; new hand-drawn speaker/mute glyphs in `Design.kt`;
  a unit test (`tutorialStepsAreWellFormed`) checks the content itself (non-blank, no duplicate titles).
- **Fixed a bottom-navigation inset bug**: `Scaffold` already pads its content for the system navigation
  bar, and `NavigationBar`/`NavigationRail` were *also* reserving their own bottom/side inset internally —
  the double-inset showed up as a visible gap between the nav bar and the true screen edge. Fixed by
  setting `windowInsets = WindowInsets(0.dp)` on both in `ui/Root.kt`.
- **Wired the public GitHub release repository**: `rollora.updateRepo=MirFaizan06/Rollora` is now set in
  `gradle.properties` (confirmed public via the GitHub API) and compiles into `BuildConfig.UPDATE_REPO`
  automatically — no `-UpdateRepo` flag needed for normal builds. See `docs/UPDATES.md` for the concrete,
  filled-in publishing steps. No token is stored anywhere; the repo name is public build configuration.
- **Named build outputs properly**: APKs are now `Rollora-v1.0.0-beta-debug.apk` /
  `Rollora-v1.0.0-beta-release.apk` instead of AGP's generic `app-debug.apk`/`app-release.apk`, via an
  `androidComponents.onVariants` block in `app/build.gradle.kts` (requires casting each output to the
  internal `VariantOutputImpl` — the public `VariantOutput` interface doesn't expose a settable
  `outputFileName` in this AGP version; this is the standard, if slightly awkward, documented workaround).
- A signing key was **not** generated by this assistant — keystore/keychain passwords should never pass
  through an agent's tool calls or logs. The exact `keytool` command is in `docs/BUILD_WINDOWS.md`; run it
  yourself in your own terminal, then fill in `signing.properties` from `signing.properties.example`.
- Re-verified: `testDebugUnitTest lintDebug assembleDebug` — **BUILD SUCCESSFUL** with all of the above
  included (41 tests, 0 failures — the tutorial-content test added the 41st).

## Verification completed in this environment
- `./gradlew testDebugUnitTest lintDebug assembleDebug` — **BUILD SUCCESSFUL**, using Unity 6000.5.8f1's
  bundled OpenJDK 17.0.18 and its bundled Android SDK (`platforms;android-36`, `build-tools;36.0.0`);
  system Java 26 and Unity's tools were left untouched (see `docs/DEPENDENCIES.md` for exact paths).
- 40 unit/integration tests, 0 failures: 23 rule/crypto/workbook tests, 10 snapshot-validation tests
  (written previously but never run — now run and passing), 1 export-verification test, and 6 new
  Robolectric/Room integration tests exercising the real SQLite persistence layer.
- The generated Room schema (`app/schemas/design.techbytes.rollora.data.RolloraDatabase/1.json`) exists
  and is included — required for writing correct migrations when the schema next changes.
- Android Lint: 0 errors, 15 warnings (all either informational "a newer library version exists" notices
  for deliberately-pinned dependencies already justified in `docs/DEPENDENCIES.md`, or an `OldTargetApi`
  notice that compileSdk/targetSdk 36 is not lint's very latest known value).
- A debug APK was built (`app/build/outputs/apk/debug/app-debug.apk`, ~14 MB) and confirmed present; not
  included in the source ZIP (see `README.md`).
- The generated `.xlsx` workbook was independently verified with `openpyxl` (a Python library with no
  relation to this project's own `XlsxWriter`) against a realistic sample register: leading-zero roll
  numbers (`007`, `0013`) survive as text; a name containing `&`, `<`, `>` and an apostrophe survives
  exactly; a name deliberately shaped like a formula (`=HYPERLINK(...)`) is stored as a literal string
  cell, never as an executable formula; Present/Absent/Leave and percentage are real numeric cells; the
  all-Leave student's percentage is the text `N/A`. This is not shipped as an automated project test (it
  would require a Python dependency the Android toolchain doesn't otherwise need) but the fixture that
  produced the file lives in `app/src/test/java/design/techbytes/rollora/ExportVerificationTests.kt`.

## Still unverified — genuinely untested in this environment
No Android emulator or physical device was available on this machine (Unity's bundled SDK does not
include the `emulator` package or any system images, and `adb devices` shows nothing connected). None of
the following were exercised on an actual device or emulator:
- Real device/tablet UI: narrow-screen and tablet layout, rotation, large fonts, TalkBack/accessibility,
  keyboard-visibility behavior, split-screen.
- Biometric prompt behavior end-to-end (the code path was reviewed but never triggered on real hardware).
- FLAG_SECURE screenshot/recents protection, and the 30-second background auto-lock timer, in an actual
  foreground/background transition.
- The Save/Share file flow (`Intent.ACTION_CREATE_DOCUMENT`, `FileProvider`, the system share sheet,
  Google Drive specifically).
- The GitHub updater's live network path: checking a real `update.json`, downloading a real APK,
  cancelling mid-download, the Android "install unknown apps" permission prompt, and installing an
  in-place signed update. The release repository (`MirFaizan06/Rollora`, confirmed public) is now wired
  into `gradle.properties`, but no release has been published to it yet, so this path is still untested
  end to end.
- Actually installing and running the signed release APK on a device (see below for what *is* verified
  about it).
- The `docs/DEVICE_ACCEPTANCE.md` checklist as a whole — it still describes manual steps to run yourself
  on a phone and a tablet before distributing to teachers.

## A signed release build now exists
A permanent release key was created by the user directly via `keytool` (never through this assistant, so
the passwords never passed through any tool call or log) and `signing.properties` filled in. `scripts\build.ps1 -Mode Release` then produced `app/build/outputs/apk/release/Rollora-v1.0.0-beta-release.apk`
(BUILD SUCCESSFUL, same `testDebugUnitTest`/`lintDebug` gate as debug, plus `assembleRelease`).
Independently verified, not just trusted from the build log:
- `apksigner verify --print-certs` confirms it **is signed** (APK Signature Scheme v2), one signer,
  certificate DN `CN=Faizan Mir, OU=Tech Bytes Design, O=Tech Bytes Design, L=Srinagar, ST=Jammu & Kashmir, C=IN` — the identity entered during key creation, not a debug placeholder.
- `aapt dump badging` confirms package `design.techbytes.rollora` (the production ID, no `.debug` suffix),
  `versionCode=1`, `versionName='1.0.0 Beta'`, `minSdk=26`, `targetSdk=36`.
- **Not yet verified**: installing this APK on a real device.

## The GitHub release is published and live (with explicit go-ahead)
After the debug-verified build above, the user asked to publish for real. GitHub CLI was installed
(`winget install GitHub.cli`), the user authenticated it themselves via the device-code browser flow (the
OAuth token never passed through this assistant — `gh` stores it in the OS keychain), and the full source
was pushed to `https://github.com/MirFaizan06/Rollora` (branch `main`). `scripts/make-update.ps1` generated
`update.json` from the actual signed release APK, and `gh release create v1.0.0-beta` published both the
APK and the manifest as release assets.

Independently verified after publishing (not just trusted from the `gh` output):
- `curl -sI https://github.com/MirFaizan06/Rollora/releases/latest/download/update.json` — 302 redirects to
  the `v1.0.0-beta` asset, exactly the URL `Updater.check()` requests.
- The fetched `update.json` content matches what `make-update.ps1` generated (`versionCode=1`,
  `sha256=58d237e6c93329342692f65003cfb003e598fca9a70bd539198e002d1e50a2d3`, `bytes=9279837`).
- `curl -sIL` on the `apkUrl` returns `HTTP/1.1 200 OK` with `Content-Length: 9279837`, matching the
  manifest's `bytes` field exactly.
- Since this first release's `versionCode` (1) equals the installed app's own version, `Updater.check()`
  correctly reports "no update" for this exact build — that's expected: there's nothing to update *to*
  yet. The full download/verify/install path is still only exercised by code review until a second,
  higher-`versionCode` release exists to actually test the upgrade path end to end.

Do not treat this beta as "production ready" or "fully verified" until that checklist has been run on
real hardware with a release-signed build, per `docs/DEVICE_ACCEPTANCE.md`.

## Continue locally
See `docs/BUILD_WINDOWS.md` for exact commands. In short:
```powershell
.\scripts\build.ps1 -UnityAndroidPath 'D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer'
```
(substitute your own Unity editor version/path). This writes `local.properties`, verifies the Gradle
wrapper checksum, and runs `testDebugUnitTest lintDebug assembleDebug`.

SDK downloads, build caches, `local.properties` and temporary compiler files are omitted from this source
ZIP. The Gradle wrapper JAR is included. No APK is included (build one locally with the command above).
