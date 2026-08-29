<#
    rebuild.ps1 - build Four in a Row and publish it so the phone updates itself.

        powershell -ExecutionPolicy Bypass -File tools\rebuild.ps1

    Same delivery route as Benmoji: the phone runs Obtainium, Obtainium watches this
    repo's GitHub releases, and publishing a release IS the delivery step. That only
    works if two things stay true, and both are handled here so neither can be forgotten:

      1. versionCode goes UP every release, or Android does not see an update at all, and
      2. every APK is signed with the SAME key, or Android refuses to install over the
         copy already on the phone.

    Switches:
      -NoPublish   build and verify the signed APK, but create no GitHub release.
      -TestOnly    run the unit tests and stop. Nothing is built or published.
#>
[CmdletBinding()]
param(
    [switch]$NoPublish,
    [switch]$TestOnly
)

$ErrorActionPreference = 'Stop'

$Project   = Split-Path $PSScriptRoot -Parent
$Android   = Join-Path $Project 'android'
$Toolchain = Join-Path $env:LOCALAPPDATA 'AndroidBuild'
$VersionF  = Join-Path $Android 'version.properties'
$KeyProps  = Join-Path $Android 'keystore.properties'
$Gradle    = Join-Path $Toolchain 'gradle\bin\gradle.bat'

$env:JAVA_HOME    = Join-Path $Toolchain 'jdk'
$env:ANDROID_HOME = Join-Path $Toolchain 'sdk'

function Step($n, $text) { Write-Host "`n[$n] $text" -ForegroundColor Cyan }

# ---- 1. tests -----------------------------------------------------------
# The board and the three difficulty levels are plain Kotlin with no Android types, so
# the whole game is covered here in seconds. A failure stops the build: a release that
# ships a broken win check is worse than no release.
Step 1 'Running the unit tests'
Push-Location $Android
try {
    & $Gradle :app:testDebugUnitTest --no-daemon --console=plain |
        Select-String -Pattern 'BUILD|tests completed|FAILED|^e: '
    if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed. Nothing was built.' }
} finally { Pop-Location }

if ($TestOnly) {
    Write-Host "`nTests only - stopping before the build." -ForegroundColor Yellow
    return
}

if (-not (Test-Path $KeyProps)) {
    throw "No signing key configured ($KeyProps is missing). See signing\README in the " +
          "OneDrive FourInARow folder. Without the ORIGINAL key a new build cannot " +
          "install over the copy already on the phone."
}

# ---- 2. bump the version ------------------------------------------------
# Before the build, so the number baked into the APK is the one published.
Step 2 'Bumping the version'
$verText = Get-Content $VersionF -Raw
$oldCode = [int]([regex]::Match($verText, '(?m)^versionCode=(\d+)$').Groups[1].Value)
$newCode = $oldCode + 1
$verName = [regex]::Match($verText, '(?m)^versionName=(.+)$').Groups[1].Value.Trim()
$verText = [regex]::Replace($verText, '(?m)^versionCode=\d+$', "versionCode=$newCode")
Set-Content -Path $VersionF -Value $verText -NoNewline
Write-Host "  versionCode $oldCode -> $newCode   (versionName $verName)"

# ---- 3. build -----------------------------------------------------------
Step 3 'Building the signed APK'
Push-Location $Android
try {
    & $Gradle assembleRelease --no-daemon --console=plain |
        Select-String -Pattern 'BUILD|^e: |error:|FAILURE'
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
} finally { Pop-Location }

$built = Join-Path $Android 'app\build\outputs\apk\release\app-release.apk'
$named = Join-Path $Project "FourInARow-$verName.apk"
Copy-Item $built $named -Force

# Confirm it really carries the release certificate before anything is published. An
# unsigned or wrongly-signed APK installs nowhere.
# -join first: apksigner prints many lines, and PowerShell's -match against an array
# FILTERS it rather than returning a boolean, so the test would pass on any line matching.
$verify = (& (Join-Path $Toolchain 'sdk\build-tools\35.0.0\apksigner.bat') verify --print-certs $built 2>&1) -join "`n"
if ($verify -notmatch 'CN=Ben Bershad') { throw "APK is not signed with the release key.`n$verify" }
Write-Host '  signature verified (CN=Ben Bershad)'

# The app claims to be ad-free on its own menu screen. Prove it every build rather than
# trusting that nobody added a dependency that drags INTERNET back in via manifest merge.
$badging = (& (Join-Path $Toolchain 'sdk\build-tools\35.0.0\aapt2.exe') dump permissions $built 2>&1) -join "`n"
if ($badging -match 'android.permission.INTERNET') {
    throw "The APK requests INTERNET. Something added a networking dependency - fix that " +
          "before publishing, the menu screen promises there is no network access."
}
Write-Host '  no INTERNET permission (still provably ad-free)'

if ($NoPublish) {
    $mb = (Get-Item $named).Length / 1MB
    Write-Host ("`nBuilt but not published: {0}  ({1:N1} MB)" -f $named, $mb) -ForegroundColor Yellow
    return
}

# ---- 4. publish ---------------------------------------------------------
Step 4 'Publishing the GitHub release'
$tag = "v$verName+$newCode"
Push-Location $Project
try {
    # Commit the bump first so the tag points at source that actually says $newCode.
    & git add android/version.properties
    & git commit -m "Release $verName (versionCode $newCode)" --quiet
    & git push --quiet
    if ($LASTEXITCODE -ne 0) { throw 'git push failed - not publishing a release with no matching commit.' }

    & gh release create $tag $named --title "Four in a Row $verName" --notes "versionCode $newCode."
    if ($LASTEXITCODE -ne 0) { throw 'gh release failed.' }
} finally { Pop-Location }

$mb = (Get-Item $named).Length / 1MB
Write-Host ("`nDone. Published {0}  ({1:N1} MB) as {2}" -f (Split-Path $named -Leaf), $mb, $tag) -ForegroundColor Green
Write-Host 'Open Obtainium on your phone and pull to refresh - the update will be there.'
