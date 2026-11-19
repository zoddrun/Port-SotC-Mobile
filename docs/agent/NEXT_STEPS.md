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

- [ ] Boot on x86_64 emulator first (faster iteration), then ARM64 device when available (B4).
- [ ] Verify: title/loading sequence, no emulator, native ARM64 code executing (verify via maps/ABI checks).

## Milestone 7+ — Gameplay, Optimization, Polish, Release

- [ ] Gameplay verification checklist (menu → control → first area).
- [ ] Profile before optimizing (GS/VU1 hot paths), document measurements.
- [ ] Touch overlay tuning, gamepad support, lifecycle/resume, save persistence.
- [ ] Release validation per AGENTS.md Definition of Success.

## Standing Rules (apply at every step)

- English only. No fake functionality. Commit at meaningful milestones. Never touch the ISO. Never push without authorization.
- Update SESSION_LOG.md at the end of every work session; update PROJECT_STATE.md whenever milestone status changes.
