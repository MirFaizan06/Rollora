# Rollora · 1.0.0 Beta

> DEBUG-VERIFIED BETA: `testDebugUnitTest`, `lintDebug` and `assembleDebug` all pass in this environment. Real-device UI, biometrics and the live GitHub updater path are still untested — read `STATUS.md` first.
Native, local-first Android attendance for teachers. Developer: **Mir Faizan**. Company: **Tech Bytes Design**.

## Start here
1. Read `docs/BUILD_WINDOWS.md` for your Unity Android SDK and separate build JDK.
2. Build a debug APK for testing using `scripts/build.ps1`.
3. Follow `docs/DEVICE_ACCEPTANCE.md` on a phone and tablet.
4. Create your permanent release signing key and configure a public release repository before distributing to teachers.
5. Read `docs/UPDATES.md` to publish releases. No server, Firebase, Expo, Flask or GitHub access token is required.

This ZIP contains source, resources, Gradle wrapper, Windows build scripts, release-manifest generator, vector branding, tests and documentation. Build dependencies download on the first build; this is not an offline SDK/dependency bundle. See `docs/VERIFICATION.md` for the checks actually completed in the delivery environment and `STATUS.md` for what changed to get here.

## Included workflows
- Six-digit PIN setup, persisted throttling, optional strong biometrics, background auto-lock and protected screenshots.
- Teacher/college profile; system, charcoal dark and light themes; phone bottom navigation and tablet rail.
- Class groups: batch, semester, configurable course type, optional CT component, subject and default time. Semester combinations are not restricted.
- Students: roll number and name, with internal membership dates. Single entry, validated batch paste, search, rename and end/reactivate membership. Rosters are frozen once a register is saved; a student mistakenly left out can be added afterward only through an explicit, reasoned, audited correction — never a silent rewrite.
- One class per group per date, enforced by a real database constraint (not just application logic). Present / Absent / Leave, mark-all confirmation, saved drafts, final review, correction reasons, tap/save timestamps, revision audit and reversible cancellation.
- Automatic Sundays; global and group holidays; explicit reason to record on a day off.
- In-app statistics from conducted classes. Leave excluded from P/(P+A); N/A when denominator is zero.
- Real Excel `.xlsx` with date columns, legend, teacher/group metadata, P/A/L counts, percentage, original recorded names and class/mark timestamps.
- Encrypted portable backups, validation before transactional restore, pre-restore snapshot, fourteen rotating local snapshots and periodic WorkManager backups.
- Android Save Document and Share actions, including Google Drive when available on the device.
- GitHub public-release updates: launch check, changelog, progress, cancellation, SHA-256/size/package/version/signing-certificate verification, Android installer handoff.

## Deliberate boundaries
Android 8.0+ phones/tablets. One teacher workspace per installation. No live account, student-facing login, QR attendance or sync in V1. Those need their own authentication and consent design later. No silent APK installation or forced restart; Android owns installation and the user taps Open afterward. Rollora's proposed name has not been trademark-cleared.

Attendance data stays local. GitHub update requests contain no roster or attendance data. Explicit sharing sends the selected file to the app chosen by the teacher. Android's app sandbox and device encryption protect the live database; it is not separately encrypted with the app PIN. Portable backups are AES-GCM encrypted using a separate passphrase. See `docs/SECURITY_AND_DATA.md`.

## Code layout
| Area | Responsibility |
|---|---|
| `data/` | Room entities, DAO, transaction boundary, snapshot validation/serialization |
| `domain/` | Attendance and input rules |
| `security/` | Keystore keys, PIN verification, authenticated encryption |
| `transfer/` | Local/portable backups and OOXML export |
| `update/` | Public GitHub updater and installer integration |
| `ui/` | ViewModel, adaptive Compose screens, forms and custom drawn icons |
| `scripts/` | Windows builds, release metadata and programmatic logo |
| `app/src/test/` | Rules, encryption, workbook and snapshot tests, plus Robolectric/Room persistence integration tests |

Only `local.properties` and `signing.properties` are machine-specific. They are intentionally excluded. Never commit your signing key, passphrases, actual attendance, or backups to GitHub.
