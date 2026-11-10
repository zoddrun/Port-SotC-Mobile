# Blockers

> Agent memory file: anything blocking progress. Track resolution status here.

## Active Blockers

| # | Blocker | Impact | Status | Workaround |
|---|---|---|---|---|
| B1 | Repo path contains a space ("Port SotC Mobile") | Ninja/CMake builds emit non-portable path warnings; risky toolchain behavior | OPEN | Copy/clone pipeline into space-free WSL ext4 path (`~/sotc-port`) per D6 |
| B2 | WSL RAM 1.7 GiB visible | Parallel compile jobs can OOM | OPEN | Limit `ninja -j` / `CELOSIA_JOBS` (start with `-j2`, raise if stable) |
| B3 | WSL lacks `xvfb` (and `libarchive-tools`/`pkg-config` may be missing) | Celosia capture/autotest stages need a display; extract needs bsdtar | OPEN | `sudo apt install -y xvfb libarchive-tools pkg-config` (passwordless sudo confirmed) |
| B4 | No ARM64 physical Android device connected | Milestone 6+ hardware verification cannot run yet | OPEN | Cross-compile + x86_64 emulator testing first; hardware test when developer connects a device |

## Resolved Blockers

| # | Was | Resolution |
|---|---|---|
| R1 | No disc image available for validation | Milestone 1 complete: developer supplied the USA retail ISO; validated compatible (2026-11-09) |
| R2 | Boot-ELF serial derivation assumed 12-char filename | Fixed: 11-char `SCUS_974.72` → `SCUS-97472` (D3) |
| R3 | Windows-native pipeline impossible (bash/macOS-oriented Celosia) | Resolved by decision D1: run in WSL2 Ubuntu 26.04 |

## Non-Blockers (explicitly ruled out)

- **Ghidra missing:** not required — celosia's `funcmap_elf.py` heuristics cover symbol recovery (D8).
- **No Android CI:** nice-to-have, not blocking any milestone.
- **Remote never pushed:** intentional — no push without developer authorization.
