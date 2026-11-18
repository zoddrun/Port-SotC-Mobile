# Release History

Official project releases: **https://github.com/zoddrun/Port-SotC-Mobile/releases**

## Current status

**The latest published release is `v0.1.0-alpha.1` — Platform Preview (2026-11-18).**

- The release contains the Android host application only; it is **not playable**.
- No native game core is packaged, and gameplay has not been verified.
- Debug/local builds must not be presented as official releases unless they are explicitly published on GitHub Releases with accurate notes, verification evidence, and required compliance/license information.

## Published releases

| Version | Date | Label | Highlights | Verification notes | Assets |
| --- | --- | --- | --- | --- | --- |
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
