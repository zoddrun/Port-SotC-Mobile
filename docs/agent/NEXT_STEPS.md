# Next Steps — Ordered Roadmap

> Agent memory file: the actionable queue. Work top-down; update as items complete.

## Milestone 3 — Real Recompilation Pipeline (IN PROGRESS in /root/sotc, WSL2)

Workspace adopted from the developer (D10): ROOT=`/root/celosia`, BUILD=`/root/sotc/build`, DISC=`/root/sotc/disc`, ISO=`/root/sotc/source.iso`.

1. [x] **WSL workspace ready** — all tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14); zlib1g-dev installed; FFmpeg/GL/X11 dev packages preinstalled.
2. [x] **Extract disc** — `/root/sotc/disc/` complete (SCUS_974.72, all XFF modules, NICO.DAT).
3. [x] **Configure + build ps2_recomp** — deps fetched; `CMAKE_POLICY_VERSION_MINIMUM=3.5` (D11); recompiler binary built.
4. [x] **stage_boot** — KERNEL.XFF symbols freed; funcmap_elf.py mapped the boot ELF; recompilation emitted 1429 C++ files into `build/runner/` ("Recompilation completed successfully").
5. [x] **Build ps2EntryRunner — BUILD_OK (attempt 6, 2026-11-11 07:05)** — attempts 1–5 resolved sequentially (NEON shim D13, `-msse4.1` D13, force-include + restricted aliases D13, mach-o shim D16, developer's 2 source fixes, unity OFF D17); attempt 6 = non-unity, 1567 targets at `-j2`, log `/root/sotc/logs/build-ps2EntryRunner-attempt6.log`. Binary `/root/sotc/build/cmake/ps2xRuntime/ps2EntryRunner`, 17,944,488 bytes. One benign GCC false-positive warning (`sotc_native.cpp:438` — KNOWN_ISSUES #13).
6. [x] **Run the remaining pipeline - COMPLETE (2026-11-18 20:34)**: all three modules captured, the three `stage_game` rounds rebuilt the runner (final x86 size 72,237,696 bytes, 9,024 functions), the VU1 autotest rendered a real frame and codegen emitted 12 sources, and `PIPELINE_DONE` was logged. **Do not run `celosia setup`** - it re-runs stage_boot and discards the build (D19).
7. [ ] **stage_game**: handled inside the pipeline above (build_elf.py merge XFF@auto@dump → gen_config.py → recompile → runner rebuild). Do not run `cmd_recompile` manually outside the script (it requires `$WORK/game` to exist; `pipeline-remaining.sh` pre-creates it — identical to what `stage_game` itself does first).
8. [ ] **VU1 collection**: handled by the driver (`cmd_vu1`: autotest 40 s with `PS2X_VU1_DUMP_MISSING`, then `PS2X_VU1_GEN=dumps:outdir` codegen; runner rebuild).
9. [ ] Commit pipeline progress notes/patches (never the dumps/binaries; they are derived game content — keep under gitignored paths).

## Milestone 4 — ARM64 Cross-Compile

- [x] **Apply both patch series - DONE (2026-11-18 20:35)** via `tools/capture-host/configure-android.sh`: capture-host fixes verified present, android-integration patches 0001-0007 applied to `/root/celosia`, configure reached `CONFIGURE_OK`. The first attempt aborted because the guard required the mirrored `apply-patches.sh` to be executable (`-x`) and a Windows copy has no exec bit; the guard now tests `-f` and the mirrors are chmodded. Patch 0007 (`0007-android-no-execinfo.patch`) was added after the first ARM64 compile failed on `backtrace()` in `EeScheduler.cpp`: bionic declares it only from API 33 and the app targets 28, so the audit block is host-only now.
- [x] **Cross-compile the recompiled core + `game/overrides/` + `build/vu1rec/` for arm64-v8a - DONE (2026-11-18 22:22)**: 9,333 objects compiled and the thin-LTO link finished (`/root/sotc/logs/build-android-ps2EntryRunner-20261118-212511.log`). `libps2EntryRunner.so` = 777,574,008 bytes with debug info (SHA-256 `289f4bab...0d2fb8`), ELF64 AArch64, Android 28, BuildID `8c5f7557f979db6ad9bcc3b64785c64f6d83413c`. Two Android-only errors were fixed on the way: 0007 (bionic `backtrace` in `EeScheduler.cpp`, at object 67) and 0008 (`-Werror=format-security` in `sotc_native.cpp`'s `sfmt105`, at object 9,264). WSL dropped the build twice (one `WSAETIMEDOUT`, one SIGTERM during the link); ninja resumed both times. Keep the build detached with `setsid` and re-enable `/root/swapfile` after any `wsl --shutdown`.
- [x] **NDK toolchain smoke test — NDK_SMOKE_OK (2026-11-11 07:22)** (`tools/capture-host/tests/ndk-smoke.sh`, mirrored to `/root/sotc/ndk-smoke.sh`, log `/root/sotc/logs/ndk-smoke.log`): NDK r26b `android.toolchain.cmake` + CMake 4.2.3 + Ninja + `arm64-v8a` + `android-28` + the D11 policy flag produce valid AArch64 Android ELF artifacts — verified `ELF 64-bit LSB, ARM aarch64, for Android 28, built by NDK r26b (10909125)`, both a SHARED library and a PIE executable. D20's NDK 28.2 fallback is retired unless the real project configure hits something project-specific. Trivial CPU + disk load — safe to run (and run) while the pipeline builds.
- [x] **Produce the runner shared library for Android - DONE (2026-11-18 22:22)**: upstream already builds `ps2EntryRunner` as a SHARED lib with `-Wl,--undefined=ANativeActivity_onCreate` for raylib's NativeActivity glue. **Architecture settled (D18): raylib-owned NativeActivity, not a host-driven ABI-v1 port** (see `tools/android-integration/README.md` and `TECHNICAL_DECISIONS.md` D18). The artifact is `/root/sotc/build-android/ps2xRuntime/libps2EntryRunner.so`; the app loads it under that name (`android.app.lib_name`), so no `libsotc_game.so` rename was taken.
- [x] Translate Portuguese runtime strings to English (D7 — patch prepared); add SCUS-97472 to games database (patch prepared). Both land with the first bullet above.

## Milestone 5 — Runtime Integration (D18: raylib-owned NativeActivity)

- [x] **Package the runner library into the app APK (`jniLibs/arm64-v8a`) - DONE (2026-11-18 22:31)**: `package-runner.sh` now strips the copy with `llvm-strip --strip-unneeded` (777,574,008 -> 107,384,168 bytes, SHA-256 `134f2fbf...b9aeb8c`) and re-runs `verify-runner.sh` on the packaged file (`RUNNER_VERIFY_OK`). `.dynsym` keeps `ANativeActivity_onCreate` and the touch entry points; the unstripped library stays in the build tree for symbolisation. Two `set -o pipefail` + `grep -q` SIGPIPE bugs were fixed in `verify-runner.sh` and `package-runner.sh` (the symbol list is 1.5 MB, so grep's early exit turned present symbols into false failures).
- [x] Add `GameNativeActivity` — a Kotlin subclass of `android.app.NativeActivity` with `android.app.lib_name` pointing at the runner library. It publishes the boot path and closes defensively when the runtime or disc tree is missing. (2026-11-18)
- [x] Pre-launch extraction — **I/O model confirmed by source read (2026-11-11)**: extract the FULL disc tree from the imported `disc.iso` into `<files>/games/SCUS-97472/disc/` using the existing `Iso9660Image` reader (one-time, cached, original filenames). Implemented as `DiscTreeExtractor` + `DiscTree` marker, exposed in Settings → Storage as `PREPARE GAME DATA` with progress and cancellation; 5 unit tests cover extraction, no-op reuse, digest change, cancellation, and a missing image. (2026-11-18)
  - Boot path: **superseded the configure-time bake**. The app's private path is device/user dependent and Android gives a `NativeActivity` no argv, so patch 0004 reads `SOTC_BOOT_ELF`, which `GameNativeActivity` sets before `super.onCreate`. Confirmed runtime behavior:
  - `main()` uses the resulting absolute path as-is; `PS2Runtime::configureIoPathsFromElf` then derives `cdRoot` = `hostRoot` = the boot ELF's directory, so every guest `cdrom:`/`cdrom0:` path resolves under the extraction root — no runtime patch (D18 confirmed).
  - File-content reads are directory-only: `PS2Vfs::resolveHostPath` mounts `hostRoot`/`cdRoot`/`mcRoot`; `cdImage` is NOT a VFS mount, so the ISO cannot back file reads — full extraction is required, not an ISO-backed layout.
  - Filename robustness (no special naming needed): `registerCdFile` resolves exact → case-insensitive walk → leaf-name index → loose numeric key (`;1` version suffix stripped, leading zeros collapsed within digit runs).
  - Memory cards: `mc0` is auto-created beside the boot ELF (`disc/mc0`); port 1 maps to sibling `mc1` — M9 save persistence targets those two directories.
  - Raw `sceCdRead` (LBN) reads: resolved first against the runtime's pseudo-LBN map (files registered as they are opened; synthetic LBNs), then read directly from `cdImage` (the ISO file) for real-disc LBNs. `cdImage` CANNOT be set on Android without a patch (no env var; `setIoPaths` has exactly one caller, `configureIoPathsFromElf`; a `:game` process would not inherit launcher-set env anyway). Plan: leave unset at first boot; if M6 logcat shows `sceCdRead unresolved LBN`, add a small patch baking the ISO path (mirroring the `SOTC_BOOT_ELF` pattern) and keep the imported ISO at a fixed private path (~2.5 GiB extra storage; revisit only with M6 evidence).
- [x] Quit handling: prepared `tools/android-integration/patches/0003-android-quit-graceful.patch` (`main()` returns on `__ANDROID__` so raylib's `android_main` finishes the NativeActivity cleanly — verified against the fetched raylib 5.5 source). The game activity is `sensorLandscape`; the separate `:game` process is deferred until first-boot evidence (the graceful quit makes it non-urgent).
- [x] Connect touch input to the runtime. Implemented as patch 0005: `ps2_android_input` samples raylib's `CORE.Input.Touch` once per host frame on the render thread, publishes a mutex-guarded pad state, and `PSPadBackend::readState` merges it into the PS2 wire format at each VBlank; the overlay is drawn with raylib in the same GL frame. The launcher's Controls settings reach it through `core/RuntimeEnvironment.kt` (`SOTC_TOUCH_*`, `SOTC_LOOK_*`). Linked into the ARM64 runner and exported (`verify-runner.sh` checks `ps2_android_input::update`); **not yet run on a device** — layout, multitouch ownership and pad merge still need first-boot verification. (2026-11-18)
  - Custom touch preset geometry is not sent to the runner (it falls back to Classic); L3/R3 have no on-screen control. Both are documented limitations, not silent failures.
  - Gamepads reach the runner through raylib's own gamepad API in `ps2_pad.cpp`, so no Android-side bridge is needed for them.
- [x] Re-scope `docs/CORE_ABI.md` wording at implementation time: ABI-v1 remains the diagnostics / future-headless contract; gameplay integrates via D18 (no behavior change to `game_core.cpp`). (2026-11-18)

## Milestone 6 — First Boot

- [ ] **BLOCKED on hardware (B4)**: no device is attached (`adb devices` empty on 2026-11-19) and the x86_64 emulators on this host cannot load the arm64-v8a runner (API 37 has insufficient RAM; API 28 reports OpenGL ES 2.0 only). The ARM64 library is built, verified and packaged, so the remaining work is device access.
- [x] First-boot capture tooling - `tools/device/first-boot.ps1` installs the debug APK, refuses a non-arm64 device or an APK without `lib/arm64-v8a/libps2EntryRunner.so`, launches `MainActivity` or `GameNativeActivity`, and writes `build/device/<stamp>/{device.txt,logcat.txt,screen.png,game.log}`. Its failure path is verified on this host (no device attached -> `FIRST_BOOT_FAILED`). (2026-11-19)
- [ ] Verify: title/loading sequence, no emulator, native ARM64 code executing (verify via maps/ABI checks), and that `game-*.log` shows the recompiled runtime's own boot messages.

## Milestone 7+ — Gameplay, Optimization, Polish, Release

- [ ] Gameplay verification checklist (menu → control → first area).
- [ ] Profile before optimizing (GS/VU1 hot paths), document measurements.
- [ ] Touch overlay tuning, gamepad support, lifecycle/resume, save persistence.
- [ ] Release validation per AGENTS.md Definition of Success.

## Milestone 8 — Host Performance and GPU Compatibility (host engine only)

- [x] **OpenGL ES 2 floor with ES 3 fast path - DONE (2026-11-19)**: manifest `glEsVersion` lowered to `0x00020000`; `EglRenderer` requests ES 3 and retries config/context/surface/make-current for ES 2 on any failure; GLSL ES 1.00 shaders with a VBO fullscreen triangle instead of `gl_VertexID`/VAOs; `GLESv2` linked; `Force OpenGL ES 2.0` setting skips the ES 3 attempt for a session.
- [x] **Real capability query - DONE (2026-11-19)**: `engine/gpu_info.cpp` creates a short-lived offscreen EGL context (pbuffer, progressively simpler configs) and reports backend/vendor/renderer/version/GLSL/limits/error as `key=value`; `GpuInfo.kt` parses it (5 tests) and Settings → Graphics + Advanced render it. `engine/egl_display_lock.h` serializes display lifecycle with the render thread.
- [x] **Frame pacing and memory pressure - DONE (2026-11-19)**: adaptive resolution (5% steps every 0.75 s, 25% floor, ceiling from the configured scale and thermal status), frame limit 0/60/30 with wall-clock simulation delta, thermal ceiling 100/100/85/70/60/50%, low-memory direct-render fallback via `onTrimMemory`/`onLowMemory`, and Battery/Balanced/Performance presets (4 tests).
- [x] **Emulator evidence - DONE (2026-11-19)**: ES 2 fallback rendered frames at 1280x720 under SwiftShader; adaptive resolution stepped `26% -> 25%`; `am send-trim-memory ... RUNNING_LOW` released the offscreen target and rendering continued; preset persistence confirmed in `shared_prefs/sotc_settings.xml`.
- [ ] **Runtime-verify the ES 3 path, the frame limiter and the thermal ceiling** — needs a device (or emulator) that accepts ES 3, can exceed 30 fps, and reports non-zero thermal status.
- [x] **Extend resolution and frame limit to the raylib-owned runtime loop (2026-11-19)**: `RuntimeEnvironment.graphics` publishes `SOTC_RENDER_SCALE`, `SOTC_ADAPTIVE_RESOLUTION`, `SOTC_FRAME_LIMIT`, `SOTC_PERF_OVERLAY` and `PS2X_STATS`; patch 0009 lets the runner own its adaptive scale and raylib's `SetTargetFPS` cap while remaining the single gameplay owner. Thermal handling stays host-only. Device verification is still pending (B5).
- [x] **Capability-based GPU tiers (2026-11-19)**: the native query now runs shader/FBO/colour/depth/render-to-texture self-tests and collects EGL identity and limits; `GpuInfo.Tier` is derived from probes, never from a vendor string; advertised extensions are labelled separately from self-test results.
- [x] **APK-wide native library gate (2026-11-19)**: `tools/android-integration/verify-apk.py` checks every packaged `.so` for ELF64/ABI/`ET_DYN`/16 KB LOAD alignment, dependencies and ZIP alignment. `app/build.gradle` switched to `c++_static`, removing the 4 KB-aligned `libc++_shared.so`; the gate now reports `APK_NATIVE_VERIFY_OK` with zero failures and warnings.

## No-Phone Verification Gate (2026-11-19 expansion)

Ordered checks that must pass before a rebuilt runner is packaged or any release is claimed:

1. [x] Patch 0009 contract: `git apply --check`, `git apply`, `git apply -R --check`, `git apply -R`, and byte-for-byte restore at celosia `f8c269e`. SHA-256 `bd9c5c7a5e0dc5361f9eb58b833610c2c393d6919ca0c8939c94643549dde74e`.
2. [x] JVM contract tests: 106 tests, 0 failures, including the new environment names, 0/30/60 limits, locale-independent decimals, runner scale floor, GPU tier parsing and recognition of all nine regional serials without marking them supported.
3. [x] `lintDebug`: BUILD SUCCESSFUL.
4. [x] `assembleDebug` + `verify-apk.py`: `APK_NATIVE_VERIFY_OK`, 0 failures and 0 warnings; every `.so` is 16 KB aligned.
5. [x] Host x86 smoke run for `SOTC_FRAME_LIMIT=0/30/60` and patches 0009/0010: raylib timer logs, `[pace] limit=...`, positive `frameMs` with the perf overlay off, adaptive activation and an actual `[scale-adaptive]` change (`tools/android-integration/smoke-runtime-pacing.sh`) — `HOST_SMOKE_0009_OK`, 13/13. The host smoke binary was built without LTO under the 1.7 GiB WSL limit; this does not change the shipped runner.
6. [x] ARM64 rebuild with patches 0009/0010: `BUILD_OK`, new size/SHA-256/BuildID `44df4606...706` recorded, `verify-runner.sh` prints `RUNNER_VERIFY_OK`, repackaged and APK gate re-run passing (`APK_NATIVE_VERIFY_OK`, 0 failures/0 warnings).
7. [ ] Launcher smoke on the API 28 x86_64 emulator: host diagnostics UI and Settings still work with the static C++ runtime (deferred under host memory pressure; diagnostics only).
8. [ ] First-boot evidence on a real ARM64 device — the only step that can promote gameplay claims above `COMPILED/PACKAGED`.

Steps 7-8 are the remaining work; no device access may block delivering the APK, and device-only items must stay labelled unverified.

## Production Specification Subsets (D29, 2026-11-19)

- [x] Recognize every regional serial named in the second production specification without claiming support for it. `KnownReleases` records serial, boot file, region and refresh rate; `SupportedDiscs` remains a single verified `SCUS-97472` entry, and other recognized discs fail with `KNOWN_UNVERIFIED_SERIAL`. Eight additional JVM tests cover the eight unverified serials. No memory offsets, trophy data or pacing profile is activated by recognition.
- [x] Fix the adaptive-resolution no-op in the runner (patch 0010) and complete the host/ARM64/APK gate chain (2026-11-20): `HOST_SMOKE_0009_OK` 13/13, ARM64 `BUILD_OK`, `RUNNER_VERIFY_OK`, packaged runner SHA-256 `FBB1D788...19FF9` (107,386,992 bytes), local APK SHA-256 `F2761B4A...9308`, APK gate passing. No device evidence and no new release publication.
- [ ] Revisit original, English local achievements only when verified memory offsets exist for an actual disc. Official trophy text, RetroAchievements IDs/hashes and unverified region tables remain rejected under D29.

## HD Shader and Touch Subsets (v0.3.0, no-device gates complete 2026-11-20)

- [x] Bundle an original ES2/ES3 GLSL pack with manifest and MIT license; install it into app-private storage with staged SHA-256 verification, state file and rollback.
- [x] Wire validated presets into the native diagnostics presentation pipeline and publish the verified directory, preset and version to the raylib-owned runner as process environment values.
- [x] Add the launcher HD status card, preset selectors, grip-mode selectors and honest captions in English. Re-run JVM tests (21 suites / 139 tests), `lintDebug` and `assembleDebug` with JDK 17: all passed.
- [x] Finish and review runner patch 0011 (SHA-256 `b6eec5cff3913840f89b79a08cec86ad0f3c35ba940012d9cdf307c5b4e73782`), run the host smoke with independent `[hd-shader]` assertions (`HOST_SMOKE_0009_OK`, 13/13 + 7/7), rebuild the ARM64 runner (`RUNNER_VERIFY_OK`), re-package and re-run every APK gate (`APK_NATIVE_VERIFY_OK`, 0 failures/0 warnings).
- [x] Confirm the eight unverified serials still fail with `KNOWN_UNVERIFIED_SERIAL` and that no trophy/RetroAchievements data was added. The JVM suite includes the eight-region rejection checks and no such data exists in `app/src`.
- [x] Record exact hashes, update all agent docs and release notes, then publish only with the explicit "physical ARM64 gameplay remains unverified" statement. Published asset: `SotC-Mobile-v0.3.0-alpha.1-debug.apk`, SHA-256 `8D83BD57FC6858DD41F95256CC4F1C4BEB1A4E8B348E401F02464E2FD18BDEE8`.

## Presentation Hardening and UI Polish (v0.3.3, no-device gates complete 2026-11-21)

- [x] Apply the safe v0.3.3 subset under D34: declarative `shortEdges` cutout theme, `GameNativeActivity` focus re-apply, `smallestScreenSize` in `configChanges`, ordered import actions with an `import_close` string, icon-above-label launcher tiles, structured keycap rows and a mapped Latch/Hold/Hybrid grip selector.
- [x] Remove two source-level black-screen risks in the host diagnostics blit: reset `GL_CULL_FACE` before every fullscreen present and gate mipmap generation on POT/ES3/`GL_OES_texture_npot`. No ES3-only rewrite and no new runner patch.
- [x] Reject `SotcSafePresenter`, a 33.3 ms simulation clamp and runner-side readback as written; record the rationale in D34.
- [x] Re-run the JDK 17 Gradle gate (21 suites / 139 tests, lint, `assembleDebug`), the host smoke, `RUNNER_VERIFY_OK` on the unchanged patch-0012 runner and `verify-apk.py` (`APK_NATIVE_VERIFY_OK`); publish `v0.3.3-alpha.1` with the explicit no-device statement.
- [ ] Add host-smoke assertions for non-black pixels and fullscreen viewport dimensions, and verify fullscreen, touch ergonomics, presets and frame pacing on a physical ARM64 device (B4/B6).

## Local Tooling Follow-up (v0.3.4, no-device gates complete 2026-11-21)

- [x] Add a real app-private disc inspector: whole-image SHA-256, volume data, ELF facts and a PS2 BOOT2 MD5 from the executable name in `SYSTEM.CNF` plus the executable bytes, with durable local inspection evidence (D35).
- [x] Add original local setup milestones evaluated from real file/digest/library checks; no game trophies, RetroAchievements or hardware claims.
- [x] Add a file-level memory-card vault (backup/list/restore/delete, retained previous directory, path-traversal rejection) and preserve existing `mc0` files across digest-driven re-extraction; no PS2 save parsing or compatibility claim.
- [x] Add a versioned, diagnostics-only native report cache isolated from gameplay configuration; keep `GameNativeActivity` + `libps2EntryRunner.so` active and `GameActivity` + native `Engine` diagnostics-only.
- [x] Re-run the JDK 17 Gradle gate (25 suites / 155 tests, lint, `assembleDebug`), the host smoke, `RUNNER_VERIFY_OK` on the unchanged patch-0012 runner and `verify-apk.py` (`APK_NATIVE_VERIFY_OK`); publish `v0.3.4-alpha.1` with the explicit no-device statement.
- [ ] Add host-smoke assertions for non-black pixels and fullscreen viewport dimensions, and verify fullscreen, touch ergonomics, presets and frame pacing on a physical ARM64 device (B4/B6).

## Standing Rules (apply at every step)

- English only. No fake functionality. Commit at meaningful milestones. Never touch the ISO. Never push without authorization.
- Update SESSION_LOG.md at the end of every work session; update PROJECT_STATE.md whenever milestone status changes.
