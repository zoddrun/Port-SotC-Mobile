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
