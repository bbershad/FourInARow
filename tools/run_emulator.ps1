<#
    run_emulator.ps1 - start the Android emulator and put Four in a Row on it.

        powershell -ExecutionPolicy Bypass -File tools\run_emulator.ps1

    Started with Start-Process on purpose, so the emulator is NOT a child of whatever
    shell launched it. Run as a background job it gets reaped when the job ends, and the
    window disappears mid-session with a clean shutdown and no error to explain it.

    Notes on this VM:
      - No GPU, so rendering is software (-gpu swiftshader_indirect). Everything is slow;
        that is the harness, not the app.
      - "System UI isn't responding" shortly after boot is the emulator's own launcher
        crashing under software rendering. Tap Wait. It is not this app.
      - The FIRST `adb install` after a boot often fails with a StorageManager
        allocateBytes stack trace. That is a boot race, so this script just retries.

    Switches:
      -Debug   install the debug APK from the build tree instead of the release APK.
#>
[CmdletBinding()]
param(
    [switch]$Debug
)

$ErrorActionPreference = 'Stop'

$Project   = Split-Path $PSScriptRoot -Parent
$Toolchain = Join-Path $env:LOCALAPPDATA 'AndroidBuild'
$Sdk       = Join-Path $Toolchain 'sdk'
$Adb       = Join-Path $Sdk 'platform-tools\adb.exe'
$Avd       = 'fourinarow'
$Package   = 'com.bershad.fourinarow'

$apk = if ($Debug) {
    Join-Path $Project 'android\app\build\outputs\apk\debug\app-debug.apk'
} else {
    Join-Path $Project 'FourInARow-1.0.apk'
}
if (-not (Test-Path $apk)) { throw "APK not found: $apk  (run tools\rebuild.ps1 first)" }

$env:ANDROID_HOME     = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

if (Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue) {
    Write-Host 'Emulator already running.' -ForegroundColor Yellow
} else {
    Write-Host 'Starting the emulator (first boot takes about five minutes here)...' -ForegroundColor Cyan
    Start-Process -FilePath (Join-Path $Sdk 'emulator\emulator.exe') `
        -ArgumentList @('-avd', $Avd, '-no-audio', '-no-boot-anim',
                        '-gpu', 'swiftshader_indirect', '-no-snapshot-save')
}

Write-Host 'Waiting for Android to come up...' -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(10)
do {
    Start-Sleep -Seconds 15
    $ready = (& $Adb shell pm list packages 2>&1 | Out-String) -match 'package:android'
} while (-not $ready -and (Get-Date) -lt $deadline)
if (-not $ready) { throw 'Emulator did not finish booting in ten minutes.' }

# Retry: the first install after a boot loses a race with the package manager.
$installed = $false
foreach ($attempt in 1..4) {
    $out = (& $Adb install -r $apk 2>&1) -join "`n"
    if ($out -match 'Success') { $installed = $true; break }
    Write-Host "  install attempt $attempt lost the boot race, retrying..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 10
}
if (-not $installed) { throw "Could not install $apk" }

& $Adb shell am start -n "$Package/.MainActivity" | Out-Null
$code = (& $Adb shell dumpsys package $Package 2>&1 | Select-String 'versionCode=(\d+)').Matches.Groups[1].Value
Write-Host "`nFour in a Row (versionCode $code) is running in the emulator." -ForegroundColor Green
Write-Host 'If "System UI isn''t responding" appears, tap Wait - that is the emulator, not the game.'
