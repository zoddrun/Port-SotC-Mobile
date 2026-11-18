# Building and Running on Android

> For the broader public build/install overview (including recompilation pipeline context), see [BUILDING.md](BUILDING.md). This document focuses on detailed Android host build, installation, and runtime verification steps.

This document describes how to build the Android host from a clean checkout.
It does not describe how to generate the playable game core/runtime; that host-side pipeline is separate and not yet complete in this project.

## 1. Prerequisites

| Tool | Version used | Notes |
| --- | --- | --- |
| JDK | 17 | AGP 8.5.2 is used; JDK 21+ may work but is not validated. |
| Android SDK Platform | 34 | `compileSdk 34`, `targetSdk 34`. |
| Android Build Tools | 34.0.0 | Installed with the SDK. |
| Android NDK | 26.1.10909125 | Declared in `app/build.gradle`. |
| CMake | 3.22.1 | Declared in `app/build.gradle`. |
| Gradle | 8.7 | Provided by the wrapper. |
| Android Gradle Plugin | 8.5.2 | |
| Kotlin | 1.9.20 | |
| Target device | Android 9 (API 28)+, `arm64-v8a` | |

The NDK and CMake can be installed through Android Studio's SDK Manager or
`sdkmanager`. No command-line tools are committed to the repository.

## 2. SDK configuration

Create `local.properties` in the repository root. It is intentionally untracked:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

On macOS/Linux the path is typically:

```properties
sdk.dir=/Users/<user>/Library/Android/sdk
```

Alternatively, set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) in the environment.

Set `JAVA_HOME` to a JDK 17 installation. On Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-17"
$env:ANDROID_HOME = "C:\Users\<user>\AppData\Local\Android\Sdk"
```

## 3. Build commands

From the repository root:

```powershell
# JVM unit tests
.\gradlew.bat testDebugUnitTest --console=plain

# Lint (fails on errors; warnings are currently allowed)
.\gradlew.bat lintDebug --console=plain

# Debug APK for arm64-v8a and x86_64
.\gradlew.bat assembleDebug --console=plain
```

On macOS/Linux use `./gradlew` instead.

Output:

```
app/build/outputs/apk/debug/app-debug.apk
```

The debug APK contains:

- `lib/arm64-v8a/libsotc_core.so`
- `lib/arm64-v8a/libc++_shared.so`
- `lib/x86_64/libsotc_core.so`
- `lib/x86_64/libc++_shared.so`

`arm64-v8a` is the shipping target. `x86_64` exists so the host can be
exercised on an emulator.

## 4. Verified build result

On 2026-11-09, on Windows with JDK 17, SDK 34, NDK 26.1.10909125, CMake 3.22.1:

- `testDebugUnitTest`: 49 tests, 0 failures.
- `lintDebug`: success (warnings only).
- `assembleDebug`: success for `arm64-v8a` and `x86_64`.

On 2026-11-18, after the launcher/settings/control work:

- `testDebugUnitTest`: 57 tests, 0 failures.
- `lintDebug`: success.
- `assembleDebug`: success for `arm64-v8a` and `x86_64`.
- APK badging: installed label `SOTC`, package `com.portsotc.mobile`, version `0.1.0`.

Runtime behaviour was also verified on an Android 9 (API 28) `x86_64` emulator;
see `COMPATIBILITY.md` section 10 for the exact checks. ARM64 hardware remains
unverified.

## 5. Installing on a device

Enable USB debugging on the Android device and connect it. Then:

```powershell
$env:ANDROID_HOME = "C:\Users\<user>\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Launch:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.portsotc.mobile/.MainActivity
```

Capture host logs:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat -s SotcNative SotcGameCore GameImporter GameLibrary SotcInput
```

Expected on a working device without a game core:

- `EGL 1.x initialized`
- `EGL config selected (RGBA8888)` and `EGL config: R8 G8 B8 A8 depth24 stencil8 samples0`
- `EGL surface ready: 1280x720, GL OpenGL ES 3.0 (...)`
- `AAudio started: 48000 Hz, 2 channels` (when audio is enabled)
- Debug builds log `Frame stats: ...` every five seconds while the game screen
  is running, including the audio callback count. If that line never appears,
  the render thread is not running.
- The launcher status card reports the install/core state; with game data but no
  native runtime it shows `Game data verified` and keeps Play disabled.
- Engine diagnostics are opened from Settings → Advanced → Open Engine
  Diagnostics and show the atmospheric diagnostic scene plus host statistics.

`SotcInput` logs every pad change in debug builds
(`Pad CROSS L(0.00, 0.00) R(0.00, 0.00)`) and the pause panel contents, which
makes touch and gamepad verification possible without UI dumps.

If the native library fails to load, the app remains usable and reports
`NATIVE UNAVAILABLE`.

## 6. Importing a disc image

1. Copy a legally obtained `SCUS-97472` ISO image to the device.
2. Open the app and choose **IMPORT DISC IMAGE**.
3. Pick the ISO through the system file picker.
4. Wait for copy, validation, and extraction. Progress and cancellation are
   available.
5. On success, the library shows the title, serial, SHA-256, and size.

The importer copies the image into app-private storage. The original document
is opened read-only and is never deleted or modified.

The import fails with a specific message if:

- The file is not ISO9660.
- `SYSTEM.CNF` is missing or malformed.
- The serial is not `SCUS-97472`.
- The boot file is not `SCUS_974.72`.
- The boot ELF is not a 32-bit little-endian MIPS `ET_EXEC`.
- A required module is missing or lacks the `xff2` magic.
- There is not enough free space.

### 6.1 Synthetic disc fixture for device QA

The repository never contains a game image. To exercise the whole import path
without one, generate a structurally valid synthetic ISO9660 image that uses the
real serial, boot file name, and module names:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*SyntheticDiscExportTest*' --console=plain
```

Output:

```
app/build/synthetic-disc/SCUS-97472.iso
```

The image is 256 KB, contains no copyrighted data, and is rejected by nothing in
the validator. To test on a device:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" push app\build\synthetic-disc\SCUS-97472.iso /sdcard/Download/SCUS-97472.iso
```

Then open the app, choose **IMPORT GAME**, use the system picker (for example
**Show roots > Downloads**), and select the file. A successful run shows
`Import complete`, `VALID YES`, serial `SCUS-97472`, and the extracted module
list. The published files appear under the app-private
`files/games/SCUS-97472/` directory, and the pushed copy in `Download/` must
remain untouched.

This fixture proves the importer, validator, and storage layout. It does not
prove that a real retail disc imports at multi-gigabyte size.

## 7. Adding a recompiled game core (development only)

Two different native artifacts can be packaged, and they are not interchangeable:

- `libsotc_game.so` — the ABI-v1 core used by the diagnostics host
  (`GameActivity`). It must export `sotc_game_query_api` (ABI version 1 from
  `app/src/main/cpp/sotc_game_api.h`).
- `libps2EntryRunner.so` — the gameplay runtime (D18). It is the recompiled
  `ps2EntryRunner` built as a raylib `NativeActivity` shared library, hosted by
  `GameNativeActivity` under `android.app.lib_name=ps2EntryRunner`.

Both must be packaged in the APK's native library directory. They cannot be
loaded from writable game storage on Android 10+. The `.gitignore` excludes
`*.so`, so they will not be committed accidentally.

For the gameplay runtime:

1. Complete the host pipeline, then cross-compile for `arm64-v8a`:
   `tools/capture-host/configure-android.sh` followed by
   `tools/capture-host/build-android-runner.sh`.
2. Package and verify the artifact:

   ```bash
   wsl -e bash tools/android-integration/package-runner.sh
   ```

   It checks the AArch64 machine type and the `ANativeActivity_onCreate`
   export, then copies the library to
   `app/src/main/jniLibs/arm64-v8a/libps2EntryRunner.so`.
3. Rebuild and install the debug APK.
4. Import the matching `SCUS-97472` image, then use Settings → Storage →
   `PREPARE GAME DATA` to extract the disc tree.
5. Play becomes enabled. Check `logcat` for `SOTC_BOOT_ELF`, the runtime's
   boot messages, and the first picture.

The runtime reports its progress with `std::cout` / `std::cerr`, and Android
discards the stdio of a framework-started app. `GameNativeActivity` therefore
redirects the process's stdout and stderr to a file before the runner library
loads:

```
/data/data/com.portsotc.mobile/files/logs/game-<yyyyMMdd-HHmmss>.log
```

Read it with `adb shell run-as com.portsotc.mobile cat files/logs/game-<stamp>.log`
or pull it out. Each launch creates a new file, so a crash keeps its own trace.
If the redirection fails, `GameNativeActivity` logs the failure and only the
in-memory runtime console remains available.

On the device, Settings → Advanced → `GAME LOG` shows the newest session file:
`VIEW LATEST GAME LOG` opens its last 64 KiB in a selectable monospace dialog
and `COPY LATEST GAME LOG` puts the same text on the clipboard. That is the
fastest way to read a failed boot without a PC.

For the diagnostics core, produce `libsotc_game.so` for `arm64-v8a` with the
separate GPL-3.0 Celosia/PS2Recomp host pipeline and place it at
`app/src/main/jniLibs/arm64-v8a/libsotc_game.so`.

Do not distribute either artifact unless its GPL-3.0 source and notices are
handled according to `THIRD_PARTY_LICENSES.md`.

## 8. Running on an emulator

The project builds an `x86_64` ABI for emulator testing. The configuration below
was used to verify the host at runtime:

```powershell
$env:ANDROID_HOME = "C:\Users\<user>\AppData\Local\Android\Sdk"

# One-time AVD creation (Android 9, x86_64, software GPU friendly)
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" `
    "system-images;android-28;default;x86_64"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd `
    -n Sotc_API_28 -k "system-images;android-28;default;x86_64" -d pixel_2

# Start the emulator
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd Sotc_API_28 `
    -no-snapshot -no-boot-anim -no-window `
    -gpu swiftshader_indirect -memory 1536 -feature GLESDynamicVersion

& "$env:ANDROID_HOME\platform-tools\adb.exe" wait-for-device
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.portsotc.mobile/.MainActivity
```

`-feature GLESDynamicVersion` is required on this setup. Without it the guest
reports GLES 2.0 (`ro.opengles.version=0x00020000`) and the GLES 3.0 EGL config
cannot be selected.

Optional display tuning for small headless sessions:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell wm size 720x1280
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell wm density 320
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell settings put secure immersive_mode_confirmations confirmed
```

The game and touch editor screens use landscape orientation, so input
coordinates for those screens are in the 1280x720 landscape frame.

An emulator verifies Android integration only. It does not verify ARM64
performance or native device behaviour. SwiftShader also makes frame rates
unrepresentative; use the on-screen statistics for relative comparisons only.

## 9. Troubleshooting

### `SDK location not found`

Create `local.properties` or export `ANDROID_HOME`.

### `Unsupported class file major version` / AGP requires Java 17

Point `JAVA_HOME` at JDK 17 and reopen the terminal. Verify with
`$env:JAVA_HOME` and `.\gradlew.bat --version`.

### `resource style/Text ... not found`

The dotted styles `Text.Title`, `Text.Subtitle`, `Text.Body`, and `Text.Section`
inherit from the base `Text` style in `res/values/themes.xml`. Keep the base
style present.

### Lint fails on `readNBytes`

`InputStream.readNBytes` is API 33+. The project supports API 28 and uses
`disc/IoUtil.kt` (`readAtMost` / `readExactly`) instead. Do not reintroduce
direct `readNBytes` calls.

### CMake cannot find the NDK or CMake version

Install NDK `26.1.10909125` and CMake `3.22.1`, or update the versions in
`app/build.gradle` and `app/src/main/cpp/CMakeLists.txt` together.

### Emulator refuses to start due to RAM

The API 37 emulator requires at least 4 GB of host RAM. Use an older, lighter
system image (the verified setup uses Android 9 / API 28 `x86_64` with
`-memory 1536`), or use a physical device.

### Emulator shows only GLES 2.0 and EGL config selection fails

Start the emulator with `-feature GLESDynamicVersion`. Check the guest value
with:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell getprop ro.opengles.version
```

`196608` is `0x30000` (GLES 3.0).

### `uiautomator dump` reports "could not get idle state" on the game screen

The render surface and statistics overlay keep the UI busy, so the hierarchy
cannot always be dumped while the game activity is running. Use the debug
`SotcNative` / `SotcInput` logcat lines instead; they report frame statistics,
pad changes, and pause panel contents.

### `dlopen` fails for a side-loaded core

Android 10+ blocks executable code in writable app storage. Put the library in
`app/src/main/jniLibs/<abi>/` and rebuild; do not copy it into the game
directory.

### Import succeeds but the game screen says the core is not built

This is expected without `libsotc_game.so`. The diagnostic renderer is not
gameplay.

## 10. Useful Gradle tasks

```powershell
.\gradlew.bat tasks                 # list tasks
.\gradlew.bat clean                 # remove build outputs
.\gradlew.bat assembleRelease       # release APK (signing not configured)
.\gradlew.bat testDebugUnitTest     # JVM tests
.\gradlew.bat lintDebug             # Android lint
```

`assembleRelease` currently builds an unsigned release variant because no
signing configuration is committed. Configure a keystore outside the repository
before producing a distributable release.
