#pragma once

#include <GLES3/gl3.h>

/**
 * Offscreen colour target used to implement the internal resolution scale.
 * The scene is rendered at the scaled size and then blitted to the window with
 * either nearest or linear filtering.
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

private:
    GLuint framebuffer_ = 0;
    GLuint texture_ = 0;
    GLuint program_ = 0;
    GLuint vertexArray_ = 0;
    int width_ = 0;
    int height_ = 0;
};
