#pragma once

#include <GLES2/gl2.h>

#include <cstddef>
#include <cstdint>
#include <unordered_map>

/**
 * Small FNV-1a helper shared by the shader and texture caches. It is not a
 * cryptographic digest: it only needs to detect identical immutable uploads.
 */
uint64_t sotcHdFnv1a(const void* data, size_t size);

/**
 * Tracks the last immutable upload for each texture object. The diagnostics
 * renderer recreates its offscreen target on resize; identical resize requests
 * must not allocate the same storage repeatedly.
 */
class HdTextureCache {
public:
    struct Key {
        int width = 0;
        int height = 0;
        GLenum internalFormat = 0;
        GLenum format = 0;
        GLenum type = 0;
        uint64_t pixelHash = 0;
    };

    bool matches(GLuint texture, const Key& key) const;
    void remember(GLuint texture, const Key& key);
    void forget(GLuint texture);
    void clear();

private:
    std::unordered_map<GLuint, Key> entries_;
};
