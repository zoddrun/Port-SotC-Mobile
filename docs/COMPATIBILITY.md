# Compatibility

Status: honest current state, not a promise of future compatibility.
Last updated: 2026-11-08.

## 1. Compatibility summary

| Item | Status |
| --- | --- |
| Native Android host (this repository) | Implemented; build and unit tests pass |
| Native game core (`libsotc_game.so`) | Not built, not included |
| Actual gameplay | Not available in this milestone |
| Real disc import on device | Not verified in this environment |
| ARM64 device execution | Not verified (no device available) |
| Emulator execution | Not verified (host RAM below emulator minimum) |

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

Verified on 2026-11-08 on the development host:

- [x] `./gradlew testDebugUnitTest` - 44 tests passed.
- [x] `./gradlew assembleDebug` - APK built for `arm64-v8a` and `x86_64`.
- [x] `./gradlew lintDebug` - passed with warnings only.
- [x] Synthetic ISO9660 images parse correctly.
- [x] Valid synthetic disc is accepted.
- [x] Invalid `SYSTEM.CNF`, unsupported serial, wrong boot file, invalid ELF,
  missing module, and bad module magic are rejected.
- [x] PS2 ELF header validation.
- [x] Settings sanitization and persistence logic.
- [x] Touch layout persistence.
- [x] Pad button merging and analog magnitude selection.

Not verified:

- [ ] APK install and launch on a device or emulator.
- [ ] EGL surface creation on Android.
- [ ] AAudio stream startup and callback output.
- [ ] Import of a real multi-GB disc image through SAF.
- [ ] Touch and gamepad input on hardware.
- [ ] Surface loss, pause/resume, and process death behaviour.
- [ ] Any gameplay, rendering, audio, save, or performance behaviour of the
  original game.
- [ ] `libsotc_game.so` loading and ABI interoperability.

## 5. Why runtime verification is missing

The development host has approximately 3.8 GB of total physical RAM. The only
installed emulator image is `Medium_Phone_API_37.0` (`x86_64`, Android API 37),
whose launcher requires at least 4 GB of host RAM and refuses to start:

```
FATAL | Available system RAM is not enough to run avd:
        'Medium_Phone_API_37.0'. Available: 3790 MiB, minimum required: 4096 MiB.
```

No physical Android device was connected through `adb`. No retail disc image
was available in the environment. These are environment limitations, not
statements that the code is known to fail at runtime.

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

No frame-rate, CPU, GPU, memory, or thermal measurements exist yet. Static
recompilation removes interpreter overhead, but it does not guarantee that the
PS2 workload will meet mobile thermal budgets.

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
