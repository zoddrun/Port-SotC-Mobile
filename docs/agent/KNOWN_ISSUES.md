# Known Issues

> Agent memory file: real, verified problems to remember. No speculation — only observed issues.

## Third-Party Code (must fix in our port)

1. **Portuguese UI strings in celosia/PS2Recomp runtime.** The runner and overlay text is Portuguese. Violates our English-only mandate → translate when integrating (D7 in TECHNICAL_DECISIONS.md). **Addressed by `0001-english-only-host-menu.patch`**: compiled and linked into the ARM64 runner, never run on a device, so the selected English strings have no runtime proof yet.
2. **Android input glue does not exist upstream** (`ps2_android_runtime.cpp` was a 3-line TODO; `ps2_pad.cpp` had no touch path). **Addressed by `0005-android-touch-input.patch`**: `ps2_android_input` samples raylib's `CORE.Input.Touch` each host frame, publishes a pad state, and `PSPadBackend::readState` merges it. Linked into the ARM64 runner and exported (`verify-runner.sh` checks `ps2_android_input::update`); **not yet run on a device**, so overlay geometry, multitouch ownership and the pad merge are unverified.
3. **No SCUS-97472 entry in `games_database.cpp`** (the database only affects the window/log title, so this is cosmetic). **Addressed by `0002-games-database-scus-97472.patch`**.
4. **FFmpeg disabled on Android** in the vendored scaffold → SotC MPEG cutscenes will render stub frames initially. Must be handled honestly (documented limitation) until an Android-compatible decoder is integrated.
5. **PS2X runtime performance is acknowledged weak upstream**, especially VU1 and GS ("very bad" per project wiki) → optimization is a planned milestone; no performance claims until measured.
6. **Celosia was never built for x86_64 hosts**: 8 override files use the NEON API — five include `<arm_neon.h>` unguarded, three (`sotc_native.cpp`, `decomp_char104.cpp`, `decomp_hot47j.cpp`) guard it with `__ARM_NEON`/`__arm__` while their NEON-typed code still compiles. Mitigated for the capture build with the restricted-alias SIMDe shim picked up via both `-I` and a `-include` force-include (D13) — no source edits. Real ARM64 builds must use the NDK's native header and must never see the shim directory.
7. **`decomp_lines67.cpp` unguarded macOS include**: guards `<mach-o/dyld.h>` with `#if !defined(__vita__)` (not `__APPLE__` like its sibling diagnostics), so every non-Vita non-Mac host needs a shim. Mitigated via `mach-o/dyld.h` implementing `_dyld_get_image_header(0)` with `dl_iterate_phdr` (D16). Also applied to the Android ARM64 build (bionic has `dl_iterate_phdr`); that build is complete.

## Environment

7. **Repo path contains a space** ("Port SotC Mobile") → Ninja non-portable path warnings; build only in a space-free WSL path (D6). *(Resolved for the pipeline by D10: /root/sotc.)*
8. **Host RAM ~4 GB total (WSL sees 1.7 GiB)** → mitigated: builds at `-j2` + 8 GB swap file (D15); LTO link can spill.
9. **C: drive ~11.6 GB free** → all pipeline artifacts in WSL ext4 (951 GB free). Watch `/root/sotc/build` growth (~1.3 GB so far) — fine.
10. **Per-boot WSL state resets:** WSLg re-mounts `/tmp/.X11-unix` read-only (breaks Xvfb — fix via `fix-x11.sh`, now loops for stacked mounts) AND the `/root/swapfile` swap deactivates. Observed after the 2026-11-11 reboot: BOTH had to be re-applied. Re-check `swapon --show` and `mount | grep X11` after any WSL restart before builds/captures.
11. **PowerShell/WSL quoting traps:** (a) `\$?` inside a double-quoted PowerShell string is expanded BY POWERSHELL (`True`/`False`), not bash — bash exit codes never reach the harness this way; (b) inner double quotes inside a `wsl -- bash -c '...'` command are STRIPPED in transport — any pattern containing spaces (`"struct NextRegs"`), pipes (`"UNITY\|unity"`), or semicolons gets split and misparsed by bash; use quote-free single-word patterns, or script files; (c) inline single-quoted multi-word `-D` args survive but `ps` must be trusted over partial CMakeCache reads mid-configure (~150 s). Rule: any non-trivial WSL command goes in a script file copied into ext4 (`/root/sotc/*.sh`), never inline. Long-running builds must write to per-attempt log files, not a shared one — stale tails mislead.
12. **Developer's live working-tree edits in /root/celosia** (2026-11-10 10:04, uncommitted, on top of f8c269e): `coll125.h` (vf0c→vf0u redeclaration fix) and `decomp_collision.cpp` (guard Vita-only `fast::check49()` behind `#if PS2X_VU_NEON_A32`). They fix real x86-host build errors — preserve them; never reset that checkout.
13. **GCC `-Wstringop-overflow` false positive, `sotc_native.cpp:438`** (upstream override code, seen building attempt 6): "writing 1 byte into a region of size 0" at offset 3 into `bone[3]`. Triaged 2026-11-11: `v.bones` is clamped via `std::min(m.word(q + 12u), 3u)` before the loop `k < v.bones`, so `k` stays ≤ 2 — `bone[3]`/`weight[3]` are never overrun; GCC cannot correlate the clamp with the loop bound. Expect the same warning in the Android build; ignore it, do not patch upstream for it.

## Host Engine — GPU and Frame Pacing (observed 2026-11-19)

1. **The ES 3 probe logs an error on ES 2-only drivers.** `eglChooseConfig` failure is logged with `SOTC_LOGE` before the fallback warning. The fallback is expected and works, but the log looks alarming. Keep it (it is the real driver answer) and rely on the following `Renderer backend:` line.
2. **Low-memory mode is much slower on the diagnostic scene.** Releasing the offscreen target means the procedural shader runs at full surface size: ~2-3 fps instead of ~13-15 fps at 25% scale on the API 28 emulator. That is the intended memory/GPU trade, not a regression; the offscreen path is the normal one.
3. **Frame limit, thermal ceiling and the ES 3 path have no runtime evidence** (B5). Do not describe them as verified. `Frame stats:` prints `limit` and `thermal` so the first capable device can settle it.
4. **Adaptive resolution is only observed stepping down.** Step-up (frame time under 75% of budget) is implemented but unverified.
5. **`uiautomator dump` reports "could not get idle state"** while the diagnostics screen is animating; use the `SotcNative` debug log instead of the hierarchy.

## Runtime Settings and Graphics (observed 2026-11-19)

1. **Patch 0009 has no device evidence.** `SOTC_FRAME_LIMIT` and `SOTC_ADAPTIVE_RESOLUTION` are published by the app and read by the runner, but no ARM64 device has run a session. The `[pace]` log is the intended proof; until it is captured from a device, the frame limit and adaptive scale are compiled and host-smoke-tested only.
2. **The GPU self-tests use synthetic shaders and synthetic targets.** They compile a tiny GLSL ES 1.00 program and clear 4x4 RGBA/RGB/depth targets. They do not prove that the game's own shaders, blending or render-target sizes work. A passing tier is a launcher-diagnostics result, not full-game compatibility.
3. **EGL display lifetime source contradiction.** `gpu_info.cpp` documents that the query leaves the display initialized, while `egl_renderer.cpp:225-235` calls `eglTerminate`. Context-recreation self-tests were deferred because tearing down a shared display could expose a real lifetime bug. Review before adding those tests.
4. **`ImportActivity.kt:197` checks `CoreProbe.isInstalled`**, which looks for `libsotc_game.so`, not the packaged `libps2EntryRunner.so`. The post-import message can therefore say no core is installed even though the runner is present. Follow-up fix; does not block Play gating elsewhere.
5. **`invertLookY` has no runner reader.** The UI and host touch view apply it, but patch 0005 publishes no `SOTC_INVERT_LOOK_Y`. The caption now states that it affects the diagnostics view only.
6. **Touch UI ranges exceed runner clamps.** The app allows size 60-160%, opacity 15-100% and deadzone 0-30%; the runner clamps 60-140%, 20-100% and 0-60% respectively. Captions state the effective ranges; a follow-up can align the sliders.
7. **Values below 50% render scale are raised before publishing.** The app slider allows 25%, but celosia clamps `renderScale` to 0.5. `RuntimeEnvironment.graphics` clamps to `0.500`, so a 25% selection is honestly reported as 50%.

## Documentation Debt

11. `docs/releases/v0.1.0-alpha.1.md` and `docs/agent/PROJECT_AUDIT.md` are historical snapshots that describe the OpenGL ES 3 requirement as it was at that release. Treat them as historical, not current; current requirements live in `COMPATIBILITY.md`, `INSTALLATION.md`, `FAQ.md` and `README.md`.

## Not Issues (avoid re-investigating)

- ISO `volumeIdentifier` is `"1"` — verified normal for this disc (matches declared/image sectors); not a corruption sign.
- Boot filename length 11 (not 12) — handled in the inspector (D3).
