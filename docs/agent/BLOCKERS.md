# Blockers

> Agent memory file: anything blocking progress. Track resolution status here.

## Active Blockers

| # | Blocker | Impact | Status | Workaround |
|---|---|---|---|---|
| B2 | WSL RAM 1.7 GiB | Compile/link can OOM | MITIGATED | `-j2` builds + 8 GB `/root/swapfile` (D15); watch link phase |
| B4 | No ARM64 physical Android device connected | Milestone 6+ hardware verification cannot run; the built arm64-v8a runner has never been loaded | OPEN | Everything that does not need hardware is done: the runner is built, stripped, verified and packaged, and the host engine's GPU/performance paths are verified on the x86_64 emulator. The emulators cannot stand in for the runner: they are x86_64 and cannot load an arm64-v8a library, and the API 37 image needs more RAM than this host has. `tools/device/first-boot.ps1` captures the first boot the moment a device is attached. |
| B5 | No environment can exercise the ES 3 path, the frame limiter or the thermal ceiling | Those three code paths are compiled and unit-covered at the settings level but have no runtime evidence | OPEN | The API 28 image rejects ES 3, SwiftShader cannot reach 30 fps (so a 30 fps cap never binds) and the emulator always reports thermal status 0. Verify on a device that accepts ES 3 and can sustain more than 30 fps; read `Frame stats:` for the scale, limit and thermal fields. |

## Resolved Blockers

| # | Was | Resolution |
|---|---|---|
| R1 | No disc image available for validation | Milestone 1 complete: developer supplied the USA retail ISO; validated compatible (2026-11-09) |
| R2 | Boot-ELF serial derivation assumed 12-char filename | Fixed: 11-char `SCUS_974.72` → `SCUS-97472` (D3) |
| R3 | Windows-native pipeline impossible (bash/macOS-oriented Celosia) | Resolved by decision D1: run in WSL2 Ubuntu 26.04 |
| R4 | Repo path contains a space ("Port SotC Mobile") — Ninja path warnings | Resolved: pipeline runs in WSL ext4 at `/root/sotc` (space-free), per D10 |
| R5 | WSL missing xvfb/libarchive-tools/pkg-config/zlib dev | Resolved 2026-11-09/10: all present (bsdtar, pkg-config, xvfb-run verified; zlib1g-dev installed) |
| R6 | /mnt/c copy of 2.5 GiB ISO fails ("Cannot allocate memory") | Resolved via D12: copy from Windows side through `\\wsl.localhost` |
| R7 | raylib 5.5 FetchContent incompatible with CMake 4.2 | Resolved via D11: `-DCMAKE_POLICY_VERSION_MINIMUM=3.5` (already applied in the adopted configure) |
| R8 | Game overrides fail to compile on x86_64 (`<arm_neon.h>` missing) + `_mm_extract_epi64` SSE4.1 error in ps2_runtime + guarded `<arm_neon.h>` includes never load the shim on x86 (attempt 3, unity_48) | Resolved via D13: restricted A32V7-alias SIMDe shim picked up by both `-I` (unguarded includes) and `-include` force-include (guarded includes), plus `-msse4.1` (upstream CI parity); `CMAKE_CXX_FLAGS='-msse4.1 -I/root/sotc/neon-shim -include /root/sotc/neon-shim/arm_neon.h'` (attempt 4 with these flags in progress) |
| R9 | WSLg read-only `/tmp/.X11-unix` blocks Xvfb; no desktop for capture runs | Resolved via D14: umount + Xvfb :99 + `LIBGL_ALWAYS_SOFTWARE=1` (llvmpipe) |
| R10 | LTO link of ~1600 TUs vs 1.7 GiB RAM + 1 GB swap | Resolved via D15: 8 GB swap file at /root/swapfile |
| R11 | `decomp_lines67.cpp` demands macOS-only `<mach-o/dyld.h>` on any non-Vita host (guard is `#if !defined(__vita__)`, not `__APPLE__`) — build attempt 4 failure | Resolved via D16: `mach-o/dyld.h` shim in the D13 shim dir implementing `_dyld_get_image_header(0)` via `dl_iterate_phdr` (validated PIE/no-PIE/unity, ELF magic checked); attempt 5 launched |
| R12 | Attempt 5, unity_45: two x86-host source bugs (`coll125.h` vf0c redeclaration; `decomp_collision.cpp` ungated Vita-only `fast::check49()`) + unity ODR collision (`decomp_group::NextRegs` in 3 override files) | Source bugs fixed by the developer directly in /root/celosia (2026-11-10, uncommitted local edits — preserve them); unity collision resolved via D17: `PS2X_ENABLE_RUNNER_UNITY_BUILD=OFF` for the capture host (attempt 6, running) |
| R13 | WSL reboot reset per-boot state: 8 GB swap gone + WSLg ro `/tmp/.X11-unix` (stacked twice) | Re-applied 2026-11-11: `swapon /root/swapfile`; `fix-x11.sh` (now loops for stacked mounts) + Xvfb :99 restarted. KNOWN_ISSUES #10 documents the per-boot re-check |
| R14 | First ARM64 compile failed at object 67: `EeScheduler.cpp` calls `backtrace()` behind a `!defined(__vita__)` guard, but bionic declares it only from API 33 and the app targets 28 | Resolved 2026-11-18 via patch 0007 (`0007-android-no-execinfo.patch`): both guards also exclude `__ANDROID__`, so the `PS2X_XFER_AUDIT` diagnostics stay host-only |
| R15 | ARM64 compile failed at object 9,264: the NDK's `-Werror=format-security` rejects `sfmt105`'s variadic forward in `game/overrides/sotc_native.cpp` | Resolved 2026-11-18 via patch 0008 (`0008-android-format-security.patch`): a scoped `#pragma clang diagnostic` around the two forwards; every call site passes a literal format |

## Non-Blockers (explicitly ruled out)

- **Ghidra missing:** not required — celosia's `funcmap_elf.py` heuristics cover symbol recovery (D8).
- **No Android CI:** nice-to-have, not blocking any milestone.
- **Remote publication:** resolved — `origin` was published with developer authorization on 2026-11-18 (repository public; documentation, source, and the `v0.1.0-alpha.1` preview released).
