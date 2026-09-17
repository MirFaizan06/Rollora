# Data and security model
## Local workspace
Room/SQLite holds profile, groups, membership roster, sessions, marks, holidays and audit entries. Android's app sandbox and device file encryption protect these records at rest. The database is **not separately SQLCipher-encrypted**. The six-digit PIN controls app access; it is not a database encryption password and does not defend against a rooted/compromised device. Enable a device screen lock. V1 does not claim institutional compliance certification or forensic/tamper-proof records.

PIN verification uses a random salt, PBKDF2-HMAC-SHA256 (210,000 iterations), then a Keystore-protected HMAC; plaintext PINs are not persisted. Repeated failures trigger persisted increasing cooldowns. Biometrics uses Android's strong-biometric prompt. Screenshots/recents are protected with FLAG_SECURE. Fresh processes start locked; returning after 30 seconds away locks again. Explicit lock is available. No account or online PIN reset exists.

## Attendance semantics
- Group is the unit of attendance. UNIQUE(groupId,date) prevents a second daily class, including when the first is cancelled.
- Students have UUID membership IDs; roll numbers are strings with a normalized per-group unique key. Leading zeroes survive.
- Course type, semester and CT component are data, not semester-specific code branches.
- Joined date is inclusive. Inactive-from date is exclusive. New sessions use membership effective on that day. Once a draft/final record exists its roster is frozen.
- Names, rolls, teacher and group label are snapshotted for history. Editing a roster does not rewrite old marks. To add a student missed from a frozen roster, V1 intentionally does not silently alter it; correct the roster before initially saving that day's register.
- P/A/L are the only statuses. Drafts may have unmarked rows. Finalisation requires all students. No default automatic absence.
- Final registers cannot be demoted to draft. Corrections require a reason; edits preserve mark tap times for unchanged statuses and record each changed status in audit.
- Cancelling a class removes it from totals without deleting its original records. Restore reverses cancellation; one-class-per-date remains enforced.
- Sundays and holidays are calendar information. Conducting a class on a day off requires a reason. Adding a holiday later never silently changes attendance.
- Statistics count final, non-cancelled classes and eligible snapshotted marks. Percentage=P/(P+A). Leave is reported and excluded; all Leave means N/A.

Timestamp precision is milliseconds as reported by the device. Class date/time, UTC save/tap instants, time zone and revision are separate fields. Device clocks can be wrong or manually altered; offline timestamps are not independently authoritative. Audit is useful history, not a cryptographically sealed ledger.

## Backups
Portable `.rollora` files use a versioned magic header, random salt, PBKDF2-HMAC-SHA256, random AES-GCM IV and authenticated ciphertext. Header/salt are authenticated. Passphrases require at least 12 characters and are never saved. Wrong passwords or modifications fail authentication. Payloads are JSON snapshots, never SQL or executable code. Files over 32 MiB are rejected; this V1 limit keeps restore memory bounded.

A portable backup contains business data, profile, audit, theme and preferences. PIN verifier, device Keystore keys and GitHub credentials are absent. Restoring retains the destination PIN and disables biometrics until explicitly re-enabled.

Restoring parses and validates data, checks unique IDs/rolls/dates, parent references, dates/times/statuses and complete final rosters, then uses one Room transaction. The existing workspace is snapshotted before replacement. Failure during the transaction rolls back. Local snapshots are encrypted with an installation-bound Android Keystore key, written with temp+fsync+rename, and rotated to fourteen copies. Automatic snapshots follow mutations and a WorkManager daily task; Android may delay background work. Failed backup creation is reported without falsely rolling back successfully saved attendance.

Local snapshots do not survive uninstall or device loss reliably and cannot be decrypted on another installation. Share a portable backup off-device. Android cloud backup and device-transfer rules exclude application records and security settings. OEM behavior can vary; application-level sharing remains explicit.

## Exports and sharing
XLSX exports are intentionally readable. Roll numbers and untrusted text are inline string cells, never executable formulas. Share only with intended recipients. The system share sheet can expose Drive if installed; Rollora does not hold a Google account or schedule Drive uploads. FileProvider grants temporary read access only to cache/shared and cache/updates. No broad storage permission is requested.

## Scalability
The repository is the write boundary; transactions and explicit snapshot format versions are the extension seams. New Room schema versions require explicit migrations and migration tests. Keep entities, export schema and UI changes separate. Future online authentication, school tenancy, QR verification, replay prevention, student roles and sync-conflict handling belong in new services; an offline QR is not secure proof of attendance by itself. V1 keeps the required offline workflow small and does not ship nonfunctional online controls.
