# Known Issues

> Agent memory file: real, verified problems to remember. No speculation — only observed issues.

## Third-Party Code (must fix in our port)

1. **Portuguese UI strings in celosia/PS2Recomp runtime.** The runner and overlay text is Portuguese. Violates our English-only mandate → translate when integrating (D7 in TECHNICAL_DECISIONS.md).
2. **`ps2_android_runtime.cpp` is a 3-line TODO** (third_party/PS2Recomp/ps2xRuntime/src/lib/). Android input glue does not exist upstream → we must implement it (or route through our own ABI).
3. **No SCUS-97472 entry in `games_database.cpp`** (both checkouts; the database only affects the window title, so this is cosmetic — verify at integration) → add a proper USA entry during integration.
4. **FFmpeg disabled on Android** in the vendored scaffold → SotC MPEG cutscenes will render stub frames initially. Must be handled honestly (documented limitation) until an Android-compatible decoder is integrated.
5. **PS2X runtime performance is acknowledged weak upstream**, especially VU1 and GS ("very bad" per project wiki) → optimization is a planned milestone; no performance claims until measured.
6. **Celosia was never built for x86_64 hosts**: the game overrides include `<arm_neon.h>` unconditionally (8 files). Mitigated for the capture build with the SIMDe shim (D13) — no source edits. Real ARM64 builds must use the NDK's native header.

## Environment

7. **Repo path contains a space** ("Port SotC Mobile") → Ninja non-portable path warnings; build only in a space-free WSL path (D6). *(Resolved for the pipeline by D10: /root/sotc.)*
8. **Host RAM ~4 GB total (WSL sees 1.7 GiB)** → mitigated: builds at `-j2` + 8 GB swap file (D15); LTO link can spill.
9. **C: drive ~11.6 GB free** → all pipeline artifacts in WSL ext4 (951 GB free). Watch `/root/sotc/build` growth (~1.3 GB so far) — fine.
10. **WSLg quirk: `/tmp/.X11-unix` is a read-only mount** after boot (breaks Xvfb). Fixed by umount (D14). If WSL restarts, re-apply before captures.

## Documentation Debt

11. `README.md`, `docs/ANDROID_PORTING_PLAN.md` (Phase 3), `docs/COMPATIBILITY.md` still state the disc image is not yet available → update after the memory-file commit to reflect Milestone 1 completion.

## Not Issues (avoid re-investigating)

- ISO `volumeIdentifier` is `"1"` — verified normal for this disc (matches declared/image sectors); not a corruption sign.
- Boot filename length 11 (not 12) — handled in the inspector (D3).
