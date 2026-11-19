#include "diagnostics_scene.h"

#include "gl_util.h"
#include "log.h"

namespace {

// GLSL ES 1.00 works on both ES 2 and ES 3 contexts. The fullscreen triangle
// replaces the gl_VertexID trick because that built-in only exists in ES 3.
constexpr const char* kVertexShader = R"(#version 100
attribute vec2 aPosition;
varying vec2 vUv;
void main() {
    vUv = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
)";

// highp is optional in ES 2 fragment shaders; the guard keeps the shader
// compiling on drivers that only offer mediump.
constexpr const char* kFragmentShader = R"(#version 100
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vUv;

uniform vec2 uResolution;
uniform float uTime;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        value += amplitude * noise(p);
        p *= 2.03;
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec2 uv = vUv;
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float horizon = 0.02;

    vec3 skyTop = vec3(0.045, 0.062, 0.086);
    vec3 skyMid = vec3(0.190, 0.204, 0.216);
    vec3 skyWarm = vec3(0.600, 0.505, 0.360);
    float height = smoothstep(horizon - 0.18, horizon + 0.62, p.y);
    vec3 color = mix(skyWarm, skyMid, smoothstep(0.0, 0.42, height));
    color = mix(color, skyTop, smoothstep(0.42, 1.0, height));

    vec2 sunPosition = vec2(-0.34 * aspect, -0.015);
    float sunDistance = length(p - sunPosition);
    color += vec3(0.86, 0.62, 0.31) * exp(-sunDistance * 5.0) * 0.50;
    color += vec3(1.00, 0.87, 0.62) * exp(-sunDistance * 20.0) * 0.48;

    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float base = -0.115 + fi * 0.038;
        float ridge = fbm(vec2(p.x * (1.45 + fi * 0.85) + fi * 17.0, fi * 7.0 + 0.4));
        float silhouette = base + ridge * (0.105 + fi * 0.012);
        if (p.y < silhouette) {
            vec3 rock = vec3(0.085, 0.103, 0.112) - fi * 0.011;
            color = mix(color, rock, 0.94 - fi * 0.16);
        }
    }

    float ground = smoothstep(horizon - 0.01, horizon - 0.38, p.y);
    color = mix(color, vec3(0.080, 0.092, 0.100), ground * 0.82);

    float fog = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 q = vec2(p.x * 1.25 + uTime * (0.005 + 0.0035 * fi), p.y * 3.2 + fi * 1.7);
        fog += fbm(q + uTime * 0.006) * 0.115;
    }
    color += vec3(0.29, 0.30, 0.30) * fog * smoothstep(-0.36, 0.14, p.y);

    vec2 dustUv = (uv + vec2(0.0, uTime * 0.0085)) * vec2(150.0, 95.0);
    vec2 cell = floor(dustUv);
    float h = hash(cell);
    vec2 local = fract(dustUv) - 0.5;
    float mote = smoothstep(0.085, 0.0, length(local)) * step(0.986, h);
    color += vec3(0.80, 0.72, 0.55) * mote * 0.62;

    float vignette = smoothstep(1.28, 0.22, length((uv - 0.5) * vec2(1.0, 1.16)));
    color *= mix(0.58, 1.0, vignette);
    color += (hash(uv * uResolution + uTime) - 0.5) * 0.012;

    gl_FragColor = vec4(color, 1.0);
}
)";

// One triangle that covers the whole viewport: (-1,-1), (3,-1), (-1,3).
constexpr GLfloat kFullscreenTriangle[] = {
    -1.0f, -1.0f,
     3.0f, -1.0f,
    -1.0f,  3.0f
};

}  // namespace

DiagnosticsScene::~DiagnosticsScene() {
    shutdown();
}

bool DiagnosticsScene::initialize() {
    if (program_ != 0) return true;
    program_ = sotcLinkProgram(kVertexShader, kFragmentShader);
    if (program_ == 0) {
        SOTC_LOGE("Unable to create diagnostic scene program");
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
    resolutionLocation_ = glGetUniformLocation(program_, "uResolution");
    timeLocation_ = glGetUniformLocation(program_, "uTime");
    return true;
}

void DiagnosticsScene::render(int width, int height, double timeSeconds) {
    if (program_ == 0 || width <= 0 || height <= 0) return;
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);
    glUseProgram(program_);
    glUniform2f(resolutionLocation_, static_cast<float>(width), static_cast<float>(height));
    glUniform1f(timeLocation_, static_cast<float>(timeSeconds));
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

void DiagnosticsScene::shutdown() {
    if (vertexBuffer_ != 0) {
        glDeleteBuffers(1, &vertexBuffer_);
        vertexBuffer_ = 0;
    }
    if (program_ != 0) {
        glDeleteProgram(program_);
        program_ = 0;
    }
    positionLocation_ = -1;
    resolutionLocation_ = -1;
    timeLocation_ = -1;
}
