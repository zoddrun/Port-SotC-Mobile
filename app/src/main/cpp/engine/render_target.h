#pragma once

#include <GLES2/gl2.h>

#include "hd_texture_cache.h"

/**
 * Offscreen colour target used to implement the internal resolution scale.
 * The scene is rendered at the scaled size and then blitted to the window with
 * either nearest or linear filtering.
 *
 * Written against GLSL ES 1.00 and OpenGL ES 2.0 entry points only, so it runs
 * unchanged on an ES 2 context and on an ES 3 context.
 */
class RenderTarget {
public:
    RenderTarget() = default;
    ~RenderTarget();

    RenderTarget(const RenderTarget&) = delete;
    RenderTarget& operator=(const RenderTarget&) = delete;

    bool ensure(int width, int height);
    void bind() const;
    void present(int surfaceWidth, int surfaceHeight, bool linear);
    void shutdown();

    int width() const { return width_; }
    int height() const { return height_; }
    bool valid() const { return framebuffer_ != 0 && texture_ != 0 && program_ != 0; }
    GLuint texture() const { return texture_; }
    GLuint framebuffer() const { return framebuffer_; }

private:
    GLuint framebuffer_ = 0;
    GLuint texture_ = 0;
    HdTextureCache uploadCache_;
    GLuint program_ = 0;
    GLuint vertexBuffer_ = 0;
    GLint positionLocation_ = -1;
    int width_ = 0;
    int height_ = 0;
};
