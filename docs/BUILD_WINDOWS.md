# Build on Windows using the Unity Android tools

Your `java -version` currently reports **Java 26.0.2.1**. Keep it installed for other work. This project pins **AGP 8.13.2 + Gradle 8.13**. Gradle 8.13 cannot run on Java 26; use **JDK 17**, or JDK 21, for this build. The script sets JAVA_HOME only inside its process and restores it afterward. It does not alter your system Java configuration.

Unity's Android SDK is a normal Android SDK and can be reused. Its OpenJDK may be usable; the script checks the version and requires `javac.exe`. Older Unity versions may bundle JDK 11, which is insufficient. The NDK is not needed by Rollora.

Confirmed in this environment: **Unity 6000.5.8f1** bundles **OpenJDK 17.0.18** (Temurin) and an Android SDK
with `platforms;android-36` and `build-tools;36.0.0` already installed — both usable as-is, with nothing
added to or removed from Unity's own SDK/JDK. Your Unity version may differ; check the actual `java
-version` output from Unity's `OpenJDK\bin\java.exe` and the contents of its `SDK\platforms` /
`SDK\build-tools` folders before assuming an exact match.

## Find your folders
In Unity: Edit → Preferences → External Tools → Android. Copy the SDK and JDK paths. A usual folder looks like:

`D:\Unity\Hub\Editor\<editor-version>\Editor\Data\PlaybackEngines\AndroidPlayer`

or, for a direct (non-Hub) Unity install:

`D:\UnityEditor\<editor-version>\Editor\Data\PlaybackEngines\AndroidPlayer`

Inside it are usually `SDK`, `NDK`, and `OpenJDK`. Use your actual paths, not the example placeholders.

Required SDK packages:
- `platforms;android-36`
- `build-tools;36.0.0` (AGP 8.13's matching build tools for compileSdk 36; some Unity installs may instead
  have `35.0.0` — either works with AGP 8.13, but `app/build.gradle.kts` is pinned to `36.0.0` to match
  what this environment actually had installed. Change the pin if your SDK only has `35.0.0`.)
- `platform-tools` (for adb installation/testing)

Packages install side by side. Do not replace or remove Unity's existing packages.

## Build with PowerShell
Extract the ZIP to a writable folder, such as `D:\Projects\Rollora`. Open PowerShell there.

```powershell
.\scripts\build.ps1 -UnityAndroidPath 'D:\YOUR_UNITY_EDITOR\Editor\Data\PlaybackEngines\AndroidPlayer' -InstallMissingSdk
```

The exact command verified in this environment (Unity 6000.5.8f1, direct non-Hub install), which produced
a passing `testDebugUnitTest lintDebug assembleDebug`:
```powershell
.\scripts\build.ps1 -UnityAndroidPath 'D:\UnityEditor\6000.5.8f1\Editor\Data\PlaybackEngines\AndroidPlayer'
```
(`-InstallMissingSdk` was not needed: `platforms;android-36` and `build-tools;36.0.0` were already present.)

Or point to independent SDK/JDK folders:

```powershell
.\scripts\build.ps1 -SdkPath 'D:\YourAndroidSDK' -JdkPath 'C:\Program Files\Java\jdk-17' -InstallMissingSdk
```

The script checks Java and the Gradle wrapper checksum, writes `local.properties`, installs missing packages if requested, asks you to accept Android SDK licenses through sdkmanager, runs unit tests/lint, and builds the debug APK.

Result: `app\build\outputs\apk\debug\Rollora-v1.0.0-beta-debug.apk`.

Debug uses `design.techbytes.rollora.debug`, separate from the release package. It is for testing. Do not distribute debug APKs as production builds. Debug data will not automatically appear in release; use a portable encrypted backup to transfer it.

If PowerShell blocks a downloaded script, inspect it first, then use `Unblock-File .\scripts\build.ps1` for that file or run the equivalent Gradle commands manually. Do not change your system-wide execution policy just for this project.

## Manual / Android Studio alternative
Install SDK 36 and Build Tools 36.0.0 with Android Studio SDK Manager. Open the extracted Rollora directory. Set Settings → Build Tools → Gradle → Gradle JDK to JDK 17. Sync and run.

PowerShell session-only manual commands:

```powershell
$env:JAVA_HOME = 'D:\YourJDK17'
$env:ANDROID_HOME = 'D:\YourAndroidSDK'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

In `local.properties` put `sdk.dir=D\:/YourAndroidSDK` using forward slashes and an escaped drive-letter colon (`\:`) — Android Lint's `PropertyEscape` check rejects a bare `:` in a Windows path even though it still loads correctly. `scripts/build.ps1` writes this automatically. For Linux/macOS, use JDK 17, set the SDK path (no drive letter, so no escaping needed), and run `./gradlew testDebugUnitTest lintDebug assembleDebug`.

## Signed distribution build
Create a permanent key once. Use keytool interactively so passwords do not enter shell history:

```powershell
New-Item -ItemType Directory -Force keys
& 'D:\YourJDK17\bin\keytool.exe' -genkeypair -v -keystore keys\rollora-release.jks -alias rollora -keyalg RSA -keysize 3072 -validity 10000
Copy-Item signing.properties.example signing.properties
```

Edit `signing.properties` locally with your path, alias and passwords. Back up the key and credentials securely; losing the key prevents normal in-place updates. Do not regenerate it for each version.

```powershell
.\scripts\build.ps1 -SdkPath 'D:\YourAndroidSDK' -JdkPath 'D:\YourJDK17' -Mode Release -UpdateRepo 'YOUR_GITHUB_NAME/rollora-releases'
```

Result: `app\build\outputs\apk\release\Rollora-v1.0.0-beta-release.apk`.

The updater repo can alternatively be pinned in your private/local `gradle.properties` using `rollora.updateRepo=owner/repo`. This is public configuration, not a token. It is compiled into the app. Do not change it after distribution unless an update points existing users to the new repository.

No release credentials are provided in this ZIP, intentionally. A raw `assembleRelease` without signing.properties can produce an unsigned APK; the supplied Release script refuses that configuration.

## Common failures
| Message | Fix |
|---|---|
| Unsupported Java / class version | Use JDK 17 or 21, not your system Java 26 |
| SDK location not found | Pass the actual SDK folder; inspect local.properties |
| Platform android-36 missing | Install API 36, not just Android platform-tools |
| Plugin/dependency not resolved | Allow internet access to Google Maven, Maven Central, Gradle distributions and Plugin Portal |
| License not accepted | Run SDK Manager / sdkmanager --licenses interactively |
| Update signature mismatch | Use the original release key and production package |
| Older Unity JDK | Install JDK 17 separately and pass -JdkPath |

References: [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Gradle Java matrix](https://docs.gradle.org/current/userguide/compatibility.html), [Unity Android tools](https://docs.unity3d.com/6000.0/Documentation/Manual/android-sdksetup.html).
