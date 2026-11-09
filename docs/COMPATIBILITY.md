# Compatibility

Status: honest current state, not a promise of future compatibility.
Last updated: 2026-11-09.

## 1. Compatibility summary

| Item | Status |
| --- | --- |
| Native Android host (this repository) | Implemented; build, tests, and runtime verified |
| Native game core (`libsotc_game.so`) | Not built, not included |
| Actual gameplay | Not available in this milestone |
| Disc import on device (synthetic image) | Verified end to end through SAF |
| Real retail disc import | Not verified (no disc image available) |
| ARM64 device execution | Not verified (no ARM64 device available) |
| Emulator execution | Verified on Android 9 (API 28) `x86_64` with SwiftShader |
| Rendering, audio, input, lifecycle | Verified on the emulator only |

The application is a real native Android host. It is not yet a playable port,
because the statically recompiled game core has not been produced. The UI says
this explicitly instead of presenting diagnostic output as gameplay.

## 2. Supported disc

| Field | Value |
| --- | --- |
| Title | Shadow of the Colossus |
| Region | USA (NTSC-U) |
| Serial | `SCUS-97472` |
| Boot executable | `SCUS_974.72` |
| Required modules | `KERNEL.XFF`, `MANAGER.XFF`, `GAMECORE.XFF` |
| Container | ISO9660 image (`.iso`) |

Only this serial is accepted by `SupportedDiscs`. Other serials are rejected
with an explicit message. This is intentional: a core and asset assumptions are
valid only for the release they were produced from.

Known incompatible releases:

- The Japanese preview build `SCPS_150.97` used by Fantaskink/SOTC. Different
  serial and executable; not supported.
- PAL and Japanese retail releases are not validated or supported.
- Any modified, translated, or repacked image is unsupported even if it claims
  the same serial.

The importer accepts any file through the Storage Access Framework file picker
and then validates the content. A file named `.iso` is not trusted; the staged
copy must parse as the supported PS2 disc.

## 3. Android requirements

| Requirement | Value |
| --- | --- |
| Minimum Android version | 9 (API 28) |
| Target Android version | 14 (API 34) |
| Compile SDK | 34 |
| Shipping ABI | `arm64-v8a` |
| Debug/test ABI | `x86_64` |
| Graphics | OpenGL ES 3.0 or later, required |
| Touchscreen | Required |
| Gamepad | Optional |
| Audio | AAudio; 48 kHz float stereo requested |
| Storage | App-private storage for ISO copy, modules, and saves |

The manifest declares OpenGL ES 3.0 and a touchscreen as required features. A
gamepad is optional.

The `x86_64` debug build exists only so the host can be exercised on an
emulator. It is not a supported shipping target.

## 4. What was actually verified

### 4.1 Build and unit tests

Verified on 2026-11-09 on the development host:

- [x] `./gradlew testDebugUnitTest` - 49 tests passed.
- [x] `./gradlew assembleDebug` - APK built for `arm64-v8a` and `x86_64`.
- [x] `./gradlew lintDebug` - passed with warnings only.
- [x] Synthetic ISO9660 images parse correctly.
- [x] Valid synthetic disc is accepted.
- [x] Invalid `SYSTEM.CNF`, unsupported serial, wrong boot file, invalid ELF,
  missing module, and bad module magic are rejected.
- [x] PS2 ELF header validation.
- [x] Settings sanitization and persistence logic.
- [x] Touch layout persistence.
- [x] Pad button merging, analog magnitude selection, and short-tap latching.

### 4.2 Runtime on the Android emulator

Verified on 2026-11-09 on an Android 9 (API 28) `x86_64` AVD named
`Sotc_API_28` with SwiftShader (`-gpu swiftshader_indirect`) and the
`-feature GLESDynamicVersion` emulator flag:

- [x] APK installs and launches (`adb install -r`, `am start`).
- [x] EGL 1.4 initializes, GLES 3.0 context created
  (`OpenGL ES 3.0 SwiftShader 4.0.0.1`), `RGBA8888 depth24 stencil8 samples0`
  config selected.
- [x] `EGL surface ready: 1280x720` after the `SurfaceView` is created.
- [x] Diagnostic scene renders; the statistics overlay reports FPS, frame time,
  surface size, and resolution scale.
- [x] AAudio starts at 48 kHz stereo and the callback counter advances
  (`364` to `715` audio callbacks over 5 s).
- [x] Resolution scaling works: about 2.5 fps at 100% and about 12 fps at 26%
  on the software renderer (see 6.6).
- [x] Pause/resume: the on-screen pause button opens the pause panel; HOME and
  return produce `Engine paused`, `Render thread stopped`, `AAudio stopped`,
  then `Engine resumed` with a new `EGL surface ready` line.
- [x] Settings persist across force-stop and `install -r`.
- [x] Touch input reaches the pad: face buttons, D-pad directions, and analog
  stick values were observed through debug `SotcInput` logs.
- [x] Multitouch: holding a face button while tapping the pause button shows
  `PAD    CROSS` in the pause panel.
- [x] Pause menu navigation, controller settings, and touch layout editor
  screens open and render.
- [x] Touch layout editing: select, drag, save, and reload into the game view.
- [x] Synthetic disc import through the Storage Access Framework: staged copy,
  validation, module extraction, and manifest publish; the source file is left
  untouched.
- [x] Unit tests, lint, and the APK are reproducible from a clean checkout.

Not verified:

- [ ] Import of a real multi-GB disc image through a real content provider.
- [ ] Execution on ARM64 hardware (no device connected).
- [ ] Frame rate and thermal behaviour on real mobile hardware.
- [ ] Bluetooth and USB gamepads on physical hardware.
- [ ] Any gameplay, rendering, audio, save, or performance behaviour of the
  original game.
- [ ] `libsotc_game.so` loading and ABI interoperability.

### 4.3 Defects found and fixed during runtime verification

- Raw-string shader sources started with a newline, so `#version 300 es` was not
  the first token. SwiftShader rejected all three shaders; the source is now
  normalized and every shader starts with `#version`.
- EGL config selection assumed one config was always returned. The renderer now
  binds the GLES API explicitly, logs the client APIs, and tries three config
  candidates.
- Frame time reported the clamped simulation delta instead of the measured frame
  time.
- Taps shorter than the 16 ms pad poll could be dropped. Presses are now latched
  and reported on the next snapshot.
- Overlapping on-screen controls picked the last drawn control instead of the
  nearest one, which could trigger the wrong button.
- The touch layout editor's bottom panel covered the lower controls, so they
  could not be selected. The preview now occupies the space above the panel.
- The controller settings mapping list contained mojibake instead of an arrow.

## 5. Runtime verification environment

Runtime verification used a local Android 9 (API 28) `x86_64` AVD with a
software GPU, not the earlier API 37 image that requires 4 GB of host RAM:

```
emulator.exe -avd Sotc_API_28 -no-snapshot -no-boot-anim -no-window \
    -gpu swiftshader_indirect -memory 1536 -feature GLESDynamicVersion
```

The `-feature GLESDynamicVersion` flag is required on this setup. Without it the
guest reports `ro.opengles.version=0x00020000` (GLES 2.0) and EGL config
selection for the GLES 3.0 context fails.

No physical Android device was connected. No retail disc image was available.
The imported image used for the end-to-end test is a synthetic, structurally
valid ISO9660 image generated by `SyntheticDiscExportTest`; it contains no
copyrighted data. These are environment limitations, not statements that the
code is known to fail elsewhere.

## 6. Known limitations

### 6.1 No game core

Without `libsotc_game.so`, the application:

- Shows "native game core not built" in the library and settings screens.
- Starts a diagnostic renderer with an atmospheric procedural scene.
- Plays a quiet procedural ambience if audio is enabled.
- Accepts touch and gamepad input and updates the status JSON.
- Does not render or simulate game content.

### 6.2 Core generation dependency

The core must be generated on a host machine with a legally obtained image and
the GPL-3.0 Celosia/PS2Recomp toolchain. The exact steps and their current
automation level are host-tooling work, not Android application work. See
`ANDROID_PORTING_PLAN.md`, Phase 2.

### 6.3 Renderer scope

The host supplies an EGL/GLES 3 surface and resolution-scaled offscreen target.
The core owns its draw calls. Features such as texture format coverage, depth
handling, blending accuracy, or post-processing depend on the core and have not
been validated.

### 6.4 Audio scope

The host requests 48 kHz float stereo through AAudio. Latency, underrun
behaviour, resampling, and synchronization with video can only be evaluated
once a core produces real audio.

### 6.5 Saves

The game directory reserves a `saves/` folder and re-import preserves it. The
actual memory-card format is a core responsibility. Save compatibility with
other ports, emulators, or the original hardware has not been tested.

### 6.6 Performance

Measured on the API 28 `x86_64` emulator with SwiftShader, 1280x720 surface:

| Resolution scale | FPS | Frame time |
| --- | --- | --- |
| 100% | 1.8 - 2.7 | 360 - 570 ms |
| 26% | 10 - 13 | 67 - 160 ms |

These numbers are software-renderer bound. They are logged from the native
engine as `Frame stats: ... fps, ... ms, WxH surface, N% scale, M audio
callbacks` and are not device benchmarks. No CPU, GPU, memory, thermal, or
ARM64 measurements exist yet. Static recompilation removes interpreter
overhead, but it does not guarantee that the PS2 workload will meet mobile
thermal budgets.

### 6.7 Legal

The repository contains no BIOS, ISO, game assets, or recompiled core. Users
must supply their own legally obtained disc. Distribution of a generated
GPL-3.0 core would carry separate obligations.

## 7. Compatibility policy

A release may only claim a feature as compatible when:

1. It has been tested on the stated hardware or a documented emulator.
2. The exact disc serial and boot file are recorded.
3. Evidence exists (logs, screenshots, measurements, or automated tests).
4. Known defects are documented rather than hidden.

Until then, status remains "implemented, not verified" or "blocked".
