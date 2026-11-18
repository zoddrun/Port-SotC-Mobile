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
- Multitouch support for simultaneous inputs, tracked by pointer id so releasing one finger does not cancel another held control.
- Virtual controls including D-pad, analog sticks, face buttons, shoulder buttons, and Start/Select (and L3/R3 where provided by the layout).
- Named presets: **Classic** (original-style layout), **Modern** (phone ergonomics), **Compact** (smaller footprint), and **Custom** (your edited layout). Presets change position, size, and scale only; button masks and stick sides are invariant.
- Touch layout editor for positioning/resizing/hiding controls. Saving an edit records the layout as Custom; resetting records Classic.
- Radial deadzone with rescaling, camera sensitivity, and horizontal/vertical look inversion.
- Touch controls release all input when the activity pauses or the window loses focus.
- Touch controls can hide automatically while a physical gamepad is connected, including hot-plug events.

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
- Gamepads are detected through Android input APIs; hot-plug add/remove/change is observed by the game activity.
- Left stick maps to movement; right stick maps to camera with deadzone, sensitivity, and inversion settings.
- Triggers map to L2/R2; hats map to the D-pad.
- Historical emulator/integration work focused primarily on touch and host behavior; **Bluetooth/USB gamepad and rumble verification on physical hardware is pending**.
- End-to-end physical controller behavior in actual gameplay depends on runtime integration and must be verified.

## 4. Controller customization

The app exposes:

- Touch presets (Classic, Modern, Compact, Custom)
- Touch control size and opacity
- Touch radial deadzone
- Camera sensitivity
- Horizontal and vertical look inversion (touch and physical right stick)
- Hide touch controls while a gamepad is connected
- Physical gamepad deadzone, inversion, and vibration preference
- Touch layout editing (position, size, opacity, visibility)
- Controller mapping/reset options where implemented in the current UI

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
| Touch UI/multitouch/layout editor (host, emulator) | Historically verified (2026-11-09, x86_64 emulator); new presets/settings built and unit-tested, emulator UI verification pending |
| Physical touchscreen on ARM64 device | Not verified |
| USB/Bluetooth gamepad on hardware | Not verified |
| Rumble on hardware | Not verified |
| Controls in actual gameplay | Not verified (no playable runtime) |

For setup issues, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md). For platform requirements, see [COMPATIBILITY.md](COMPATIBILITY.md).