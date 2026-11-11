# Original ISO (developer asset)

> Status: **Validated and protected** (2026-11-09).

## Location and identity

- The developer's legally obtained disc image lives in `original iso/` at the
  repository root.
- File: `SCUS_974.72.Shadow of the Colossus.iso` — 2.51 GiB.
- Game: Shadow of the Colossus, USA retail, serial `SCUS-97472`,
  boot executable `SCUS_974.72` (11 characters).
- SHA-256: `d945516ed6684e49989afd5dee9b79538a65de929484da7ff2b800f8f015eb37`.

## Validation

`tools/inspect_original_iso.py` (Python stdlib only) verifies the ISO9660
primary volume descriptor, `SYSTEM.CNF`, the boot executable's PS2 ELF header
and program segments, and the three XFF modules (`KERNEL`, `MANAGER`,
`GAMECORE` magic). The JSON report is written to
`build/reports/original_iso.json` (build output — never committed).

Notes from validation:

- The volume identifier is `"1"` — verified normal for this disc, not a sign of
  corruption.
- The boot filename is 11 characters, not 12: the serial derivation
  (`SCUS_974.72` → `SCUS-97472`) handles this (decision D3).

## Protection rules

- `/original iso/` is listed in `.gitignore` and verified with
  `git check-ignore`.
- The image is never modified, moved, or deleted.
- It is never committed, never packaged into the APK, and never uploaded to
  GitHub Releases or build services.
- Pipeline copies (for recompilation) are made into WSL ext4 outside the
  repository (`/root/sotc/source.iso`, hash-verified identical).

## What the ISO is used for

The recompilation pipeline (`docs/RECOMPILATION_PIPELINE.md`) extracts the disc
into `/root/sotc/disc` and statically recompiles the game code into native C++.
End users perform their own import through the app's SAF importer
(`docs/ISO_IMPORT.md` when written; current implementation: `disc/` + `game/`
packages under `app/src/main/java/com/portsotc/mobile/`).
