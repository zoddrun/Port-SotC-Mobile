# Building from Source

This document describes how to build and test the **Shadow of the Colossus Mobile** Android host from source, and what is additionally required to produce a playable native game runtime/core.

> **Important:** Building the Android APK from this repository does **not** produce a playable game. Gameplay requires a separately generated, compatible recompiled game runtime/core (Celosia/PS2Recomp pipeline) and verified runtime integration.

## 1. Repository components

| Component | Location | Purpose |
| --- | --- | --- |
| Android app + native host | `app/`, Gradle files | Kotlin UI/import/input/lifecycle and C++20/NDK host (EGL/GLES 2-3, AAudio, JNI, diagnostics, core loading) |
| Project tools | `tools/` | Utilities such as ISO inspection/validation helpers |
| Pipeline/runtime integration materials | `tools/capture-host/`, `tools/android-integration/`, `docs/RECOMPILATION_PIPELINE.md` | Host-side recompilation/integration scripts, patches, and documentation |
| Third-party checkouts (local, gitignored) | `third_party/celosia/`, `third_party/PS2Recomp/` | Upstream pipeline/framework sources used for recompilation work (not redistributed here as committed binaries/game data) |

## 2. Build the Android host APK

### 2.1 Development dependencies

| Tool | Version used / required | Notes |
| --- | --- | --- |
| JDK | 17 | Required for the current Gradle/AGP configuration (validated with JDK 17). |
| Android SDK Platform | 34 | `compileSdk 34`, `targetSdk 34`. |
| Android Build Tools | 34.x (SDK-managed) | Install via Android SDK Manager. |
| Android NDK | `26.1.10909125` | Declared in `app/build.gradle`. |
| CMake | `3.22.1` | Declared in `app/build.gradle` / native build config. |
| Gradle Wrapper | 8.7 | Included (`gradlew`, `gradlew.bat`). |
| Android Gradle Plugin | 8.5.2 | Declared in project Gradle config. |
| Kotlin | 1.9.20 | Declared in project Gradle config. |
| Python | 3.x (optional for tools) | `tools/inspect_original_iso.py` uses Python standard library only. |

### 2.2 SDK configuration

Create an untracked `local.properties` in the repository root:

```properties
sdk.dir=/path/to/Android/Sdk
```

Windows example:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

Alternatively, set `ANDROID_HOME` / `ANDROID_SDK_ROOT`. Ensure `JAVA_HOME` points to JDK 17.

### 2.3 Build commands

From the repository root:

```bash
# JVM unit tests
./gradlew testDebugUnitTest --console=plain

# Android lint
./gradlew lintDebug --console=plain

# Debug APK
./gradlew assembleDebug --console=plain
```

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-17"
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

**Debug APK output:** `app/build/outputs/apk/debug/app-debug.apk`

The debug build is configured for `arm64-v8a` (shipping target) and `x86_64` (emulator/integration testing). `x86_64` is not a supported device/gameplay target.

### 2.4 Release build notes

```bash
./gradlew assembleRelease --console=plain
```

- No signing configuration is committed. Configure a keystore **outside the repository** before producing a distributable release.
- Do not commit signing secrets (`*.jks`, `*.keystore`, passwords, service credentials).

## 3. Install and test the Android host

### 3.1 Install on a device/emulator

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.portsotc.mobile/.MainActivity
```

Useful logs:

```bash
adb logcat -s SotcNative SotcGameCore GameImporter GameLibrary SotcInput
```

On a working host without a game core, the app should report its native/core status explicitly and can show diagnostics rather than gameplay.

### 3.2 Verified host-build state (historical)

As documented on **2026-11-09** (development host): `testDebugUnitTest` (49 tests), `lintDebug`, and `assembleDebug` (arm64-v8a + x86_64) succeeded; emulator integration checks were performed on Android 9 (API 28) x86_64 with SwiftShader. The current working build (2026-11-18) passes 57 unit tests plus lint and `assembleDebug`. See [COMPATIBILITY.md](COMPATIBILITY.md) for exact scope and limitations. **ARM64 hardware execution remains unverified.**

## 4. Recompilation pipeline and game runtime/core (separate host-side work)

> This is **not** part of the Android Gradle build and cannot be performed on-device.

To produce a playable native port for the supported release (`SCUS-97472`), the project depends on a static recompilation pipeline (Celosia/PS2Recomp) and compatible runtime integration. High-level stages include:

1. Obtain a legally owned `SCUS-97472` disc image (never committed).
2. Set up the host pipeline environment (WSL2/Linux workflow is used for the Celosia pipeline; see [RECOMPILATION_PIPELINE.md](RECOMPILATION_PIPELINE.md) and `docs/agent/` records).
3. Extract/configure/build recompilation tools and generate recompiled code (boot stage, captures, relinking, VU1 steps as applicable).
4. ARM64 cross-compile the runner/runtime for Android (NDK toolchain; scripts/prep documented under `tools/capture-host/` and agent docs, e.g. M4 steps).
5. Package/integrate the runtime into the Android app (planned D18 path: raylib-owned NativeActivity runner, pre-launch disc extraction, input bridge), then verify on emulator/device.

**Current pipeline status:** M3 is not complete and, based on the latest documented audit (2026-11-18), requires manual verification/recovery in the WSL workspace before M4/M5 can proceed. Do not document or claim pipeline completion without evidence.

### Required inputs (not included)

- Legally obtained original game disc image (`SCUS-97472`) supplied by the user/developer locally.
- Upstream GPL-3.0 pipeline/runtime sources (Celosia/PS2Recomp) obtained separately and used according to their licenses.
- Appropriate host tools and environment for the pipeline stages.

## 5. Testing guidance

| Level | Command / method | What it covers |
| --- | --- | --- |
| JVM unit tests | `./gradlew testDebugUnitTest` | Disc parsing/validation helpers, settings/input/storage logic as covered by existing tests. |
| Lint | `./gradlew lintDebug` | Static Android lint checks. |
| Debug APK build | `./gradlew assembleDebug` | Kotlin + native host compilation/packaging. |
| Emulator/device runtime checks | Install APK, exercise UI/import/diagnostics, inspect `logcat` | Android integration behavior (not ARM64 gameplay proof unless on ARM64 hardware with a real core). |
| ISO validation tool | `python tools/inspect_original_iso.py` | Host-side inspection mirroring validator logic (requires local ISO path/config as implemented). |

Only document test results that have actually been observed in the stated environment.

## 6. Troubleshooting build issues

- **SDK/NDK/CMake not found:** verify `local.properties`, SDK packages, and exact NDK/CMake versions.
- **Java version errors:** ensure JDK 17 is active for Gradle.
- **Native build failures:** confirm NDK/CMake installation and ABI/toolchain configuration; clean/rebuild if needed.
- **WSL/pipeline issues:** follow `docs/RECOMPILATION_PIPELINE.md`, `tools/capture-host/README.md`, and `docs/agent/` recovery notes (per-boot swap/Xvfb prerequisites, space-free ext4 paths, no `celosia setup` when it would discard current build state, etc.).

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) and [BUILDING_ANDROID.md](BUILDING_ANDROID.md) for additional host-build details.