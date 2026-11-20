# Release History

Official project releases: **https://github.com/zoddrun/Port-SotC-Mobile/releases**

## Current status

**The latest published release is `v0.3.0-alpha.1` — HD Presentation and Controls Preview (2026-11-20).**

- The release contains the Android host application, an original bundled shader pack, refined launcher/quick-settings UI and a packaged ARM64 recompiled runtime; it is **not playable**.
- The runtime is built, statically verified and wired to settings, but has never loaded on a physical device; gameplay has not been verified.
- Debug/local builds must not be presented as official releases unless they are explicitly published on GitHub Releases with accurate notes, verification evidence, and required compliance/license information.

## Published releases

| Version | Date | Label | Highlights | Verification notes | Assets |
| --- | --- | --- | --- | --- | --- |
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
