// Correctness test for the mach-o/dyld.h shim (non-Apple hosts):
// _dyld_get_image_header(0) must return the main executable's image header
// address — i.e. the memory there starts with the ELF magic 0x7F 'E' 'L' 'F'.
// Compiled in three shapes:
//   1. plain PIE (the runner's default layout)
//   2. -no-pie (ET_EXEC: dlpi_addr == 0, header at its link-time vaddr)
//   3. with the arm_neon.h shim force-included (the unity TU flag layout)
#include <mach-o/dyld.h>
#include <cstdio>
#include <cstring>

int main()
{
    const struct mach_header *h = _dyld_get_image_header(0);
    if (h == nullptr)
    {
        std::printf("FAIL: _dyld_get_image_header(0) returned null\n");
        return 1;
    }
    unsigned char magic[4];
    std::memcpy(magic, h, sizeof(magic));
    const bool ok = magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F';
    std::printf("header=%p magic=%02x %02x %02x %02x -> %s\n",
                static_cast<const void *>(h), magic[0], magic[1], magic[2], magic[3],
                ok ? "ELF_OK" : "NOT_ELF");
    // Non-zero index has no defined analogue: must not pretend otherwise.
    if (_dyld_get_image_header(1) != nullptr)
    {
        std::printf("FAIL: image_index != 0 must return null\n");
        return 1;
    }
    return ok ? 0 : 1;
}
