#version 100
precision mediump float;

varying vec2 vUv;
uniform sampler2D uSceneTex;
uniform vec2 uTexelSize;
uniform float uSharpness;
uniform float uBloomIntensity;
uniform float uFxaaStrength;

float luma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec3 c = texture2D(uSceneTex, vUv).rgb;
    if (uFxaaStrength > 0.5) {
        vec3 nw = texture2D(uSceneTex, vUv + vec2(-uTexelSize.x, -uTexelSize.y)).rgb;
        vec3 ne = texture2D(uSceneTex, vUv + vec2( uTexelSize.x, -uTexelSize.y)).rgb;
        vec3 sw = texture2D(uSceneTex, vUv + vec2(-uTexelSize.x,  uTexelSize.y)).rgb;
        vec3 se = texture2D(uSceneTex, vUv + vec2( uTexelSize.x,  uTexelSize.y)).rgb;
        float lowest = min(luma(c), min(min(luma(nw), luma(ne)), min(luma(sw), luma(se))));
        float highest = max(luma(c), max(max(luma(nw), luma(ne)), max(luma(sw), luma(se))));
        if ((highest - lowest) > max(0.0625, highest * 0.125)) {
            c = mix(c, (nw + ne + sw + se) * 0.25, 0.35);
        }
    }
    if (uSharpness > 0.001) {
        vec3 n = texture2D(uSceneTex, vUv + vec2(0.0, -uTexelSize.y)).rgb;
        vec3 s = texture2D(uSceneTex, vUv + vec2(0.0,  uTexelSize.y)).rgb;
        vec3 e = texture2D(uSceneTex, vUv + vec2( uTexelSize.x, 0.0)).rgb;
        vec3 w = texture2D(uSceneTex, vUv + vec2(-uTexelSize.x, 0.0)).rgb;
        vec3 blur = (n + s + e + w) * 0.25;
        c = clamp(c + (c - blur) * (uSharpness * 1.15), 0.0, 1.0);
    }
    if (uBloomIntensity > 0.001) {
        vec3 glow = (
            texture2D(uSceneTex, vUv + uTexelSize * 2.0).rgb +
            texture2D(uSceneTex, vUv - uTexelSize * 2.0).rgb
        ) * 0.5;
        vec3 bright = max(glow - vec3(0.65), vec3(0.0)) * 2.0;
        c = clamp(c + bright * uBloomIntensity, 0.0, 1.0);
    }
    gl_FragColor = vec4(c, 1.0);
}
