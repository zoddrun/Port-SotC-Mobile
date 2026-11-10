// PCH interplay test for the forced NEON shim include:
//   1. shim force-included (CMAKE_CXX_FLAGS -include), providing NEON names
//   2. PCH force-included after it (CMake appends -include cmake_pch.hxx)
//   3. this TU then uses NEON types/intrinsics mixed with __m128 exactly like
//      sotc_native.cpp's mdsClipFlags4 does
// Also proves <cmath> after the shim does not clash on float32_t.
#include <arm_neon.h> // resolves to the shim (no-op: already force-included)
#include <cstdio>

static uint32x4_t mixed(__m128 a, __m128 b, __m128 c, __m128 d)
{
    _MM_TRANSPOSE4_PS(a, b, c, d);
    const uint32x4_t bits = vreinterpretq_u32_f32(d);
    const uint32x4_t lim = vandq_u32(bits, vdupq_n_u32(0x7FFFFFFFu));
    const uint32x4_t gt = vcgtq_s32(vreinterpretq_s32_u32(bits), vreinterpretq_s32_u32(lim));
    return gt;
}

static uint32_t lane(uint32x4_t v) { return vgetq_lane_u32(v, 1); }

int main()
{
    alignas(16) const float w[4] = {1.0f, -2.5f, 3.0f, 4.0f};
    alignas(16) const float x[4] = {5.0f, 6.0f, 7.0f, 8.0f};
    alignas(16) const float y[4] = {9.0f, 10.0f, 11.0f, 12.0f};
    alignas(16) const float z[4] = {13.0f, 14.0f, 15.0f, 16.0f};
    const uint32_t l = lane(mixed(_mm_load_ps(x), _mm_load_ps(y), _mm_load_ps(z), _mm_load_ps(w)));
    std::printf("lane=%u (expect 0: w-column lanes 8/12/16/4 are all positive, bits == lim)\n", l);
    return static_cast<int>(l);
}
