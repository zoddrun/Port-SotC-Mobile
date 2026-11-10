# Architecture (Agent Summary)

> Agent memory file: condensed architecture view.
> Full detail lives in `docs/ARCHITECTURE.md` (host app) — keep that synchronized.

## Target Architecture (end state)

```
┌──────────────────────────────────────────────────────┐
│ Android App (Kotlin)                                 │
│  MainActivity → GameImporter → GameLibrary           │
│  GameActivity → EngineHandle (JNI)                   │
├──────────────────────────────────────────────────────┤
│ libport-native.so (host C++)                         │
│  engine.cpp (loop) ─ egl_renderer (GLES3)             │
│  audio_engine (AAudio/OpenSL) ─ input bridge          │
│  game_core.cpp ── dlopen("libsotc_game.so")          │
├──────────────────────────────────────────────────────┤
│ libsotc_game.so  ← THE PORT (static recomp output)   │
│  ABI v1 per sotc_game_api.h                          │
│  Recompiled SotC EE code (ARM64) + PS2X runtime      │
│  (RDRAM 32MB, EE/IOP schedulers, GS rasterizer,      │
│   VU1, GTE) + game/overrides native fixes             │
└──────────────────────────────────────────────────────┘
```

## How the Game Core Is Produced (Celosia pipeline)

1. **Extract** the validated ISO (bsdtar) → `disc/` tree.
2. **Configure + build** `ps2_recomp` (the recompiler binary) via CMake.
3. **stage_boot**: extract symbols from KERNEL.XFF (`xff.py` + `funcmap_elf.py` heuristics), generate `game/scus.toml` inputs.
4. **Capture ×3** (KERNEL, MANAGER, GAMECORE): run the boot ELF in the PS2X runner with `PS2X_DUMP_RAM_ON_MISSING`; polls produce 32 MB RDRAM dumps.
5. **stage_game**: `build_elf.py` merges `XFF@auto@dump` images into a single game ELF; `gen_config.py` produces recomp config.
6. **Recompile**: ps2_recomp emits native C++ for all functions + runtime glue.
7. **VU1 collect**: autotest run (40 s) with `PS2X_VU1_DUMP_MISSING`; then codegen with `PS2X_VU1_GEN=dumps:outdir`.
8. **Build runner**: compile recompiled code + `game/overrides/` (161 files incl. sotc_native.cpp renderer hooks) into an executable/shared lib.

For Android we adapt stage 8: cross-compile with the Android NDK (ARM64) using the
vendored PS2Recomp Android path (sse2neon for x86 intrinsics → NEON,
`PS2X_DEFAULT_BOOT_ELF` baked in, raylib's ANativeActivity glue optionally
replaced by our own `sotc_game_api.h` ABI shim), producing `libsotc_game.so`.

## ABI v1 (`sotc_game_api.h` essentials)

- Version symbol check + init(frame callbacks for display/audio/input) + per-frame step + teardown.
- Host supplies GLES3 context, audio sink, pad state; core supplies game logic + draw calls.

## Two PS2Recomp Checkouts — Which to Use

- `third_party/celosia/` vendors a game-tuned fork with SotC-specific patches and overrides → **use this for generating the game core**.
- `third_party/PS2Recomp/` is the separate upstream checkout with the newest Android scaffold → **use as reference for the Android build integration**.

## Key Runtime Facts (PS2X runtime)

- 32 MB RDRAM; EE at 294.912 MHz; GS is a CPU software rasterizer (GLES backend via overrides); VU1 interpreter + collected microcode replacement; IOP R3000A interpreter.
- Known weak point upstream: VU and GS performance — expect optimization work in Milestone 8.
