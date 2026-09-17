[CmdletBinding()]
param(
 [string]$UnityAndroidPath = "",
 [string]$SdkPath = "",
 [string]$JdkPath = "",
 [ValidateSet('Debug','Release','Check')][string]$Mode = 'Debug',
 [string]$UpdateRepo = "",
 [switch]$InstallMissingSdk
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if ($UnityAndroidPath) {
 if (!$SdkPath) { $SdkPath = Join-Path $UnityAndroidPath 'SDK' }
 if (!$JdkPath) { $JdkPath = Join-Path $UnityAndroidPath 'OpenJDK' }
}
if (!$SdkPath -or !$JdkPath) {
 $patterns = @('D:\Unity\Hub\Editor\*\Editor\Data\PlaybackEngines\AndroidPlayer',
 'D:\Unity Hub\Editor\*\Editor\Data\PlaybackEngines\AndroidPlayer',
 'D:\UnityEditor\*\Editor\Data\PlaybackEngines\AndroidPlayer',
 'C:\Unity\Hub\Editor\*\Editor\Data\PlaybackEngines\AndroidPlayer',
 'C:\Program Files\Unity\Hub\Editor\*\Editor\Data\PlaybackEngines\AndroidPlayer')
 foreach ($pattern in $patterns) {
  $candidate = Get-Item $pattern -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1
  if ($candidate) {
   if (!$SdkPath) { $SdkPath = Join-Path $candidate.FullName 'SDK' }
   if (!$JdkPath) { $JdkPath = Join-Path $candidate.FullName 'OpenJDK' }
   break
  }
 }
}
if (!$SdkPath -or !(Test-Path $SdkPath)) { throw 'Pass -SdkPath to your Android SDK, or -UnityAndroidPath to the Unity AndroidPlayer folder.' }
if (!$JdkPath -or !(Test-Path (Join-Path $JdkPath 'bin\javac.exe'))) { throw 'Pass -JdkPath to a full JDK 17 (recommended) or JDK 21. Java 26 cannot run the pinned Gradle 8.13 toolchain.' }
$java = Join-Path $JdkPath 'bin\java.exe'
# PowerShell 5 treats native stderr as an error record; java -version writes to stderr.
$oldPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$versionText = (& $java -version 2>&1 | Out-String)
$ErrorActionPreference = $oldPreference
if ($versionText -notmatch 'version "(17|21)\.') { throw "Use JDK 17 or 21 for this build. Found: $versionText" }
$wrapper = Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar'
if ((Get-FileHash $wrapper -Algorithm SHA256).Hash.ToLowerInvariant() -ne '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f') { throw 'Gradle wrapper checksum mismatch.' }
$originalJavaHome = $env:JAVA_HOME
$originalAndroidHome = $env:ANDROID_HOME
try {
 $env:JAVA_HOME = (Resolve-Path $JdkPath).Path
 $env:ANDROID_HOME = (Resolve-Path $SdkPath).Path
 # Properties-file format: escape ':' (a valid key/value separator) so strict parsers such as Android
 # Lint's PropertyEscape check don't flag the drive-letter colon in a Windows path.
 $sdkForward = $env:ANDROID_HOME.Replace('\','/').Replace(':','\:')
 [IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'),"sdk.dir=$sdkForward`n",[Text.Encoding]::ASCII)
 $platform = Join-Path $SdkPath 'platforms\android-36\android.jar'
 $tools = Join-Path $SdkPath 'build-tools\36.0.0\aapt.exe'
 if (!(Test-Path $platform) -or !(Test-Path $tools)) {
  if (!$InstallMissingSdk) { throw 'Missing Android API 36 and/or Build Tools 36.0.0. Re-run with -InstallMissingSdk or install them through Android Studio SDK Manager.' }
  $sdkManager = Get-ChildItem (Join-Path $SdkPath 'cmdline-tools') -Filter sdkmanager.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
  if (!$sdkManager) { throw 'SDK command-line tools not found. Install Android SDK Command-line Tools through Android Studio.' }
  & $sdkManager.FullName "--sdk_root=$SdkPath" --licenses
  if ($LASTEXITCODE -ne 0) { throw 'SDK license step failed.' }
  & $sdkManager.FullName "--sdk_root=$SdkPath" 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
  if ($LASTEXITCODE -ne 0) { throw 'SDK package installation failed.' }
 }
 if ($Mode -eq 'Release' -and !(Test-Path (Join-Path $projectRoot 'signing.properties'))) { throw 'Create signing.properties from its example and use your permanent signing key before making a release.' }
 if ($UpdateRepo -and $UpdateRepo -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'UpdateRepo must be owner/repository.' }
 Push-Location $projectRoot
 try {
  $tasks = switch ($Mode) { 'Release' { @('testDebugUnitTest','lintDebug','assembleRelease') }; 'Check' { @('testDebugUnitTest','lintDebug') }; default { @('testDebugUnitTest','lintDebug','assembleDebug') } }
  $gradleArgs = $tasks + @('--console=plain', '--no-daemon')
  if ($UpdateRepo) { $gradleArgs += "-Prollora.updateRepo=$UpdateRepo" }
  & .\gradlew.bat @gradleArgs
  if ($LASTEXITCODE -ne 0) { throw 'Build failed. See the first Gradle error above.' }
  if ($Mode -ne 'Check') { Write-Host "Built: app\build\outputs\apk\$($Mode.ToLowerInvariant())\" -ForegroundColor Green }
 } finally { Pop-Location }
} finally {
 $env:JAVA_HOME = $originalJavaHome
 $env:ANDROID_HOME = $originalAndroidHome
}
