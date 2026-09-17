# Rollora

A local, offline-first Android attendance app for college teachers.

**Developer:** Mir Faizan · **Company:** Tech Bytes Design · **Current version:** 1.0.1 Beta

Rollora keeps a teacher's class rosters and attendance records entirely on their own phone. There is no account, no login, and no cloud sync — attendance data never leaves the device unless the teacher explicitly exports or backs it up.

## Get Rollora

1. Open the [latest release](https://github.com/MirFaizan06/Rollora/releases/latest).
2. Download `Rollora-v<version>-release.apk` from the release's Assets.
3. On an Android 8.0+ phone or tablet, open the downloaded file. Android will ask you to allow installs from this source the first time — allow it, then return and tap install again.
4. Open Rollora, set a teacher profile and a six-digit PIN, and follow the in-app guided tutorial.

Rollora checks this repository for new versions automatically in the background, and on demand from Settings. Every update is verified (checksum, package identity, and signing certificate) before you're asked to install it, and your attendance data is preserved through the update.

## What it does

- **Class groups** — batch, semester, course type (Major, Minor, MDC, AEC, Skill, VAC, or a custom type you name), optional CT component, subject, and a default class time.
- **Students** — roll number and name, added one at a time or pasted in bulk. Leading zeroes in roll numbers (`007`, `0013`) are always preserved exactly as typed.
- **Attendance** — Present / Absent / Leave, saved drafts, a final review before a register locks in, and a required reason for any later correction. A saved register is never silently rewritten: even a student missed by mistake is added back only through an explicit, audited correction.
- **Calendar** — Sundays are calculated automatically; add your own holidays, either for every class or just one. Recording a class on a day off always asks why.
- **Statistics** — based only on classes that were actually conducted and finalised. Percentage is Present ÷ (Present + Absent); Leave is reported separately and never counts against a student.
- **Excel export** — a genuine `.xlsx` workbook for any class and date range, with a legend, per-student totals, and original recorded timestamps.
- **Backups** — rotating encrypted local snapshots happen automatically, plus a portable encrypted backup with a passphrase you choose, shareable to Google Drive or another device.
- **Guided tutorial** — a short in-app walkthrough with optional spoken narration, replayable any time from Settings.
- **What's New** — a short popup after every update, and a full version history in Settings, so you always know what changed.

## Privacy

All attendance and student data stays on the device. There are no accounts, no ads, no analytics, and attendance data is never uploaded anywhere. The only network activity Rollora performs is checking this GitHub repository for a newer release — those requests carry no roster or attendance data. See [`docs/SECURITY_AND_DATA.md`](docs/SECURITY_AND_DATA.md) for the full data and security model.

## Status

Rollora is in beta. It has a fully passing automated test suite and a clean signed release build, but has not yet completed a full round of testing on real devices — see [`STATUS.md`](STATUS.md) for exactly what has and hasn't been verified. Treat it as an early release for a small group of teachers who understand that, rather than a finished, widely-distributed product.

## Scope

Android 8.0+ phones and tablets; one teacher workspace per installation. There is no student-facing login, QR attendance, or online sync in this version. "Rollora" is a working name and has not been trademark-cleared.

## For developers

Rollora is native Kotlin + Jetpack Compose with Room for local persistence — no backend, no server. To build it yourself on Windows:

```powershell
git clone https://github.com/MirFaizan06/Rollora.git
cd Rollora
.\scripts\build.ps1 -SdkPath 'D:\YourAndroidSDK' -JdkPath 'D:\YourJDK17'
```

See [`docs/BUILD_WINDOWS.md`](docs/BUILD_WINDOWS.md) for full build instructions (including reusing a Unity-bundled Android SDK/JDK), [`docs/DEPENDENCIES.md`](docs/DEPENDENCIES.md) for pinned dependency versions and why, and [`docs/UPDATES.md`](docs/UPDATES.md) for how releases here get published.

| Area | Responsibility |
|---|---|
| `data/` | Room entities, DAO, transaction boundary, snapshot validation/serialization |
| `domain/` | Attendance and input rules |
| `security/` | Keystore keys, PIN verification, authenticated encryption |
| `transfer/` | Local/portable backups and OOXML export |
| `update/` | Public GitHub updater and installer integration |
| `ui/` | ViewModel, adaptive Compose screens, guided tutorial and changelog |
| `scripts/` | Windows build/release scripts and the programmatic logo generator |
| `app/src/test/` | Unit tests plus Robolectric/Room persistence integration tests |

## License and credit

Rollora's own source and branding were created for Mir Faizan / Tech Bytes Design. See [`NOTICE.md`](NOTICE.md) for the third-party license notices that apply to build tooling and dependencies (Gradle wrapper, AndroidX, Kotlin, and others fetched at build time).
