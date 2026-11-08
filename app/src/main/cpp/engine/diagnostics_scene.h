#pragma once

#include <GLES3/gl3.h>

/**
 * Diagnostic renderer used while no recompiled game core is installed.
 *
 * This draws a procedural atmospheric scene entirely on the GPU. It is
 * explicitly labelled in the UI as an engine diagnostic and must never be
 * presented as game content.
 */
class DiagnosticsScene {
public:
    DiagnosticsScene() = default;
    ~DiagnosticsScene();

    DiagnosticsScene(const DiagnosticsScene&) = delete;
    DiagnosticsScene& operator=(const DiagnosticsScene&) = delete;

    bool initialize();
    void render(int width, int height, double timeSeconds);
    void shutdown();

    bool ready() const { return program_ != 0; }

private:
    GLuint program_ = 0;
    GLuint vertexArray_ = 0;
    GLint resolutionLocation_ = -1;
    GLint timeLocation_ = -1;
};
