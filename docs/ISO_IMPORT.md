# ISO Import (user-facing disc import)

> Status: **Implemented** (Milestone 2/3). Import produces the on-device game
> directory the future recompiled core will read through the ABI's
> `readGameFile` callback (see [CORE_ABI.md](CORE_ABI.md)).

The application never ships or downloads game content. Each user imports their
own legally obtained disc image through Android's Storage Access Framework.

## User flow

1. `ImportActivity` opens the system file picker (`ACTION_OPEN_DOCUMENT`) for a
   single ISO image.
2. The import runs with progress and cancel support
   (`GameImporter.Stage`: COPYING → VALIDATING → EXTRACTING → FINALIZING).
3. On failure the user sees the specific validation error; on success the game
   appears in the library (`MainActivity`) and `GameActivity` can be started.

## Staged import (`core/GameImporter.kt`)

A partially copied or partially validated image can never be mistaken for an
installed game:

1. **Copy** the SAF content URI into `files/import-cache/import-<uuid>.iso.part`
   (1 MiB chunks, progress every 4 MiB) while computing the SHA-256 digest.
   The display name from the provider is never trusted.
2. **Validate** the staged copy (`disc/DiscValidator.kt`) — structure only, no
   filename heuristics.
3. **Stage** `files/games/<serial>.tmp/`: move the verified image to
   `disc.iso`, extract the engine modules into `modules/`.
4. **Publish** atomically to `files/games/<serial>/`: the previous install is
   renamed to `<serial>.backup` and removed only after the new install passes
   the final consistency check; saves are carried over. Interrupted `.tmp`
   staging directories are recovered on the next import
   (`GameLibrary.recoverInterruptedImports`).

Installed layout (constants in `core/GameLibrary.kt`):

```
files/games/SCUS-97472/
├── manifest.properties    # serial, title, boot file, sizes, module list, format version
├── disc.iso               # the imported image (byte-identical to the source)
├── disc.sha256             # digest recorded during the copy
├── modules/                # KERNEL.XFF, MANAGER.XFF, GAMECORE.XFF
└── saves/                  # memory-card data, preserved across re-imports
```

## Validation (`disc/DiscValidator.kt`)

Never trusts the file name; every decision comes from the disc contents:

1. **ISO9660 structure** (`disc/Iso9660.kt`): primary volume descriptor
   (`CD001`), 2048-byte logical blocks, recursive directory-record parsing
   (multi-extent files and multi-sector directories included; Rock Ridge and
   Joliet intentionally not implemented — PS2 software uses plain ISO9660
   names). Raw 2352-byte CD images are rejected: the retail release is a DVD
   title. Depth (32) and record (200 000) limits guard against malformed discs.
2. **`SYSTEM.CNF`** present and readable, with a `BOOT2` executable path.
3. **Boot ELF** (`disc/Ps2Elf.kt`): exists, is a 32-bit little-endian MIPS
   `ET_EXEC` ELF, and has loadable memory.
4. **Serial support** (`disc/SupportedDiscs.kt` table): the serial derived
   from the boot file must match a supported release — currently only
   `SCUS-97472` (USA, boot file `SCUS_974.72`). The boot file must match the
   release table exactly.
5. **Engine modules**: every required module (`KERNEL.XFF`, `MANAGER.XFF`,
   `GAMECORE.XFF`) exists and begins with the `xff2` magic.
6. **Volume size sanity**: declared sectors vs. image size — a truncated image
   is an error; trailing bytes beyond the declared volume are a warning.

Every failure carries a machine code (`UNSUPPORTED_SERIAL`,
`INVALID_MODULE_MAGIC`, …) and a human message shown verbatim to the user.

## Relationship to the developer's private ISO

The `original iso/` copy used by the recompilation pipeline
([ORIGINAL_ISO.md](ORIGINAL_ISO.md)) is a separate, protected asset and is
never committed. On-device import is the same disc, re-validated end-to-end at
import time by the same criteria the pipeline used.
