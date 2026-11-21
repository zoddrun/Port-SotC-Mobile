# FAQ

## What is Shadow of the Colossus Mobile?

Shadow of the Colossus Mobile (repository: Port-SotC-Mobile) is a development project aiming to run the original PlayStation 2 game *Shadow of the Colossus* natively on Android through **static recompilation** and a native host/runtime, not by bundling a general-purpose PS2 emulator.

## Is the game playable right now?

**No.** The project currently provides a host foundation (app UI, ISO import/validation, input/render/audio/diagnostics groundwork, etc.), but a completed, packaged, and verified native game runtime/core for gameplay is not yet available. The app must report this honestly rather than showing fake gameplay.

## What version of *Shadow of the Colossus* is required?

The initial supported target is:

- **Platform:** PlayStation 2
- **Region:** USA (NTSC-U) retail
- **Serial:** `SCUS-97472`
- **Boot executable:** `SCUS_974.72`

Other releases (PAL, Japanese, preview builds, modified images, or other games) are not verified/supported. See [COMPATIBILITY.md](COMPATIBILITY.md).

## Does the application require an ISO?

Yes, for game data import you must provide your own legally obtained disc image of the supported release (`SCUS-97472`). The repository/app does **not** include game data.

## Does it require a PS2 BIOS?

No PlayStation 2 BIOS file is distributed by this project. The project does not use a PS2 emulator backend. Only dependencies demonstrated by the actual implementation should be considered required.

## Is this an emulator?

**No.** This is not PCSX2, AetherSX2, NetherSX2, RetroArch, Play!, Wine, or any emulator frontend/wrapper. The intended approach is static recompilation with native Android execution.

## Does it support ARM64?

The shipping target ABI is **`arm64-v8a`**. However, **ARM64 hardware execution/compatibility has not yet been verified** because a complete playable runtime has not been packaged and tested on physical ARM64 devices.

## What Android version is required?

- **Minimum:** Android 9 (API 28)
- **Target/compile SDK:** 34
- **Graphics:** OpenGL ES 2.0 minimum; ES 3 is used when the driver accepts it (automatic fallback, with `Force OpenGL ES 2.0` in Settings → Graphics). Vulkan is not implemented.

## Does it support Bluetooth or USB controllers?

Physical USB/Bluetooth gamepads are intended and there is host-side controller support in development, but **hardware and gameplay verification is pending**. Touch controls are also implemented at the host/UI level.

## Can it use touchscreen controls?

Yes, the app includes virtual PS2-style touchscreen controls with multitouch and layout editing as part of the host/input foundation. Verified in-game touchscreen behavior depends on future runtime integration.

## Does it support other PS2 games?

No. This project is game-specific and currently targets only *Shadow of the Colossus* USA retail `SCUS-97472`.

## Where can I download APK releases?

Official releases: **https://github.com/zoddrun/Port-SotC-Mobile/releases**

The latest release is [`v0.3.4-alpha.1` — Local Tooling Follow-up](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.3.4-alpha.1). It contains the debug-signed Android host application, an original bundled shader pack, refined launcher/quick-settings UI, a real local disc inspector/BOOT2 identifier, original local setup milestones, a memory-card vault, a diagnostics-only native cache and a packaged ARM64 recompiled runtime, but is **not playable** because the runtime has not been verified on a physical device. See [releases/v0.3.4-alpha.1.md](releases/v0.3.4-alpha.1.md) and [releases/RELEASE_HISTORY.md](releases/RELEASE_HISTORY.md).

## Can I build it myself?

Yes, you can build the Android host APK from source (see [BUILDING.md](BUILDING.md)). Building the host APK alone does **not** produce a playable game; generating and integrating the recompiled runtime/core is a separate, host-side process with additional requirements.

## Is game content included in this repository?

No. The repository does not include copyrighted game images, BIOS files, extracted assets, or recompiled game cores. The `icon/` artwork provided by the developer is a project asset; the original disc image is kept private and gitignored.

## How can I report bugs or contribute?

Use the repository issue tracker and follow [CONTRIBUTING.md](CONTRIBUTING.md). Do not share copyrighted game materials. Include device/build details, steps to reproduce, and logs when possible.