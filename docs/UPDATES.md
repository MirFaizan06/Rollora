# Public GitHub releases, no server
The repository serving release APKs must be **public**. It can be a separate distribution-only repository containing releases and a short README; your development source repository can remain private. Public APKs can be downloaded by anyone, so this is distribution, not licensing enforcement.

**Configured for this project**: `rollora.updateRepo=MirFaizan06/Rollora` is already set in `gradle.properties` and confirmed public. `BuildConfig.UPDATE_REPO` is baked in automatically — you do not need to pass `-UpdateRepo` on every build unless you want to override it for a different repo.

## First release
1. The release repository already exists: [github.com/MirFaizan06/Rollora](https://github.com/MirFaizan06/Rollora) (public, confirmed via the GitHub API). If you'd rather use a separate distribution-only repo later, create it and change `rollora.updateRepo` in `gradle.properties` before the *first* release — do not change it after teachers already have the app installed, or point a follow-up release at the new repo explicitly.
2. Build a signed release. With the repo already pinned in `gradle.properties`, a plain `-Mode Release` build is enough; `-UpdateRepo` is only needed to override it. Use the same permanent key and `design.techbytes.rollora` package for every release.
3. Create a GitHub Release on that repository with a tag such as `v1.0.0-beta`. Keep it a normal published release if using `/releases/latest`; GitHub's Latest route does not select prereleases. The displayed app name can still say Beta.
4. Generate the manifest from the actual APK:

```powershell
.\scripts\make-update.ps1 -Apk '.\app\build\outputs\apk\release\Rollora-v1.0.0-beta-release.apk' -Repo 'MirFaizan06/Rollora' -Tag 'v1.0.0-beta' -SdkPath 'D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK' -Notes 'Initial teacher beta.'
```

5. Upload **both** the release APK and the generated **`update.json`** to that release. Publish only after both are present. The script derives versionCode, versionName, minSdk, byte length and SHA-256 from the APK.
6. Give teachers that first signed APK manually. On future cold starts, Rollora checks `https://github.com/MirFaizan06/Rollora/releases/latest/download/update.json` in the background. Manual checks are in Settings.

## Later versions
Increase `versionCode` (strictly larger integer) and change `versionName` in app/build.gradle.kts. Implement and test Room migrations whenever the database schema changes. Do not enable destructive migration fallback. Build with the same signing key/repo/package. Generate a fresh manifest for the new tag and upload both assets. Never reuse a checksum for a rebuilt APK.

## What teachers see
Update notice → changelog in Settings → Download with live percentage → verified package → Install → Android confirmation. If needed, Android first asks the teacher to allow installs from Rollora; after allowing, return and tap Install again. Following successful installation, tap Open. Android does not guarantee automatic relaunch after a sideloaded update; the app does not claim silent install or restart.

Downloads are optional and cancellable. A failed check never blocks attendance. Downloads run while this app process is alive; if the process is killed, restart the download. Bytes are never treated as an installable APK until complete and verified. Updater supports APKs up to 150 MiB and HTTPS GitHub redirects only. An APK requiring a newer Android version is not offered to an incompatible device.

## Integrity and data preservation
The app validates manifest bounds, repository URL, exact downloaded byte count, SHA-256, APK package name, increasing versionCode and the same signing certificate as the installed app. A checksum alone is not a substitute for the signing check. Release key rotation is not implemented: a different certificate is rejected.

A local backup precedes the download. Android normally retains app data for an in-place update signed by the same key; future migrations must also preserve it. No program can guarantee against storage failure or a defective future migration. Keep an off-device portable backup before important upgrades. Never ask a teacher to uninstall to resolve an update conflict without first exporting a portable backup.

No GitHub token, repository credential, student information or server endpoint is embedded. The public repository name is build configuration. Keep signing keys and backups out of the public repository.
