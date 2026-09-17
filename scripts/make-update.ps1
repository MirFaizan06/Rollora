[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][string]$Apk,
 [Parameter(Mandatory=$true)][string]$Repo,
 [Parameter(Mandatory=$true)][string]$Tag,
 [Parameter(Mandatory=$true)][string]$SdkPath,
 [string]$Notes = 'Maintenance update.',
 [string]$Out = 'update.json'
)
$ErrorActionPreference='Stop'
if ($Repo -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$' -or $Tag -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Use owner/repo and a simple release tag such as v1.0.1.' }
$file=Get-Item $Apk
if ($file.Length -gt 150MB) { throw 'APK exceeds updater size limit.' }
$aapt=Join-Path $SdkPath 'build-tools\36.0.0\aapt.exe'
$badging=(& $aapt dump badging $file.FullName | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Could not read APK metadata.' }
if ($badging -notmatch "package: name='design.techbytes.rollora' versionCode='([0-9]+)' versionName='([^']+)'") { throw 'Use a release APK with the production package name.' }
$code=[long]$Matches[1];$name=$Matches[2]
if ($badging -notmatch "sdkVersion:'([0-9]+)'") { throw 'Cannot read minSdk.' }
$minSdk=[int]$Matches[1]
$result=[ordered]@{versionCode=$code;versionName=$name;apkUrl="https://github.com/$Repo/releases/download/$Tag/$([Uri]::EscapeDataString($file.Name))";sha256=(Get-FileHash $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant();bytes=$file.Length;minSdk=$minSdk;notes=$Notes}
$json=$result | ConvertTo-Json
[IO.File]::WriteAllText((Join-Path (Get-Location) $Out),$json,(New-Object Text.UTF8Encoding($false)))
Write-Host "Created $Out. Upload it and $($file.Name) to the same published GitHub Release."
