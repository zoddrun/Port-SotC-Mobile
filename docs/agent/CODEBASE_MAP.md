# Codebase Map

> Agent memory file: where everything lives in this repository.
> Last updated: 2026-11-09

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
├── AndroidManifest.xml            # MainActivity entry; GameActivity native-render path
├── java/com/portsoct/mobile/
│   ├── MainActivity.kt            # Library/launcher UI, ISO import
│   ├── GameActivity.kt            # Fullscreen game surface + engine lifecycle
│   ├── engine/EngineHandle.kt     # JNI bridge to libport-native
│   ├── disc/                       # Iso9660.kt, SystemCnf.kt, Ps2Elf.kt, DiscValidator.kt
│   ├── game/GameImporter.kt        # Copies/validates ISO into app storage
│   ├── game/GameLibrary.kt         # Installed game records
│   ├── input/PadState.kt           # 2-stick + buttons digital/analog state
│   ├── input/TouchControllerView.kt# On-screen dual-stick overlay
│   ├── input/GamepadManager.kt     # Android Controller mappings
│   └── ui/...                      # Import/theme UI
└── cpp/
    ├── CMakeLists.txt             # Builds libport-native (host engine)
    ├── sotc_game_api.h            # ABI v1 contract for future libsotc_game.so
    ├── native_core.cpp            # JNI surface (surface/audio/input callbacks)
    ├── engine/engine.cpp          # Engine loop: init/frame/resize/destroy
    ├── engine/game_core.cpp       # dlopen("libsotc_game.so") + ABI checks
    ├── engine/egl_renderer.cpp   # EGL/GLES3 renderer + shader pipeline
    ├── engine/render_target.cpp   # FBO management
    ├── engine/audio_engine.cpp    # AAudio/OpenSL ES output
    ├── engine/gl_util.cpp         # GL helpers
    └── engine/diagnostics_scene.cpp # Fallback scene when no game core loaded
```

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
- `ps2xRuntime/` — the PS2 runtime: 32 MB RDRAM, EE 294.912 MHz scheduler, CPU software GS rasterizer, VU1 interpreter, IOP R3000A. `src/lib/ps2_android_runtime.cpp` is a 3-line TODO stub.
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

## original iso/ — Private Asset (NEVER COMMIT)

- `SCUS_974.72.Shadow of the Colossus.iso` — 2.51 GiB, validated, SHA-256 `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37`.
- Protected by `.gitignore` line `/original iso/` (verified via `git check-ignore`).
