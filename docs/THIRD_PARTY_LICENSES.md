# Third-Party Licenses and Obligations

Status: audit of components referenced or used as of 2026-11-08.
This document is not legal advice. It records what was inspected so the project
owner can make informed distribution decisions.

## 1. What this repository contains

The repository contains original Kotlin, C++, Gradle, and documentation code.
It does **not** currently vendor or link any third-party runtime source code
beyond the Android platform libraries and the Kotlin standard library supplied
by the build toolchain.

The only test dependency is JUnit 4, which is not packaged into the application.

No game code, ISO image, BIOS file, extracted asset, or recompiled core is
included.

## 2. Components intentionally not bundled

These projects were researched and may be used to generate a game core outside
this repository. They are not present in the repository or APK today.

### 2.1 Celosia

- Project: https://github.com/andai/Celosia
- License: GNU General Public License, version 3.0 (GPL-3.0).
- Use: reference architecture and potential basis for a recompiled core.
- Obligation if used/distributed: the derived core must be licensed under
  GPL-3.0, complete corresponding source must be made available, copyright and
  license notices must be preserved, and no additional restrictions may be
  imposed. The GPL also requires that recipients can rebuild the work.

### 2.2 PS2Recomp

- Project: https://github.com/ran-j/PS2Recomp
- License: GNU General Public License, version 3.0 (GPL-3.0).
- Use: static recompiler and runtime used by Celosia.
- Obligation if used/distributed: same GPL-3.0 obligations as above. Static
  linking or incorporation of generated code with the runtime is a derivative
  work; do not relicense it as proprietary.

### 2.3 Fantaskink/SOTC

- Project: https://github.com/Fantaskink/SOTC
- License: MIT.
- Use: research reference only. Targets `SCPS_150.97`, not `SCUS-97472`.
- Obligation if any code is copied: retain the MIT copyright and permission
  notice. No code is currently copied.

### 2.4 raylib

- Project: https://www.raylib.com/
- License: zlib.
- Use: referenced by upstream renderer experiments; not used here.
- Obligation if used: retain the notice, do not misrepresent the origin, and
  mark modified versions.

### 2.5 sse2neon

- Project: https://github.com/DLTcollab/sse2neon
- License: MIT.
- Use: possible SIMD translation helper in a future core; not used here.
- Obligation if used: retain the MIT notice.

### 2.6 FFmpeg

- Project: https://ffmpeg.org/
- License: LGPL-2.1-or-later by default; optional components can make the build
  GPL. Some codec configurations are patent-encumbered.
- Use: not used. Upstream Android work disabled it during the audit.
- Obligation if introduced: choose LGPL vs GPL configuration deliberately,
  provide source and notices, allow relinking as required by the LGPL, and
  audit codec patents for the distribution territory.

## 3. Build tools and libraries shipped or used

### 3.1 Kotlin standard library

- License: Apache License 2.0.
- Shipped in the APK by the Kotlin Android plugin.
- Obligation: retain the Apache 2.0 notice and NOTICE file if one is required
  by the distribution.

### 3.2 Android NDK libc++ (`libc++_shared.so`)

- License: MIT / University of Illinois/NCSA dual license, with LLVM
  exceptions as documented by the NDK.
- Shipped because `ANDROID_STL=c++_shared` is configured.
- Obligation: retain the applicable notices. If the shared STL is undesirable,
  switch to `c++_static` in a future build.

### 3.3 Khronos EGL/GLES headers and Android platform libraries

- EGL, GLES, AAudio, `android`, `log`, and `dl` are platform interfaces
  provided by Android. They are linked, not redistributed as source.
- Header licenses are the Khronos MIT-style license and Apache 2.0 where
  applicable.

### 3.4 JUnit 4.13.2

- License: Eclipse Public License 1.0 (EPL-1.0).
- Test-only dependency, not packaged in the APK.
- Obligation: retain notices if test artifacts are distributed.

### 3.5 Gradle, Android Gradle Plugin, Kotlin compiler

- Build-time tools, not part of the application runtime.
- Gradle: Apache 2.0. Android Gradle Plugin: Apache 2.0. Kotlin compiler:
  Apache 2.0.
- Their binaries are downloaded by the build, not committed to the repository.

## 4. Google Play services and AndroidX

- No Google Play services dependency.
- No AndroidX runtime UI dependency. Platform `Activity`, `View`, and custom
  views are used instead.
- If AndroidX is added later, Apache 2.0 notices must be reviewed.

## 5. Game content and BIOS

The following must never be committed or distributed:

- The Shadow of the Colossus ISO or any extracted game data.
- PlayStation 2 BIOS files.
- Recompiled game code produced from copyrighted game data, unless the
  distribution model and GPL-3.0 obligations have been reviewed and satisfied.
- Save files or memory-card images from users.

The `.gitignore` excludes ISO, BIN, PS2, and memory-card extensions, plus the
`games/`, `saves/`, `disc/`, and research checkout directories.

## 6. Distribution checklist

Before publishing any build:

1. Decide and add a license for this repository's own code.
2. Decide whether a game core will be distributed at all.
3. If a core is distributed:
   - License it GPL-3.0.
   - Publish the complete corresponding source and build scripts.
   - Preserve all upstream copyright and license notices.
   - Provide the GPL-3.0 text and a written offer/source link as required.
   - Verify that no non-GPL-only component is incompatible with GPL-3.0.
4. Include the notices for Kotlin, libc++, and any added libraries.
5. Confirm that the APK contains no ISO, BIOS, game asset, or core unless
   intentionally and lawfully added.
6. Re-run this audit whenever a dependency is added.

## 7. Repository license status

No `LICENSE` file has been added yet. The project owner must choose the license
for the original Android host code before any public distribution. Nothing in
this document should be read as choosing that license automatically.
