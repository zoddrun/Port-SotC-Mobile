#version 300 es
precision highp float;

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uSceneTex;
uniform vec2 uTexelSize;
uniform float uSharpness;
uniform float uBloomIntensity;
uniform float uFxaaStrength;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 applyFxaaAndSharpen(vec2 uv) {
    vec3 c  = texture(uSceneTex, uv).rgb;
    vec3 nw = texture(uSceneTex, uv + vec2(-uTexelSize.x, -uTexelSize.y)).rgb;
    vec3 ne = texture(uSceneTex, uv + vec2( uTexelSize.x, -uTexelSize.y)).rgb;
    vec3 sw = texture(uSceneTex, uv + vec2(-uTexelSize.x,  uTexelSize.y)).rgb;
    vec3 se = texture(uSceneTex, uv + vec2( uTexelSize.x,  uTexelSize.y)).rgb;

    if (uFxaaStrength > 0.5) {
        float lC  = luma(c);
        float lNW = luma(nw);
        float lNE = luma(ne);
        float lSW = luma(sw);
        float lSE = luma(se);
        float lMin = min(lC, min(min(lNW, lNE), min(lSW, lSE)));
        float lMax = max(lC, max(max(lNW, lNE), max(lSW, lSE)));
        if ((lMax - lMin) > max(0.0625, lMax * 0.125)) {
            vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), ((lNW + lSW) - (lNE + lSE)));
            float dirReduce = max((lNW + lNE + lSW + lSE) * 0.03125, 1.0 / 128.0);
            float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
            dir = clamp(dir * rcpDirMin, vec2(-4.0), vec2(4.0)) * uTexelSize;
            vec3 rgbA = 0.5 * (
                texture(uSceneTex, uv + dir * (1.0 / 3.0 - 0.5)).rgb +
                texture(uSceneTex, uv + dir * (2.0 / 3.0 - 0.5)).rgb
            );
            c = mix(c, rgbA, 0.65);
        }
    }

    if (uSharpness > 0.001) {
        vec3 n = texture(uSceneTex, uv + vec2(0.0, -uTexelSize.y)).rgb;
        vec3 s = texture(uSceneTex, uv + vec2(0.0,  uTexelSize.y)).rgb;
        vec3 e = texture(uSceneTex, uv + vec2( uTexelSize.x, 0.0)).rgb;
        vec3 w = texture(uSceneTex, uv + vec2(-uTexelSize.x, 0.0)).rgb;

        vec3 minRgb = min(c, min(min(n, s), min(e, w)));
        vec3 maxRgb = max(c, max(max(n, s), max(e, w)));
        vec3 amp = clamp(min(minRgb, 1.0 - maxRgb) / max(maxRgb, vec3(1e-4)), 0.0, 1.0);
        vec3 wRgb = amp * (-0.16 * uSharpness);
        c = clamp((c + (n + s + e + w) * wRgb) / (1.0 + 4.0 * wRgb), 0.0, 1.0);
    }

    return c;
}

void main() {
    vec3 color = applyFxaaAndSharpen(vUv);

    if (uBloomIntensity > 0.001) {
        vec2 bStep = uTexelSize * 2.5;
        vec3 glow = (
            texture(uSceneTex, vUv + vec2(-bStep.x, -bStep.y)).rgb +
            texture(uSceneTex, vUv + vec2( bStep.x, -bStep.y)).rgb +
            texture(uSceneTex, vUv + vec2(-bStep.x,  bStep.y)).rgb +
            texture(uSceneTex, vUv + vec2( bStep.x,  bStep.y)).rgb
        ) * 0.25;
        vec3 brightPass = max(glow - vec3(0.62), vec3(0.0)) * 2.2;
        color += brightPass * uBloomIntensity;
        color = color / (1.0 + color * 0.14);
    }

    fragColor = vec4(color, 1.0);
}
