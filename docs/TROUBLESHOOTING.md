# Troubleshooting

This guide covers **actual, project-relevant** issues for building, installing, importing, and running the current Shadow of the Colossus Mobile host app. Because the project is not playable yet, some behaviors (such as "core not built" / diagnostics instead of gameplay) are expected.

## 1. Expected behavior in current development state

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| App says native game core/runtime is not built/available | No verified packaged game core/runtime is integrated yet | Expected. The app must not show fake gameplay. Track progress in [README.md](../README.md), [ROADMAP.md](ROADMAP.md), and [COMPATIBILITY.md](COMPATIBILITY.md). |
| Game screen shows diagnostics/status instead of gameplay | Host diagnostics path running without a playable core | Expected in current state. |
| Import succeeds but game is still not playable | Import validation/storage is separate from runtime availability | Expected until a compatible native core/runtime is packaged and verified. |

## 2. Build and environment issues (Android host)

### SDK/NDK/CMake not found

- **Symptoms:** Gradle/CMake errors about missing SDK, NDK, or CMake.
- **Checks/fixes:**
  - Create `local.properties` with a valid `sdk.dir`, or set `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
  - Install Android SDK Platform 34, NDK `26.1.10909125`, and CMake `3.22.1` (versions used by this project).
  - Re-sync/rebuild after installation.

### JDK / Java version mismatch

- **Symptoms:** Gradle/AGP errors such as unsupported class file version or Java 17 requirement failures.
- **Fix:** Set `JAVA_HOME` to JDK 17 and verify with `java -version` / `./gradlew --version`.

### Native build fails

- **Symptoms:** CMake/Ninja/NDK compilation or configuration errors.
- **Checks/fixes:**
  - Confirm NDK and CMake versions match project configuration.
  - Run a clean build (`./gradlew clean assembleDebug`) if toolchain state is inconsistent.
  - Ensure required SDK packages are fully installed.

### Emulator cannot start or is too resource-heavy

- **Symptoms:** AVD fails due to host RAM/resources.
- **Notes:** Development/runtime host verification has used lighter emulator configurations (e.g. Android 9 / API 28 x86_64 with software rendering in documented tests). Use a configuration appropriate for your host machine; emulator results do not validate ARM64 device behavior.

## 3. Installation and runtime issues

### APK will not install

- Confirm the device runs **Android 9 (API 28)+**.
- Confirm device ABI compatibility (`arm64-v8a` target; debug APKs may also include `x86_64` for emulator use).
- Re-download the APK if the file may be incomplete (when releases exist).
- Enable installation from the selected source if Android blocks it.

### App crashes or native host unavailable

- Collect `adb logcat` (relevant tags include `SotcNative`, `SotcGameCore`, `GameImporter`, `GameLibrary`, `SotcInput`).
- If native libraries fail to load, the app should report status instead of silently pretending to work; report the exact message/logs.
- Reinstall a clean debug/release build from a known-good source.

### Gameplay window exits or shows no picture

- On the device, Settings -> Advanced shows the newest runtime log: `VIEW
  LATEST GAME LOG` opens it, `COPY LATEST GAME LOG` puts it on the clipboard.
- The recompiled runtime writes its own console output to
  `/data/data/com.portsotc.mobile/files/logs/game-<yyyyMMdd-HHmmss>.log`
  (one file per launch). Read the newest with:
  `adb shell run-as com.portsotc.mobile ls -t files/logs` then
  `adb shell run-as com.portsotc.mobile cat files/logs/<name>`.
- A missing boot path, a missing runner library or a failed disc-tree check is
  logged by `GameNativeActivity` before the runtime starts; `logcat` shows the
  exact reason.
- The Play button is disabled when the runner library is not packaged or the
  disc tree is not prepared. Use Settings → Storage → `PREPARE GAME DATA`.

### EGL/GLES or audio initialization issues

- The manifest floor is **OpenGL ES 2.0**. The host requests an OpenGL ES 3 config and context first and falls back to OpenGL ES 2 automatically when the driver rejects ES 3; the log shows which path was taken (`Renderer backend: OpenGL ES 3.0` or `OpenGL ES 2.0`).
- If a device misbehaves on the ES 3 path, Settings → Graphics → `Force OpenGL ES 2.0` skips the ES 3 attempt for the next session.
- Every shader is GLSL ES 1.00 and no vertex array objects are used, so both context versions run the same code path.
- `Renderer backend: unavailable` plus `Unable to create an OpenGL ES context` means neither ES 3 nor ES 2 produced a usable config/context/surface; that is a driver or emulator problem, not a missing setting. Settings → Advanced prints the driver's own vendor, renderer, version and GLSL strings from a real offscreen context.
- On emulators, GLES capability depends on the emulator image and GPU settings. The Android 9 (API 28) `x86_64` image used for verification reports OpenGL ES 2.0 only and exercises the fallback path.
- Vulkan is not implemented in this build; there is no Vulkan backend to select.
- Audio issues may be device/emulator/backend specific; check logs for AAudio startup/callback messages.

## 4. Disc image import issues

### Image rejected as unsupported or invalid

- **Verify the target release:**
  - USA retail *Shadow of the Colossus*
  - Serial: `SCUS-97472`
  - Boot executable: `SCUS_974.72`
  - Required modules: `KERNEL.XFF`, `MANAGER.XFF`, `GAMECORE.XFF`
- Do not use PAL/Japanese/preview/translated/repacked/modified images.
- Validation is content-based; renaming a file to `.iso` is not sufficient.

### Import fails or runs out of space

- Ensure enough free app/device storage for a private copy of the ISO and extracted data (multi-GB images require significant space).
- Retry after freeing space. The importer is designed to use staging/atomic publish and avoid leaving a broken installed state.

### Import cancelled or interrupted

- Re-run the import. Recovery logic is intended to clean interrupted staging data and preserve valid installed data/saves where applicable.

### SAF/file picker problems

- Ensure the ISO is accessible to Android's document picker/provider.
- Try copying the ISO to local device storage if a remote/provider source is unreliable.
- Very large transfers via some providers/devices may be slower; wait for progress to complete unless you intentionally cancel.

## 5. Controls issues

- **Touch controls not behaving as expected:** verify layout/editor settings and reset layout if needed. In-game behavior cannot be fully validated until runtime integration is complete.
- **Gamepad not detected/mapped correctly:** confirm Android recognizes the controller, reconnect/re-pair it, and check controller settings/mapping in the app. Physical hardware verification is limited/pending.
- **Rumble not working:** vibration support depends on device/controller/runtime path and is not yet verified in gameplay.

## 6. WSL recompilation pipeline issues (developers)

These apply to host-side pipeline work (not end users installing an APK):

- **`wsl -e`/commands hang or time out under load:** inspect filesystem evidence and logs; avoid destructive recovery until you confirm no active build.
- **Xvfb/display issues:** WSLg can remount `/tmp/.X11-unix` read-only after reboot; re-run the project’s Xvfb recovery scripts as documented (`fix-x11.sh` / `diag-x11.sh`, D14).
- **Swap resets after reboot:** re-activate the configured swapfile before long builds/links (D15, KNOWN_ISSUES #10).
- **Do not run `celosia setup` to "continue":** it can re-run `stage_boot` and discard current build progress; use the documented continuation flow (`pipeline-remaining.sh`, D19) when appropriate.
- **Space/path constraints:** run pipeline builds in a space-free WSL ext4 path; avoid `/mnt/c` for large ISO copies into WSL (use `\\wsl.localhost` from Windows side as documented).

See [RECOMPILATION_PIPELINE.md](RECOMPILATION_PIPELINE.md), `tools/capture-host/README.md`, and `docs/agent/` memory files for authoritative recovery details.

## 7. Reporting problems

When reporting an issue, include:

- App version/build (if known), commit hash, and device/emulator details (model, Android version, ABI)
- Exact steps to reproduce
- Full error text/status shown in the app
- Relevant `adb logcat` output
- For import issues: supported release confirmation (`SCUS-97472`), image source type (without sharing copyrighted content), and validation/error message

Do **not** attach or link copyrighted game images, BIOS files, extracted assets, or recompiled game cores. Use [GitHub Issues](https://github.com/zoddrun/Port-SotC-Mobile/issues) and follow [CONTRIBUTING.md](CONTRIBUTING.md).