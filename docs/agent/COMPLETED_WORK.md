# Completed Work Log

> Agent memory file: what has actually been done and verified. Only verified results are listed here.

## Milestone 0 — Recovery Audit (2026-11-09) — COMPLETE

- Read all 13 first-party native C++ files and ~30 Kotlin files in full (see FILE_AUDIT.md).
- Confirmed docs match code; confirmed no emulator, no fake gameplay, no stubs in the host app.
- Audited `third_party/celosia` completely (driver script, tools, overrides, platforms, licensing GPL-3.0).
- Audited `third_party/PS2Recomp` completely (runtime architecture, Android scaffold, gaps).
- Verified repository state: branch `main`, 12 commits, HEAD `194804f`, remote configured but never pushed.

## Milestone 1 — Original ISO Validation (2026-11-09) — COMPLETE

- Created `tools/inspect_original_iso.py` (Python stdlib only).
- Validated `original iso/SCUS_974.72.Shadow of the Colossus.iso` (2.51 GiB):

| Check | Result |
|---|---|
| ISO9660 PVD | PLAYSTATION / SCEA, 2048-byte blocks, 1,315,408 sectors — matches image size |
| SYSTEM.CNF | `BOOT2 = cdrom0:\SCUS_974.72;1`, VER 1.00, VMODE NTSC |
| Serial | **SCUS-97472** (derived from 11-char boot name) |
| Boot ELF | `/SCUS_974.72`, 242,940 bytes, entry 0x00100010, 2 loadable segments — VALID |
| Modules | KERNEL.XFF (415,908), MANAGER.XFF (1,650,876), GAMECORE.XFF (2,141,864) — all `xff2` magic OK |
| Contents | 19 files, 2,672,332,589 bytes data incl. NICO.DAT (2.48 GiB), MODULES/, MODULES2/, IOPRP300.IMG |
| SHA-256 | `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37` |

- Report written: `build/reports/original_iso.json` (not committed — build output).
- Verdict: **COMPATIBLE** — image is the genuine USA retail disc.

## Milestone 2 — Upstream Audits (2026-11-09) — COMPLETE

- Celosia pipeline fully mapped (stage-by-stage command sequence, env vars, platform matrix).
- PS2Recomp Android scaffold + runtime gaps catalogued (see KNOWN_ISSUES.md).

## Session Housekeeping (2026-11-09)

- `.gitignore` updated to protect `/original iso/` (uncommitted until first milestone commit).
- Created `docs/agent/` memory system (this file set).
