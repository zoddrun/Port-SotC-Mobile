# GPU Compatibility Matrix and Feature Detector Contract

> Agent memory file. Describes the target GPU families, the feature-based capability model, the current implementation, and the evidence limits.
> Last updated: 2026-11-19

## 1. Compatibility policy

The application must not hardcode a GPU vendor and must not claim universal GPU support. Compatibility is decided by **detected features at runtime**, not by renderer strings alone.

Rules:

1. The launcher renderer keeps a safe OpenGL ES 2.0 floor. Requesting ES3 and falling back to ES2 is already implemented and emulator-verified for the host diagnostics path.
2. The game renderer uses the minimum API and features genuinely required by Celosia/raylib. The runner's Android EGL path requests `EGL_CONTEXT_CLIENT_VERSION, 2` (`rcore_android.c:855-858`); no full-game GLES 2 compatibility claim follows from that fact.
3. GLES 3.0 is the practical baseline to evaluate for the game renderer, because the R36S fork's optimizations target GLES 3.2. Until an ARM64 device exercises the runner, the game's minimum API is **unverified**.
4. GLES 3.1/3.2 features are optional and enabled only when detected and exercised by a self-test.
5. Vulkan is not implemented. It may be evaluated later only if the real runtime can support it reliably; OpenGL ES remains the fallback.
6. Context creation success is not proof that shaders, framebuffer formats, texture uploads, blending, depth, or render-to-texture work. Those are separate tests.
7. No experimental or vendor-only driver extension is required for the default installation.

## 2. Vendor/device families

This matrix is a planning and fallback guide. A row is a family of drivers, not a guarantee. Only the last column contains observed evidence for this project.

| Family | Typical API floor | Notable characteristics | Required handling | Project evidence |
|---|---|---|---|---|
| Qualcomm Adreno (6xx/7xx/8xx) | GLES 3.0-3.2 | Broad driver variation; timer queries and sync support differ by generation; some experimental extensions are device-specific | Use core ES features; gate extensions; never require Adreno-only paths | None on device |
| ARM Mali (T6xx/G3x/G5x/G7xx) | GLES 3.0-3.2 | R36S reference uses Mali-G31 GLES 3.2; tile-based rendering; framebuffer format support varies | Feature probe; avoid assuming desktop-style render target formats | None on device; R36S fork studied as reference only |
| ARM Immortalis | GLES 3.2 | Modern ARM driver; generally broad ES 3.2 coverage | Same feature-based path as Mali | None on device |
| Imagination PowerVR (GE8xxx/9xxx) | GLES 3.0-3.2 | Tile-based deferred rendering; sensitive to load/store patterns and unsupported formats | Conservative render-target checks; avoid vendor-specific workarounds by default | None on device |
| Other GLES drivers | ES 2.0-3.2 | Unknown behaviour | Safe ES2 tier with reduced optional features | SwiftShader x86_64 emulator only |
| SwiftShader (software, dev only) | ES 2.0 (observed) | Slow; cannot reach real-time frame rates; thermal status always 0 | Useful for correctness smoke tests, never performance claims | API 28 x86_64, ES2 non-black frames observed |

## 3. Capability tiers

Tiers are computed from the self-test result, not from a vendor allowlist. A lower tier must remain usable.

| Tier | Minimum detected features | Launcher renderer | Game renderer expectation | Optional features disabled |
|---|---|---|---|---|
| `ES2_SAFE` | ES2 context, GLSL ES 1.00 compile, RGBA/RGB framebuffer complete, texture upload, blend, depth, render-to-texture | ES2, GLSL ES 1.00, single render target | Unverified; use only if runner is proven compatible later | ES3-only formats, timer queries, sync objects, MRT |
| `ES3_BASE` | ES3 context plus all `ES2_SAFE` tests passed | ES3 requested, ES2 shaders still valid | Practical baseline candidate; still needs device evidence | ES3.1/3.2-only features |
| `ES3_ENHANCED` | `ES3_BASE` plus preferred render-target formats, disjoint timer query or equivalent, fence sync, required extensions | ES3 | Optional instrumentation and faster paths enabled | Only features that failed their individual probes |
| `UNKNOWN` | No context or a self-test failure | Report diagnostic screen; do not claim rendering | Do not start gameplay | All optional features |

Tier assignment is advisory. It must never fabricate support: a failed probe disables exactly that feature and records the reason.

## 4. Data to collect

The native GPU query (`app/src/main/cpp/engine/gpu_info.cpp`, exposed through `NativeCore` and `GpuInfo.kt`) currently returns the basics. The full detector must collect:

| Category | Fields | Current state |
|---|---|---|
| EGL identity | EGL vendor, version, extensions | Partially implemented |
| GL identity | GL vendor, renderer, version, GLSL version | Implemented |
| Core limits | maximum texture size, maximum renderbuffer size, maximum viewport | Implemented for texture size; others to add |
| Extensions | full supported extension list | Implemented as a string; not gated per feature |
| Render targets | RGBA8/RGB8/RGBA4/RGB565/DEPTH_COMPONENT16/DEPTH24_STENCIL8 framebuffer completeness | Missing |
| Framebuffer features | draw buffers / MRT count, blit support, sRGB where relevant | Missing |
| Timing | disjoint timer query support | Missing |
| Synchronization | fence sync and client wait support | Missing |
| Context behaviour | recreation, surface destruction, rotation, pause/resume, driver recovery | Not automated |

EGL/GL string parsing must remain defensive: strings can be null, truncated, or report wrapper layers. The renderer string is informational only; a `SwiftShader` or `llvmpipe` string must not be presented as GPU support evidence.

## 5. Self-test design

Add a host-side self-test that runs only when a valid context exists and records one result per capability:

1. **Shader compilation** — compile and link the GLSL ES 1.00 shaders actually used by `render_target.cpp` and `diagnostics_scene.cpp`; report the info log on failure.
2. **Framebuffer creation** — for each candidate colour/depth format, create an FBO, check `glCheckFramebufferStatus`, then delete it.
3. **Texture upload** — upload a synthetic RGBA image with supported filtering and wrap modes; check `glGetError`.
4. **Alpha blending** — draw two known synthetic quads and read back a pixel; verify the expected blend result within tolerance.
5. **Depth testing** — draw near and far synthetic geometry and verify depth ordering by readback.
6. **Render-to-texture** — render the synthetic scene into an FBO, bind it as a texture, and verify a non-black sample.
7. **Multiple render targets** — only attempt when the reported draw-buffer count allows it; otherwise record `unsupported` rather than `failed`.
8. **Timer query / fence sync** — probe availability and a single begin/end or fence/wait cycle; record support without using unproven values for performance claims.
9. **Context recreation** — destroy and recreate the host test context, then repeat shader and FBO checks.
10. **Lifecycle events** — surface destruction, rotation, pause/resume, and driver recovery are device/emulator checks; on a workstation they remain `NOT TESTED`.

Every result is `PASSED`, `FAILED`, `UNSUPPORTED`, or `NOT TESTED`. No result may be inferred from another: a texture upload pass does not imply render-to-texture pass.

## 6. Fallback rules

- If ES3 context creation or any ES3-specific self-test fails, stay on the ES2 safe tier for the launcher.
- If an optional render-target format fails, remove that format from the candidate list; do not fall back to a different format silently without recording it.
- If render-to-texture fails, use the direct-render path already present in the host engine for diagnostics; do not apply that path to gameplay.
- If no context can be created, show the graphics diagnostic screen and keep settings/import usable.
- `forceEs2` remains an explicit user override for the launcher path; it does not change the runner's fixed Android EGL request.

## 7. Evidence ladder for graphics

| Level | Current status |
|---|---|
| Compiled | ES2/ES3 EGL request code, GLSL ES 1.00 shaders, GPU query |
| Packaged | Present in `libsotc_core.so`; runner packaged separately |
| Emulator-tested | API 28 x86_64 SwiftShader: ES3 request failed, ES2 fallback produced non-black 1280x720 frames |
| Runtime-tested on ES2 | Launcher diagnostics only |
| Runtime-tested on ES3 | Not tested |
| Game rendering tested | Not tested |
| Vendor GPU tested | Not tested |

SwiftShader cannot reach real-time frame rates and reports neutral thermal status, so it is valid only for correctness smoke tests.

## 8. Known implementation gap

`gpu_info.cpp:188-190` states that the EGL display is intentionally left initialized, while `egl_renderer.cpp:225-235` calls `eglTerminate(display_)` during shutdown. This is an unresolved source-level contradiction. It must be reviewed before adding context-recreation self-tests, because a self-test that tears down a display used by another thread could expose a real lifetime bug.

## 9. Acceptance criteria for a compatibility claim

A future claim such as "supports vendor X" requires all of the following:

1. feature detector results captured on that vendor, including driver and GLSL versions;
2. full self-test result set with no unreported failures;
3. game runner booted and at least one real frame captured through the runner path;
4. pause/resume and rotation exercised;
5. frame-time report recorded with the exact driver version;
6. no reliance on experimental vendor-only extensions.

Until then the only accurate claim is: the launcher has an ES2 compatibility path; maximum game GPU compatibility is a design target, not a verified result.
