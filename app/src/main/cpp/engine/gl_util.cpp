#include "gl_util.h"
#include "log.h"

#include <vector>

namespace {

bool shaderCompiled(GLuint shader) {
    GLint status = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (status == GL_TRUE) return true;
    GLint length = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
    std::vector<char> message(static_cast<size_t>(length > 0 ? length : 1));
    glGetShaderInfoLog(shader, length, nullptr, message.data());
    SOTC_LOGE("Shader compilation failed: %s", message.data());
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
    GLuint shader = glCreateShader(type);
    if (shader == 0) return 0;
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    if (!shaderCompiled(shader)) {
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
