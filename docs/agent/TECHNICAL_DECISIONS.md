# Technical Decisions Log

> Agent memory file: rationale for every significant decision. Append new decisions; never delete old ones.

## D1 — WSL2 Ubuntu as pipeline execution host (2026-11-09)

**Decision:** Run the Celosia recompilation pipeline inside WSL2 Ubuntu 26.04, not Windows-native.

**Rationale:**
- Celosia is bash-orchestrated and macOS-oriented; Windows native lacks cmake/ninja on PATH anyway.
- WSL2 has CMake 4.2.3, Ninja 1.13.2, g++ 15.2, Python 3.14 installed and 953 GB free on its ext4 filesystem.
- Building inside ext4 avoids the C: drive constraint (only ~11.6 GB free) and the space-in-path problem.

**Trade-offs:** capture stage opens a real window → need `xvfb-run` (install required); file access from Windows via `/mnt/c` is slower (build in ext4, not on /mnt/c).

## D2 — Python-stdlib ISO inspector (2026-11-09)

**Decision:** `tools/inspect_original_iso.py` uses only Python stdlib, mirroring the app's Kotlin DiscValidator logic.

**Rationale:** zero dependencies; validates independently of the Android build; produces the JSON report `build/reports/original_iso.json`.

## D3 — Serial derivation fix for SCUS-974.72 (2026-11-09)

**Decision:** Boot filename `SCUS_974.72` is 11 characters, not 12. Correct check: `len(base) == 11` with pattern `AAAA_NNN.NN` → serial `SCUS-97472`.

**Rationale:** Live testing against the real disc; the initial 12-char assumption failed and was corrected immediately.

## D4 — Prefer celosia's vendored PS2Recomp for game core generation (2026-11-09)

**Decision:** Use `third_party/celosia/`'s internal PS2Recomp fork (game-tuned, with SotC patches) to generate the game core; use `third_party/PS2Recomp/android/` scaffold as reference for the Android build integration.

**Rationale:** The celosia fork contains 161 SotC-specific override files and pipeline glue the upstream lacks; the upstream checkout has the newer Android packaging knowledge. Each is used where it is strongest.

## D5 — ISO stays at repo root under `original iso/` (2026-11-09)

**Decision:** Developer-supplied ISO lives in `original iso/` (with space), gitignored via `/original iso/`; never modified, moved, or committed. Copies for pipeline use are made read-only into WSL ext4.

**Rationale:** Preserves the developer's asset location; gitignore verified with `git check-ignore -v`.

## D6 — Space-in-path workaround via WSL ext4 workspace (2026-11-09)

**Decision:** Never run Ninja/CMake builds under a path containing a space (repo dir "Port SotC Mobile" does). Clone or copy the pipeline sources into a space-free WSL path (e.g. `~/sotc-port/`) and run there.

**Rationale:** Ninja emits non-portable path warnings for spaces; CMake policy + toolchain edge cases. ext4 also sidesteps C: free-space limits.

## D7 — English-only translation of celosia runtime UI (pending)

**Decision:** When integrating the PS2X runtime + overrides into our port, all Portuguese UI strings must be translated to English per the project mandate.

**Rationale:** AGENTS.md mandates 100% English in all project components, including UI text carried in from third-party code we build on.

## D8 — Ghidra not required for symbol mapping (2026-11-09)

**Decision:** Rely on celosia's `funcmap_elf.py` heuristics for boot-ELF symbol recovery; no Ghidra install.

**Rationale:** Celosia was designed to work without Ghidra; revisit only if heuristics prove insufficient during Milestone 3 captures.

## D9 — Commit cadence (2026-11-09)

**Decision:** Commit at meaningful milestones only (docs/memory, pipeline results, ARM64 core, ABI integration...); never push to `origin` without explicit developer authorization; never commit the ISO, build artifacts, or `build/reports/original_iso.json` (contains local path but no secrets — still build output, keep out of git).

**Rationale:** Atomic, reviewable history per AGENTS.md Git Practices.

## D10 — Adopt the developer's /root/sotc WSL pipeline workspace (2026-11-10)

**Decision:** The developer runs their own Celosia pipeline instance in WSL at `/root/celosia` (clean clone of our third_party/celosia @ f8c269e) with build dir `/root/sotc/build` and disc dir `/root/sotc/disc`. We adopt and continue that workspace instead of maintaining a duplicate. My duplicate in `~/sotc-port` was deleted.

**Rationale:** Same commit, same pipeline; duplicate builds waste the 1.7 GiB WSL RAM budget. Developer confirmed adoption.

**Trade-offs:** The workspace layout differs from the celosia script defaults — set `CELOSIA_BUILD=/root/sotc/build CELOSIA_DISC=/root/sotc/disc` when invoking the script, or drive cmake directly as the developer did.

## D11 — CMake 4.x policy floor for raylib 5.5 FetchContent (2026-11-10)

**Decision:** The vendored PS2Recomp's FetchContent of raylib 5.5 requires `-DCMAKE_POLICY_VERSION_MINIMUM=3.5` under CMake 4.2. The developer's configure already applied it.

**Rationale:** CMake 4 rejects projects declaring cmake_minimum_required < 3.5; raylib 5.5's CMakeLists declares an older minimum. Reproduce in any fresh configure via `CELOSIA_CMAKE_ARGS="-DCMAKE_POLICY_VERSION_MINIMUM=3.5"`.

## D12 — Large file copies into WSL go through \\wsl.localhost, not /mnt/c (2026-11-10)

**Decision:** Copy multi-GB files (the ISO) from Windows into WSL ext4 via `Copy-Item ... \\wsl.localhost\Ubuntu\...`, never `cp` from `/mnt/c` inside WSL.

**Rationale:** WSL-side `cp` of the 2.5 GiB ISO over the 9p filesystem failed with "Cannot allocate memory" (WSL sees only 1.7 GiB RAM). The Windows-side path streams without WSL RAM pressure.

## D13 — SIMDe shim for ARM-targeted game overrides on x86_64 hosts (2026-11-10)

**Decision:** The celosia game overrides (`game/overrides/*.cpp`, 8 files incl. sotc_native.cpp, decomp_pose82.cpp) include `<arm_neon.h>` unconditionally — celosia was only ever built for ARM hosts (Vita / Apple Silicon). For the x86_64 capture build we provide `/root/sotc/neon-shim/arm_neon.h`: a guarded shim that defines `SIMDE_ENABLE_NATIVE_ALIASES` and includes `<simde/arm/neon.h>` (package `libsimde-dev` 0.8.2), giving the full NEON API via SSE. Added to the build with `-DCMAKE_CXX_FLAGS=-I/root/sotc/neon-shim` (cache-persistent across the celosia script's re-configures). No vendored sources were modified.

**Rationale:** Least invasive fix; SIMDe exists precisely for this; the runtime's own NEON uses (ps2xIOP sound_mixer.cpp) are correctly `__ARM_NEON`-guarded and unaffected. The Android ARM64 build uses the NDK's real `arm_neon.h` and must never include this shim directory.

**Trade-offs:** NEON→SSE translation is not bit-identical to real NEON (float rounding paths differ in last bits) and slightly slower — acceptable for capture runs; the ARM64 target build uses real NEON. If an override uses an intrinsic SIMDe 0.8.2 lacks, we will see it as a compile error and handle it explicitly.

## D14 — Capture display on WSL: unmount WSLg ro socket dir + Xvfb :99 + llvmpipe (2026-11-10)

**Decision:** WSLg leaves `/tmp/.X11-unix` mounted **read-only** (its own X0 socket is unusable from root when the systemd user session fails), which blocks Xvfb from creating a socket. Fix: `umount /tmp/.X11-unix` (as root, distro /tmp is then a normal rw tmpfs), run `Xvfb :99 -screen 0 1024x768x24 -nolisten tcp`, and give the runner `LIBGL_ALWAYS_SOFTWARE=1` (Mesa llvmpipe; `swrast_dri.so` present). `/root/sotc/capture-module.sh` replicates celosia's `capture()` with these env vars baked in.

**Rationale:** raylib/GLFW needs an X display; Xvfb + software GL is the standard headless recipe. The WSLg socket mount is per-boot state — if a future WSL restart restores it, re-run the umount (script: diag/fix-x11.sh in the session temp; reproduce with `umount /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix`).

**Trade-offs:** llvmpipe rendering is slow — fine for captures (the runner is only run until the first not-yet-recompiled branch). If capture runs turn out frame-starved, raise Xvfb screen size down (640x480) or run with `PS2X_GS_GPU=0`.

## D15 — 8 GB swap file for the LTO link (2026-11-10)

**Decision:** `/root/swapfile` (8 GiB, mkswap+swapon) added to WSL. The ps2EntryRunner link unifies ~1600 translation units with `-flto=auto` under a 1.7 GiB RAM ceiling; stock swap was 1 GB.

**Rationale:** LTO link memory scales with TU count; OOM at link was the predictable next failure. Disk has 951 GB free; swap costs nothing when unused.

**Trade-offs:** Swapping is slow, but a slow link beats a failed one. If it proves too slow, rebuild the runner with `-DPS2X_ENABLE_RUNNER_UNITY_BUILD=ON` already on (49 batches) — worst case disable LTO for capture builds only.
