# ENGINEERING RULES AND DEVELOPMENT GUIDELINES

## MANDATORY PROJECT LANGUAGE

**100% of this project MUST be written in English.**

This includes:
- All source code
- All comments
- All documentation
- All UI text and strings
- All error messages
- All log messages
- All test names
- All commit messages
- All technical specifications
- All variable names, function names, class names
- All file names
- All configuration files

**NO EXCEPTIONS.**

Do not introduce Portuguese, Spanish, or any other language into any part of this codebase.

## Project Identity

**Project Name:** Port-SotC-Mobile

**Repository:** https://github.com/zoddrun/Port-SotC-Mobile.git

**Objective:** Native Android port of Shadow of the Colossus (PS2) through static recompilation, not emulation.

## Agent Session Recovery Procedure

When resuming work on this project (new session, lost context, or recovery):

1. **Read `docs/agent/PROJECT_STATE.md` first** — current milestone status and immediate next actions.
2. **Read `docs/agent/NEXT_STEPS.md`** — the ordered work queue.
3. **Read `docs/agent/BLOCKERS.md`** and `docs/agent/KNOWN_ISSUES.md` — active constraints before touching anything.
4. **Read `docs/agent/CODEBASE_MAP.md`** for file locations; `docs/agent/TECHNICAL_DECISIONS.md` for prior rationale (never re-litigate a logged decision without new evidence).
5. **Append to `docs/agent/SESSION_LOG.md`** at the end of every work session, and update `PROJECT_STATE.md` whenever a milestone status changes.
6. **Protected asset:** `original iso/` holds the developer's private disc image — never commit, modify, move, or delete it.
7. **Git discipline:** commit at meaningful milestones in English; never push to `origin` without explicit developer authorization; never force-push or reset.

## Core Engineering Principles

### 1. Native Execution Requirement

This project MUST implement native ARM64 execution of the game logic. It MUST NOT:
- Use PCSX2, AetherSX2, NetherSX2, or any PS2 emulator as a backend
- Use RetroArch cores
- Use Wine or compatibility layers
- Implement an emulator launcher disguised as a port
- Use remote game streaming

### 2. Real Implementation Only

- Never substitute actual implementation with pseudocode
- Never use placeholder functions without documenting the blocker
- Never use mock implementations
- Never claim a feature works without testing it
- Never invent APIs or dependencies

### 3. Research Before Implementation

Before writing major code, investigate:
- Existing upstream projects (Celosia, PS2Recomp)
- Current technical architectures
- Proven solutions
- Licensing requirements
- Android compatibility

### 4. Incremental Development

- Make changes in small, testable increments
- Test after each significant change
- Commit working code frequently
- Fix build regressions immediately
- Never mark incomplete features as done

### 5. Testing Requirements

- Write automated unit tests
- Write integration tests
- Perform actual gameplay verification
- Test on real Android hardware when possible
- Document test results accurately

### 6. Android-Specific Rules

- Use Android NDK for native code
- Target ARM64-v8a architecture
- Use Kotlin for Android application layer
- Handle Android lifecycle properly
- Implement proper touch controls
- Support external controllers
- Follow Android UI/UX guidelines

### 7. Performance Requirements

- Profile before optimizing
- Measure performance changes
- Document optimization rationale
- Never claim improvements without measurements
- Design for mobile thermal constraints

### 8. Legal and Licensing

- Never include copyrighted game content
- Never distribute PS2 BIOS files
- Never redistribute game ISOs
- Respect all third-party licenses
- Document all dependencies
- Maintain proper attribution

### 9. Code Quality

- Keep platform-specific code isolated
- Maintain clear module boundaries
- Write meaningful comments
- Use consistent naming conventions (English only)
- Handle errors properly
- Never hide crashes
- Provide structured logging

### 10. Documentation Requirements

- Keep documentation synchronized with code
- Document technical decisions
- Explain architectural changes
- Maintain compatibility matrix
- Document known issues
- Provide build instructions

## Strict Prohibitions

1. Never use non-English text in any project component
2. Never fake native execution by hiding an emulator
3. Never remove working code without understanding it
4. Never ignore compiler warnings about safety or concurrency
5. Never include game assets in the repository
6. Never violate third-party licenses
7. Never claim gameplay compatibility without verification
8. Never deploy untested code to release branches

## Definition of Success

The project is successful when:
- It installs on real Android devices
- It uses native ARM64 execution (verified)
- It does NOT use a PS2 emulator
- It imports and validates supported ISO files
- It renders using native Android graphics
- It outputs audio through Android audio system
- It supports touch controls and gamepads
- It handles Android lifecycle correctly
- It preserves game saves
- It has been tested in actual gameplay
- All code and documentation are in English

## Development Phases

Follow the prescribed development sequence:
- PHASE 0: Technical Audit
- PHASE 1: Native Foundation
- PHASE 2: Engine Port
- PHASE 3: ISO Support
- PHASE 4: Gameplay
- PHASE 5: Controls
- PHASE 6: Optimization
- PHASE 7: Application Polish
- PHASE 8: Release Validation

## Questions and Decisions

When facing technical decisions:
1. Research existing solutions first
2. Compare alternatives
3. Explain trade-offs
4. Document the rationale
5. Choose the most reliable approach

When encountering blockers:
1. Document exactly what is blocking progress
2. Continue all independent work
3. Never invent fake solutions
4. Be transparent about limitations

## Git Practices

- Write clear commit messages in English
- Make atomic commits
- Never commit generated binaries
- Never commit user data or ISOs
- Never commit API keys or secrets
- Keep clean history
- Tag major milestones

---

**Remember: Build actual software, not documentation pretending the software exists.**
