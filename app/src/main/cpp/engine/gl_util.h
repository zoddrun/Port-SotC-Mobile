#pragma once

#include <GLES3/gl3.h>

GLuint sotcCompileShader(GLenum type, const char* source);
GLuint sotcLinkProgram(const char* vertexSource, const char* fragmentSource);
