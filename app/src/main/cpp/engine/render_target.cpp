#include "render_target.h"

#include "gl_util.h"
#include "log.h"

namespace {

// GLSL ES 1.00 works on both ES 2 and ES 3 contexts, so the same program runs
// on every driver. The fullscreen triangle replaces the gl_VertexID trick
// because that built-in only exists in ES 3.
constexpr const char* kVertexShader = R"(#version 100
attribute vec2 aPosition;
varying vec2 vUv;
void main() {
    vUv = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
)";

constexpr const char* kFragmentShader = R"(#version 100
precision mediump float;
varying vec2 vUv;
uniform sampler2D uTexture;
void main() {
    gl_FragColor = vec4(texture2D(uTexture, vUv).rgb, 1.0);
}
)";

// One triangle that covers the whole viewport: (-1,-1), (3,-1), (-1,3).
constexpr GLfloat kFullscreenTriangle[] = {
    -1.0f, -1.0f,
     3.0f, -1.0f,
    -1.0f,  3.0f
};

}  // namespace

RenderTarget::~RenderTarget() {
    shutdown();
}

bool RenderTarget::ensure(int width, int height) {
    if (width <= 0 || height <= 0) return false;
    if (program_ == 0) {
        program_ = sotcLinkProgram(kVertexShader, kFragmentShader);
        if (program_ == 0) {
            SOTC_LOGE("Unable to create resolution-scale program");
            return false;
        }
        positionLocation_ = glGetAttribLocation(program_, "aPosition");
        glGenBuffers(1, &vertexBuffer_);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer_);
        glBufferData(
            GL_ARRAY_BUFFER,
            sizeof(kFullscreenTriangle),
            kFullscreenTriangle,
            GL_STATIC_DRAW
        );
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    if (framebuffer_ != 0 && width == width_ && height == height_) {
        return true;
    }
    width_ = width;
    height_ = height;

    if (framebuffer_ == 0) {
        glGenFramebuffers(1, &framebuffer_);
    }
    if (texture_ == 0) {
        glGenTextures(1, &texture_);
    }
    glBindTexture(GL_TEXTURE_2D, texture_);
    HdTextureCache::Key uploadKey;
    uploadKey.width = width_;
    uploadKey.height = height_;
    uploadKey.internalFormat = GL_RGBA;
    uploadKey.format = GL_RGBA;
    uploadKey.type = GL_UNSIGNED_BYTE;
    uploadKey.pixelHash = sotcHdFnv1a(nullptr, 0);
    if (!uploadCache_.matches(texture_, uploadKey)) {
        // ES 2 requires the internal format to match the pixel format; GL_RGBA
        // is accepted by ES 2 and ES 3 drivers alike. Identical immutable
        // allocations are skipped by the upload dedup cache.
        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            width_,
            height_,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            nullptr
        );
        uploadCache_.remember(texture_, uploadKey);
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);
    glFramebufferTexture2D(
        GL_FRAMEBUFFER,
        GL_COLOR_ATTACHMENT0,
        GL_TEXTURE_2D,
        texture_,
        0
    );
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        SOTC_LOGE("Offscreen framebuffer incomplete: 0x%x", status);
        return false;
    }
    return true;
}

void RenderTarget::bind() const {
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);
    glViewport(0, 0, width_, height_);
    // A driver may leave scissor state enabled from a previous session. That
    // would clip the blit to a stale rectangle and produce a black surface.
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);
}

void RenderTarget::present(int surfaceWidth, int surfaceHeight, bool linear) {
    if (!valid() || surfaceWidth <= 0 || surfaceHeight <= 0) return;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, surfaceWidth, surfaceHeight);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);

    glUseProgram(program_);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture_);
    const GLint filter = linear ? GL_LINEAR : GL_NEAREST;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    if (positionLocation_ >= 0) {
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer_);
        glEnableVertexAttribArray(static_cast<GLuint>(positionLocation_));
        glVertexAttribPointer(
            static_cast<GLuint>(positionLocation_),
            2,
            GL_FLOAT,
            GL_FALSE,
            0,
            nullptr
        );
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDisableVertexAttribArray(static_cast<GLuint>(positionLocation_));
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    glUseProgram(0);
}

void RenderTarget::shutdown() {
    if (framebuffer_ != 0) {
        glDeleteFramebuffers(1, &framebuffer_);
        framebuffer_ = 0;
    }
    if (texture_ != 0) {
        uploadCache_.forget(texture_);
        glDeleteTextures(1, &texture_);
        texture_ = 0;
    }
    if (vertexBuffer_ != 0) {
        glDeleteBuffers(1, &vertexBuffer_);
        vertexBuffer_ = 0;
    }
    if (program_ != 0) {
        glDeleteProgram(program_);
        program_ = 0;
    }
    positionLocation_ = -1;
    width_ = 0;
    height_ = 0;
}
