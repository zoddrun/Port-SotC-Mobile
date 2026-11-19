# Android Runtime Integration

> Agent memory file. Describes the gameplay runner topology, the patch series, the environment contract, and the verification/rebuild gates.
> Last updated: 2026-11-19

## 1. Topology

| Element | Role | Evidence level |
|---|---|---|
| `MainActivity` | Launcher: install state, disc readiness, settings and import entry points | IMPLEMENTED, EMULATOR-VERIFIED |
| `GameNativeActivity` | Hosts the raylib-owned runner; publishes environment before library load | IMPLEMENTED, COMPILED, NOT DEVICE-VERIFIED |
| `lib/arm64-v8a/libps2EntryRunner.so` | Recompiled game: simulation, rendering, presentation, audio | COMPILED, PACKAGED, STATICALLY VERIFIED, NEVER LOADED |
| `libsotc_core.so` | Host diagnostics: EGL/GLES test renderer, AAudio diagnostics, ABI-v1 loader | IMPLEMENTED, COMPILED, EMULATOR-VERIFIED |
| `libps2EntryRunner.so` manifest binding | `android.app.lib_name=ps2EntryRunner` at `AndroidManifest.xml:80-90` | IMPLEMENTED |

`GameNativeActivity` does not start `libsotc_core.so`'s `Engine`. The only gameplay owner is the runner. See `PERFORMANCE_ARCHITECTURE.md`.

## 2. Runner lifecycle contract

`GameNativeActivity.onCreate`:

1. redirect `stdout`/`stderr` to `<files>/logs/game-<yyyyMMdd-HHmmss>.log` via `NativeCore.redirectStdio`;
2. resolve the primary installed game and its prepared disc tree;
3. log an error if no boot path exists, but still continue;
4. load settings and build the environment map;
5. publish each value with `NativeCore.setEnvironment`;
6. log published names and failures;
7. call `super.onCreate(savedInstanceState)`, which loads the runner library.

Ordering matters: a value published after `super.onCreate` may be missed by startup-cached readers. `GameLogs.kt` already lists and tails the redirected log files, so no new transport is needed.

## 3. Environment interface

Defined in `RuntimeEnvironment.kt`. Format rules: integers for flags, `String.format(Locale.US, "%.3f", value)` for decimals because the runner uses `std::atof`.

| Constant | Environment name | Format / range | Runner reader |
|---|---|---|---|
| `BOOT_PATH` | `SOTC_BOOT_ELF` | absolute path; omitted when blank | patch 0004 in `main.cpp` |
| `TOUCH_OVERLAY` | `SOTC_TOUCH_OVERLAY` | `1`/`0` | patch 0005 |
| `TOUCH_PRESET` | `SOTC_TOUCH_PRESET` | `classic`, `modern`, `compact` | patch 0005 |
| `TOUCH_HIDE_WITH_GAMEPAD` | `SOTC_TOUCH_HIDE_WITH_GAMEPAD` | `1`/`0` | patch 0005 |
| `TOUCH_SCALE` | `SOTC_TOUCH_SCALE` | decimal, runner clamps `0.6..1.4` | patch 0005 |
| `TOUCH_ALPHA` | `SOTC_TOUCH_ALPHA` | decimal, runner clamps `0.2..1.0` | patch 0005 |
| `TOUCH_DEADZONE` | `SOTC_TOUCH_DEADZONE` | decimal, runner clamps `0..0.6` | patch 0005 |
| `LOOK_SENSITIVITY` | `SOTC_LOOK_SENSITIVITY` | decimal, runner clamps `0.2..3` | patch 0005 |
| `INVERT_LOOK_X` | `SOTC_INVERT_LOOK_X` | `1`/`0` | patch 0005 |
| `AUDIO_VOLUME` | `SOTC_AUDIO_VOLUME` | decimal `0..1`, quantized to 11 steps | patch 0006 in `initialize()` |

Names added for the pacing work:

| Constant | Environment name | Values | Runner reader |
|---|---|---|---|
| `FRAME_LIMIT` | `SOTC_FRAME_LIMIT` | `0`, `30`, `60` | proposed patch 0009 |
| `ADAPTIVE_RESOLUTION` | `SOTC_ADAPTIVE_RESOLUTION` | `1`, `0` | proposed patch 0009 |
| `RENDER_SCALE` | `SOTC_RENDER_SCALE` | decimal `0.500`..`1.000` | existing `native_renderer.cpp:102` |
| `PERF_OVERLAY` | `SOTC_PERF_OVERLAY` | `1`, `0` | existing `native_renderer.cpp:181` |
| `STATS` | `PS2X_STATS` | present / absent | existing `ps2_runtime.cpp:3356` |

`PS2X_STATS` is a diagnostic gate. Publishing it enables the 5-second `[stats]` and `[pace]` records in the redirected game log. It does not alter gameplay.

All runners read these values once at session start. A settings change applies to the **next** game session. The UI must state this.

## 4. Patches applied to celosia

Base checkout: `/root/celosia` at `f8c269e5a5dc3b7afd7b25d25fe56684e1ad96f5`. Both patch series are required; capture-host first, then Android integration. The checkout contains unrelated developer capture fixes (`game/overrides/coll125.h`, `decomp_collision.cpp`, and others) that must not be overwritten or reset.

| Patch | Purpose | Status |
|---|---|---|
| 0001 | English-only runner menu | compiled/linked, not runtime-verified |
| 0002 | `SCUS-97472` games database entry | compiled/linked, not runtime-verified |
| 0003 | Graceful Android quit instead of `std::_Exit` | compiled/linked, not runtime-verified |
| 0004 | Boot ELF from `SOTC_BOOT_ELF` | compiled/linked, not runtime-verified |
| 0005 | Android touch overlay and look knobs | compiled/linked, geometry not device-verified |
| 0006 | Audio volume from `SOTC_AUDIO_VOLUME` | compiled/linked, not runtime-verified |
| 0007 | Android API 28 backtrace compile fix | compile-verified |
| 0008 | NDK format-security compile fix | compile-verified |
| 0009 (proposed) | `SOTC_FRAME_LIMIT`, `SOTC_ADAPTIVE_RESOLUTION`, `[pace]` log | not yet generated |

`apply-patches.sh` pins the expected commit and uses per-patch markers for idempotence. Add 0009 to both the application loop and the marker list; verify `git apply --check` and `git apply -R --check` for every patch.

## 5. Static verification

`tools/android-integration/verify-runner.sh` checks, for the runner library:

- ELF64, AArch64, `ET_DYN`;
- 16 KB `PT_LOAD` alignment;
- `ANativeActivity_onCreate` export;
- touch overlay symbols;
- no `libc++_shared.so` dependency (runner uses `c++_static`);
- `libandroid.so` dependency;
- size, SHA-256, and pass/fail markers.

Current result: `RUNNER_VERIFY_OK` for the `134F2FBF...` baseline. Extend verification to every `.so` in the APK, not only the main library: ELF class/ABI/shared-object type, 16 KB LOAD alignment, NEEDED dependencies, and ZIP alignment. Record size, SHA-256, and BuildID beside patch hashes.

## 6. Rebuild acceptance gate

A rebuilt runner is accepted only if all of the following hold:

1. all patches apply and reverse-check cleanly at the pinned celosia commit;
2. host x86 smoke assertions pass for `30`, `60`, and `0` frame limits, including raylib timer logs and `[pace] limit=...`;
3. `[scale-adaptive]` appears only with adaptive resolution enabled;
4. `verify-runner.sh` prints `RUNNER_VERIFY_OK` for the new artifact and records its new SHA-256;
5. the APK builds with the new library and all native-library alignment checks pass;
6. first-boot device evidence is obtained before any release claim.

A failed rebuild must not overwrite the packaged baseline. Revert patches in reverse numeric order with `git apply -R --check` then `git apply -R`, starting at 0009.

## 7. Rebuild logistics

- About 9,333 objects; thin-LTO link roughly 55 minutes in WSL with 1.7 GiB RAM plus an 8 GB swapfile.
- Use `-j2`, `setsid`, and ninja resume. Do not delete the build tree after an interrupted link.
- NDK r26b (`/root/android-ndk-r26b`; Windows SDK reports `26.1.10909125`).
- FFmpeg off on Android.
- `sse2neon` v1.9.1 with `USE_SSE2NEON`; no x86 SIMDe shim on the ARM64 include path.
- `-Wl,-z,max-page-size=16384` and `c++_static`.
- Record object count, link time, BuildID, stripped size, SHA-256, and verifier output.

## 8. No-phone regression evidence

Can be produced here:

- JVM unit tests for environment names, values, locale formatting, sanitization, presets, and publish failures;
- patch contract checks, including marker presence and forward/reverse application;
- symbol and ELF checks with `llvm-nm`/`readelf`;
- APK ZIP entry and alignment checks for every native library;
- API 28 x86_64 launcher smoke tests for the host path;
- host x86 celosia smoke runs with fixed environment values, if the host runner can be built.

Requires a real ARM64 device and remains explicitly unverified:

- runner load and first boot;
- window initialization and actual game rendering;
- touch overlay geometry, multitouch ownership, pad merge;
- game audio volume result;
- pause/resume, rotation, and context recovery;
- thermal behaviour and sustained frame times;
- 16 KB page-size device loading.

## 9. Known defects affecting this path

- `ImportActivity.kt:197` checks `CoreProbe.isInstalled` (which looks for `libsotc_game.so`) instead of the packaged runner, so the post-import message can incorrectly say no core is installed.
- `invertLookY` is applied by the host touch view but has no runner environment reader.
- Touch scale/alpha/deadzone UI ranges exceed runner clamps; captions must state the effective values.
- FFmpeg is disabled on Android, so cutscenes are expected to be stubbed.

## 10. Related documents

- `docs/research/RUNNER_INTEGRATION.md` — full patch 0009 design and smoke-test matrix.
- `docs/research/UPSTREAM_CELOSIA.md`
- `docs/research/UPSTREAM_PLATFORM.md`
- `docs/agent/UPSTREAM_REPO_AUDIT.md`
- `docs/agent/PERFORMANCE_ARCHITECTURE.md`
