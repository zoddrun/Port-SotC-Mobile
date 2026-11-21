# Release History

Official project releases: **https://github.com/zoddrun/Port-SotC-Mobile/releases**

## Current status

**The latest published release is `v0.3.4-alpha.1` — Local Tooling Follow-up (2026-11-21).**

- The release contains the Android host application, an original bundled shader pack, refined launcher/quick-settings UI, a real local disc inspector/BOOT2 identifier, local setup milestones, a memory-card vault, a diagnostics-only native cache, and a packaged ARM64 recompiled runtime; it is **not playable**.
- The runtime is built, statically verified and wired to settings, but has never loaded on a physical device; gameplay has not been verified.
- Debug/local builds must not be presented as official releases unless they are explicitly published on GitHub Releases with accurate notes, verification evidence, and required compliance/license information.

## Published releases

| Version | Date | Label | Highlights | Verification notes | Assets |
| --- | --- | --- | --- | --- | --- |
| [`v0.3.4-alpha.1`](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.3.4-alpha.1) | 2026-11-21 | Alpha / Local Tooling Follow-up | Real app-private disc inspector with whole-image SHA-256 and PS2 BOOT2 MD5; original local setup milestones; file-level memory-card vault with `mc0` preservation across re-extraction; isolated diagnostics-only native report cache. Active gameplay path, patch-0012 runner and patch chain unchanged. | 25 suites / 155 unit tests, lint and `assembleDebug` pass; `RUNNER_VERIFY_OK` and `APK_NATIVE_VERIFY_OK` (0 failures/warnings); `HOST_SMOKE_0009_OK` (20/20); debug APK 131,473,668 bytes, SHA-256 `E36F822DE6A5307A8A3941A8E4809630F6C5BCC873B1CA49EAAE664501F105B9`. No physical device evidence. See [release notes](v0.3.4-alpha.1.md). | APK (debug-signed) |
| [`v0.3.3-alpha.1`](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.3.3-alpha.1) | 2026-11-21 | Alpha / Presentation Hardening and UI Polish | Declarative true-cutout fullscreen with focus re-apply, host-cull and NPOT/anisotropy mipmap black-screen guards, ordered import actions, icon-above-label launcher tiles and keycap/grip controls screen; unchanged patch-0012 runner. | 21 suites / 139 unit tests, lint and `assembleDebug` pass; `RUNNER_VERIFY_OK` and `APK_NATIVE_VERIFY_OK` (0 failures/warnings); debug APK 132,300,796 bytes, SHA-256 `73A1D9BC5A8940BA166804AFFC0E8888FC55829F2ADA18ADBB66077C4BC2FFA6`. No physical device evidence. See [release notes](v0.3.3-alpha.1.md). | APK (debug-signed) |
| [`v0.3.2-alpha.1`](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.3.2-alpha.1) | 2026-11-21 | Alpha / Presentation, Pacing and Fullscreen Fixes | FBO safety and HD shader fallback, sampled FPS with 1.0x EE clock and true `UNLIMITED` FPS, FNV-1a + CLUT texture cache with `glTexSubImage2D`, fullscreen widescreen presentation with FOV correction, vector touch HUD and launcher polish (patch 0012). | `APK_NATIVE_VERIFY_OK` (0 failures, 0 warnings) | APK (debug-signed) |
| [`v0.3.1-alpha.1`](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.3.1-alpha.1) | 2026-11-20 | Alpha / UI Overhaul, Edge-to-Edge Fullscreen and Vector Touch Controls Preview | Luxury console launcher aesthetic, 3-tab HD preset bar, cutout-spanning edge-to-edge window, PlayStation geometric vector touch controls, gamepad noise filtering. | 21 suites / 139 unit tests pass; lint and `assembleDebug` pass; `RUNNER_VERIFY_OK` and `APK_NATIVE_VERIFY_OK` (0 failures/warnings). Debug APK 131,110,544 bytes, SHA-256 `0FBDEF69E79DC6FC23A4BD2303286AFD41DBEECF0D312E266ED54993E36BAD01`. No physical device evidence. See [release notes](v0.3.1-alpha.1.md). | APK (debug-signed) |
| [`v0.3.0-alpha.1`](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.3.0-alpha.1) | 2026-11-20 | Alpha / HD Presentation and Controls Preview | Original ES2/ES3 shader pack with verified installer, refined launcher and quick settings, runner-side sigil touch overlay and grip hold (patch 0011), unchanged GLES2 floor. | 139 unit tests pass; lint/build pass; patch 0011 round trip; host smoke keeps 13 pacing checks and adds `[hd-shader]` checks; `RUNNER_VERIFY_OK` and `APK_NATIVE_VERIFY_OK` (0 failures/warnings); debug-signed. No physical device evidence. See [release notes](v0.3.0-alpha.1.md). | APK (debug-signed) |
| [`v0.2.0-alpha.1`](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.2.0-alpha.1) | 2026-11-19 | Alpha / Runtime Integration Preview | Host application plus packaged ARM64 `libps2EntryRunner.so`; runner-owned frame limit and adaptive resolution (patch 0009), capability-based GPU tiers, static C++ runtime, APK-wide 16 KB native gate. | 105 unit tests pass; lint/build pass; patch 0009 round trip; `RUNNER_VERIFY_OK` and `APK_NATIVE_VERIFY_OK` (0 failures/warnings); debug-signed. No device evidence. See [release notes](v0.2.0-alpha.1.md). | APK (debug-signed) |
| [`v0.1.0-alpha.1`](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.1.0-alpha.1) | 2026-11-18 | Alpha / Platform Preview | Android host application: UI, ISO import/validation (`SCUS-97472`), settings, controller settings, touch controls foundation, diagnostics (AAudio, EGL). No game core. | 49 unit tests pass; lint/build pass; emulator install/launch and host screens verified; APK v2 signature valid; debug-signed. See [release notes](v0.1.0-alpha.1.md). | APK (debug-signed) |

## Release checklist (for future releases)

Before publishing any release entry/asset:

- [ ] Build is reproducible from a known commit/tag and documented.
- [ ] APK/AAB is tested on stated targets (and ARM64 device testing is included when claimed).
- [ ] Supported game release remains clearly documented (`SCUS-97472` initial target unless expanded with evidence).
- [ ] README, compatibility, installation, and known issues are updated and consistent.
- [ ] No copyrighted game data, BIOS files, or private assets are included.
- [ ] Licensing/third-party notices and GPL-3.0 obligations (if distributing derived core/runtime artifacts) are satisfied and documented.
- [ ] Release notes accurately describe implemented/verified behavior and known limitations.
- [ ] Download links/assets on GitHub Releases are verified.
