#include "gl_util.h"
#include "log.h"

#include <vector>

namespace {

// GLSL ES requires the #version directive to be the first token of a shader.
// String literals frequently start with a newline for formatting, and several
// drivers (including SwiftShader) reject that with
// "#version directive must occur on the first line". Strip leading whitespace
// so shader sources behave identically on every driver.
const char* skipLeadingWhitespace(const char* source) {
    if (source == nullptr) return nullptr;
    const char* cursor = source;
    while (*cursor == ' ' || *cursor == '\t' || *cursor == '\r' || *cursor == '\n') {
        ++cursor;
    }
    return *cursor == '#' ? cursor : source;
}

const char* shaderStageName(GLenum type) {
    switch (type) {
        case GL_VERTEX_SHADER: return "vertex";
        case GL_FRAGMENT_SHADER: return "fragment";
        default: return "unknown";
    }
}

bool shaderCompiled(GLuint shader, GLenum type) {
    GLint status = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (status == GL_TRUE) return true;
    GLint length = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
    std::vector<char> message(static_cast<size_t>(length > 0 ? length : 1));
    glGetShaderInfoLog(shader, length, nullptr, message.data());
    SOTC_LOGE("Shader compilation failed (%s): %s", shaderStageName(type), message.data());
    return false;
}

bool programLinked(GLuint program) {
    GLint status = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &status);
    if (status == GL_TRUE) return true;
    GLint length = 0;
    glGetProgramiv(program, GL_INFO_LOG_LENGTH, &length);
    std::vector<char> message(static_cast<size_t>(length > 0 ? length : 1));
    glGetProgramInfoLog(program, length, nullptr, message.data());
    SOTC_LOGE("Program link failed: %s", message.data());
    return false;
}

}  // namespace

GLuint sotcCompileShader(GLenum type, const char* source) {
    if (source == nullptr) {
        SOTC_LOGE("Refusing to compile a null %s shader", shaderStageName(type));
        return 0;
    }
    const char* normalized = skipLeadingWhitespace(source);
    GLuint shader = glCreateShader(type);
    if (shader == 0) return 0;
    glShaderSource(shader, 1, &normalized, nullptr);
    glCompileShader(shader);
    if (!shaderCompiled(shader, type)) {
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint sotcLinkProgram(const char* vertexSource, const char* fragmentSource) {
    GLuint vertex = sotcCompileShader(GL_VERTEX_SHADER, vertexSource);
    if (vertex == 0) return 0;
    GLuint fragment = sotcCompileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (fragment == 0) {
        glDeleteShader(vertex);
        return 0;
    }
    GLuint program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glLinkProgram(program);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    if (!programLinked(program)) {
        glDeleteProgram(program);
        return 0;
    }
    return program;
}
