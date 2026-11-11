# File Audit — Milestone 0 Recovery

> Agent memory file: record of what was read and verified during the recovery audit.
> Audit date: 2026-11-09. HEAD at audit time: `194804f` (12 commits on `main`).

## First-Party Native Code (all 13 files, read in full)

| File | Verdict |
|---|---|
| `app/src/main/cpp/CMakeLists.txt` | Consistent with module layout; builds `libport-native` |
| `app/src/main/cpp/sotc_game_api.h` | ABI v1: init/frame/audio/input/teardown contract — sound design |
| `app/src/main/cpp/native_core.cpp` | JNI surface correct; forwards surface/audio/pad to engine |
| `app/src/main/cpp/engine/engine.cpp` | Loop, timing, resize handling all real implementations |
| `app/src/main/cpp/engine/game_core.cpp` | dlopen + ABI symbol checks + version guard — correct, awaits real core |
| `app/src/main/cpp/engine/egl_renderer.cpp` | EGL config selection, GLES3 shaders — real |
| `app/src/main/cpp/engine/render_target.cpp` | FBO lifecycle — real |
| `app/src/main/cpp/engine/audio_engine.cpp` | AAudio primary / OpenSL fallback — real |
| `app/src/main/cpp/engine/gl_util.cpp` | GL helper utilities — real |
| `app/src/main/cpp/engine/diagnostics_scene.cpp` | Fallback render scene — real |

**Conclusion:** no stubs, no fake gameplay, no hidden emulator. Host foundation is genuine.

## Kotlin Layer (~30 files, read in full)

- `MainActivity.kt`, `GameActivity.kt` — import/launch flow, engine lifecycle wiring.
- `disc/Iso9660.kt`, `disc/SystemCnf.kt`, `disc/Ps2Elf.kt`, `disc/DiscValidator.kt` — real ISO9660/SYSTEM.CNF/ELF parsing (matches `tools/inspect_original_iso.py` logic).
- `game/GameImporter.kt`, `game/GameLibrary.kt` — import + persistence.
- `input/PadState.kt`, `input/TouchControllerView.kt`, `input/GamepadManager.kt` — real input stack.
- `engine/EngineHandle.kt` — JNI bridge.
- Unit tests: 49 passing (per docs; gradle run not repeated this session).

## Build System

- `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` — arm64-v8a + x86_64 ABIs, NDK via local.properties.
- Android SDK: `C:\Users\maria\AppData\Local\Android\Sdk` (NDK 26.1.10909125, CMake 3.22.1).
- JDK 17 available at `C:\Users\maria\Desktop\jdk17-temp`; system Java 25.

## Documentation Cross-Check

- `README.md`, `docs/ARCHITECTURE.md`, `docs/RESEARCH.md`, `docs/ANDROID_PORTING_PLAN.md` — all accurately describe code (verified against sources).
- `docs/COMPATIBILITY.md` + Phase 3 of plan still say "no disc image available" — **stale after Milestone 1**; must be updated to record validation.

## Third-Party Audits (background research, 2026-11-09)

### third_party/celosia (commit f8c269e, GPL-3.0)
- `celosia` driver read fully (342 lines): commands, env vars (`PS2X_*`), pipeline stages.
- `game/overrides/` enumerated: 161 files incl. `sotc_native.cpp` (634 KB renderer hooks).
- `tools/`: funcmap_elf.py, build_elf.py, gen_config.py, xff.py, modimage.py — all reviewed.
- macOS-oriented (Metal); Windows blockers: bash, `.exe` suffixes, `bsdtar`, `python3`, `getconf`, `shasum`, and space-in-path. WSL2 recommended.
- In-game UI strings in its runtime are Portuguese — must be translated to English in our port.

### third_party/PS2Recomp (commit e563698, GPL-3.0)
- `android/` scaffold complete: AGP 8.6.1, NativeActivity, raylib 5.5 Android platform, sse2neon.
- `ps2xRuntime/` reviewed: EE scheduler, GS software rasterizer, VU1 interpreter, IOP interpreter.
- Gaps: `ps2_android_runtime.cpp` is a 3-line TODO; no touch overlay; no ISO import; FFmpeg OFF on Android; no SCUS-97472 in `games_database.cpp`.
- Wiki assessment: runtime is the most underdeveloped part; "Performance is very bad for VU and GS".

## Repository Hygiene

- Uncommitted at audit time: `.gitignore` (adds `/original iso/`) and untracked `tools/`.
- Remote `origin` exists but has never been pushed to. No force anything — preserved as-is.

## Session 5 Additions (2026-11-11)

- `icon/icon.png` (developer-supplied 2026-11-10 19:16) — INSPECTED: 1254×1254 RGB (no alpha), centered subject on pure black; bright content bbox ≈ 68.8%×69.9%, dim ≈ 71.9%×70.3% of image. Source asset preserved unmodified; committed as the project identity asset.
- `tools/capture-host/patches/` (created 2026-11-10 10:06 by an unlogged continuation) — INSPECTED: `0001-non-vita-build-fixes.patch` = exact capture of the developer's live /root/celosia edits (vf0c→vf0u; `#if PS2X_VU_NEON_A32` guard on `keepFastOk`), plus `apply-patches.sh` (git-apply wrapper pinned to f8c269e with already-applied detection). Content-verified identical to the live tree.
- `/root/celosia/PS2Recomp/ps2xRuntime/CMakeLists.txt` Android/aarch64 sections — INSPECTED (M4 prep): `PS2X_IS_ANDROID` (from `ANDROID`), runner becomes SHARED lib with `-Wl,--undefined=ANativeActivity_onCreate`; `PS2X_RUNNER_DIRS` glob drives the game sources; unity hazards excluded per-file (`lean_*.cpp`, `ps2_vita_runtime.cpp`); FFmpeg default OFF on Android; raylib FetchContent `PLATFORM=Android`.
- `/root/celosia/PS2Recomp/CMakeLists.txt` — INSPECTED: `PS2X_IS_AARCH64_TARGET` from `CMAKE_SYSTEM_PROCESSOR`; fetches sse2neon v1.9.1, defines `USE_SSE2NEON`, adds `-march=armv8-a+fp+simd(+crypto+crc)` for non-Apple ARM64.
- `/root/celosia/PS2Recomp/android/` scaffold — INSPECTED: `hasCode="false"` NativeActivity (`android.app.lib_name=ps2EntryRunner`), minSdk 28, NDK 28.2, `PS2X_DEFAULT_BOOT_ELF` via `ps2xBootElf` Gradle property, abiFilters arm64-v8a + x86_64, game data pushed to `/storage/emulated/0/Android/data/com.ps2x.runner/files`.
- `ps2xRuntime/src/lib/ps2_pad.cpp` — INSPECTED: pad input from raylib gamepad API (dead zone 0.18 radial, rescaled to 0..0xFF, 0x80 center) + keyboard arcade layout + `PS2X_INPUT_SCRIPT` scripting engine (VBlank-anchored timing, memory-anchored waits, repeat presses, SNAP/MARK). NO touch→pad path exists (that is the `ps2_android_runtime.cpp` TODO).
- `ps2xRuntime/src/main.cpp` — INSPECTED: Android stdio→logcat redirect; `PS2X_DEFAULT_BOOT_ELF` for argv-less hosts.
- `app/src/main/res/drawable/ic_launcher.xml` — placeholder vector (generic mountains) REPLACED by developer artwork (deleted after manifest switch to `@mipmap/ic_launcher` + `@mipmap/ic_launcher_round`).
