# Upstream Celosia research report

Date: 2026-11-19

## Scope

This report compares two GPL-3.0 research repositories for the native ARM Android port:

- Upstream: `HenryKun55/celosia`
- R36S fork: `amarqs182/celosia`

The goal is to identify portable engineering ideas and to separate them from platform-specific code that must not be copied verbatim into this project. The report is text/source research only. It contains no game assets, ISO data, BIOS data, copyrighted game code, or non-English report text. Short source excerpts are included for technical comparison from the GPL-3.0 repositories; line numbers refer to the inspected snapshots listed below. Every quoted excerpt is 10 lines or fewer.

## 1. Snapshot metadata

| Repository | Exact URL | Default branch | Inspected commit SHA | Inspected tree SHA | License confirmation |
| --- | --- | --- | --- | --- | --- |
| Upstream Celosia | `https://github.com/HenryKun55/celosia` | `main` | `1f8bb16d4fa1aff63d04a991893bde629efe802f` | `6cf321ec5bcb41d1ae2bb5cd2a454b7bd3bc9e22` | GitHub API license field: `GPL-3.0` |
| R36S fork | `https://github.com/amarqs182/celosia` | `main` | `cc194bfab533748a8d9b858c0f797748d7eea8f1` | `6791f9414d9c7e8c8a21940487826fb3f6adf3ad` | GitHub API license field: `GPL-3.0` |

The tree endpoint was queried before raw file fetches:

- `GET https://api.github.com/repos/HenryKun55/celosia/git/trees/1f8bb16d4fa1aff63d04a991893bde629efe802f?recursive=1`
- `GET https://api.github.com/repos/amarqs182/celosia/git/trees/cc194bfab533748a8d9b858c0f797748d7eea8f1?recursive=1`

Path casing was resolved against the tree. In particular, the fork contains lowercase `docs/r36s_port.md`; `docs/R36S_PORT.md` does not exist. Upstream contains `tools/frametime.py`; the inspected fork tree does not.

### Game identity

Both repositories target the USA retail release of *Shadow of the Colossus*:

- Serial: `SCUS-97472`
- Disc identifier used by the tooling: `SCUS_974.72`

This was confirmed from upstream `README.md`, `docs/STATUS.md`, and the recompiler configuration. No disc image or game data was downloaded or added to this project.

### Branch availability limitation

The fork's previously referenced `r36s` branch is no longer present in the API. The exact failures observed were:

```text
GET https://api.github.com/repos/amarqs182/celosia/git/ref/heads/r36s
404

GET https://api.github.com/repos/amarqs182/celosia/commits/r36s
422 {"message":"No commit found for SHA: r36s", ...}
```

The branch list contains only protected `main`. Therefore, this report uses:

1. The fork's current `main` snapshot at `cc194bfab533748a8d9b858c0f797748d7eea8f1`.
2. Merged pull-request head SHAs recorded from the fork's history for the R36S work.

Historical PR head SHAs used as context, not as the inspected `main` snapshot:

| PR | Head SHA |
| --- | --- |
| #42 | `7a065815990c5bd15a776d0ecc7e95eb0d9ed278` |
| #43 | `a4d568c5747112fe55c1494f5595cb054a475917` |
| #46 | `c6644f395a42e57a1af60717b0da830567da0802` |
| #47 | `766d416c68ff0efd515ada46d747cb3decdae669` |
| #48 | `5ddd4e01dd6ff27988ae1f49c085b22bbc64ee83` |
| #49 | `5a3ff9dfa553249129d8cb52050c872d3dc6a63f` |
| #50 | `6df7c76aef3b57dc4ec7ecead15cad8780855038` |
| #51 | `3f96eda0476502d327ed59e67c1f60568815b4c3` |

## 2. Architecture findings

### Upstream

Upstream Celosia is a static recompilation project, not an emulator backend. Its MIPS code is translated ahead of time into C++ with PS2Recomp. The boot ELF and the three runtime modules `KERNEL.XFF`, `MANAGER.XFF`, and `GAMECORE.XFF` are captured, merged, and recompiled. DMA, IOP, SPU2, timers, and interrupts are reimplemented as host code. Drawing is performed by native renderers, Metal on Apple Silicon and OpenGL ES on PS Vita.

Upstream's stated targets are macOS Apple Silicon and PS Vita. Android support is minimal and is not a usable model for this project's application layer:

```cpp
// PS2Recomp/ps2xRuntime/src/lib/ps2_android_runtime.cpp:1-3
// TODO: on-screen touch joystick / button overlay.
#if defined(__ANDROID__)
#endif // __ANDROID__
```

`PS2Recomp/ps2xRuntime/src/main.cpp` redirects stdout/stderr to logcat, and CMake links `log` and `android`, but there is no upstream touch implementation. `PS2Recomp/android/` contains only a basic Gradle/NDK runner APK.

### Fork

The fork retargets the same native runner to the R36S handheld:

- SoC: Rockchip RK3326
- CPU: 4x Cortex-A35, approximately 1.0-1.5 GHz
- GPU: Mali-G31 MP2 via ARM `libmali`, OpenGL ES 3.2
- Display: 640x480 KMSDRM at 60 Hz
- Memory: approximately 895 MiB shared RAM
- Kernel: Linux `4.4.189`
- Platform layer: SDL2, KMSDRM/GBM, ALSA

The fork's results are valuable as a disciplined optimization and measurement record. They are not Android performance predictions.

### Post-pin upstream changes

The upstream snapshot is `ahead_by=162`, `behind_by=0`, with 241 changed files relative to the pinned upstream commit `f8c269e5a5dc3b7afd7b25d25fe56684e1ad96f5`. Correctness, reliability, and security-relevant changes recorded after that pin include:

| Short SHA | Topic | Relevance |
| --- | --- | --- |
| `1180ae69` | Profiler boot crash | Reliability; do not enable profiler hooks blindly on Android startup. |
| `c2af5c20`, `089a56c2` | Collision defaults | Gameplay correctness; port with difftest. |
| `6cb95cb6` | `fin125` handling | Native-decomp correctness. |
| `2b97c76a` | Mac/NEON gating | Prevents unsupported SIMD paths; relevant to ARM dispatch discipline. |
| `9b5bacee` | Fresh setup behavior | Build/bootstrap correctness. |
| `c3edf027` | Python 3.9 compatibility | Host tooling compatibility. |
| `0ae4f67d` | Unity build clashes | Build correctness with generated code. |
| `7ee342eb` | Mac native/audio changes | Native audio and renderer reliability. |
| `17a05d57` | Grip-ring draw range | Correct draw-range usage; review before using range draws. |
| `4f116f23` | Round S4 null guest RAM boot fix | Boot-path null handling. |
| `7607e706` | Check-mode RAM copy | Difftest correctness; avoid stale guest state. |
| `906a28cf` | Per-lane decode buffer race | Thread-safety/correctness; important for any parallel decode path. |
| `2908a8cf` | Round S17 off by default | Unsafe/incomplete native path disabled by default. |

Security-relevant build flags were also found in both snapshots' `PS2Recomp/ps2xRuntime/cmake/ReleaseMode.cmake`:

```cmake
# ReleaseMode.cmake:20-23
/arch:AVX2
/GS- # Disable Buffer Security Check (faster)
/Qspectre- # Disable Spectre mitigations (faster)
```

These are MSVC-only flags. They must not be copied into Android NDK builds, and disabling exploit mitigations should not be treated as a performance idea.

## 3. Portable ideas and platform-specific boundaries

### Portable concepts

The following ideas are transferable in concept:

1. Deterministic A/B frame-time measurement with paired differences and statistical error.
2. Release builds that compile out diagnostics while retaining a minimal frame log.
3. Draw sorting by program/state before texture identity when it is proven safe.
4. Persistent VAOs where OpenGL ES 3.x and driver behavior permit them.
5. Texture array/atlas batching concepts, with correctness checks for wrap mode, mipmaps, alpha, and LOD.
6. Indexed and merged draws with strict safety predicates.
7. PGO, selective LTO, and profile-derived hot-code ordering as build-time experiments.
8. Hot-path native decompilation with register/RAM difftest.
9. Content-hashed texture caching and bounded cache eviction.
10. Frame pacing separated from game simulation, with fixed-deadline sleeping.
11. Native audio driver work that preserves command and reply semantics.

### Platform-specific code that must not be copied verbatim

| Fork source/behavior | Why it is not portable |
| --- | --- |
| SDL2 KMSDRM/GBM swap-interval handling and `actual=1` diagnostics | Android uses `Surface`/`Choreographer`/`ASurfaceTransaction`, not KMSDRM or SDL swap control. |
| `glInvalidateFramebuffer` placed immediately before the KMSDRM swap | The invalidation idea is portable, but the exact placement is driver/backend-specific and must be validated on Android GLES/Vulkan. |
| ARM `libmali` GLES 3.2 quirks, r13p0 UBO regression, texture-array capacity choices | These are driver-specific observations from one Mali blob. |
| 4 GiB `PROT_NONE` `mmap` + `memfd_create` mirrors + `SIGSEGV` handler fastmem | Linux aarch64-specific; Android has different memory, linker, seccomp, signal, and lifecycle constraints. |
| `clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, ...)` in the Linux platform layer | POSIX pacing is useful as an algorithm, but the Android implementation belongs in the host app with Android frame APIs. |
| `-mtune=cortex-a35`, `+crypto`, `+crc` tuning | Specific to RK3326; Android must tune for the actual target device and must not assume optional ISA features. |
| Linux `4.4.189` syscall gaps and `memfd_create` number `279` | Kernel-version-specific and unsafe on Android. |
| SDL right-stick camera inversion | Android controller/touch mappings need their own input policy. |
| MSVC `/GS-` and `/Qspectre-` | Security-relevant; do not copy. |

## 4. Optimization findings

### 4.1 Deterministic measurement tooling

Upstream `tools/frametime.py` implements a portable A/B frame-time analysis. It deterministically assigns each frame to an `off` or `on` state from the frame number and a seed, then compares paired block means with a standard-error estimate. This avoids relying on wall-clock phase alignment.

```python
# tools/frametime.py:13-21
seed, lo, shift = int(opt('--seed', 7)), int(opt('--from', 3000)), int(opt('--shift', -1))
scan = '--scan' in a
M = (1 << 64) - 1
def off(f):
    z = (f * 0x9E3779B97F4A7C15 + seed * 0xBF58476D1CE4E5B9 + 0x94D049BB133111EB) & M
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & M
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & M
    z ^= z >> 31
    return (z >> 63) != 0
```

Upstream `tools/measure.sh`, `tools/bench.sh`, and `tools/gameplay_fps.sh` use scripted input to reach the same in-game moments across builds. This is directly applicable to Android integration testing: keep a fixed input script, fixed memory-card state, and frame-based comparison windows.

Upstream `tools/pgo_fix.py` is a GCDA repair and merge tool for Vita PGO output. It parses tags `0x01000000` (function), `0x01a10000` (counter arcs), and `0xa1000000` (summary), sums matching arc counters across runs, writes `runs=1`, and sets `sum_max` to the largest observed arc counter. The portable idea is to verify profile metadata before `-fprofile-use`; the file format handling is toolchain-specific.

### 4.2 Release instrumentation and build configuration

The fork's `PS2X_LINUX_RELEASE=ON` compiles out diagnostic timers, counts, and logging in the runtime and overrides. This removed measured hook overhead from `1.10 ms` to `0.00 ms` in its R36S benchmark.

```cmake
# PS2Recomp/ps2xRuntime/CMakeLists.txt:608-616
if(PS2X_LINUX_RELEASE)
    message(STATUS "Linux/aarch64: release build (SOTC_RELEASE=1)")
    list(APPEND PS2X_LINUX_OVERRIDE_OPTS -DSOTC_RELEASE=1)
    foreach(PS2X_LINUX_TARGET IN ITEMS ps2_runtime ps2_iop)
        if(TARGET ${PS2X_LINUX_TARGET})
            target_compile_definitions(${PS2X_LINUX_TARGET} PRIVATE SOTC_RELEASE=1)
        endif()
    endforeach()
endif()
```

The fork's PGO generation flags are useful as a pattern:

```cmake
# PS2Recomp/ps2xRuntime/CMakeLists.txt:631-636
set(PS2X_LINUX_PGO_OPTS
    -fprofile-generate=/tmp/sotc_pgo/
    -fprofile-prefix-path=${PS2X_LINUX_PGO_PREFIX}
    -fprofile-update=single
    -fno-profile-values
    -fno-profile-reorder-functions)
```

The generation link step also adds `-Wl,-u,__gcov_dump`, ensuring libgcov can be triggered at a controlled frame. The runtime must avoid early or repeated dumps that set libgcov's `dump_done` flag before the intended gameplay window.

Selective LTO and hot-code ordering are opt-in:

```cmake
# PS2Recomp/ps2xRuntime/CMakeLists.txt:598-605
message(STATUS "Linux/aarch64: selective LTO on ps2_runtime + overrides (...)")
list(APPEND PS2X_LINUX_OVERRIDE_OPTS -flto=auto -ffat-lto-objects -fno-plt)
foreach(PS2X_LINUX_TARGET IN ITEMS ps2_runtime ps2_iop)
    target_compile_options(${PS2X_LINUX_TARGET} PRIVATE -flto=auto -ffat-lto-objects -fno-plt)
endforeach()
target_link_options(ps2EntryRunner PRIVATE ${PS2X_LINUX_LTO_FLAG} -fno-plt ...)
```

```cmake
# PS2Recomp/ps2xRuntime/CMakeLists.txt:670-674,687-690
if(PS2X_LINUX_FUNCTION_SECTIONS)
    target_compile_options(ps2_runtime PRIVATE -ffunction-sections)
    ...
    target_link_options(ps2EntryRunner PRIVATE "-Wl,--section-ordering-file=${PS2X_LINUX_HOT_ORDER_FILE}")
endif()
```

Important caveat: stale PGO profiles are worse than no PGO. The fork observed that changing instrumentation changed control-flow graph arc counts and caused `-Wcoverage-mismatch` warnings. Profiles must be regenerated after material runtime or override changes.

### 4.3 GLES submission, batching, and sorting

The fork added several opt-in GLES switches in `native_renderer_gl.cpp`. They are runtime-rebuild changes, not Android host changes.

Texture-array batching is gated by an environment option:

```cpp
// PS2Recomp/ps2xRuntime/src/lib/native/native_renderer_gl.cpp:2574-2582
static inline bool texArrayEnabled()
{
#if !defined(PS2X_NATIVE_VITA)
    static const bool on = std::getenv("SOTC_GL_TEXARRAY") && std::atoi(std::getenv("SOTC_GL_TEXARRAY")) != 0;
    return on;
#else
    return false;
#endif
}
```

The array bucket setup preserves wrap mode and mipmap filtering:

```cpp
// native_renderer_gl.cpp:7555-7564
glBindTexture(GL_TEXTURE_2D_ARRAY, b.tex);
cache.arrTex = b.tex;
glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LEVEL, static_cast<GLint>(b.levels - 1u));
const bool mipped = b.levels > 1u;
glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER,
                filter == 0u ? (mipped ? GL_NEAREST_MIPMAP_NEAREST : GL_NEAREST)
                             : (filter == 1u ? (mipped ? GL_LINEAR_MIPMAP_NEAREST : GL_LINEAR) : (mipped ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR)));
glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, filter == 0u ? GL_NEAREST : GL_LINEAR);
glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, (c & 1u) ? GL_CLAMP_TO_EDGE : GL_REPEAT);
glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, (c & 2u) ? GL_CLAMP_TO_EDGE : GL_REPEAT);
```

Small power-of-two texture promotion into a `128x128` bucket is carefully constrained:

```cpp
// native_renderer_gl.cpp:2906-2914
static inline bool shouldPromoteToArray128(const Texture &t) noexcept
{
    if (!promote128Enabled())
        return false;
    if (!isPow2Dim(t.width) || !isPow2Dim(t.height))
        return false;
    if (t.width < 32u || t.height < 32u || t.width > 128u || t.height > 128u)
        return false;
    return t.width < 128u || t.height < 128u;
}
```

Persistent VAOs are created only for static indexed meshes:

```cpp
// native_renderer_gl.cpp:8705-8713
else if (staticVaoEnabled() && !mesh.dynamic && mesh.indexed && !indexedRun && mesh.buffer && mesh.indexBuffer)
{
    if (!mesh.vao)
    {
        mesh.vao = makeStaticVao(mesh.buffer, mesh.indexBuffer, skinned);
        cache.vao = mesh.vao;
        cache.buffer = mesh.buffer;
        ++drawStats.bufferSwitches;
    }
```

Range draws are restricted to a narrow safe case:

```cpp
// native_renderer_gl.cpp:4735-4738
if (drawRangeEnabled() && !mesh.dynamic && mesh.pool < 0 && mesh.vertexCount > 0u)
    glDrawRangeElements(GL_TRIANGLES, 0u, mesh.vertexCount - 1u, static_cast<GLsizei>(count), GL_UNSIGNED_INT, idxOff);
else
    glDrawElements(GL_TRIANGLES, static_cast<GLsizei>(count), GL_UNSIGNED_INT, idxOff);
```

Program-first sorting builds a compact program/bucket key after proving the draw is safe:

```cpp
// native_renderer_gl.cpp:7020-7026
const uint64_t cut = (noDisc && ((d.state.blend == native_renderer::Blend::Opaque && !(d.state.alphaTest & 1u)) || leastA >= 0.03f)) ? 0ull : 1ull;
const uint64_t fogBit = (d.state.fogged && fog.enabled && !(native_renderer::tuning().glNoFog98 && plan[j].maxW > 0.0f && plan[j].maxW < fog.start * 0.999f)) ? 1ull : 0ull;
const uint64_t skinned = (m && m->skinned) ? 1ull : 0ull;
const uint64_t untex = (!d.state.texture && native_renderer::tuning().glUntex98) ? 1ull : 0ull;
const bool lod0 = (d.state.texture && texPtr && texPtr->mipped && !(d.state.maxLod > 0.0f) && native_renderer::tuning().glLod0_98 && native_renderer::tuning().glPlainReads);
const uint64_t plain = (d.state.texture && texPtr && (!texPtr->mipped || lod0) && native_renderer::tuning().glPlainReads) ? 1ull : 0ull;
const uint64_t progBits = (texArr ? 8ull : (untex ? 4ull : (plain ? 2ull : 1ull))) | (cut << 4) | (fogBit << 5) | (skinned << 6);
```

Measured fork results:

| Change | Measured result on R36S | Decision |
| --- | --- | --- |
| `SOTC_GL_TEXARRAY=1` + `SOTC_GL_STATIC_VAO=1` | Draw calls `-40.0%` to `-42.6%`; CPU `render` `-35.7%` to `-39.6%` | Kept as opt-in and later enabled in production script |
| `SOTC_GL_PROMOTE_128=1` + `SOTC_GL_DRAW_RANGE=1` | Per-draw cost `35.3-37.5 us` to `30.9-34.1 us` | Kept as opt-in/production flags |
| `SOTC_GL_SORT_PROG=1` | Shrine GL calls `183 -> 124`; `rsd 117.4 -> 81.2`; `prog 92.4 -> 62.2` | Kept under strict opaque safety checks |
| `SOTC_GL_UBO=1` | `+15 us/call` on `libmali r13p0` | Rejected as default |
| `SOTC_GL_NODISCARD=1` | `+3.20..+4.08 ms` or `+1.21 ms` CPU render regression despite GPU gain | Rejected as default |

### 4.4 Tile-based rendering, invalidation, and presentation

The fork reduced redundant framebuffer work and invalidated depth/stencil before swap:

```cpp
// PS2Recomp/ps2xRuntime/platform/linux/raylib_linux.cpp:869-871
static constexpr GLenum kDefaultDiscard[2] = {GL_DEPTH, GL_STENCIL};
glInvalidateFramebuffer(GL_FRAMEBUFFER, 2, kDefaultDiscard);
SDL_GL_SwapWindow(g_window);
```

It also added fixed-deadline pacing:

```cpp
// raylib_linux.cpp:880-884
static const int envCapFps = std::getenv("SOTC_FPS_CAP") ? std::atoi(std::getenv("SOTC_FPS_CAP")) : 60;
int targetFps = envCapFps;
const int runtimeFps = g_targetFps.load(std::memory_order_relaxed);
if (targetFps > 0 && runtimeFps > 0 && runtimeFps < targetFps)
    targetFps = runtimeFps;
if (targetFps > 0)
```

The actual sleep uses `clock_nanosleep` with an absolute monotonic deadline. The portable idea is fixed-deadline pacing with no busy-wait; the Android implementation should use Android lifecycle-aware frame scheduling instead of this Linux file.

The measured KMSDRM/libMali behavior was `swap interval req=0 setRc=0 actual=1`. Even when SDL accepted interval `0`, the backend kept a roughly 60 Hz scanout cadence. The fork's controlled benchmark selected `PS2X_SWAP_INTERVAL=0` plus a `60` FPS cap; light/menu scenes stayed at `59.8-60.1 FPS`.

Camera inversion was implemented in the SDL pad path:

```cpp
// raylib_linux.cpp:340-348
static const bool invertCamX = []
{
    const char *e = std::getenv("SOTC_CAM_INVERT_X");
    return !e || std::atoi(e) != 0;
}();
int16_t rxRaw = SDL_GameControllerGetAxis(ctrl, SDL_CONTROLLER_AXIS_RIGHTX);
if (invertCamX)
    rxRaw = (rxRaw == -32768) ? 32767 : static_cast<int16_t>(-rxRaw);
merged.rx = pick(merged.rx, rxRaw);
```

This is R36S input policy, not a reusable Android control implementation.

### 4.5 Fast memory and MMIO trap reduction

The fork's fastmem path reserves a 4 GiB window and maps mirrors of RDRAM and scratchpad:

```cpp
// PS2Recomp/ps2xRuntime/src/lib/ps2_memory.cpp:953-957
#if defined(PS2X_LINUX_FASTMEM) && PS2X_LINUX_FASTMEM && defined(__linux__) && defined(__aarch64__)
void *fastBase = ::mmap(nullptr, kFastmemWindowBytes, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
if (fastBase == MAP_FAILED)
{
    std::fprintf(stderr, "[fastmem] mmap 4GiB failed\n");
```

It uses `memfd_create`, 64 KiB circular overhang guard pages, and an ARM64 `SA_SIGINFO` `SIGSEGV` handler:

```cpp
// ps2_memory.cpp:768-772
struct sigaction sa{};
sa.sa_sigaction = fastmemSigsegv;
sigemptyset(&sa.sa_mask);
sa.sa_flags = SA_SIGINFO | SA_RESTART;
if (::sigaction(SIGSEGV, &sa, &g_prevSigsegv) != 0)
```

This must not be copied to Android. The portable lesson is to remove repeated MMIO trap overhead by identifying the exact guest reads that can be handled natively. The fork combined this with native overrides for `kernel_sub_001C1538`, `kernel_sub_001C37F0`, and `iosSyncV`, reducing `GS_CSR` traps from more than `14,000` to `16` one-time boot traps and `0` gameplay traps.

Measured fork results for build options:

| Build | Stripped size | Uncapped FPS effect |
| --- | --- | --- |
| PGO baseline | `64.09 MB` | `8.86 fps` |
| Release + PGO | `63.50 MB` | `9.72 fps` |
| Release + PGO + selective LTO | `64.51 MB` | `9.91 fps` |
| Release + PGO + fastmem | `48.49 MB` | `10.92 fps` |
| Combined Release + PGO + LTO + fastmem + sorting | `50.03 MB` | `11.37 fps`, about `+28.3%` |

Later combined builds reached `47.65 MB` stripped with `16-31` total traps and `0` gameplay traps.

### 4.6 Native decompilation and difftest

Upstream's central correctness workflow is reusable:

```text
# docs/STATUS.md:201-207, paraphrased from the inspected English source
The loop:
1. A guest profiler finds hot functions (PS2X_PROFILE).
2. Ghidra reads them (tools/ghidra.sh).
3. The rewrite is registered with SOTC_DECOMP_FUNCTION.
4. SOTC_DIFFTEST runs native and recompiled code on the same state and
   compares every register and every byte of RAM.
```

The fork extended this with direct native chaining in generated override code, removing cross-unit `callGuest` overhead. This produced real gains, but also showed limits:

- `characterClassFast`, `layoutClassPlainOnly`, and `layoutClassFast` helped in specific hot sites.
- Inlining too many direct native guards in display-walk loops bloated instruction cache and regressed performance on Cortex-A35.
- Offloading character work (`SOTC_CHAR104=1`) lost to worker synchronization and shared-L2 contention on four Cortex-A35 cores.
- Additional collision caches (`B4_COLL125`, `B4_COLLDB`) were discarded despite zero difftest differences.
- `SOTC_PF141=0`/`SOTC_PFC141=0` won by removing mutex/condition-variable work and speculative L2 thrashing.

The transferable rule is: profile first, require difftest equivalence, then A/B test on the actual target. A native rewrite is not automatically faster.

### 4.7 Indexed mesh optimization and visual correctness

The fork's Phase 6 work made `SOTC_MESHOPT=1` the default after restricting triangle reordering and using indexed uploads rather than expanding triangles. It also introduced strict `isSafeOpaqueDraw()` predicates and atomic publication of complete terrain packets.

Measured results:

- `SOTC_MESHOPT=1` improved median FPS `28.90 -> 29.60` and average `present` by `-4.42 ms`.
- `SOTC_MESHOPT_VCACHE=1`, `SOTC_MESHOPT_OVERDRAW=1`, and unsafe opaque sorting regressed and remained off.
- Held incomplete terrain packets dropped from `1284` (`1212` terrain) to `0` at the measured frame.
- A cutscene hang was traced to guest `$sp`/`$ra` corruption around a native fallback call and fixed by saving/restoring those registers.

These are runner-side correctness and performance changes. They require rebuilding the runner and validating with frame captures and difftest.

### 4.8 Content-hashed caching and native audio

Upstream `docs/STATUS.md` reports that content-hashed mesh and texture caches grew to fill the Vita's 160 MB in about 12 minutes, leading to bounded caches and eviction of entries unused for 30 seconds. This is a portable design idea for Android: key by content, bound memory, and evict by last use under pressure.

Upstream `docs/native_audio_plan.md` documents a native SG2 IOP driver and native SPU2 mixer. The important design constraints are:

- Preserve the 512-entry command ring and 400-byte reply layout.
- Preserve libsd command encodings and stream-position semantics.
- Keep sample transfer and refill behavior equivalent.
- Validate by recorded loudness over the same route.

These are runner/IOP rebuild items, not Android host enhancements. Android audio output itself belongs in the host app.

## 5. Measured fork progression

The following R36S numbers are useful as a measurement record only. They are not Android expectations.

| Stage | Median FPS | Median `game` | Notes |
| --- | --- | --- | --- |
| Pass 8A, release without PGO | `25.60` | `38.90 ms` | Diagnostics compiled out |
| Pass 8B, release + PGO | `27.00` | `36.90 ms` | `+5.5%` median FPS |
| Pass 9, Mali TBDR/pacing without PGO | `27.30` | `36.50 ms` | `render 17.30 -> 14.90 ms` |
| Final Pass 9 + PGO | `29.40` | `33.80 ms` | Net `+14.8%` median FPS vs Pass 8A |
| Phase A release instrumentation cleanup + matched PGO | `33.00` | `30.30 ms` | Binary `-18.7%`, RSS `-5.1 MiB` |
| Phase 6 + merged PGO + indexed mesh optimization | `30.10` | `33.10 ms` | Different route window |
| Post-PR #46-#50 deployment | `36.20` | `27.50 ms` | Release + PGO + selective LTO + fastmem |

The strongest general conclusions are:

1. Remove instrumentation from release builds.
2. Regenerate PGO after any material CFG change.
3. Treat driver- and core-specific results as local measurements, not universal rules.
4. Preserve visual and numerical correctness with captures and difftest.
5. Avoid worker offload unless synchronization cost is measured on the target.

## 6. Runner rebuild versus Android host app

Almost all performance work found in the fork lives inside the PS2Recomp runtime, generated recompiled code, `game/overrides/`, or CMake build configuration. For this project, those changes require rebuilding `libps2EntryRunner.so` from `/root/celosia` in WSL and redeploying the native library.

| Work item | Location | Classification |
| --- | --- | --- |
| Release instrumentation gating | Runtime/override CMake and source | Runner rebuild in WSL |
| PGO generation/use and `__gcov_dump` control | CMake, runtime overrides | Runner rebuild in WSL |
| Selective LTO and hot-section ordering | CMake, order file | Runner rebuild in WSL |
| Texture arrays, VAOs, draw ranges, sorting | `native_renderer_gl.cpp` | Runner rebuild in WSL |
| Framebuffer invalidation in GL backend | Native renderer/platform layer | Runner rebuild in WSL, Android validation required |
| Fastmem and MMIO overrides | `ps2_memory.cpp`, runtime, overrides | Linux-specific; do not port verbatim |
| Native decompilation/direct chaining | `game/overrides/` and generated code | Runner rebuild in WSL |
| Indexed mesh optimization | Overrides and renderer | Runner rebuild in WSL |
| Content-hashed cache and eviction policy | Native renderer | Runner rebuild in WSL |
| Native SG2/SPU2 driver | IOP runtime | Runner rebuild in WSL |
| Settings/env plumbing for runner switches | Android host -> runner boundary | Android host app plus runner support |
| Frame pacing/present orchestration | Host platform layer | Android host app; use Android frame APIs |
| Touch controls and external gamepad mapping | Host platform layer | Android host app |
| Logcat/stdout capture and diagnostics UI | Host platform layer | Android host app |
| ISO import/validation, save paths, lifecycle | Android host app | Android host app |
| A/B test harness and frame-log analysis | Host scripts/tools | Android host/tooling, consuming runner logs |

Practical recommendation: first add stable environment/settings plumbing and frame-log capture in the Android host. Then rebuild the runner in WSL for release instrumentation control, PGO support, and the smallest safe renderer changes. Keep each change independently switchable and A/B testable.

## 7. Files and algorithms inspected

### Upstream repository files

| Exact path | Main information taken |
| --- | --- |
| `README.md` | Project scope, serial, native architecture, requirements, settings, decomp status |
| `docs/STATUS.md` | Native feature status, renderer map reference, difftest loop, cache/eviction history |
| `docs/native_port_log.md` | Measured native renderer progression and comparison gates |
| `docs/native_renderer_map.md` | `modelDisp`, display layers, surfaces, batches, skinning, shadows |
| `docs/vu1_fast_recompiler.md` | Timing-simulation approach for VU1 fast recompilation |
| `docs/native_audio_plan.md` | SG2 command ring, libsd command encodings, native SPU2 mixer design |
| `docs/worklist.md` | Renderer CPU, scheduler, display-list, memory, and fidelity work queue |
| `docs/known_issues.md` | Long-run stability, fidelity caveats, unresolved rendering issues |
| `tools/frametime.py` | Deterministic paired A/B frame-time analysis |
| `tools/pgo_fix.py` | GCDA parse/merge/summary repair algorithm |
| `tools/measure.sh` | Scripted Mac measurement route and per-thread busy-time summary |
| `tools/bench.sh` | Scripted New Game benchmark |
| `tools/gameplay_fps.sh` | Scripted gameplay FPS measurement |
| `PS2Recomp/ps2xRuntime/src/main.cpp` | Android logcat redirect |
| `PS2Recomp/ps2xRuntime/src/lib/ps2_android_runtime.cpp` | Empty Android touch TODO |
| `PS2Recomp/ps2xRuntime/CMakeLists.txt` | Runtime build configuration |
| `PS2Recomp/ps2xRuntime/cmake/ReleaseMode.cmake` | IPO/LTO and security-relevant MSVC flags |
| `PS2Recomp/ps2xRuntime/src/lib/native/native_renderer.cpp` | Native tuning/diagnostic paths |
| `PS2Recomp/ps2xRuntime/src/lib/native/native_renderer_gl.cpp` | OpenGL renderer comparison baseline |
| `PS2Recomp/ps2xRuntime/src/lib/ps2_memory.cpp` | Baseline guest memory implementation |
| `PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp` | Runtime comparison baseline |
| `PS2Recomp/ps2xRuntime/src/lib/vu/ps2_vu1_rec.cpp` | VU1 recompiler comparison baseline |
| `PS2Recomp/ps2xIOP/src/emulator/core/iop_spu2.cpp` | SPU2 emulation baseline |

### Fork repository files

| Exact path | Main information taken |
| --- | --- |
| `SOTC_OPTIMIZATION_LOG.md` | Complete measured phase history, A/B tables, rejected experiments |
| `docs/r36s_port.md` | Device constraints, enabled switches, reproducible Release+PGO build |
| `docs/worklist.md` | Fork-specific active backlog and remaining bottlenecks |
| `docs/STATUS.md` | Fork status summary and deployment state |
| `docs/r36s_render_merge_audit.md` | Render merge audit referenced by the optimization log |
| `docs/r36s_native_audit.md` | Native audit referenced by the fork documentation |
| `PS2Recomp/ps2xRuntime/src/lib/native/native_renderer_gl.cpp` | Texture arrays, VAOs, range draws, sorting, invalidation, safety predicates |
| `PS2Recomp/ps2xRuntime/src/lib/native/native_renderer.cpp` | Boolean parsing and additive/effect toggle behavior |
| `PS2Recomp/ps2xRuntime/CMakeLists.txt` | Release, PGO, selective LTO, fastmem, function sections, hot order |
| `PS2Recomp/CMakeLists.txt` | ARM64 compile flags and `cortex-a35` tuning |
| `PS2Recomp/cmake/r36s-toolchain.cmake` | R36S sysroot, libmali names, linker setup |
| `PS2Recomp/ps2xRuntime/cmake/ReleaseMode.cmake` | Fork IPO/LTO behavior and MSVC flags |
| `PS2Recomp/ps2xRuntime/platform/linux/raylib_linux.cpp` | SDL2/KMSDRM swap behavior, FPS cap, invalidation, camera inversion |
| `PS2Recomp/ps2xRuntime/src/lib/ps2_memory.cpp` | Linux aarch64 4 GiB fastmem implementation |
| `game/overrides/sotc_native.cpp` | PGO dump control, GL flags, mesh optimization, native hooks |
| `game/overrides/sotc_decomp.h` | Direct native call declarations and platform-gated defaults |
| `game/overrides/linux_hot_order.ld` | Profile-derived section ordering data |
| `tools/linux_hot_order.py` | Profile-to-order-file generation |
| `tools/gamecore_base_check.py` | Game-core base check tool |

Some fork documentation contains non-English prose. This report contains only English translations and summaries of the relevant facts.

## 8. Limitations and conclusions

1. The deleted `r36s` branch could not be inspected directly. Current `main`, recorded PR head SHAs, and documented phase history were used instead.
2. R36S measurements depend on RK3326, Mali-G31, `libmali r13p0`, SDL2 KMSDRM, and Linux `4.4.189`. They must be reproduced on Android hardware before being claimed for this project.
3. The most valuable portable contribution is the measurement discipline: deterministic routes, paired frame-time analysis, release instrumentation gating, and strict correctness checks.
4. The most valuable runner-side ideas are PGO with matched profiles, selective LTO, safe draw sorting/VAOs/indexed draws, native decompilation with difftest, and content-hashed cache eviction.
5. The Linux fastmem, KMSDRM pacing, SDL input mapping, libmali-specific GLES workarounds, and MSVC mitigation-disabling flags must not be copied verbatim.
6. No project source files were modified for this research. The only project file added is this report.
