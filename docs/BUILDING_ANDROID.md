# Building and Running on Android

This document describes how to build the Android host from a clean checkout.
It does not describe how to generate the game core; that host pipeline is
separate and currently not automated in this repository.

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

On 2026-11-08, on Windows with JDK 17, SDK 34, NDK 26.1.10909125, CMake 3.22.1:

- `testDebugUnitTest`: 44 tests, 0 failures.
- `lintDebug`: success (warnings only).
- `assembleDebug`: success for `arm64-v8a` and `x86_64`.

Runtime verification was not possible in that environment. See
`COMPATIBILITY.md` for details.

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
& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat -s SotcNative SotcGameCore GameImporter GameLibrary
```

Expected on a working device without a game core:

- `EGL 1.x initialized`
- `AAudio started: 48000 Hz, 2 channels` (when audio is enabled)
- The main screen reports `CORE NOT BUILT`
- Opening engine diagnostics shows the atmospheric diagnostic scene and a
  statistics overlay

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

## 7. Adding a recompiled game core (development only)

The host expects `libsotc_game.so` to be packaged in the APK's native library
directory. It cannot be loaded from writable game storage on Android 10+.

For a local development build:

1. Produce `libsotc_game.so` for `arm64-v8a` with the separate GPL-3.0
   Celosia/PS2Recomp host pipeline.
2. Place it at:

   ```
   app/src/main/jniLibs/arm64-v8a/libsotc_game.so
   ```

   The `.gitignore` excludes `*.so`, so it will not be committed accidentally.
3. Rebuild and install the debug APK.
4. Import the matching `SCUS-97472` image.
5. Launch the game and check `logcat` for the core name, serial, build ID, and
   ABI version.

The core must export:

```c
const SOTCGameApi* sotc_game_query_api(void);
```

and implement ABI version 1 from `app/src/main/cpp/sotc_game_api.h`.

Do not distribute a core unless its GPL-3.0 source and notices are handled
according to `THIRD_PARTY_LICENSES.md`.

## 8. Running on an emulator

The project builds an `x86_64` ABI for emulator testing, but the host used
during this milestone could not run the installed API 37 image:

```
FATAL | Available system RAM is not enough to run avd:
        'Medium_Phone_API_37.0'. Available: 3790 MiB, minimum required: 4096 MiB.
```

With sufficient RAM:

```powershell
$env:ANDROID_HOME = "C:\Users\<user>\AppData\Local\Android\Sdk"
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd <AVD_NAME> -no-snapshot -gpu swiftshader_indirect
& "$env:ANDROID_HOME\platform-tools\adb.exe" wait-for-device
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

An emulator verifies Android integration only. It does not verify ARM64
performance or native device behaviour.

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

The API 37 emulator requires at least 4 GB of host RAM. Use a machine with more
memory, a lower-requirement system image, or a physical device.

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
