# Recompilation Pipeline

> Status: **In progress** (Milestone 3). Verified stages and the exact host
> workarounds are recorded here; anything pending is labeled as such. The
> pipeline itself is [Celosia](https://github.com/HenryKun55/celosia) (GPL-3.0)
> with its game-tuned PS2Recomp fork, executed in WSL2 on the development
> machine. Nothing in this document describes an emulator: the output is native
> C++ compiled for the host CPU architecture.

## Workspace

The developer's live pipeline workspace (adopted, decision D10 in
`docs/agent/TECHNICAL_DECISIONS.md`):

| Path | Content |
|---|---|
| `/root/celosia` | Celosia checkout at `f8c269e` + two uncommitted local fixes (captured in-repo as `tools/capture-host/patches/0001-non-vita-build-fixes.patch`) |
| `/root/sotc/build` | Build tree (`build/cmake` = CMake/Ninja, `build/runner` = generated EE code, `build/vu1rec` = generated VU1 code, `build/recomp` = recompiler stages) |
| `/root/sotc/disc` | Extracted disc contents (from the developer's private ISO, `original iso/`) |
| `/root/sotc/neon-shim` | Host shims: `arm_neon.h` (SIMDe) + `mach-o/dyld.h` (`dl_iterate_phdr`) |
| `tools/capture-host/` | In-repo, byte-verified mirror of every workspace script and shim |

Recover the environment after a WSL restart: `swapon /root/swapfile`,
`/root/sotc/fix-x11.sh`, then start `Xvfb :99` (see `tools/capture-host/README.md`).

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 1 | Disc extract (`bsdtar`) | **Done** | `SCUS_974.72`, `KERNEL/MANAGER/GAMECORE.XFF`, `NICO.DAT`; ISO hash-verified against `original iso/` |
| 2 | CMake configure | **Done** | raylib 5.5, imgui, rlimgui, elfio, fmt, libdwarf, rabbitizer, toml11 fetched; `CMAKE_POLICY_VERSION_MINIMUM=3.5` (CMake 4.2) |
| 3 | Build `ps2_recomp` | **Done** | The recompiler binary |
| 4 | `stage_boot` | **Done** | KERNEL.XFF SHN_ABS symbols → `scus_symbols.txt`; `funcmap_elf.py` maps the boot ELF; recompiler emits 1429 C++ files (`build/runner/`), "Recompilation completed successfully" |
| 5 | Build `ps2EntryRunner` (capture host, x86_64) | **Attempt 6 in progress** | Attempts 1–5 resolved: SIMDe NEON shim + `-msse4.1` + force-include + restricted A32V7 aliases (D13), `mach-o/dyld.h` shim (D16), the two non-Vita source fixes (patch 0001), unity build OFF (D17) |
| 6 | Module captures (KERNEL → MANAGER → GAMECORE) | Pending | Runner runs under Xvfb with `PS2X_DUMP_RAM_ON_MISSING`; each capture is a 32 MiB guest RAM dump taken when execution reaches not-yet-recompiled code |
| 7 | `stage_game` (after each capture) | Pending | `build_elf.py` merges `XFF@auto@dump` modules into one ELF → `gen_config.py` → recompile → runner rebuild |
| 8 | VU1 microcode collection + codegen | Pending | `autotest` scripted run with `PS2X_VU1_DUMP_MISSING`, then `PS2X_VU1_GEN` emits `build/vu1rec/` |
| 9 | ARM64 cross-compile (Android) | Pending | Milestone 4; NDK toolchain, sse2neon, real `arm_neon.h` (never the x86 shim), mach-o shim on the include path. Prepared patch series: `tools/android-integration/patches/` (English-only menu per D7 + SCUS-97472 game-name entry), applied together with the capture-host fixes at Milestone 4 start |

## Running the remaining stages

After the capture-host runner builds:

```bash
cd /root/sotc
./celosia-env.sh setup        # captures ×3 → stage_game ×3 → VU1 → final rebuild
```

`celosia-env.sh` wraps the celosia driver with every host workaround (paths,
`-j2`, SSE4.1 + NEON/mach-o shims, unity OFF, `DISPLAY=:99`, software GL).
Individual captures can be driven with `capture-module.sh KERNEL|MANAGER|GAMECORE`.
If MANAGER/GAMECORE need game progression, set `PS2X_INPUT_SCRIPT` (see the
scripting language documented inside `ps2_pad.cpp`).

## Host workarounds (x86_64 capture build)

Celosia targets ARM hosts (Vita, Apple Silicon). Building the capture host on
x86_64 Linux needs (details and rationale in `docs/agent/TECHNICAL_DECISIONS.md`):

1. **SIMDe NEON shim** (`neon-shim/arm_neon.h`) — 8 of the 161 game overrides
   use the NEON API; on x86 it comes from SIMDe with **only**
   `SIMDE_ARM_NEON_A32V7_ENABLE_NATIVE_ALIASES` (the global alias switch corrupts
   GCC's own `immintrin.h`). A `-include` force-include covers the overrides
   that guard `#include <arm_neon.h>` behind `__ARM_NEON`/`__arm__`.
2. **`-msse4.1`** — `ps2_runtime_macros.h` needs SSE4.1 (`_mm_extract_epi64`);
   matches upstream's Linux CI flags.
3. **`mach-o/dyld.h` shim** — `decomp_lines67.cpp` includes the macOS header
   on every non-Vita host (guard bug); the shim implements
   `_dyld_get_image_header(0)` via `dl_iterate_phdr`.
4. **Unity build OFF** (D17) — several overrides redeclare the same
   namespace-scope types per-TU (`decomp_group::NextRegs` in 3 files), which
   only collides inside unity batches; disabling unity removes the class and
   lowers peak per-compile memory under the WSL RAM limit.
5. **Non-Vita source fixes** — two latent override bugs that upstream never
   compiled (`PS2X_VU_NEON_A32 == 0` path); kept as
   `tools/capture-host/patches/0001-non-vita-build-fixes.patch`.

None of these touch the Android/ARM64 target: real ARM64 builds use the NDK's
native `arm_neon.h` and sse2neon (`USE_SSE2NEON`) for the runtime's SSE
intrinsics. The x86 shims must never appear on an ARM64 include path.

## Android integration patches

`tools/android-integration/patches/` is applied by
`tools/capture-host/configure-android.sh` after the pipeline is complete:

| Patch | Purpose |
|---|---|
| `0001-english-only-host-menu.patch` | Removes the Portuguese runtime menu (D7). |
| `0002-games-database-scus-97472.patch` | Adds the USA serial so the runtime resolves the game title. |
| `0003-android-quit-graceful.patch` | Returns from `main()` on Android instead of `_Exit`, so raylib can finish the `NativeActivity`. |
| `0004-android-boot-path.patch` | Reads the boot executable path from `SOTC_BOOT_ELF`; Android provides no argv and the app's private path is device/user dependent, so it cannot be baked in at configure time. |

Patch 0004 is generated from the pristine pinned `main.cpp` and verified
byte-for-byte in both patch orders. `GameNativeActivity` sets the variable
before the runner library is loaded.

## Legal notes

- The recompiled code and RAM dumps are derived from the developer's legally
  obtained disc image. They are build outputs, not repository content: nothing
  under `/root/sotc/build` is committed, and no release ships them unless
  redistribution is authorized.
- The original ISO stays in `original iso/` (gitignored, never modified).
- Celosia and PS2Recomp are GPL-3.0 — see `docs/THIRD_PARTY_LICENSES.md`.
