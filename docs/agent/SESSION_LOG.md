# Session Log

> Agent memory file: append-only record of work sessions. Newest last.

## Session 1 — 2026-11-09 (recovery + validation)

**Goal:** Full project recovery per the master prompt (Milestones 0–1), then set up for recompilation.

**Done:**
- Milestone 0 complete: full read-through of all first-party native C++ (13 files) and Kotlin (~30 files); docs cross-checked against code; repository state verified (branch main, HEAD 194804f, 12 commits, remote never pushed).
- Background audit A: `third_party/celosia` (bash driver, tools, 161 override files, platform matrix, GPL-3.0, Windows blockers identified).
- Background audit B: `third_party/PS2Recomp` (Android scaffold, runtime internals, gaps: android runtime TODO, no SCUS-97472 entry, FFmpeg off).
- Milestone 1 complete: wrote `tools/inspect_original_iso.py`; validated the developer's USA retail ISO (SCUS-97472; ELF + 3 XFF modules OK; SHA-256 d945516e...; report at `build/reports/original_iso.json`).
- Environment probed: WSL2 Ubuntu 26.04 (cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, 953 GB ext4, passwordless sudo); Windows side (Python 3.12.10, JDK 17 temp + Java 25, Android SDK w/ NDK 26.1).
- Fixed serial derivation bug (11-char boot filename, D3). `.gitignore` hardened for `/original iso/`.
- Created this `docs/agent/` memory system.

**State at session end:** Milestones 0–2 complete (audit + validation + upstream research). Nothing committed yet from this session (`.gitignore` modified, `tools/` untracked). Next: first milestone commit, then WSL workspace setup (NEXT_STEPS.md Milestone 3 item 1).

**Carry-overs:** B1 space-in-path, B2 WSL RAM 1.7 GiB, B3 missing xvfb/libarchive-tools, B4 no ARM64 device.

## Session 2 — 2026-11-09 (memory system + doc sync + commit)

**Done:**
- Created `docs/agent/` memory system: PROJECT_STATE, CODEBASE_MAP, FILE_AUDIT, ARCHITECTURE, TECHNICAL_DECISIONS, COMPLETED_WORK, KNOWN_ISSUES, BLOCKERS, NEXT_STEPS, SESSION_LOG.
- AGENTS.md: added Agent Session Recovery Procedure section.
- Doc sync for Milestone 1: README.md (verified-state row + new "Original disc image validation" section), docs/COMPATIBILITY.md (summary row + section 5 note), docs/ANDROID_PORTING_PLAN.md (phase summary row + Phase 3 status/exit criteria).
- `.gitignore`: added `__pycache__/`; re-verified ISO and pycache both ignored via `git check-ignore`.
- Committed: `docs: establish agent memory and record retail ISO validation`.

**Carry-overs:** unchanged (B1–B4).

## Session 3 — 2026-11-09/10 (Milestone 3: WSL pipeline setup → adoption)

**Done:**
- Probed WSL: all pipeline tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, git; FFmpeg/GL/X11 dev packages). Installed only `zlib1g-dev`. Network OK.
- Learned: `/mnt/c` copies of the 2.5 GiB ISO fail in-WSL with "Cannot allocate memory" (D12 — use `\\wsl.localhost` from the Windows side).
- Set up a duplicate workspace in `~/sotc-port` (clone + ISO copy) — then discovered the developer's own live pipeline at `/root/celosia` + `/root/sotc` (disc extracted, configure running with the CMake-4 policy fix). Asked the developer; **confirmed adoption** — deleted my duplicate, took over `/root/sotc` (D10).
- Adopted workspace state verified: configure complete (all deps fetched), `ps2_recomp` built, **stage_boot complete** (1429 recompiled functions in `build/runner/`, "Recompilation completed successfully" in recomp.log, build.ninja regenerated post-copy).
- Started the `ps2EntryRunner` build (`-j2`, log `/root/sotc/logs/build-ps2EntryRunner.log`).
- Memory docs updated: decisions D10–D12; blockers B1/B3 → resolved (R4–R7); PROJECT_STATE and NEXT_STEPS reflect Milestone 3 in progress.

## Session 3 — 2026-11-09/10 (Milestone 3: WSL pipeline setup → adoption)

**Done:**
- Probed WSL: all pipeline tools present (bsdtar, pkg-config, xvfb-run, cmake 4.2.3, ninja 1.13.2, g++ 15.2, python 3.14, git; FFmpeg/GL/X11 dev packages). Installed only `zlib1g-dev`. Network OK.
- Learned: `/mnt/c` copies of the 2.5 GiB ISO fail in-WSL with "Cannot allocate memory" (D12 — use `\\wsl.localhost` from the Windows side).
- Set up a duplicate workspace in `~/sotc-port` (clone + ISO copy) — then discovered the developer's own live pipeline at `/root/celosia` + `/root/sotc` (disc extracted, configure running with the CMake-4 policy fix). Asked the developer; **confirmed adoption** — deleted my duplicate, took over `/root/sotc` (D10).
- Adopted workspace state verified: configure complete (all deps fetched), `ps2_recomp` built, **stage_boot complete** (1429 recompiled functions in `build/runner/`, "Recompilation completed successfully" in recomp.log, build.ninja regenerated post-copy).
- **ISO hash verified in the adopted workspace**: `/root/sotc/source.iso` SHA-256 `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37` — byte-identical to the validated original.
- First `ps2EntryRunner` build attempt failed: game overrides include `<arm_neon.h>` unconditionally (x86_64 has none). Fixed with a SIMDe shim (`/root/sotc/neon-shim/arm_neon.h`, D13; `libsimde-dev` 0.8.2 installed; `-DCMAKE_CXX_FLAGS=-I/root/sotc/neon-shim` added to the cache). Build restarted (`-j2`).
- Prepared headless display for captures: found WSLg's `/tmp/.X11-unix` mounted read-only (blocked Xvfb); unmounted it, started persistent `Xvfb :99` (D14), confirmed Mesa llvmpipe (`swrast_dri.so`) for `LIBGL_ALWAYS_SOFTWARE=1`; wrote `/root/sotc/capture-module.sh` (celosia capture() replica with display env baked in).
- Added 8 GB swap (`/root/swapfile`) ahead of the LTO link (D15) — 1.7 GiB RAM + 1 GB stock swap would likely OOM linking ~1600 TUs.
- Memory docs updated: decisions D10–D15; blockers R4–R10 resolved; KNOWN_ISSUES/PROJECT_STATE/NEXT_STEPS current.

**State at session end:** ps2EntryRunner rebuild running with the NEON shim (Xvfb :99 + swap ready). Next: verify build exit; run `/root/sotc/capture-module.sh KERNEL` (triggers right after boot); then MANAGER/GAMECORE (may need `PS2X_INPUT_SCRIPT` progression), then stage_game, then VU1.

**Carry-overs:** B2 (RAM 1.7 GiB — build at `-j2`), B4 (no ARM64 device).
