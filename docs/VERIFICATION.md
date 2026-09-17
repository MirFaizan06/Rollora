# Verification record

What was actually checked, when, how, and by what — not a claim of "production ready" or "fully
verified." See `STATUS.md` for the narrative of what changed to get here, and `docs/DEVICE_ACCEPTANCE.md`
for the manual, on-device checklist that still needs to be run before distributing to teachers.

## Environment (17 September 2026, Windows)
- Build JDK: Unity 6000.5.8f1's bundled OpenJDK 17.0.18 (Temurin) at
  `D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK`.
- Android SDK: Unity 6000.5.8f1's bundled SDK at
  `D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK`
  (`platforms;android-36`, `build-tools;36.0.0`, `platform-tools`). Nothing was installed into or removed
  from this SDK.
- System Java 26 and Unity's own tooling were untouched; `JAVA_HOME`/`ANDROID_HOME` were only set for the
  duration of the build commands.

## Automated checks and results

| Check | Command | Result |
|---|---|---|
| Unit + integration tests | `gradlew testDebugUnitTest` | **PASS** — 40 tests, 0 failures, 0 errors |
| Lint | `gradlew lintDebug` | **PASS** — 0 errors, 15 warnings (see below) |
| Debug build | `gradlew assembleDebug` | **PASS** — `app/build/outputs/apk/debug/Rollora-v1.0.0-beta-debug.apk` produced |
| Room schema export | (part of `kspDebugKotlin`) | **PASS** — `app/schemas/…/1.json` generated |
| Independent `.xlsx` verification | `openpyxl` (Python, external to this project) | **PASS** — see below |

Full combined command used for the final green run:
```powershell
$env:JAVA_HOME = 'D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK'
$env:ANDROID_HOME = 'D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
```

### Test breakdown (`app/build/test-results/testDebugUnitTest/`)
- `CoreTests` — 23 tests (input rules, encryption round-trip/tamper detection, workbook XML structure,
  formula-injection resistance, numeric-vs-text cell typing). Pure JVM, no Android framework needed.
- `SnapshotValidationTests` — 10 tests (written in the partial handoff, never run until now): orphan
  references, duplicate dates/rolls/marks, cross-group marks, incomplete finalised registers, roster
  renames/departures preserving history. All pass unchanged.
- `RepositoryIntegrationTests` — 6 new tests using Robolectric + an in-memory Room database (real SQLite,
  not a mock): `UNIQUE(groupId,date)` and `UNIQUE(groupId,rollKey)` are enforced by the database itself
  (this test caught a real bug — see `STATUS.md`); a draft→final→correction round trip through the actual
  Repository API; the new omission-correction path; an invalid restore leaves existing data untouched; a
  valid restore fully and correctly replaces the workspace.
- `ExportVerificationTests` — 1 test that writes a realistic sample register workbook to
  `app/build/verification/sample-register.xlsx` for the independent check below.

### Lint warnings left in place (not errors, each already justified)
- `AndroidGradlePluginVersion`, `GradleDependency` (×4: lifecycle, Room ×3, WorkManager),
  `NewerVersionAvailable` (coroutines): all deliberate pins already documented with rationale in
  `docs/DEPENDENCIES.md`. Not blindly bumped per the instruction to keep compatible pins rather than
  upgrade everything.
- `OldTargetApi`: compileSdk/targetSdk 36 is the pinned, officially-current target per AGP 8.13 release
  notes; lint's database is simply aware of a newer preview level.

### Independent `.xlsx` verification
`app/src/test/java/design/techbytes/rollora/ExportVerificationTests.kt` writes a sample register (4
students, 2 finalised sessions) to `app/build/verification/sample-register.xlsx`. That file was then
opened with `openpyxl` — a Python library unrelated to this project's own `XlsxWriter` — and checked for:
- Leading-zero roll numbers (`007`, `0013`) preserved exactly as text cells.
- A name containing `&`, `<`, `>`, and an apostrophe (`O'Brien & Sons <Ltd>`) preserved byte-for-byte.
- A name deliberately shaped like a spreadsheet formula (`=HYPERLINK("http://example.com")`) stored as a
  literal string cell (`t="inlineStr"`), confirmed **not** to be an executable formula (no `<f>` element).
- Present/Absent/Leave counts and the attendance percentage stored as genuine numeric cells (`t="n"`).
- The all-Leave student's percentage correctly falls back to the text `N/A` (undefined denominator).
- All three sheets (`Register`, `Recorded classes`, `Recorded marks`) parse without error and contain the
  expected header rows.

This check is not wired into the Gradle build (it would need a Python dependency the Android toolchain
otherwise doesn't use) — it was run once, manually, in this environment. Re-run it yourself with any
spreadsheet reader (Excel, LibreOffice, Google Sheets, `openpyxl`) against a real exported register if you
change `XlsxWriter.kt` or `RegisterExport.kt`.

## What this does NOT verify
Everything that requires a physical phone/tablet or emulator — none was available in this environment.
See `STATUS.md`'s "Still unverified" section for the full list (device UI, biometrics, FLAG_SECURE,
auto-lock timing, the share sheet, the live GitHub updater path, a real signed release build). Run
`docs/DEVICE_ACCEPTANCE.md` on real hardware before distributing to teachers.
