#pragma once

#include <GLES2/gl2.h>

GLuint sotcCompileShader(GLenum type, const char* source);
GLuint sotcLinkProgram(const char* vertexSource, const char* fragmentSource);
