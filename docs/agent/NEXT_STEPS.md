# Next Steps — Ordered Roadmap

> Agent memory file: the actionable queue. Work top-down; update as items complete.

## Milestone 3 — Real Recompilation Pipeline (NEXT)

1. [ ] **WSL workspace setup** (addresses B1/B3):
   - `mkdir ~/sotc-port` in WSL ext4; clone/copy repo there (space-free path).
   - Copy ISO read-only: `cp "/mnt/c/Users/maria/Desktop/Port SotC Mobile/original iso/SCUS_974.72.Shadow of the Colossus.iso" ~/sotc-port/disc.iso` (verify SHA-256 after copy).
   - `sudo apt install -y xvfb libarchive-tools pkg-config` (also confirm cmake, ninja, g++, python3 present).
2. [ ] **Extract disc**: `bsdtar -xf disc.iso -C disc` (or `tar` fallback) — verify XFF modules present.
3. [ ] **Configure + build ps2_recomp**: CMake configure with `-DPS2X_RUNNER_DIRS=build/runner;build/vu1rec;game/overrides` per celosia script; build with capped jobs (`-j2` start, B2).
4. [ ] **stage_boot**: KERNEL.XFF symbol extraction (xff.py + funcmap_elf.py) → generated scus.toml inputs. Capture logs; fix script incompatibilities as they surface (expect macOS-isms).
5. [ ] **Captures ×3** (KERNEL → MANAGER → GAMECORE) under `xvfb-run`; each uses `PS2X_DUMP_RAM_ON_MISSING` and polls the 32 MB RDRAM dump. Verify dump files appear and are non-trivial.
6. [ ] **stage_game**: build_elf.py merge → single game ELF; gen_config.py → recomp config.
7. [ ] **Recompile**: run ps2_recomp over the merged ELF; expect large C++ output.
8. [ ] **VU1 collection**: autotest 40 s with `PS2X_VU1_DUMP_MISSING`, then `PS2X_VU1_GEN=dumps:outdir`.
9. [ ] Commit pipeline results (scripts adapted for Linux/WSL, config diffs) — never the disc dump binaries if too large; store under `build/` (gitignored).

## Milestone 4 — ARM64 Cross-Compile

- [ ] Cross-compile the recompiled core + `game/overrides/` with Android NDK (26.1 on Windows; consider NDK 28.2 from upstream scaffold) for arm64-v8a: sse2neon path (`PS2X_IS_AARCH64_TARGET`), `PS2X_DEFAULT_BOOT_ELF` baked.
- [ ] Produce `libsotc_game.so` satisfying `sotc_game_api.h` ABI v1 (write the shim around PS2X runner entry).
- [ ] Translate Portuguese runtime strings to English (D7); add SCUS-97472 to games database.

## Milestone 5 — ABI Integration

- [ ] Package `libsotc_game.so` into the app; wire `game_core.cpp` dlopen path.
- [ ] Implement Android input → PS2X pad mapping (fix ps2_android_runtime.cpp TODO equivalent).
- [ ] Audio route through our `audio_engine.cpp` sink.

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
