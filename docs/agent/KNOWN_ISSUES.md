# Known Issues

> Agent memory file: real, verified problems to remember. No speculation — only observed issues.

## Third-Party Code (must fix in our port)

1. **Portuguese UI strings in celosia/PS2Recomp runtime.** The runner and overlay text is Portuguese. Violates our English-only mandate → translate when integrating (D7 in TECHNICAL_DECISIONS.md).
2. **`ps2_android_runtime.cpp` is a 3-line TODO** (third_party/PS2Recomp/ps2xRuntime/src/lib/). Android input glue does not exist upstream → we must implement it (or route through our own ABI).
3. **No SCUS-97472 entry in `games_database.cpp`** (only a "?" European SotC at ~line 590) → add a proper USA entry during integration.
4. **FFmpeg disabled on Android** in the vendored scaffold → SotC MPEG cutscenes will render stub frames initially. Must be handled honestly (documented limitation) until an Android-compatible decoder is integrated.
5. **PS2X runtime performance is acknowledged weak upstream**, especially VU1 and GS ("very bad" per project wiki) → optimization is a planned milestone; no performance claims until measured.
6. **Celosia's Windows compatibility is absent** (bash, .exe suffixes, bsdtar, python3, getconf, shasum; macOS Metal backend) → mitigated by running under WSL2 (D1).

## Environment

7. **Repo path contains a space** ("Port SotC Mobile") → Ninja non-portable path warnings; build only in a space-free WSL path (D6).
8. **Host RAM ~4 GB total (WSL sees 1.7 GiB)** → cap parallel jobs (`CELOSIA_JOBS`/`ninja -j` limits) or builds may OOM.
9. **C: drive ~11.6 GB free** → keep all pipeline artifacts in WSL ext4 (953 GB free); WSL VHDX itself lives on C: — monitor total growth.
10. **xvfb not installed in WSL** → capture/autotest stages need a display; `sudo apt install xvfb` before Milestone 3 captures.

## Documentation Debt

11. `README.md`, `docs/ANDROID_PORTING_PLAN.md` (Phase 3), `docs/COMPATIBILITY.md` still state the disc image is not yet available → update after the memory-file commit to reflect Milestone 1 completion.

## Not Issues (avoid re-investigating)

- ISO `volumeIdentifier` is `"1"` — verified normal for this disc (matches declared/image sectors); not a corruption sign.
- Boot filename length 11 (not 12) — handled in the inspector (D3).
