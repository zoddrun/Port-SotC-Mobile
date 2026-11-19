# Codebase Map

> Agent memory file: where everything lives in this repository.
> Last updated: 2026-11-18

## Top Level

```
Port-SotC Mobile/
├── AGENTS.md                  # Engineering rules (English-only, native execution mandate)
├── README.md                  # Accurate current-status doc (host foundation, no gameplay yet)
├── .gitignore                 # Uncommitted edit adds `/original iso/` protection
├── app/                       # Android host application (Kotlin + native C++)
├── build/                     # Build outputs + reports (build/reports/original_iso.json)
├── docs/                      # Project documentation (see below)
├── docs/agent/                # THIS memory system (agent-only docs)
├── original iso/              # Developer's private disc image (gitignored, NEVER commit/modify)
├── third_party/celosia/       # Celosia pipeline checkout (squash commit f8c269e)
├── third_party/PS2Recomp/     # Separate PS2Recomp checkout with Android scaffold (e563698)
└── tools/                     # Our own pipeline/dev tools (inspect_original_iso.py)
```

## app/ — Android Host

```
app/src/main/
├── AndroidManifest.xml            # MainActivity launcher; GameNativeActivity (runner); GameActivity (diagnostics)
├── java/com/portsotc/mobile/
│   ├── MainActivity.kt            # Launcher: status card, single Play action, details dialog
│   ├── GameNativeActivity.kt      # NativeActivity for libps2EntryRunner; publishes env, redirects stdio
│   ├── GameActivity.kt            # Engine diagnostics view (EGL/AAudio/input host, no game content)
│   ├── ImportActivity.kt          # SAF import + validation progress
│   ├── SettingsActivity.kt        # Settings index (General, Graphics, Performance, Audio, Controls, Storage, Advanced)
│   ├── SettingsCategory.kt        # Category keys/titles/summaries
│   ├── SettingsCategoryActivity.kt# Per-category screen; Advanced also shows the latest game log
│   ├── ControllerSettingsActivity.kt # Gamepad mapping and dead zones
│   ├── TouchLayoutEditorActivity.kt  # Custom on-screen layout editor
│   ├── SotcApplication.kt         # Process-wide library/paths/settings wiring
│   ├── core/AppPaths.kt           # files/games/logs/cache roots (created at start)
│   ├── core/CoreProbe.kt          # Detects libsotc_game.so / libps2EntryRunner.so (dir + APK)
│   ├── core/GameImporter.kt       # Copies + validates the selected ISO
│   ├── core/GameLibrary.kt        # Installed game record: ISO, disc tree, boot ELF, memory card
│   ├── core/DiscTree.kt           # Marker file bound to the image SHA-256
│   ├── core/DiscTreeExtractor.kt  # Full-disc extraction into files/games/<serial>/disc
│   ├── core/GameLogs.kt           # Newest runtime log, tail reader, size formatting
│   ├── core/RuntimeEnvironment.kt # Environment names/values published before the runner loads
│   ├── core/SettingsStore.kt      # SharedPreferences-backed settings
│   ├── disc/                      # Iso9660.kt, SystemCnf.kt, Ps2Elf.kt, DiscValidator.kt, IoUtil.kt
│   ├── input/                     # PadState, TouchControllerView, TouchLayout(+Store), GamepadManager, ControllerMapping
│   ├── nativebridge/              # NativeCore (JNI), NativeSurfaceView
│   └── ui/Ui.kt                   # Shared charcoal/gold/ivory widgets
└── cpp/
    ├── CMakeLists.txt             # Builds libsotc_core.so (host engine + JNI)
    ├── sotc_game_api.h            # ABI v1 contract (diagnostics/future headless core)
    ├── native_core.cpp            # JNI: surface/audio/input callbacks, setenv, stdio redirect
    ├── engine/engine.cpp          # Engine loop: init/frame/resize/destroy
    ├── engine/game_core.cpp       # dlopen("libsotc_game.so") + ABI checks
    ├── engine/egl_renderer.cpp    # EGL/GLES3 renderer + shader pipeline
    ├── engine/render_target.cpp   # FBO management
    ├── engine/audio_engine.cpp    # AAudio/OpenSL ES output
    ├── engine/gl_util.cpp         # GL helpers
    └── engine/diagnostics_scene.cpp # Scene shown by the diagnostics view
```

The recompiled runner is not part of this tree: `tools/android-integration/package-runner.sh`
copies `libps2EntryRunner.so` into `app/src/main/jniLibs/arm64-v8a/` (gitignored)
after the Milestone 4 build. 84 unit tests live under `app/src/test/`.

## third_party/celosia/ — Recompilation Pipeline (GPL-3.0)

- `celosia` — 342-line bash driver. Commands: `setup extract configure run vu1 recompile build autotest vita clean`.
- `game/scus.toml` — boot-ELF recomp config (stubs, entry points, mmio template).
- `game/overrides/` — 161 native override files (sotc_native.cpp 634 KB renderer hooks, decomp_*.cpp, lean_*.cpp, vita_hot_order.ld).
- `tools/` — `funcmap_elf.py` (symbol heuristics, no Ghidra needed), `build_elf.py` (merge XFF@auto@dump), `gen_config.py`, `xff.py`, `modimage.py`.
- Vendored PS2Recomp fork inside (game-tuned; differs from `third_party/PS2Recomp`).
- Pipeline: extract (bsdtar) → CMake configure → build ps2_recomp → stage_boot (KERNEL.XFF symbols) → 3× capture (needs display; PS2X_DUMP_RAM) → stage_game → recompile → VU1 collect (autotest + PS2X_VU1_DUMP_MISSING) → codegen (PS2X_VU1_GEN) → build runner.
- Platforms: macOS (Metal), Vita (vitaGL GLES). Windows/WSL gaps documented in `TECHNICAL_DECISIONS.md`.

## third_party/PS2Recomp/ — Upstream with Android Scaffold (GPL-3.0)

- `android/` — AGP 8.6.1 scaffold: `hasCode="false"` NativeActivity, `android.app.lib_name=ps2EntryRunner`, arm64-v8a + x86_64, NDK 28.2, raylib 5.5 (PLATFORM=Android).
- `ps2xRuntime/` — the PS2 runtime: 32 MB RDRAM, EE 294.912 MHz scheduler, CPU software GS rasterizer, VU1 interpreter, IOP R3000A. `src/lib/ps2_android_runtime.cpp` is the gameplay touch overlay on Android (`ps2_android_input`, patch 0005) and an empty file elsewhere.
- `ps2xRuntime/CMakeLists.txt` — Android shared-lib target, sse2neon v1.9.1 (`PS2X_IS_AARCH64_TARGET`), `PS2X_DEFAULT_BOOT_ELF`, FFmpeg OFF on Android (MPEG stub).
- `games_database.cpp` — no SCUS-97472 entry (only "?" European SotC at ~line 590).

## docs/ — Project Documentation

- `ARCHITECTURE.md` — full system architecture (host app + planned game core).
- `ANDROID_PORTING_PLAN.md` — phase plan (Phases 0–8); Phase 3 must be updated to reflect ISO now validated.
- `RESEARCH.md` — prior research (Celosia/PS2Recomp history).
- `COMPATIBILITY.md` — states "no disc image available" — needs update post-validation.
- `BUILDING_ANDROID.md`, `THIRD_PARTY_LICENSES.md`.

## tools/ — Our Pipeline Tools

- `inspect_original_iso.py` — Python-stdlib ISO inspector mirroring app's DiscValidator logic
  (ISO9660 PVD parse, SYSTEM.CNF, PS2 ELF header/segments, XFF magic, SHA-256, JSON report).
  Run: `python tools/inspect_original_iso.py`.
- `android-integration/` — the eight runner patches (`patches/`, marker-based
  `apply-patches.sh`), `verify-runner.sh` (ELF/AArch64/entry point/touch
  symbols/STL/16 KB alignment evidence, ends in `RUNNER_VERIFY_OK`) and
  `package-runner.sh` (copies the built library into `jniLibs/arm64-v8a`,
  strips it and re-verifies it).
- `device/` — `first-boot.ps1`, the ARM64 first-boot capture: installs the
  debug APK, starts the launcher or `GameNativeActivity`, and writes
  `build/device/<stamp>/{device.txt,logcat.txt,screen.png,game.log}`.
- `capture-host/` — WSL pipeline helpers: `configure-android.sh`,
  `build-android-runner.sh`, `android-after-pipeline.sh` (unattended M4 start),
  `install-ndk.sh`, plus the capture-host patch series.

## original iso/ — Private Asset (NEVER COMMIT)

- `SCUS_974.72.Shadow of the Colossus.iso` — 2.51 GiB, validated, SHA-256 `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37`.
- Protected by `.gitignore` line `/original iso/` (verified via `git check-ignore`).
