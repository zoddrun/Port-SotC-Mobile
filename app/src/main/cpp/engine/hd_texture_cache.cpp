#include "hd_texture_cache.h"

#include <cstring>

uint64_t sotcHdFnv1a(const void* data, size_t size) {
    const auto* bytes = static_cast<const unsigned char*>(data);
    uint64_t hash = 14695981039346656037ULL;
    for (size_t i = 0; i < size; ++i) {
        hash ^= static_cast<uint64_t>(bytes[i]);
        hash *= 1099511628211ULL;
    }
    return hash;
}

bool HdTextureCache::matches(GLuint texture, const Key& key) const {
    const auto found = entries_.find(texture);
    if (found == entries_.end()) return false;
    const Key& previous = found->second;
    return previous.width == key.width &&
        previous.height == key.height &&
        previous.internalFormat == key.internalFormat &&
        previous.format == key.format &&
        previous.type == key.type &&
        previous.pixelHash == key.pixelHash;
}

void HdTextureCache::remember(GLuint texture, const Key& key) {
    entries_[texture] = key;
}

void HdTextureCache::forget(GLuint texture) {
    entries_.erase(texture);
}

void HdTextureCache::clear() {
    entries_.clear();
}
