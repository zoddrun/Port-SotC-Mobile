#!/usr/bin/env python3
"""Verify every native library packaged in an APK.

This is a host-side, no-device check. It complements verify-runner.sh, which
inspects only the runner: this script walks the APK and checks every .so entry
for ELF class, ABI, ELF type, 16 KB LOAD alignment, dynamic dependencies and
ZIP storage/alignment. It never modifies the APK.

Usage: python3 tools/android-integration/verify-apk.py [path-to-apk]
Default: app/build/outputs/apk/debug/app-debug.apk
"""

from __future__ import annotations

import struct
import sys
import zipfile
from pathlib import Path

DEFAULT_APK = Path("app/build/outputs/apk/debug/app-debug.apk")

ABI_MACHINES = {
    "arm64-v8a": 183,   # EM_AARCH64
    "x86_64": 62,       # EM_X86_64
}

# Libraries provided by Android itself or shipped inside the APK. Anything else
# is a missing dependency and must fail the check.
SYSTEM_LIBRARIES = {
    "libc.so", "libdl.so", "libm.so", "liblog.so", "libandroid.so",
    "libEGL.so", "libGLESv2.so", "libGLESv3.so", "libaaudio.so",
    "libOpenSLES.so", "libz.so", "libc++_shared.so", "libstdc++.so",
    "libbinder_ndk.so", "libnativewindow.so", "libsync.so", "libgui.so",
    "libutils.so", "libcutils.so", "libbase.so", "libmedia.so",
    "libhardware.so", "libvulkan.so", "libjnigraphics.so", "libicuuc.so",
    "libicui18n.so", "libprotobuf-cpp-lite.so", "libclang_rt.asan-aarch64-android.so",
    "libcompiler_rt.so", "libunwindstack.so", "libziparchive.so",
}

PT_LOAD = 1
SHT_DYNAMIC = 6
SHT_NOBITS = 8
DT_NULL = 0
DT_NEEDED = 1

failures = 0
warnings = 0


def fail(message: str) -> None:
    global failures
    failures += 1
    print(f"FAIL  {message}")


def warn(message: str) -> None:
    global warnings
    warnings += 1
    print(f"WARN  {message}")


def ok(message: str) -> None:
    print(f"PASS  {message}")


def parse_elf(data: bytes, name: str) -> tuple[int, int, int, list[int], list[str], str]:
    """Return (elf_class, elf_type, machine, load_alignments, needed, build_id)."""
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    elf_class = data[4]
    if elf_class != 2:
        raise ValueError("not ELF64")
    elf_type, machine = struct.unpack_from("<HH", data, 16)
    phoff, shoff = struct.unpack_from("<QQ", data, 32)
    phentsize, phnum = struct.unpack_from("<HH", data, 54)
    shentsize, shnum = struct.unpack_from("<HH", data, 58)

    load_alignments: list[int] = []
    if phnum and phentsize >= 56:
        for index in range(phnum):
            offset = phoff + index * phentsize
            if offset + 56 > len(data):
                break
            p_type = struct.unpack_from("<I", data, offset)[0]
            if p_type == PT_LOAD:
                load_alignments.append(struct.unpack_from("<Q", data, offset + 48)[0])

    sections: list[tuple[int, int, int, int, int]] = []
    if shoff and shnum and shentsize >= 64:
        for index in range(shnum):
            offset = shoff + index * shentsize
            if offset + 64 > len(data):
                break
            sh_type = struct.unpack_from("<I", data, offset + 4)[0]
            sh_offset = struct.unpack_from("<Q", data, offset + 24)[0]
            sh_size = struct.unpack_from("<Q", data, offset + 32)[0]
            sh_link = struct.unpack_from("<I", data, offset + 40)[0]
            sections.append((sh_type, sh_offset, sh_size, sh_link, sh_offset))

    needed: list[str] = []
    for sh_type, sh_offset, sh_size, sh_link, _ in sections:
        if sh_type != SHT_DYNAMIC:
            continue
        if sh_link >= len(sections):
            continue
        _, string_offset, string_size, _, _ = sections[sh_link]
        strings = data[string_offset:string_offset + string_size]
        position = sh_offset
        end = sh_offset + sh_size
        while position + 16 <= end and position + 16 <= len(data):
            tag, value = struct.unpack_from("<qQ", data, position)
            position += 16
            if tag == DT_NULL:
                break
            if tag == DT_NEEDED and value < len(strings):
                terminator = strings.find(b"\0", value)
                if terminator < 0:
                    terminator = len(strings)
                needed.append(strings[value:terminator].decode("utf-8", "replace"))
        break

    build_id = ""
    for sh_type, sh_offset, sh_size, _, _ in sections:
        if sh_type != 7:  # SHT_NOTE
            continue
        notes = data[sh_offset:sh_offset + sh_size]
        cursor = 0
        while cursor + 12 <= len(notes):
            name_size, desc_size, note_type = struct.unpack_from("<III", notes, cursor)
            cursor += 12
            name_start = cursor
            cursor += (name_size + 3) & ~3
            desc_start = cursor
            cursor += (desc_size + 3) & ~3
            if name_size >= 4 and notes[name_start:name_start + 4] == b"GNU\0" and note_type == 3:
                build_id = notes[desc_start:desc_start + desc_size].hex()
                break
        if build_id:
            break

    return elf_class, elf_type, machine, load_alignments, needed, build_id


def data_offset(apk_path: Path, info: zipfile.ZipInfo) -> int:
    with apk_path.open("rb") as stream:
        stream.seek(info.header_offset)
        header = stream.read(30)
        if len(header) != 30 or header[:4] != b"PK\x03\x04":
            raise ValueError("bad local file header")
        name_length = struct.unpack_from("<H", header, 26)[0]
        extra_length = struct.unpack_from("<H", header, 28)[0]
    return info.header_offset + 30 + name_length + extra_length


def main() -> int:
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_APK
    if not apk.is_file():
        fail(f"missing APK: {apk}")
        return 1

    print(f"apk      {apk}")
    print(f"size     {apk.stat().st_size} bytes")

    with zipfile.ZipFile(apk) as archive:
        libraries = [info for info in archive.infolist() if info.filename.endswith(".so")]
        if not libraries:
            fail("APK contains no native libraries")
            return 1

        names = {info.filename for info in libraries}
        abis = sorted({info.filename.split("/")[1] for info in libraries if info.filename.startswith("lib/")})
        print(f"abis     {' '.join(abis)}")
        if "arm64-v8a" not in abis:
            fail("APK has no arm64-v8a native libraries")
        if "x86_64" not in abis:
            warn("APK has no x86_64 development libraries")

        runner = "lib/arm64-v8a/libps2EntryRunner.so"
        if runner not in names:
            fail(f"missing gameplay runner: {runner}")

        for info in libraries:
            print(f"\nlibrary  {info.filename}")
            if info.compress_type != zipfile.ZIP_STORED:
                fail(f"{info.filename}: native library is compressed (useLegacyPackaging must be false)")
                continue
            offset = data_offset(apk, info)
            print(f"zipdata  offset {offset} (mod 16384 = {offset % 16384})")
            if offset % 16384 != 0:
                if offset % 4096 == 0:
                    warn(f"{info.filename}: ZIP data is 4 KB but not 16 KB aligned")
                else:
                    fail(f"{info.filename}: ZIP data is not page aligned")

            data = archive.read(info)
            print(f"size     {len(data)} bytes")
            try:
                elf_class, elf_type, machine, alignments, needed, build_id = parse_elf(data, info.filename)
            except ValueError as error:
                fail(f"{info.filename}: {error}")
                continue

            if elf_class != 2:
                fail(f"{info.filename}: not ELF64")
            if elf_type != 3:
                fail(f"{info.filename}: not ET_DYN")
            parts = info.filename.split("/")
            expected = ABI_MACHINES.get(parts[1]) if len(parts) >= 3 else None
            if expected is not None and machine != expected:
                fail(f"{info.filename}: machine {machine}, expected {expected} for {parts[1]}")
            else:
                ok(f"ABI machine {machine}")

            print(f"align    {' '.join(str(value) for value in sorted(set(alignments)))}")
            if not alignments:
                fail(f"{info.filename}: no PT_LOAD segments")
            elif any(value < 16384 for value in alignments):
                fail(f"{info.filename}: LOAD alignment below 16384")
            else:
                ok("16 KB LOAD alignment")

            print(f"needed   {' '.join(sorted(needed))}")
            for dependency in needed:
                if dependency in SYSTEM_LIBRARIES or dependency in names:
                    continue
                if dependency.startswith("lib") and any(name.endswith("/" + dependency) for name in names):
                    continue
                fail(f"{info.filename}: unresolved dependency {dependency}")
            if build_id:
                print(f"buildid  {build_id}")

    print(f"\nAPK_NATIVE_VERIFY_{'OK' if failures == 0 else 'FAILED'}")
    print(f"summary  {failures} failures, {warnings} warnings")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
