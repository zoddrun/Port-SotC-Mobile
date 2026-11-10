#!/usr/bin/env python3
"""Inspect and validate the developer's original Shadow of the Colossus disc image.

Reads the private development image from ``original iso/`` at the repository
root, validates it the same way the Android application's DiscValidator does,
computes its SHA-256, and writes a JSON report. The image is opened read-only
and is never modified.

Validation performed (mirrors app/src/main/java/com/portsotc/mobile/disc):
  1. ISO9660 primary volume descriptor and recursive directory records,
     including multi-extent files and multi-sector directories.
  2. SYSTEM.CNF presence and BOOT2 boot path.
  3. Boot executable exists and is a 32-bit little-endian MIPS ET_EXEC ELF
     with loadable program headers.
  4. The disc serial maps to a release this port supports (SCUS-97472).
  5. Every required XFF module exists and starts with the ``xff2`` magic.

Usage:
    python tools/inspect_original_iso.py [--directory "original iso"] [--json-only]

Standard library only; runs on Python 3.8 or later on Windows, Linux, and WSL.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

SECTOR_SIZE = 2048
VOLUME_DESCRIPTOR_START = 16
VOLUME_DESCRIPTOR_LIMIT = 64
DIRECTORY_RECORD_HEADER = 33
MAX_DIRECTORY_DEPTH = 32
MAX_DIRECTORY_RECORDS = 200_000

EXPECTED_SERIAL = "SCUS-97472"
EXPECTED_BOOT_FILE = "SCUS_974.72"
REQUIRED_MODULES = ("KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF")
MODULE_MAGIC = b"xff2"
HASH_CHUNK = 1 << 20

EM_MIPS = 8
ET_EXEC = 2
PT_LOAD = 1
ELF_HEADER_SIZE = 52
ELF_PROGRAM_HEADER_SIZE = 32


class InspectionError(Exception):
    """Raised when the image cannot be parsed at all."""


def u16(data: bytes, offset: int) -> int:
    return data[offset] | (data[offset + 1] << 8)


def u32(data: bytes, offset: int) -> int:
    return data[offset] | (data[offset + 1] << 8) | (data[offset + 2] << 16) | (data[offset + 3] << 24)


@dataclass
class Extent:
    lba: int
    length: int


@dataclass
class IsoFile:
    path: str
    name: str
    extents: List[Extent]
    size: int
    is_directory: bool


@dataclass
class Issue:
    severity: str  # "info" | "warning" | "error"
    code: str
    message: str


@dataclass
class VolumeDescriptor:
    system_identifier: str
    volume_identifier: str
    publisher_identifier: str
    application_identifier: str
    logical_block_size: int
    volume_space_size: int
    root_extent_lba: int
    root_size: int


class Iso9660Image:
    """Read-only ISO9660 reader for PlayStation 2 DVD images (2048-byte sectors)."""

    def __init__(self, path: Path):
        self.path = path
        self._file = open(path, "rb")
        self.image_size = path.stat().st_size
        if self.image_size < (VOLUME_DESCRIPTOR_START + 1) * SECTOR_SIZE:
            raise InspectionError(
                "File is too small to contain an ISO9660 volume descriptor"
            )
        self.volume = self._read_volume_descriptor()
        if self.volume.logical_block_size != SECTOR_SIZE:
            raise InspectionError(
                "Unsupported ISO9660 logical block size "
                f"{self.volume.logical_block_size}; expected {SECTOR_SIZE}"
            )
        self.entries: Dict[str, IsoFile] = {}
        root = IsoFile(
            path="/",
            name="",
            extents=[Extent(self.volume.root_extent_lba, self.volume.root_size)],
            size=self.volume.root_size,
            is_directory=True,
        )
        self.entries["/"] = root
        self._read_directory(root, 0, set())

    # -- public API ---------------------------------------------------------

    def sector_count(self) -> int:
        return self.image_size // SECTOR_SIZE

    def find(self, path: str) -> Optional[IsoFile]:
        return self.entries.get(normalize_path(path))

    def read(self, entry: IsoFile, max_bytes: Optional[int] = None) -> bytes:
        if entry.is_directory:
            raise InspectionError(f"{entry.path} is a directory")
        limit = entry.size if max_bytes is None else min(entry.size, max_bytes)
        output = bytearray(limit)
        written = 0
        for extent in entry.extents:
            if written >= limit:
                break
            self._file.seek(extent.lba * SECTOR_SIZE)
            chunk = self._file.read(min(extent.length, limit - written))
            output[written : written + len(chunk)] = chunk
            written += len(chunk)
        if written != limit:
            raise InspectionError(f"Unexpected end of file while reading {entry.path}")
        return bytes(output)

    def close(self) -> None:
        self._file.close()

    def __enter__(self) -> "Iso9660Image":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.close()

    # -- internals ----------------------------------------------------------

    def _read_volume_descriptor(self) -> VolumeDescriptor:
        buffer = bytearray(SECTOR_SIZE)
        sector = VOLUME_DESCRIPTOR_START
        while sector < VOLUME_DESCRIPTOR_LIMIT:
            self._file.seek(sector * SECTOR_SIZE)
            if self._file.readinto(buffer) != SECTOR_SIZE:
                raise InspectionError("Unable to read the volume descriptor sector")
            descriptor_type = buffer[0]
            identifier = buffer[1:6]
            if identifier != b"CD001":
                raise InspectionError(
                    f"Not an ISO9660 image: missing CD001 signature at sector {sector}"
                )
            if descriptor_type == 1:
                return VolumeDescriptor(
                    system_identifier=ascii_field(buffer, 8, 32),
                    volume_identifier=ascii_field(buffer, 40, 32),
                    publisher_identifier=ascii_field(buffer, 318, 128),
                    application_identifier=ascii_field(buffer, 574, 128),
                    logical_block_size=u16(buffer, 128),
                    volume_space_size=u32(buffer, 80),
                    root_extent_lba=u32(buffer, 156 + 2),
                    root_size=u32(buffer, 156 + 10),
                )
            if descriptor_type == 255:
                break
            sector += 1
        raise InspectionError("No ISO9660 primary volume descriptor found")

    def _read_directory(self, directory: IsoFile, depth: int, visited: set) -> None:
        if depth > MAX_DIRECTORY_DEPTH:
            raise InspectionError(f"Directory nesting exceeds depth at {directory.path}")
        if directory.size <= 0:
            return
        if directory.size > 0x7FFFFFFF:
            raise InspectionError(f"Directory too large to index: {directory.path}")
        data = self._read_extents(directory.extents, directory.size)

        offset = 0
        record_count = 0
        pending_name: Optional[str] = None
        pending_extents: List[Extent] = []
        pending_size = 0
        pending_flags = 0

        def flush() -> None:
            nonlocal pending_name, pending_extents, pending_size, pending_flags
            if pending_name is None or not pending_extents:
                pending_name = None
                pending_extents = []
                pending_size = 0
                pending_flags = 0
                return
            parent = directory.path
            path = f"/{pending_name}" if parent == "/" else f"{parent}/{pending_name}"
            self.entries[normalize_path(path)] = IsoFile(
                path=normalize_path(path),
                name=pending_name,
                extents=list(pending_extents),
                size=pending_size,
                is_directory=(pending_flags & 0x02) != 0,
            )
            pending_name = None
            pending_extents = []
            pending_size = 0
            pending_flags = 0

        while offset + DIRECTORY_RECORD_HEADER <= len(data):
            record_length = data[offset]
            if record_length == 0:
                offset = ((offset // SECTOR_SIZE) + 1) * SECTOR_SIZE
                continue
            if record_length < DIRECTORY_RECORD_HEADER or offset + record_length > len(data):
                raise InspectionError(f"Malformed directory record in {directory.path}")
            record_count += 1
            if record_count > MAX_DIRECTORY_RECORDS:
                raise InspectionError(f"Directory record limit exceeded in {directory.path}")

            flags = data[offset + 25]
            extent_lba = u32(data, offset + 2)
            extent_length = u32(data, offset + 10)
            name_length = data[offset + 32]
            name_start = offset + DIRECTORY_RECORD_HEADER
            name_end = name_start + name_length
            if name_end > offset + record_length or name_length <= 0:
                offset += record_length
                continue
            is_dot_entry = name_length == 1 and data[name_start] in (0, 1)
            if not is_dot_entry:
                raw_name = data[name_start:name_end].decode("ascii", errors="replace")
                name = raw_name.split(";")[0].strip()
                if name and name not in (".", "..") and is_safe_component(name):
                    if pending_name is not None and pending_name != name:
                        flush()
                    if pending_name is None:
                        pending_name = name
                        pending_flags = flags
                    pending_extents.append(Extent(extent_lba, extent_length))
                    pending_size += extent_length
                    if flags & 0x80 == 0:
                        flush()
            offset += record_length
        flush()

        children = [
            entry
            for entry in self.entries.values()
            if entry.is_directory
            and entry.path != "/"
            and parent_of(entry.path) == directory.path
        ]
        for child in children:
            if child.size <= 0:
                continue
            key = (child.extents[0].lba, child.name)
            if key in visited:
                continue
            visited.add(key)
            self._read_directory(child, depth + 1, visited)

    def _read_extents(self, extents: List[Extent], size: int) -> bytes:
        data = bytearray(size)
        written = 0
        for extent in extents:
            if written >= size:
                break
            self._file.seek(extent.lba * SECTOR_SIZE)
            chunk = self._file.read(min(extent.length, size - written))
            data[written : written + len(chunk)] = chunk
            written += len(chunk)
        return bytes(data)


def ascii_field(buffer: bytearray, offset: int, length: int) -> str:
    raw = buffer[offset : offset + length].decode("ascii", errors="replace")
    return raw.rstrip(" \x00")


def normalize_path(path: str) -> str:
    value = path.strip()
    if ":" in value:
        value = value.split(":", 1)[1]
    value = value.replace("\\", "/").split(";")[0]
    value = value.lstrip("/").rstrip("/")
    if not value:
        return "/"
    return "/" + value.upper()


def parent_of(path: str) -> str:
    index = path.rfind("/")
    return "/" if index <= 0 else path[:index]


def is_safe_component(name: str) -> bool:
    if name in (".", ".."):
        return False
    return not any(character in name for character in ("/", "\\")) and all(
        ord(character) >= 0x20 for character in name
    )


def parse_system_cnf(text: str) -> Dict[str, str]:
    entries: Dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip().lstrip("﻿")
        if not line or line.startswith(("#", ";")):
            continue
        separator = line.find("=")
        if separator <= 0:
            continue
        key = line[:separator].strip().upper()
        value = line[separator + 1 :].strip()
        if len(value) >= 2 and value.startswith('"') and value.endswith('"'):
            value = value[1:-1]
        if key:
            entries[key] = value
    return entries


def boot_file_of(entries: Dict[str, str]) -> Optional[str]:
    raw = entries.get("BOOT2") or entries.get("BOOT")
    if raw is None:
        return None
    value = raw.strip()
    if ":" in value:
        value = value.split(":", 1)[1]
    value = value.replace("\\", "/").split(";")[0].lstrip("/").strip()
    return value or None


def serial_from_boot_file(boot_file: str) -> Optional[str]:
    base = boot_file.rsplit("/", 1)[-1].upper()
    if (
        len(base) == 11
        and base[0:4].isalpha()
        and base[4] == "_"
        and base[5:8].isdigit()
        and base[8] == "."
        and base[9:11].isdigit()
    ):
        return f"{base[0:4]}-{base[5:8]}{base[9:11]}"
    return None


def inspect_ps2_elf(header: bytes) -> dict:
    checks = inspect_elf_bytes(header)
    return checks


def inspect_elf_bytes(data: bytes) -> dict:
    result = {"valid": False, "error": None, "entry_point": None, "loadable_segments": []}
    if len(data) < ELF_HEADER_SIZE:
        result["error"] = "ELF file is shorter than the 52-byte ELF32 header"
        return result
    if data[0:4] != b"\x7fELF":
        result["error"] = "Missing ELF magic"
        return result
    if data[4] != 1:
        result["error"] = f"ELF is not 32-bit (class {data[4]})"
        return result
    if data[5] != 1:
        result["error"] = f"ELF is not little-endian (encoding {data[5]})"
        return result
    elf_type = u16(data, 16)
    if elf_type != ET_EXEC:
        result["error"] = f"ELF type {elf_type} is not ET_EXEC"
        return result
    machine = u16(data, 18)
    if machine != EM_MIPS:
        result["error"] = f"ELF machine {machine} is not EM_MIPS"
        return result
    if u32(data, 20) != 1:
        result["error"] = "Unsupported ELF version field"
        return result
    entry = u32(data, 24)
    if entry == 0:
        result["error"] = "ELF entry point is zero"
        return result
    phoff = u32(data, 28)
    phentsize = u16(data, 42)
    phnum = u16(data, 44)
    if phnum <= 0 or phnum > 64:
        result["error"] = f"ELF has an invalid program header count ({phnum})"
        return result
    if phentsize != ELF_PROGRAM_HEADER_SIZE:
        result["error"] = f"ELF program header size {phentsize} does not match ELF32"
        return result
    segments = []
    for index in range(phnum):
        base = phoff + index * phentsize
        if base + ELF_PROGRAM_HEADER_SIZE > len(data):
            result["error"] = "ELF program headers extend past the inspected data"
            return result
        segment_type = u32(data, base)
        if segment_type != PT_LOAD:
            continue
        segments.append(
            {
                "index": index,
                "fileOffset": u32(data, base + 4),
                "virtualAddress": u32(data, base + 8),
                "fileSize": u32(data, base + 16),
                "memorySize": u32(data, base + 20),
                "flags": u32(data, base + 24),
            }
        )
    if not segments:
        result["error"] = "ELF contains no loadable program segment"
        return result
    result["valid"] = True
    result["entry_point"] = entry
    result["loadable_segments"] = segments
    return result


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        while True:
            chunk = handle.read(HASH_CHUNK)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def find_candidate_images(directory: Path) -> List[Path]:
    if not directory.is_dir():
        return []
    suffixes = {".iso", ".bin", ".img"}
    candidates = [
        entry
        for entry in directory.iterdir()
        if entry.is_file() and entry.suffix.lower() in suffixes
    ]
    return sorted(candidates, key=lambda entry: unicodedata.normalize("NFC", entry.name))


def inspect_image(image_path: Path) -> dict:
    issues: List[Issue] = []
    report: dict = {
        "file": str(image_path),
        "fileName": image_path.name,
        "fileSizeBytes": image_path.stat().st_size,
    }

    try:
        image = Iso9660Image(image_path)
    except InspectionError as error:
        issues.append(Issue("error", "INVALID_ISO", str(error)))
        report["valid"] = False
        report["issues"] = [issue.__dict__ for issue in issues]
        return report

    with image:
        report["volume"] = {
            "systemIdentifier": image.volume.system_identifier,
            "volumeIdentifier": image.volume.volume_identifier,
            "publisherIdentifier": image.volume.publisher_identifier,
            "applicationIdentifier": image.volume.application_identifier,
            "logicalBlockSize": image.volume.logical_block_size,
            "declaredSectors": image.volume.volume_space_size,
            "imageSectors": image.sector_count(),
        }
        declared_bytes = image.volume.volume_space_size * SECTOR_SIZE
        if image.volume.volume_space_size <= 0:
            issues.append(Issue("error", "INVALID_VOLUME_SIZE", "ISO9660 volume declares no sectors"))
        if declared_bytes > image.image_size:
            issues.append(
                Issue(
                    "error",
                    "TRUNCATED_IMAGE",
                    f"Volume declares {image.volume.volume_space_size} sectors but the image "
                    f"contains {image.sector_count()}; the dump is truncated",
                )
            )
        elif image.image_size > declared_bytes + SECTOR_SIZE:
            issues.append(
                Issue(
                    "warning",
                    "TRAILING_DATA",
                    f"Image contains {image.image_size - declared_bytes} bytes beyond the "
                    "declared ISO9660 volume",
                )
            )

        system_cnf = image.find("SYSTEM.CNF")
        if system_cnf is None:
            issues.append(
                Issue(
                    "error",
                    "MISSING_SYSTEM_CNF",
                    "SYSTEM.CNF is missing from the disc root; this is not a PlayStation 2 game disc",
                )
            )
            report["valid"] = False
            report["issues"] = [issue.__dict__ for issue in issues]
            return report

        try:
            cnf_text = image.read(system_cnf, 64 * 1024).decode("ascii", errors="replace")
            cnf_entries = parse_system_cnf(cnf_text)
        except InspectionError as error:
            issues.append(Issue("error", "UNREADABLE_SYSTEM_CNF", f"Unable to read SYSTEM.CNF: {error}"))
            report["valid"] = False
            report["issues"] = [issue.__dict__ for issue in issues]
            return report

        report["systemCnf"] = cnf_entries
        boot_file = boot_file_of(cnf_entries)
        report["bootFile"] = boot_file
        if not boot_file:
            issues.append(
                Issue(
                    "error",
                    "INVALID_SYSTEM_CNF",
                    "SYSTEM.CNF does not declare a BOOT2 or BOOT executable",
                )
            )
            report["valid"] = False
            report["issues"] = [issue.__dict__ for issue in issues]
            return report

        serial = serial_from_boot_file(boot_file)
        report["serial"] = serial
        if serial is None:
            issues.append(
                Issue(
                    "error",
                    "INVALID_SERIAL",
                    f"Unable to derive a disc serial from boot executable name `{boot_file}`",
                )
            )
        elif serial != EXPECTED_SERIAL:
            issues.append(
                Issue(
                    "error",
                    "UNSUPPORTED_SERIAL",
                    f"Disc serial {serial} is not supported; this port targets {EXPECTED_SERIAL}",
                )
            )
        elif boot_file.upper() != EXPECTED_BOOT_FILE.upper():
            issues.append(
                Issue(
                    "error",
                    "BOOT_FILE_MISMATCH",
                    f"SYSTEM.CNF boots `{boot_file}` but {serial} requires `{EXPECTED_BOOT_FILE}`",
                )
            )

        boot_entry = image.find(boot_file)
        if boot_entry is None:
            issues.append(
                Issue("error", "MISSING_BOOT_ELF", f"Boot executable `{boot_file}` is missing")
            )
            report["bootElf"] = None
        else:
            elf_report = inspect_elf_bytes(image.read(boot_entry, 1 << 20))
            report["bootElf"] = {
                "path": boot_entry.path,
                "sizeBytes": boot_entry.size,
                "valid": elf_report["valid"],
                "error": elf_report["error"],
                "entryPoint": elf_report["entry_point"],
                "loadableSegments": elf_report["loadable_segments"],
            }
            if not elf_report["valid"]:
                issues.append(
                    Issue(
                        "error",
                        "INVALID_BOOT_ELF",
                        f"Boot executable `{boot_file}` is not a valid PlayStation 2 ELF: "
                        f"{elf_report['error']}",
                    )
                )
            else:
                loadable = sum(segment["memorySize"] for segment in elf_report["loadable_segments"])
                if loadable == 0:
                    issues.append(
                        Issue(
                            "error",
                            "EMPTY_BOOT_ELF",
                            f"Boot executable `{boot_file}` contains no loadable memory",
                        )
                    )

        modules = []
        for module_name in REQUIRED_MODULES:
            entry = image.find(module_name)
            if entry is None:
                issues.append(
                    Issue(
                        "error",
                        "MISSING_MODULE",
                        f"Required engine module `{module_name}` is missing from the disc",
                    )
                )
                continue
            magic_ok = image.read(entry, 4) == MODULE_MAGIC
            modules.append(
                {
                    "name": module_name,
                    "path": entry.path,
                    "sizeBytes": entry.size,
                    "magicOk": magic_ok,
                }
            )
            if not magic_ok:
                issues.append(
                    Issue(
                        "error",
                        "INVALID_MODULE_MAGIC",
                        f"Engine module `{module_name}` does not start with the xff2 magic",
                    )
                )
        report["modules"] = modules

        report["rootDirectory"] = sorted(
            (
                {"path": entry.path, "sizeBytes": entry.size, "directory": entry.is_directory}
                for entry in image.entries.values()
                if entry.path != "/" and parent_of(entry.path) == "/"
            ),
            key=lambda item: item["path"],
        )
        report["fileCount"] = sum(1 for entry in image.entries.values() if not entry.is_directory)
        report["totalDataBytes"] = sum(
            entry.size for entry in image.entries.values() if not entry.is_directory
        )

    if not any(issue.severity == "error" for issue in issues):
        issues.append(
            Issue(
                "info",
                "DISC_OK",
                "ISO9660 structure, boot ELF and engine modules verified",
            )
        )
    report["valid"] = not any(issue.severity == "error" for issue in issues)
    report["issues"] = [issue.__dict__ for issue in issues]
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--directory",
        default="original iso",
        help="Directory holding the developer's original disc image",
    )
    parser.add_argument(
        "--json-only",
        action="store_true",
        help="Print only the JSON report without the human-readable summary",
    )
    parser.add_argument(
        "--report",
        default="build/reports/original_iso.json",
        help="Where to write the JSON report",
    )
    args = parser.parse_args()

    repository_root = Path(__file__).resolve().parent.parent
    image_directory = Path(args.directory)
    if not image_directory.is_absolute():
        image_directory = repository_root / image_directory

    if not image_directory.is_dir():
        print(f"ERROR: The directory {image_directory} does not exist.")
        print("Place the legally obtained original disc image in that directory.")
        return 2

    candidates = find_candidate_images(image_directory)
    if not candidates:
        print(f"ERROR: No disc images found in {image_directory}.")
        return 2

    reports = []
    overall_valid = False
    for candidate in candidates:
        if not args.json_only:
            print(f"Inspecting: {candidate.name} ({candidate.stat().st_size / (1 << 30):.2f} GiB)")
        report = inspect_image(candidate)
        report["sha256"] = sha256_of(candidate)
        reports.append(report)
        overall_valid = overall_valid or report["valid"]

    now = __import__("datetime").datetime.now(__import__("datetime").timezone.utc)
    result = {"generatedAtUtc": now.isoformat(),
              "imageCount": len(reports), "anyValid": overall_valid, "images": reports}

    report_path = Path(args.report)
    if not report_path.is_absolute():
        report_path = repository_root / report_path
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(result, indent=2), encoding="utf-8")

    if not args.json_only:
        for report in reports:
            print()
            print(f"=== {report['fileName']} ===")
            print(f"  size      {report['fileSizeBytes'] / (1 << 30):.2f} GiB")
            print(f"  sha256    {report['sha256']}")
            print(f"  serial    {report.get('serial') or '-'}")
            print(f"  boot      {report.get('bootFile') or '-'}")
            volume = report.get("volume", {})
            print(f"  volume    {volume.get('volumeIdentifier', '-')} "
                  f"({volume.get('declaredSectors', '?')} sectors declared, "
                  f"{volume.get('imageSectors', '?')} present)")
            boot_elf = report.get("bootElf") or {}
            if boot_elf:
                print(f"  boot ELF  {boot_elf['sizeBytes'] / (1 << 10):.0f} KiB, "
                      f"entry 0x{boot_elf.get('entryPoint', 0):08X}, "
                      f"{len(boot_elf.get('loadableSegments', []))} loadable segments, "
                      f"{'VALID' if boot_elf['valid'] else 'INVALID'}")
            for module in report.get("modules", []):
                print(f"  module    {module['name']}: "
                      f"{module['sizeBytes'] / (1 << 20):.1f} MiB, "
                      f"magic {'ok' if module['magicOk'] else 'INVALID'}")
            print(f"  files     {report.get('fileCount', 0)} entries, "
                  f"{report.get('totalDataBytes', 0) / (1 << 20):.1f} MiB of file data")
            for issue in report["issues"]:
                print(f"  [{issue['severity'].upper()}] {issue['code']}: {issue['message']}")
            print(f"  RESULT    {'COMPATIBLE' if report['valid'] else 'NOT COMPATIBLE'}")
        print()
        print(f"JSON report written to {report_path}")

    return 0 if overall_valid else 1


if __name__ == "__main__":
    sys.exit(main())
