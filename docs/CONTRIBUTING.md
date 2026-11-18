# Contributing

Thank you for your interest in contributing to Shadow of the Colossus Mobile (Port-SotC-Mobile).

This project is a native Android port effort for *Shadow of the Colossus* (USA retail `SCUS-97472`) using static recompilation. Contributions must be technical, honest, and legally compliant.

## 1. Ground rules

- **English only:** code, comments, docs, commit messages, issue text, and UI strings must be in English.
- **No emulator backend:** do not introduce PCSX2/AetherSX2/NetherSX2/RetroArch/Play!/Wine or emulator-wrapper solutions.
- **No fake functionality:** do not add placeholder gameplay, simulated "working" game behavior, or claims not backed by implementation/evidence.
- **No copyrighted game materials:** never commit or share ISO images, BIOS files, extracted game assets, memory-card/saves from copyrighted content, or recompiled game cores/binaries derived from copyrighted game data unless explicitly reviewed for licensing/distribution compliance (and in general, do not submit them).
- **Respect licenses:** preserve third-party notices and follow GPL-3.0/other license obligations for any integrated code. See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
- **Protect private assets:** never modify, move, or commit the developer's private `original iso/` contents.

## 2. What to contribute

Good contribution areas include:

- Android host improvements (UI, import/validation robustness, diagnostics, lifecycle handling)
- Input systems (touch/gamepad mapping, accessibility of controls, layout editor improvements) aligned with a single normalized input path
- Build, tooling, and documentation fixes (accurate, reproducible, and verified)
- Test coverage (unit/integration tests for project-owned code)
- Performance profiling/instrumentation (with measured results)
- Runtime integration work consistent with documented architecture decisions (e.g. D18 path), when supported by evidence

Please discuss large architectural changes before implementing them.

## 3. Before you start

1. Read [README.md](../README.md), [COMPATIBILITY.md](COMPATIBILITY.md), [ARCHITECTURE.md](ARCHITECTURE.md), [BUILDING.md](BUILDING.md), and [ROADMAP.md](ROADMAP.md).
2. Check existing issues/PRs and project status docs (`docs/agent/` for internal engineering state).
3. Ensure your environment matches documented tool versions where relevant (JDK 17, Android SDK/NDK/CMake versions used by the project).

## 4. Development workflow

- Create focused, reviewable changes.
- Keep platform-specific code isolated and consistent with existing style.
- Prefer incremental commits with clear English messages.
- Do not commit generated build outputs, local machine files, or secrets.
- Update documentation when behavior, requirements, or status changes.

### Build/test commands (host app)

```bash
./gradlew testDebugUnitTest --console=plain
./gradlew lintDebug --console=plain
./gradlew assembleDebug --console=plain
```

(Use `gradlew.bat` on Windows.) Only report results you actually observed.

## 5. Pull requests

Please include:

- **Summary:** what changed and why
- **Scope:** files/components affected
- **Testing:** commands run, environments used, and results (or why not run)
- **Compatibility/status impact:** any changes to supported releases, requirements, or verified claims
- **Screenshots/log evidence** when relevant (no copyrighted game content)

PRs that introduce unsupported claims, emulator dependencies, or copyrighted materials will not be accepted.

## 6. Reporting issues

When filing an issue, provide:

- Device/emulator model, Android version, ABI
- App build/version/commit (if known)
- Steps to reproduce and expected vs actual behavior
- Exact error messages and relevant `adb logcat` logs
- For import issues: whether the image is a legally obtained USA retail `SCUS-97472` copy and the validator/app error shown (do not attach the ISO)

## 7. Security and secrets

- Never commit keystores, passwords, API keys, tokens, or personal data.
- Report sensitive concerns privately to the maintainer when appropriate instead of posting secrets publicly.

## 8. Code of conduct

Be respectful and constructive in discussions and reviews. Focus on technical accuracy and project goals.