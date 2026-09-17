# Device acceptance before teacher distribution
Run on an Android 8+ phone and a tablet. These are manual acceptance steps, not a claim they were run in this environment.

1. Install debug. Set teacher/college and six-digit PIN. Reject a mismatching PIN, blank teacher and unchecked local-data notice. Restart: PIN required.
2. Five wrong PIN attempts: cooldown persists across restarting. Enable a strong biometric and unlock. Return after 30 seconds away: locked. Cancel biometric: PIN remains usable.
3. Add Batch 2024/Semester 5/Major/CT1/OS at 10:00, Major/CT2/Java, and a Minor group. Add a custom course type in any semester. Reject duplicate group and malformed time.
4. Paste `001301, Student One` and `001302, Student Two`. Leading zeroes persist. Duplicate roll or a malformed row rejects the entire import. Search works. Edit a name and effective dates.
5. Mark P/A/L. Save incomplete as draft. A final save with unmarked students must fail. Review and save complete. Reopening the same day edits the same register. A second daily record must never be created.
6. Edit a final mark without a reason: reject. With reason: save a new revision; audit shows before/after. Rename the student: old recorded marks retain original name. Inactivate membership and open a new date: exclude appropriately.
7. Add a global holiday and a class-only holiday. Sundays are automatic. Save a class on a holiday without reason: reject; with reason: accept. Adding a holiday later does not change existing totals.
8. Cancel a final class: totals fall. Restore it: totals recover. All-Leave percentage is N/A; 3 P/1 A/1 L is 75%. Drafts and unrecorded days do not count.
9. Export a month. Open in Excel and LibreOffice/Google Sheets: date headers, frozen panes, filter, metadata, P/A/L, totals, legend, readable original timestamp sheets. Names containing `&`, `<` or `=...` must remain text. Cancel Save and Share dialogs without crashing.
10. Create a portable backup. Wrong passphrase and damaged file must fail before replacing records. Restore a valid backup onto another installation after setup; groups, membership, sessions, marks, holidays and audit match. Destination PIN remains unchanged. Source biometric setting must not enable biometrics on destination.
11. Restore a local snapshot after making changes. A pre-restore snapshot must remain available. Uninstall tests must use disposable data and a verified off-device backup.
12. Disconnect internet: all attendance/export/backup functions work. Update check failure never blocks access. Drive sharing depends on the installed Drive app.
13. Build two signed release APKs with increasing versionCode and the same key. Publish APK/update.json; initial app detects update. Cancel mid-download; restart cleanly. Deny installation permission then allow it. Install and reopen; data unchanged.
14. Try checksum mismatch, incorrect size, wrong package, lower version and a different signing certificate. Reject each. Run real release migration tests before any future schema change.
15. Rotate phone/tablet while marking, search a large roster, test keyboard in forms, use a large font and TalkBack, and test narrow split-screen. A dirty attendance editor must confirm before discarding. Save a draft before deliberately killing the process; unsaved in-memory changes are not durable.

Do not advertise the beta as production-certified until this list passes with the release-signed build on your target devices. Inspect app/build/reports for unit-test and lint results.
