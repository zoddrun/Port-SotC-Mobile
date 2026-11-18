# Roadmap

This roadmap summarizes completed, active, and planned work for Shadow of the Colossus Mobile. It is a high-level view; authoritative technical status and next actions live in [ANDROID_PORTING_PLAN.md](ANDROID_PORTING_PLAN.md) and `docs/agent/` memory files.

**Last updated:** 2026-11-18  
**Overall status:** Development (host foundation, not playable)

## Milestones

| Milestone | Description | Status | Notes |
| --- | --- | --- | --- |
| M0 | Technical / repository recovery audit | **Completed** | Source/docs/history audited; agent memory established. |
| M1 | Original ISO validation | **Completed** | USA retail `SCUS-97472` image validated on development host (2026-11-09). |
| M2 | Upstream / recompilation audit | **Completed** | Celosia/PS2Recomp and integration constraints reviewed. |
| M3 | Real recompilation pipeline run | **In progress / stalled (evidence, needs manual WSL verification)** | Boot stage and KERNEL capture completed previously; MANAGER/GAMECORE/VU1 and `PIPELINE_DONE` not evidenced. Latest audit indicates stall during stage_game-after-KERNEL rebuild; verify idle state before recovery. |
| M4 | ARM64 cross-compile of game runtime/core | **Not started (prep completed)** | Android NDK/tooling/scripts/patch prep recorded; blocked on M3 completion and verified recovery. |
| M5 | Runtime integration into Android app (D18 path) | **Not started** | Package runner, add `GameNativeActivity`, pre-launch disc extraction, unified input bridge, lifecycle/quit handling per design docs. |
| M6 | First boot on emulator/device | **Not started** | Requires M4+M5 artifacts/integration; x86_64 emulator iteration then ARM64 device when available. |
| M7 | Gameplay verification | **Not started** | Menu/control/area progression, rendering/audio/input/saves with evidence. |
| M8 | Performance optimization | **Not started** | Profile before optimizing; document measurements and device conditions. |
| M9 | Polish (controls, lifecycle, saves, UX) | **Not started / partial foundations exist** | Host UI/input foundations exist; gameplay-time behavior pending runtime. |
| M10 | Release validation and public release | **Not started** | Only after verified, reproducible, honestly labeled builds and compliance checks. |

## Completed highlights

- Native Android host app foundation (Kotlin + C++20/NDK) with EGL/GLES3 rendering host and AAudio path.
- Disc import/validation/storage flow for supported `SCUS-97472` target (content-based validation).
- Touch controls, layout editor, and gamepad/input handling foundations.
- Diagnostics/status reporting and core-loader ABI boundary.
- Developer launcher icon integration and documentation.
- Third-party integration evaluation and technical decision records.

## Active work

- **M3 recovery/completion:** manually verify WSL pipeline state, recover stalled stage if idle (per D14/D15/D19 and KNOWN_ISSUES), and complete captures/stage_game/VU1 to reach `PIPELINE_DONE`.
- Documentation and presentation improvements for public repository clarity (this pass).

## Planned next steps

1. Complete M3 with verified artifacts/log evidence.
2. Execute M4 ARM64 cross-compile and verify runner artifact.
3. Implement M5 D18 runtime integration and input bridge.
4. Achieve and document M6 first boot (emulator, then ARM64 device).
5. Verify gameplay (M7), optimize with measurements (M8), and polish (M9).
6. Publish only verified, accurately labeled releases (M10) after compliance and testing checks.

## Known blockers / constraints

- **Pipeline state:** WSL pipeline progress needs manual verification/recovery (process state not confirmable from all environments).
- **Hardware:** No ARM64 physical device currently available for required device validation (B4).
- **Integration complexity:** Runtime input/audio/lifecycle and disc I/O behaviors must be validated against the actual integrated runner.
- **Release prerequisites:** No claims/pushes/releases without real verification and explicit authorization.

## Release policy

- No release is "playable", "compatible", or "stable" without evidence.
- GitHub Releases must match repository state, documentation, and verified test results.
- Release history: [releases/RELEASE_HISTORY.md](releases/RELEASE_HISTORY.md)