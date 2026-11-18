# Android Integration (Milestone 4/5 artifacts)

Patches, verification, and packaging notes for cross-compiling the recompiled
game as an Android ARM64 shared library (`libps2EntryRunner.so`) from the
Celosia/PS2Recomp tree.

## Patch series (`patches/`)

Applied on top of the celosia checkout (pinned `f8c269e`) **together with**
`tools/capture-host/patches/` (the non-Vita build fixes). Both series are
needed for the Android build; neither is applied to the live
`/root/celosia` tree yet.

| Patch | Purpose |
|---|---|
| `0001-english-only-host-menu.patch` | **D7**: the in-game settings menu (`ps2xRuntime/src/lib/host_menu.cpp`) becomes English-only - the Portuguese selector is a constant false and the Language menu item is removed (its enum case, value text, change handler, settings persistence and dead translation entries). Every user-visible string in `ps2_runtime` then renders in English: the tuning value texts default to `portuguese = false` (`native_tuning.h:781`) and their only Portuguese-passing caller is this menu. |
| `0002-games-database-scus-97472.patch` | Adds `{ "SCUS-97472", "Shadow of the Colossus (USA)" }` to the runner's game-name table so the window/log title resolves (the boot ELF name `SCUS_974.72` normalizes to `SCUS-97472`). |
| `0003-android-quit-graceful.patch` | **D18**: on Android, `main()` returns instead of `std::_Exit`. Raylib's `android_main` (rcore_android.c, verified in the fetched 5.5 source) ignores main's return value and then calls `ANativeActivity_finish` and pumps events until `destroyRequested` - the designed teardown path. Quitting the game therefore returns to the app instead of killing the whole process. |
| `0004-android-boot-path.patch` | **D18**: reads the boot executable from `SOTC_BOOT_ELF` on Android. A NativeActivity receives no argv and the app's private path depends on the device user, so the path cannot be baked in at configure time. `GameNativeActivity` publishes the variable before `super.onCreate` loads the runner. |
| `0005-android-touch-input.patch` | **D18**: the gameplay touch overlay. Android gives a NativeActivity's touches to raylib's `native_app_glue` loop, so the runner samples `CORE.Input.Touch` once per host frame, publishes a pad state the EE thread merges at each VBlank, and draws the overlay into its own GL frame. Replaces the empty `ps2_android_runtime.cpp` stub, adds `include/runtime/ps2_android_input.h`, the merge block in `ps2_pad.cpp` and the `update()` / `drawOverlay()` calls in `ps2_runtime.cpp`. Layout, presets and the eight `SOTC_TOUCH_*` / `SOTC_LOOK_*` knobs: `docs/CONTROLS.md`. |

Verification state:

- All five patches were generated from the pristine `f8c269e` files and
  round-trip verified (`patch` then `cmp` against the intended result). 0003
  and 0004 were additionally verified in both application orders; 0005 is
  verified by `apply-patches.sh` on a clean worktree in both orders (the second
  run reports every patch already applied).
- 0005 is compile-verified for `aarch64-linux-android28` with NDK r26b:
  `ps2_pad.cpp` and `ps2_android_runtime.cpp` at `-O2 -Wall -Wextra`, the large
  `ps2_runtime.cpp` at `-O0` (pre-existing warnings only), and `llvm-nm` shows
  `ps2_android_input::update/state/drawOverlay` defined and referenced. It has
  not been linked into a runner or run on a device yet.
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

## Milestone 4 build notes (researched, not yet executed)

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

- Patch 0005 is compile-verified but has not been linked into a runner or run
  on a device; its overlay geometry, multitouch ownership and pad merge need
  first-boot verification. The Custom preset falls back to Classic geometry
  (the launcher's per-control layout is not sent to the runner yet), and L3/R3
  have no on-screen control.
- Raw `sceCdRead` LBN reads still need the ISO path only if first-boot logs
  show unresolved LBNs; the extracted tree covers file-content reads.
- The runner library does not exist yet: the pipeline is still capturing
  modules. Play stays disabled in a default build.

Full rationale and revisit triggers: `docs/agent/TECHNICAL_DECISIONS.md` (D18);
task list: `docs/agent/NEXT_STEPS.md` (Milestone 5).

## Packaging the runner into the APK

After `tools/capture-host/build-android-runner.sh` reports
`RUNNER_SO_PRESENT`:

```bash
wsl -e bash tools/android-integration/package-runner.sh
```

It verifies the AArch64 machine type and the `ANativeActivity_onCreate` export,
then copies the library to `app/src/main/jniLibs/arm64-v8a/`. The file is
gitignored (a derived build artifact). The next Gradle build packages it and
`CoreProbe` enables Play once the disc tree is prepared.
