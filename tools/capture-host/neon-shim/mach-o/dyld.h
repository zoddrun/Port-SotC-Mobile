// mach-o/dyld.h for non-Apple Unix hosts (celosia capture build).
//
// The vendored override game/overrides/decomp_lines67.cpp (a cache-miss
// diagnostic, "SOTC_LINES67=1, Mac only" per its own header comment) guards
// its #include <mach-o/dyld.h> only with #if !defined(__vita__), so it also
// compiles on Linux/Android — where the header does not exist. The file's
// sibling diagnostics (decomp_readcensus.cpp, sotc_prof.cpp) use the proper
// #if defined(__APPLE__) guard; this shim covers the one that does not,
// without editing vendored sources (same policy as the arm_neon.h shim, D13).
//
// API provided: _dyld_get_image_header, matching the macOS declaration
//     const struct mach_header *_dyld_get_image_header(uint32_t image_index);
// The vendored code calls it exactly once — image_index 0 — and only uses the
// result as an integer (the main executable's image address, used to turn
// return addresses into stable image-relative offsets). The Linux analogue is
// derived from dl_iterate_phdr, whose first entry is the main program
// (glibc link.h documents this): the segment that maps file offset 0 (the ELF
// header) starts at p_offset == 0, so the image header sits at
// dlpi_addr + p_vaddr - p_offset.
//
// Requirements: a Unix loader exposing dl_iterate_phdr (glibc, musl, bionic).
// C++ only (uses a lambda); every includer in the vendored tree is C++.
// ARM64 Android builds must NOT use the x86 neon-shim directory, but they
// need this file too — see docs/agent/ (Milestone 4 note).
#ifndef PS2X_MACH_O_DYLD_SHIM_H
#define PS2X_MACH_O_DYLD_SHIM_H

#include <link.h>   // dl_iterate_phdr, struct dl_phdr_info, ElfW, PT_LOAD
#include <stdint.h>

/* Opaque stand-in for the Mach-O image header: the vendored code only uses
 * its address, so an incomplete type carries the same information. */
struct mach_header;

inline const struct mach_header *_dyld_get_image_header(uint32_t image_index)
{
    /* Only the main executable (index 0) has a Linux analogue here; the
     * vendored code passes no other index. */
    if (image_index != 0u)
        return nullptr;

    const void *header = nullptr;
    /* The callback's return value stops the iteration (dl_iterate_phdr
     * returns it), so the walk ends after the main program. */
    dl_iterate_phdr([](struct dl_phdr_info *info, size_t, void *data) -> int {
        for (unsigned i = 0; i < info->dlpi_phnum; ++i)
        {
            const ElfW(Phdr) &ph = info->dlpi_phdr[i];
            if (ph.p_type == PT_LOAD && ph.p_offset == 0)
            {
                *static_cast<const void **>(data) =
                    reinterpret_cast<const void *>(info->dlpi_addr + ph.p_vaddr - ph.p_offset);
                return 1;
            }
        }
        return 0;
    }, &header);
    return static_cast<const struct mach_header *>(header);
}

#endif // PS2X_MACH_O_DYLD_SHIM_H
