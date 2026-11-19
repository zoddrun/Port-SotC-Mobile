# Third-Party Licenses and Obligations

Status: **Project license/obligation reference (must be updated when dependencies or distribution artifacts change)**  
Last updated: **2026-11-18**

This document summarizes third-party components, tools, and license considerations relevant to this repository and its planned build/distribution paths. **This is not legal advice.** Verify licenses, notices, and obligations before distributing source, binaries, or any derived game runtime/core.

For detailed integration evaluations and pinned local checkout references, see [THIRD_PARTY_INTEGRATIONS.md](THIRD_PARTY_INTEGRATIONS.md).

## 1. Repository contents (current)

This repository currently contains original project code and documentation (Kotlin, C++, CMake, Gradle, Markdown, scripts/tools), plus developer-provided project assets such as the launcher icon in `icon/`.

It does **not** include:

- PlayStation 2 BIOS files
- Game ISO images or extracted copyrighted game assets
- Recompiled game cores/runtimes (`libsotc_game.so` / runner binaries) as distributable repository artifacts
- User save data

Local third-party checkouts used for pipeline work may exist under `third_party/` (gitignored) but are not committed as part of this repository.

## 2. Core upstream projects (recompilation/runtime)

### 2.1 Celosia

- **Project:** https://github.com/andai/Celosia (upstream lineage; project sources/checkouts used per local workspace/pinning)
- **License:** GNU General Public License v3.0 (**GPL-3.0**)
- **Role in this project:** Game-specific recompilation pipeline, overrides, and runtime components used to generate/build the target game runtime (local gitignored checkout; pinned reference recorded in project docs, e.g. `f8c269e` in agent memory/integration records).
- **Obligations (if used/distributed):** A runtime/core derived from GPL-3.0 pipeline/runtime components is subject to GPL-3.0 obligations. If distributed, you must satisfy GPL requirements (license text, copyright notices, corresponding source/build information, no additional restrictions, etc.) as applicable.

### 2.2 PS2Recomp

- **Project:** https://github.com/ran-j/PS2Recomp
- **License:** GNU General Public License v3.0 (**GPL-3.0**)
- **Role in this project:** Static recompilation framework/runtime reference and Android scaffold reference; Celosia embeds a game-tuned fork for actual generation in the pipeline workflow (see decision records).
- **Obligations:** Same GPL-3.0 considerations apply to use/distribution of derived works.

## 3. Build and runtime dependencies (Android host)

| Component | Version / usage | License (typical) | Notes |
| --- | --- | --- | --- |
| Android Gradle Plugin | 8.5.2 | Apache-2.0 | Build-time |
| Gradle Wrapper | 8.7 | Apache-2.0 | Build-time |
| Kotlin Gradle Plugin / Kotlin stdlib | 1.9.20 | Apache-2.0 | Kotlin stdlib packaged by build as applicable |
| Android SDK / Build Tools / Platform APIs | compileSdk/targetSdk 34, minSdk 28 | Android SDK/license terms apply | Platform interfaces linked/used (EGL, GLES, AAudio, log, android, etc.) |
| Android NDK | 26.1.10909125 | NDK license terms; libc++ notices apply | Native host build; C++ STL configuration affects packaged runtime libraries |
| CMake | 3.22.1 | BSD-style (CMake license) | Build-time/native configuration |
| JUnit | 4.13.2 (`testImplementation`) | EPL-1.0 | Test-only, not packaged in release APK |

> Both native libraries are built with `c++_static`, so `libc++_shared.so` is not packaged in the current APK. If a future build variant packages `c++_shared`, include the applicable libc++/LLVM notices before distribution. `tools/android-integration/verify-apk.py` lists the packaged native libraries so the actual contents can be reviewed before release.

## 4. Other evaluated/referenced projects (not necessarily integrated)

See [THIRD_PARTY_INTEGRATIONS.md](THIRD_PARTY_INTEGRATIONS.md) for current evaluation status and rationale. Examples include RetroTouch, Paddleboat / `androidx.games:games-controller`, Android GameActivity/AGDK samples, Oboe, SDL3, and Ghidra (development tool only). Do not treat evaluation as adoption; pin versions and record licenses/notices before integrating and distributing.

## 5. Game content and protected materials

The following must **not** be committed or distributed by this project:

- *Shadow of the Colossus* disc images (ISOs) or extracted game data/assets
- PlayStation 2 BIOS files
- Recompiled game code/binaries produced from copyrighted game data unless licensing/distribution obligations are fully satisfied and intentionally handled
- User save data or private local reports containing sensitive/private paths beyond what is appropriate

The repository `.gitignore` excludes the private `original iso/` directory and common game/data/build artifacts. Always verify before publishing.

## 6. Distribution checklist (before any public build/release)

1. **Repository license:** decide and add a license for this repository's original code (currently undeclared).
2. **Derived runtime/core:** determine whether a game runtime/core will be distributed. If yes, review GPL-3.0 obligations for Celosia/PS2Recomp-derived components and provide complete corresponding source/build instructions/notices as required.
3. **Notices:** include required license texts/notices for all bundled third-party libraries/components in the distributed artifact.
4. **Artifact audit:** confirm the APK/AAB/release assets contain no ISO, BIOS, copyrighted game assets, or unauthorized binaries.
5. **Compatibility claims:** ensure README/release notes match verified behavior and supported targets.
6. **Repeat on changes:** re-run this audit whenever dependencies, build configuration, or distributed artifacts change.

## 7. Disclaimer

License compatibility and compliance depend on exact usage, linking, modification, and distribution choices. When in doubt, consult qualified legal guidance before public distribution.