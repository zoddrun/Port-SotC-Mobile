# Controls

This document describes the intended and currently implemented control support for Shadow of the Colossus Mobile. **Gameplay control behavior depends on a completed and integrated native game runtime/core; host/input foundations are not the same as verified in-game controls.**

## 1. Target control model (PlayStation 2)

The project targets the original *Shadow of the Colossus* (USA, `SCUS-97472`) DualShock 2-style control scheme, including:

- Left analog stick
- Right analog stick
- D-pad (Up/Down/Left/Right)
- Face buttons: **Triangle, Circle, Cross, Square**
- Shoulder buttons: **L1, L2, R1, R2**
- **L3 / R3** (analog stick press)
- **Start / Select**
- Analog stick magnitude/direction and button combinations as required by the original game

## 2. Touchscreen controls (virtual controller)

### Implemented host/UI capabilities

- On-screen PS2-style controls rendered in the game/diagnostics view.
- Multitouch support for simultaneous inputs.
- Virtual controls including D-pad, analog sticks, face buttons, shoulder buttons, and Start/Select (and L3/R3 where provided by the layout).
- Touch layout editor for positioning/resizing/hiding/customizing controls (persisted layout support in host implementation).
- Opacity/size-related settings as exposed by the app settings.

### Notes and limitations

- Touch-to-gameplay pad delivery for the planned D18 raylib-owned runtime path requires a completed runtime input bridge (tracked in project planning; upstream runtime touch mapping gaps are known).
- Verified emulator touch/multitouch behavior (2026-11-09) covered host/input and UI interactions; **real gameplay control verification is not yet available**.
- Physical touchscreen behavior on ARM64 devices has not been verified.

## 3. Physical controllers (USB / Bluetooth)

### Intended support

- USB and Bluetooth gamepads supported by Android input APIs (and potentially Paddleboat integration in future runtime work, if adopted).
- Mapping of common controller inputs to PS2 buttons/sticks.

### Current state

- Host-side physical controller handling/mapping components exist (`GamepadManager`, mapping/settings UI).
- Historical emulator/integration work focused primarily on touch and host behavior; **Bluetooth/USB gamepad and rumble verification on physical hardware is pending**.
- End-to-end physical controller behavior in actual gameplay depends on runtime integration and must be verified.

## 4. Controller customization

The app includes (or is designed to include) controller-related settings such as:

- Analog deadzone adjustment
- Stick inversion options (where implemented)
- Vibration enable/disable setting (runtime/device support dependent)
- Touch control size/opacity and layout editing
- Controller mapping/reset options (as implemented in current UI)

Actual availability and effect during gameplay depend on the integrated runtime/core.

## 5. Button/state representation (technical)

The host input model uses SCE_PAD-compatible button bits and normalized analog stick values (see `app/src/main/cpp/sotc_game_api.h`, `PadState`, and architecture docs) to provide a stable boundary for runtime integration.

## 6. Known input limitations

- No verified in-game control mapping yet (no playable runtime integrated/verified).
- Planned D18 integration requires a unified bridge from existing touch/gamepad inputs to the PS2X/runtime pad path without duplicate input systems.
- Rumble/haptics behavior during gameplay is not verified.
- Device-specific controller compatibility varies by Android device, controller firmware, and mapping.

## 7. Verification status

| Area | Status |
| --- | --- |
| Touch UI/multitouch/layout editor (host, emulator) | Historically verified (2026-11-09, x86_64 emulator) |
| Physical touchscreen on ARM64 device | Not verified |
| USB/Bluetooth gamepad on hardware | Not verified |
| Rumble on hardware | Not verified |
| Controls in actual gameplay | Not verified (no playable runtime) |

For setup issues, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md). For platform requirements, see [COMPATIBILITY.md](COMPATIBILITY.md).