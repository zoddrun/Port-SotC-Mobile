# First-boot capture for an ARM64 Android device.
#
# The ARM64 game core can only be exercised on real arm64 hardware: the
# recompiled runtime is packaged as an arm64-v8a NativeActivity library, and
# the emulators on this host cannot load it. This script does the mechanical
# part of a first boot and leaves an evidence bundle behind:
#
#   build/device/<stamp>/logcat.txt      full logcat from the launch
#   build/device/<stamp>/game.log        the runner's own stdio log, if any
#   build/device/<stamp>/screen.png      screenshot after the wait
#   build/device/<stamp>/device.txt      model, ABI, Android version, package
#
# Usage (from the repository root, PowerShell):
#   powershell -ExecutionPolicy Bypass -File tools/device/first-boot.ps1
#   powershell -ExecutionPolicy Bypass -File tools/device/first-boot.ps1 -Phase Game
#
# -Phase Launcher (default) installs the debug APK, starts MainActivity and
# captures the launcher state. -Phase Game skips the install and starts
# GameNativeActivity directly, which is what produces the runner log. Run
# -Phase Game only after the disc tree exists (Settings -> Storage ->
# PREPARE GAME DATA); without it the activity closes defensively by design.
#
# The script never fakes a result: if no device is attached, if the device is
# not arm64, or if the install fails, it stops and says so.

param(
    [ValidateSet('Launcher', 'Game')]
    [string]$Phase = 'Launcher',
    [int]$WaitSeconds = 25,
    [string]$Apk = 'app\build\outputs\apk\debug\app-debug.apk'
)

$ErrorActionPreference = 'Stop'
$Package = 'com.portsotc.mobile'
$Repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Fail([string]$Message) {
    Write-Host "FIRST_BOOT_FAILED: $Message" -ForegroundColor Red
    exit 1
}

function Resolve-Adb {
    if ($env:ADB) { return $env:ADB }
    $candidates = @()
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe') }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }
    Fail 'adb not found. Set ADB or ANDROID_SDK_ROOT.'
}

$adb = Resolve-Adb
$devices = & $adb devices | Select-String -Pattern "`tdevice$"
if (-not $devices) {
    & $adb devices
    Fail 'no authorized device. Connect the phone, enable USB debugging and accept the RSA prompt.'
}
if (@($devices).Count -gt 1) {
    Fail 'more than one device attached; disconnect the others or set ANDROID_SERIAL.'
}

$abi = (& $adb shell getprop ro.product.cpu.abi).Trim()
if ($abi -notlike 'arm64*') {
    Fail "device ABI is '$abi', not arm64. The packaged runner is arm64-v8a only."
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $Repo "build\device\$stamp"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

@(
    "model=$((& $adb shell getprop ro.product.model).Trim())"
    "device=$((& $adb shell getprop ro.product.device).Trim())"
    "abi=$abi"
    "android=$((& $adb shell getprop ro.build.version.release).Trim())"
    "sdk=$((& $adb shell getprop ro.build.version.sdk).Trim())"
    "phase=$Phase"
) | Set-Content -Encoding ASCII (Join-Path $outDir 'device.txt')

Write-Host "device   $((& $adb shell getprop ro.product.model).Trim()) ($abi, API $((& $adb shell getprop ro.build.version.sdk).Trim()))"
Write-Host "evidence $outDir"

if ($Phase -eq 'Launcher') {
    if (-not (Test-Path (Join-Path $Repo $Apk))) {
        Fail "APK not found: $Apk. Run .\gradlew.bat assembleDebug first."
    }
    & $adb install -r -d (Join-Path $Repo $Apk) | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail 'adb install failed' }

    # The runner is packaged per ABI; confirm the APK actually carries one.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $Repo $Apk))
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq 'lib/arm64-v8a/libps2EntryRunner.so' }
    } finally {
        $zip.Dispose()
    }
    if (-not $entry) {
        Fail 'the APK has no lib/arm64-v8a/libps2EntryRunner.so; Play will stay disabled.'
    }
    Write-Host "runner   libps2EntryRunner.so in the APK ($($entry.Length) bytes)"

    & $adb logcat -c
    & $adb shell am start -n "$Package/.MainActivity" | Out-Host
} else {
    & $adb logcat -c
    & $adb shell am start -n "$Package/.GameNativeActivity" | Out-Host
}

Write-Host "waiting  $WaitSeconds s"
Start-Sleep -Seconds $WaitSeconds

& $adb logcat -d -v threadtime | Set-Content -Encoding UTF8 (Join-Path $outDir 'logcat.txt')
& $adb exec-out screencap -p > (Join-Path $outDir 'screen.png')

# The runner's stdio goes to <files>/logs/game-*.log (see GameNativeActivity).
$gameLogs = & $adb shell "run-as $Package ls files/logs 2>/dev/null"
if ($gameLogs) {
    $newest = ($gameLogs -split "`n" | Where-Object { $_ -match '^game-.*\.log$' } | Sort-Object | Select-Object -Last 1)
    if ($newest) {
        $newest = $newest.Trim()
        & $adb exec-out run-as $Package cat "files/logs/$newest" > (Join-Path $outDir 'game.log')
        Write-Host "game log $newest ($((Get-Item (Join-Path $outDir 'game.log')).Length) bytes)"
    }
}

$crashes = Select-String -Path (Join-Path $outDir 'logcat.txt') -Pattern 'FATAL EXCEPTION|Fatal signal|AndroidRuntime: |UnsatisfiedLinkError' -SimpleMatch:$false
if ($crashes) {
    Write-Host ''
    Write-Host 'crashes found in logcat:' -ForegroundColor Yellow
    $crashes | Select-Object -First 12 | ForEach-Object { Write-Host "  $($_.Line)" }
} else {
    Write-Host 'no crash signature in logcat'
}

Write-Host ''
Write-Host "FIRST_BOOT_CAPTURED: $outDir"
if ($Phase -eq 'Launcher') {
    Write-Host 'next: import the SCUS-97472 image, run Settings -> Storage -> PREPARE GAME DATA,'
    Write-Host '      then re-run this script with -Phase Game to capture the runner log.'
}
