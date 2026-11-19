# Android Integration (Milestone 4/5 artifacts)

Patches, verification, and packaging notes for cross-compiling the recompiled
game as an Android ARM64 shared library (`libps2EntryRunner.so`) from the
Celosia/PS2Recomp tree.

## Patch series (`patches/`)

Applied on top of the celosia checkout (pinned `f8c269e`) **together with**
`tools/capture-host/patches/` (the non-Vita build fixes). Both series are
needed for the Android build; `configure-android.sh` applies them to
`/root/celosia` at Milestone 4 start (patch 0007 was added during the first
real ARM64 compile).

| Patch | Purpose |
|---|---|
| `0001-english-only-host-menu.patch` | **D7**: the in-game settings menu (`ps2xRuntime/src/lib/host_menu.cpp`) becomes English-only - the Portuguese selector is a constant false and the Language menu item is removed (its enum case, value text, change handler, settings persistence and dead translation entries). Every user-visible string in `ps2_runtime` then renders in English: the tuning value texts default to `portuguese = false` (`native_tuning.h:781`) and their only Portuguese-passing caller is this menu. |
| `0002-games-database-scus-97472.patch` | Adds `{ "SCUS-97472", "Shadow of the Colossus (USA)" }` to the runner's game-name table so the window/log title resolves (the boot ELF name `SCUS_974.72` normalizes to `SCUS-97472`). |
| `0003-android-quit-graceful.patch` | **D18**: on Android, `main()` returns instead of `std::_Exit`. Raylib's `android_main` (rcore_android.c, verified in the fetched 5.5 source) ignores main's return value and then calls `ANativeActivity_finish` and pumps events until `destroyRequested` - the designed teardown path. Quitting the game therefore returns to the app instead of killing the whole process. |
| `0004-android-boot-path.patch` | **D18**: reads the boot executable from `SOTC_BOOT_ELF` on Android. A NativeActivity receives no argv and the app's private path depends on the device user, so the path cannot be baked in at configure time. `GameNativeActivity` publishes the variable before `super.onCreate` loads the runner. |
| `0005-android-touch-input.patch` | **D18**: the gameplay touch overlay. Android gives a NativeActivity's touches to raylib's `native_app_glue` loop, so the runner samples `CORE.Input.Touch` once per host frame, publishes a pad state the EE thread merges at each VBlank, and draws the overlay into its own GL frame. Replaces the empty `ps2_android_runtime.cpp` stub, adds `include/runtime/ps2_android_input.h`, the merge block in `ps2_pad.cpp` and the `update()` / `drawOverlay()` calls in `ps2_runtime.cpp`. Layout, presets and the eight `SOTC_TOUCH_*` / `SOTC_LOOK_*` knobs: `docs/CONTROLS.md`. |
| `0006-android-audio-volume.patch` | **D18**: applies `SOTC_AUDIO_VOLUME` (0..1, published by the app's Audio settings) to the runtime's master volume immediately after `hostmenu::load()`, so the launcher's volume and mute reach gameplay. A later change in the runtime's own settings menu still applies for that session. |
| `0007-android-no-execinfo.patch` | **D18**: keeps the `PS2X_XFER_AUDIT` backtrace out of Android builds. Bionic only declares `backtrace()` / `backtrace_symbols_fd()` from API 33 and the app targets API 28, so `<execinfo.h>` and the audit block are now also guarded by `!defined(__ANDROID__)`. Found by the first real ARM64 compile of `EeScheduler.cpp`. |
| `0008-android-format-security.patch` | **D18**: waives `-Wformat-security` for the two forwards inside `sfmt105` in `game/overrides/sotc_native.cpp`. Every call site passes a literal format, but the variadic wrapper forwards it as a pointer and the NDK compiles with `-Werror=format-security`. The waiver is a scoped `#pragma clang diagnostic` around the `return`, so the rest of the runner keeps the check. Found at 9,264 of 9,271 objects of the first complete ARM64 compile. |
| `0009-android-runtime-pacing.patch` | **D18**: connects the Android graphics settings to the runner's own loop. Reads `SOTC_FRAME_LIMIT` (`0`/`30`/`60`, presentation only) and `SOTC_ADAPTIVE_RESOLUTION`; overrides `wantFps` before `SetTargetFPS`, adapts `native_renderer::tuning().renderScale` using the host engine's 5%/0.75 s/1.20x/0.75x contract, and emits a five-second `[pace]` record inside the existing `PS2X_STATS` gate. Rejects `SOTC_CAP20` because it changes the guest time step. **Generated and round-trip verified, not yet compiled into a runner.** |

Verification state:

- All eight patches were generated from the pristine `f8c269e` files and
  round-trip verified (`patch` then `cmp` against the intended result). 0003
  and 0004 were additionally verified in both application orders; 0005, 0006,
  0007 and 0008 are verified by `apply-patches.sh` on a clean worktree in both
  orders (the second run reports every patch already applied).
- 0005 is compile-verified for `aarch64-linux-android28` with NDK r26b:
  `ps2_pad.cpp` and `ps2_android_runtime.cpp` at `-O2 -Wall -Wextra`, the large
  `ps2_runtime.cpp` at `-O0` (pre-existing warnings only), and `llvm-nm` shows
  `ps2_android_input::update/state/drawOverlay` defined and referenced.
- 0006 is a single hunk in `ps2_runtime.cpp`; 0007 and 0008 are two-line and
  eleven-line hunks. All three now compile and link in the full runner
  (`RUNNER_VERIFY_OK`, 2026-11-18 22:22). 0005's overlay geometry, multitouch
  ownership and pad merge still need first-boot verification on a device.
- Patch 0009 was generated against the current `/root/celosia` working tree at
  the pinned commit (the tree also carries the developer's uncommitted fixes).
  It was verified with `git apply --check`, `git apply`, `git apply -R --check`,
  `git apply -R` and a byte-for-byte `cmp` of the restored file. Its SHA-256 is
  `bd9c5c7a5e0dc5361f9eb58b833610c2c393d6919ca0c8939c94643549dde74e`. It has
  not been compiled or linked into a runner yet, so the packaged baseline is
  unchanged.
- None of them touch `coll125.h` / `decomp_collision.cpp`, the files that
  carry the developer's uncommitted capture-host fixes, so both patch series
  apply to the same tree independently. `main.cpp` (patches 0003/0004) also
  carries no developer edits.

Apply at Milestone 4 start:

```bash
cd /root/celosia
tools/capture-host/patches/apply-patches.sh   # from the repo, or copy first
tools/android-integration/patches/apply-patches.sh
```

## Milestone 4 build notes (executed 2026-11-18)

- The Android target in `ps2xRuntime/CMakeLists.txt` builds `ps2EntryRunner`
  as a **shared library** with `-Wl,--undefined=ANativeActivity_onCreate`;
  the upstream scaffold (`PS2Recomp/android/`) is a `hasCode="false"`
  NativeActivity app where raylib owns the window and input.
- `PS2X_DEFAULT_BOOT_ELF` (Gradle property `ps2xBootElf` in the scaffold)
  supplies the guest ELF path on argv-less hosts, but it cannot be used for a
  user-installed game: the app's private path depends on the device user. The
  Android build therefore leaves it unset and relies on patch 0004's
  `SOTC_BOOT_ELF`, which `GameNativeActivity` sets at run time.
- The game's data is extracted once from the imported `disc.iso` into
  `<files>/games/SCUS-97472/disc/` by `DiscTreeExtractor`; the boot ELF's
  directory becomes the runtime's `cdRoot`/`hostRoot` and `mc0` becomes its
  memory-card root.
- aarch64: the top-level CMake fetches sse2neon v1.9.1 and defines
  `USE_SSE2NEON` when `CMAKE_SYSTEM_PROCESSOR` matches arm64; the NDK's real
  `arm_neon.h` is used - the x86 SIMDe shim from `tools/capture-host/`
  must never be on an ARM64 include path. The `mach-o/dyld.h` shim
  (`dl_iterate_phdr` implementation) IS still needed on bionic
  (`decomp_lines67.cpp` includes it on every non-Vita host).
- FFmpeg is OFF on Android (`PS2X_ENABLE_FFMPEG_DEFAULT OFF`), so the
  MPEG-2 video path is stubbed there.
- NDK on the Windows SDK is 26.1.10909125; the upstream scaffold pins 28.2 -
  resolve at configure time and record the choice.

Outcome of the first full ARM64 build (2026-11-18 21:19-22:22, NDK r26b,
`-j2`, WSL 1.7 GiB + 8 GB swapfile):

- 9,333 objects compiled and the thin-LTO link took 55 minutes. The result is
  `libps2EntryRunner.so`, 777,574,008 bytes with debug info
  (SHA-256 `289f4bab...0d2fb8`), ELF64 AArch64, Android 28, BuildID
  `8c5f7557f979db6ad9bcc3b64785c64f6d83413c`.
- The first attempt stopped at 67 objects on `EeScheduler.cpp` (patch 0007);
  the second at 9,264 on `sotc_native.cpp` (patch 0008). Both are Android-only
  errors that an x86 build never sees.
- `llvm-strip --strip-unneeded` takes it to 107,384,168 bytes
  (SHA-256 `134f2fbf...b9aeb8c`) and the verifier still passes. The bulk of the
  remaining size is `.text` (58 MB) plus a 40.7 MB
  `g_ps2RecompiledFunctionTable` in `.data`; both are inherent to the
  recompiled runtime, not debug info.
- WSL lost the build twice (one `WSAETIMEDOUT`, one SIGTERM during the link);
  the ninja tree resumed correctly both times. Restarting the build with
  `setsid` and re-enabling `/root/swapfile` after a `wsl --shutdown` is
  required.

## Integration architecture (D18, settled 2026-11-11)

The game integrates through celosia's own Android path, not behind our ABI-v1
step/pull contract: `PS2Runtime::run()` is built around raylib (`InitWindow`,
`PollInputEvents`, `BeginDrawing`, raylib audio, internal game/GS threads), so
host-driving it would mean writing a custom raylib platform backend — a large,
permanent fork of third-party code. Instead the app gains a Kotlin subclass of
`android.app.NativeActivity` (meta-data `android.app.lib_name` pointing at the
runner library): raylib owns the window, loop, input polling and audio. ABI-v1
stays implemented as the diagnostics / future-headless contract.

Host side implemented (2026-11-18):

- `GameNativeActivity` publishes `SOTC_BOOT_ELF` before the runner loads and
  refuses to pretend a session exists without an installed game and a prepared
  disc tree. It also publishes the control settings through
  `core/RuntimeEnvironment.kt` (`SOTC_TOUCH_*`, `SOTC_LOOK_*`), so the
  launcher's Controls page reaches the runner.
- `DiscTreeExtractor` produces the tree the runtime reads, with atomic
  publication and a marker bound to the image digest; Storage exposes it.
- The launcher enables Play only when the runner library is packaged and the
  tree is ready.
- Patch 0005 adds the gameplay touch overlay inside the runner: raylib already
  fills `CORE.Input.Touch` from every motion event, so the module samples it on
  the render thread, publishes a mutex-guarded pad state, and draws the overlay
  in the runner's own GL frame.

Known upstream gaps and remaining work:

- The overlay geometry, multitouch ownership and pad merge of patch 0005 need
  first-boot verification on a device; the library links and exports the
  entry points but has never run on ARM64 hardware. The Custom preset falls
  back to Classic geometry (the launcher's per-control layout is not sent to
  the runner yet), and L3/R3 have no on-screen control.
- Raw `sceCdRead` LBN reads still need the ISO path only if first-boot logs
  show unresolved LBNs; the extracted tree covers file-content reads.
- The only rendering evidence so far is from the x86_64 host build
  (`/root/celosia/shots/run/frame_8.png`, 512x448). The ARM64 library is built,
  verified and packaged, but no ARM64 device has run it yet.

Full rationale and revisit triggers: `docs/agent/TECHNICAL_DECISIONS.md` (D18);
task list: `docs/agent/NEXT_STEPS.md` (Milestone 5).

## Packaging the runner into the APK

After `tools/capture-host/build-android-runner.sh` reports
`RUNNER_SO_PRESENT`, inspect the artifact before packaging:

```bash
wsl -e bash tools/android-integration/verify-runner.sh
```

It prints the size and SHA-256, then checks that the library is an ELF64
AArch64 shared object, that `ANativeActivity_onCreate` and the touch overlay
symbols are exported, that `libc++_shared.so` is not a dependency (the runner
uses `c++_static`), and that the LOAD segments are aligned for 16 KB page
devices. It ends with `RUNNER_VERIFY_OK` or `RUNNER_VERIFY_FAILED`, so the
result can be quoted as evidence. A path argument inspects a different file.

Both sides are aligned for 16 KB pages: the app's `libsotc_core.so` sets
`-Wl,-z,max-page-size=16384` in `app/src/main/cpp/CMakeLists.txt`, and the
runner configure passes the same flag through `CMAKE_SHARED_LINKER_FLAGS`
(NDK r26b still defaults to 4 KB). This keeps the libraries loadable on
Android 15 devices that run a 16 KB page-size kernel.

Then:

```bash
wsl -e bash tools/android-integration/package-runner.sh
```

It verifies the AArch64 machine type and the `ANativeActivity_onCreate` export,
copies the library to `app/src/main/jniLibs/arm64-v8a/`, strips the copy with
`llvm-strip --strip-unneeded`, and runs `verify-runner.sh` on the packaged file.
Stripping is what makes the artifact shippable: the Milestone 4 build is 777 MB
with DWARF and `.symtab`, 107 MB after the strip, and `.dynsym` keeps every
exported entry point. The unstripped library in the build tree is untouched, so
a crash can still be symbolised against it. Set `SOTC_STRIP=0` to package the
library verbatim. The packaged file is gitignored (a derived build artifact).
The next Gradle build packages it and `CoreProbe` enables Play once the disc
tree is prepared.

## No-device verification gates (D23-D26)

### APK-wide native library check

`verify-runner.sh` inspects a single runner file. `verify-apk.py` extends the
same guarantees to every native library actually shipped in the APK:

```bash
python tools/android-integration/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
```

It parses the ZIP central directory and the ELF headers with the Python
standard library and checks, for each `.so`: ELF64, the correct machine for its
ABI, `ET_DYN`, `PT_LOAD` alignment of at least 16 KB, ZIP storage without
compression and a 16 KB-aligned data offset, resolvable `DT_NEEDED` entries and
a BuildID. It prints `APK_NATIVE_VERIFY_OK` only with zero failures and zero
warnings. This gate caught the 4 KB-aligned `libc++_shared.so` that the
runner-only verifier could not see (D24).

### Host x86 pacing smoke test

`smoke-runtime-pacing.sh` proves patch 0009's parsing, pacing and adaptive
decisions without any device. It rebuilds the patched host runner incrementally
and runs three 14-second sessions with fixed environments:

```bash
CELOSIA=/root/celosia SOTC=/root/sotc \
  bash tools/android-integration/smoke-runtime-pacing.sh
```

It asserts raylib's `TIMER: Target time per frame` log for 30 and 60 fps, the
five-second `[pace] limit=...` record for `0`/`30`/`60`, that `[scale-adaptive]`
appears only when enabled, and that no host `Frame stats:` line leaks into the
gameplay path. `HOST_SMOKE_0009_OK` is printed only when every assertion passes.
This is a host-runtime check of the logging/pacing contract, **not** a gameplay
or rendering verification: it needs Xvfb, software GL, the extracted disc tree
and the pinned source with patch 0009 applied. The full evidence ladder and the
device-only items that remain unverified are in
`docs/agent/NO_PHONE_VERIFICATION_REPORT.md`.
