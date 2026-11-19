# Installation

This guide explains how to install the Shadow of the Colossus Mobile Android app and import your own supported game image.

> **Current status:** The project is in development and **not playable yet**. The published `v0.1.0-alpha.1` release is an Android **host application** preview (debug-signed). Importing a supported disc image does not enable gameplay until a compatible native game runtime/core is completed, packaged, and verified.

## 1. Requirements

### Android device

- **Architecture:** `arm64-v8a` (target/shipping ABI)
- **Minimum Android version:** Android 9 (API 28)
- **Graphics:** OpenGL ES 2.0 minimum. The app requests ES 3 and falls back to ES 2 automatically; `Force OpenGL ES 2.0` is available in Settings → Graphics. Vulkan is not implemented.
- **Input:** Touchscreen required; USB/Bluetooth gamepad optional (physical controller behavior not yet fully verified)
- **Storage:** Enough free space for the APK plus a private copy of the imported disc image and extracted data (several GB recommended for a full retail image)

> `x86_64` builds may exist for emulator/integration testing during development but are **not** supported shipping targets for gameplay.

### Required game image (user-supplied)

You must provide your own legally obtained copy of:

- **Game:** *Shadow of the Colossus* (PlayStation 2)
- **Region:** USA (NTSC-U) retail
- **Serial:** `SCUS-97472`
- **Expected boot executable:** `SCUS_974.72`

A generic PS2 ISO, other regions/releases, previews, or modified images are not supported. See [COMPATIBILITY.md](COMPATIBILITY.md) for full validation requirements and status definitions.

### What you do **not** need to provide separately

- No PlayStation 2 BIOS file is distributed or required by this repository's documented native-port approach.
- No PS2 emulator app is required (this is not an emulator frontend).

## 2. Download

- Official releases page: **https://github.com/zoddrun/Port-SotC-Mobile/releases**
- Latest release: [`v0.1.0-alpha.1` — Platform Preview](https://github.com/zoddrun/Port-SotC-Mobile/releases/tag/v0.1.0-alpha.1)
- Release notes (repository): [releases/v0.1.0-alpha.1.md](releases/v0.1.0-alpha.1.md)
- Release notes/history (repository): [releases/RELEASE_HISTORY.md](releases/RELEASE_HISTORY.md)

> The current release is a **debug-signed development preview of the host application**, not a playable game build. Verify the published SHA-256 before installing.

## 3. Install the APK

1. Download the APK asset from the official GitHub Releases page (for the current preview: `SotC-Mobile-v0.1.0-alpha.1-debug.apk`).
2. On your Android device, allow installation from the app/browser/source you used if prompted (Android's "Install unknown apps" setting).
3. Open the downloaded APK and complete installation. Because the preview is debug-signed, Android may display an additional warning.
4. Launch **Port SotC Mobile**.

If installation fails, verify that your device is ARM64-compatible, runs Android 9+ and that the APK was fully downloaded from the official release page.

## 4. Import your game image

1. Copy your legally obtained `SCUS-97472` ISO image to a location accessible by Android's file picker (device storage, SD card, or a provider supported by SAF), if needed.
2. Open the app.
3. Tap **Import Game** (or the equivalent import action in the current UI).
4. Select your `.iso` file using the system file picker.
5. Wait for the import process to complete:
   - Copy to app-private storage
   - Content validation (ISO9660 / `SYSTEM.CNF` / boot ELF / serial / required modules)
   - Extraction/publishing of required data

The app opens the selected document read-only and does not delete or modify your original file.

### Supported image requirements (summary)

- Serial: `SCUS-97472`
- Boot executable: `SCUS_974.72`
- Required modules present and valid: `KERNEL.XFF`, `MANAGER.XFF`, `GAMECORE.XFF`

See [COMPATIBILITY.md](COMPATIBILITY.md) for exact target details and known unsupported releases.

## 5. Core validation and launch expectations

- A successful import means the disc image passed the app's current validation and was stored in the app's managed layout.
- **Successful import does not mean the game is playable.**
- If no compatible native game runtime/core is available, the app will report that state and may open diagnostics instead of gameplay. This is expected behavior in the current development stage.

## 6. Common installation/import problems

| Problem | What to check |
| --- | --- |
| APK will not install | Device ABI/Android version compatibility, incomplete download, install permission for the source app. |
| "Unsupported" or validation error during import | Confirm the image is USA retail `SCUS-97472` (`SCUS_974.72`), unmodified, and structurally complete; not PAL/Japanese/preview/repacked. |
| Import fails due to space | Ensure enough free storage for a private copy of the ISO and extracted data. |
| Import cancelled/interrupted | Retry import; the app is designed to avoid leaving a broken published install. |
| App opens but game does not run | Expected if no verified native game core/runtime is packaged. Check status messages and [TROUBLESHOOTING.md](TROUBLESHOOTING.md). |
| Gamepad/touch issues | See [CONTROLS.md](CONTROLS.md) and [TROUBLESHOOTING.md](TROUBLESHOOTING.md); gameplay control verification depends on runtime integration. |

## 7. Uninstalling and data

- Uninstalling the app removes app-private imported data (including the copied disc image and app-managed saves, if any are created by future runtime versions).
- Keep your own legally obtained original disc image stored separately; the app does not replace it.

For build-from-source installation, see [BUILDING.md](BUILDING.md). For troubleshooting, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).