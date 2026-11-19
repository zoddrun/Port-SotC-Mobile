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

There are two touch implementations, because the project has two windows:

| Window | Owner | Overlay |
| --- | --- | --- |
| Diagnostics host (`GameActivity`, ABI-v1) | Kotlin `TouchControllerView` | Android views on a `SurfaceView` |
| Gameplay runtime (`GameNativeActivity`, D18) | C++ `ps2_android_input` (patch 0005) | raylib shapes in the runner's own GL frame |

The launcher's settings feed both, but the gameplay overlay is the one that matters: the runner is a raylib `NativeActivity`, so its window is owned by native code and no Android view can be drawn on top of it.

### Gameplay overlay (D18 runner, patch 0005)

- Android hands each motion event to raylib's `native_app_glue` loop, which fills `CORE.Input.Touch` with every pointer's id and position. The runner samples those points once per host frame on the render thread, publishes a mutex-guarded pad state, and the EE thread merges it into the pad wire format at each VBlank.
- Because the sample happens at the top of the render frame and `EndDrawing()` polls the touches, a press reaches the game about one frame later (~17 ms at 60 Hz).
- Layout, sized from the screen's shorter side so phones, tablets, 16:9 and 21:9 share proportions:
  - left stick: bottom-left; right (look) stick: bottom-right;
  - D-pad: above the left stick; face buttons (Triangle, Circle, Cross, Square) above the right stick;
  - L1/L2: top-left; R1/R2: top-right; Select/Start: top-centre.
- Each finger owns at most one control, tracked by pointer id, so releasing one finger never cancels another held control. A stick follows its finger anywhere on the screen; a button stays held slightly past its edge and releases when the finger slides away.
- Face buttons are labelled `T`, `O`, `X`, `S` in the launcher's ivory/gold palette; a pressed control fills gold.
- A finger on a stick overrides a resting gamepad stick; buttons add to whatever the gamepad and the keyboard hold.
- L3/R3 have no on-screen control (the overlay sticks are not clickable); a gamepad provides them.
- The overlay is suppressed while the runtime's own settings menu is open (`hostmenu::inputBlocked()`), so a finger on the glass cannot press a game button behind it.

### Presets

| Preset | Runner geometry |
| --- | --- |
| Classic | Sticks, D-pad, face buttons, shoulders, Start/Select |
| Modern | Classic without the D-pad (more room for the sticks) |
| Compact | Classic at 0.85 scale, without the D-pad |
| Custom | Classic geometry — the launcher's per-control edit is not sent to the runner yet |

Presets change position and size only; the button set and stick sides are invariant.

### Knobs (environment, published by the app)

`GameNativeActivity` reads the settings before `super.onCreate` loads the runner and publishes them with `NativeCore.setEnvironment`; `core/RuntimeEnvironment.kt` owns the names and formatting.

| Variable | Meaning | Default | Range |
| --- | --- | --- | --- |
| `SOTC_TOUCH_OVERLAY` | `0` removes the overlay and its input | `1` | flag |
| `SOTC_TOUCH_PRESET` | `classic`, `modern`, `compact` | `classic` | unknown values → classic |
| `SOTC_TOUCH_HIDE_WITH_GAMEPAD` | hide the overlay while a gamepad is connected | `0` | flag |
| `SOTC_TOUCH_SCALE` | size multiplier on top of the preset | `1` | 0.6–1.4 |
| `SOTC_TOUCH_ALPHA` | opacity of the control bodies (labels stay opaque) | `1` | 0.2–1.0 |
| `SOTC_TOUCH_DEADZONE` | radial dead zone of both touch sticks, rescaled past it | `0.15` | 0–0.6 |
| `SOTC_LOOK_SENSITIVITY` | multiplier on the right stick | `1` | 0.2–3 |
| `SOTC_INVERT_LOOK_X` | invert the right stick's horizontal axis | `0` | flag |

Values are read once, when the module first needs them, so a settings change applies to the next game session.

### Host/UI capabilities (diagnostics window)

- Multitouch support for simultaneous inputs, tracked by pointer id so releasing one finger does not cancel another held control.
- Named presets and a layout editor for positioning/resizing/hiding controls. Saving an edit records the layout as Custom; resetting records Classic.
- Radial deadzone with rescaling, camera sensitivity, and horizontal/vertical look inversion.
- Touch controls release all input when the activity pauses or the window loses focus.
- Touch controls can hide automatically while a physical gamepad is connected, including hot-plug events.

### Notes and limitations

- The gameplay overlay is linked into the ARM64 runner and exported (`verify-runner.sh` checks `ps2_android_input::update`), but it has **not been exercised on a device or emulator** (no ARM64 hardware yet); its behavior in actual gameplay is unverified.
- The Custom preset's per-control geometry lives in the Kotlin layout editor only; the runner falls back to Classic. Sending the custom layout to the runner is future work.
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

The diagnostics host uses SCE_PAD-compatible button bits and normalized analog stick values (see `app/src/main/cpp/sotc_game_api.h`, `PadState`, and architecture docs) as its ABI-v1 boundary.

The runner has its own path, because it is a separate window and library: `ps2_android_input` publishes logical controls (`Button` enum + two sticks), and `PSPadBackend::readState` in `ps2_pad.cpp` converts them to the PS2 pad wire format (`0x73` analog marker, active-low button bits, `0x80`-centred stick bytes) and merges them with the gamepad, keyboard and scripted inputs. A `static_assert` keeps one wire bit per logical button.

## 6. Known input limitations

- The gameplay overlay and its pad merge compile and link but have not run on a device; no in-game control mapping has been verified.
- Custom touch layout geometry is not sent to the runner (it uses Classic geometry).
- L3/R3 are not available on the touch overlay.
- A settings change made while a game is running applies to the next session; the runner reads its knobs once.
- Rumble/haptics behavior during gameplay is not verified.
- Device-specific controller compatibility varies by Android device, controller firmware, and mapping.

## 7. Verification status

| Area | Status |
| --- | --- |
| Touch UI/multitouch/layout editor (host, emulator) | Historically verified (2026-11-09, x86_64 emulator); new presets/settings built and unit-tested, emulator UI verification pending |
| Gameplay overlay (patch 0005) | Compiles for `aarch64-linux-android28`, symbols verified; not run |
| Physical touchscreen on ARM64 device | Not verified |
| USB/Bluetooth gamepad on hardware | Not verified |
| Rumble on hardware | Not verified |
| Controls in actual gameplay | Not verified (no device) |

For setup issues, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md). For platform requirements, see [COMPATIBILITY.md](COMPATIBILITY.md).