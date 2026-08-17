#version 150

uniform sampler2D Sampler0;
uniform float Intensity;
uniform vec2 TexelSize;

in vec2 texCoord;
out vec4 fragColor;

float laserMask(vec3 color) {
    float peak = max(color.r, max(color.g, color.b));
    float floorChannel = min(color.r, min(color.g, color.b));
    float hot = smoothstep(0.52, 0.98, peak);
    float pink = smoothstep(0.04, 0.48, color.r + color.b - color.g * 1.22);
    float cyan = smoothstep(0.05, 0.42, color.g + color.b - color.r * 1.05);
    float whiteCore = smoothstep(0.68, 0.98, floorChannel);
    return hot * max(max(pink, cyan), whiteCore);
}

float maskSample(vec2 offset) {
    vec3 sampleColor = texture(Sampler0, clamp(texCoord + offset, vec2(0.0), vec2(1.0))).rgb;
    return laserMask(sampleColor);
}

float preservationMask(float strength) {
    vec2 nearStep = TexelSize * (5.0 + strength * 4.0);
    vec2 middleStep = TexelSize * (18.0 + strength * 12.0);
    vec2 farStep = TexelSize * (42.0 + strength * 26.0);

    float local = maskSample(vec2(0.0));
    float nearMask = 0.0;
    nearMask = max(nearMask, maskSample(vec2( nearStep.x, 0.0)));
    nearMask = max(nearMask, maskSample(vec2(-nearStep.x, 0.0)));
    nearMask = max(nearMask, maskSample(vec2(0.0,  nearStep.y)));
    nearMask = max(nearMask, maskSample(vec2(0.0, -nearStep.y)));
    nearMask = max(nearMask, maskSample(vec2( nearStep.x,  nearStep.y)));
    nearMask = max(nearMask, maskSample(vec2(-nearStep.x,  nearStep.y)));
    nearMask = max(nearMask, maskSample(vec2( nearStep.x, -nearStep.y)));
    nearMask = max(nearMask, maskSample(vec2(-nearStep.x, -nearStep.y)));

    float middleMask = 0.0;
    middleMask = max(middleMask, maskSample(vec2( middleStep.x, 0.0)));
    middleMask = max(middleMask, maskSample(vec2(-middleStep.x, 0.0)));
    middleMask = max(middleMask, maskSample(vec2(0.0,  middleStep.y)));
    middleMask = max(middleMask, maskSample(vec2(0.0, -middleStep.y)));
    middleMask = max(middleMask, maskSample(vec2( middleStep.x,  middleStep.y) * 0.72));
    middleMask = max(middleMask, maskSample(vec2(-middleStep.x,  middleStep.y) * 0.72));
    middleMask = max(middleMask, maskSample(vec2( middleStep.x, -middleStep.y) * 0.72));
    middleMask = max(middleMask, maskSample(vec2(-middleStep.x, -middleStep.y) * 0.72));

    float farMask = 0.0;
    farMask = max(farMask, maskSample(vec2( farStep.x, 0.0)));
    farMask = max(farMask, maskSample(vec2(-farStep.x, 0.0)));
    farMask = max(farMask, maskSample(vec2(0.0,  farStep.y)));
    farMask = max(farMask, maskSample(vec2(0.0, -farStep.y)));

    float preserved = max(local, max(nearMask * 0.92, max(middleMask * 0.60, farMask * 0.26)));
    return smoothstep(0.025, 0.62, preserved);
}

void main() {
    float strength = smoothstep(0.0, 1.0, Intensity);
    float adapted = 1.0 - exp(-3.4 * clamp(Intensity, 0.0, 1.0));
    float shade = min(adapted * 0.88, 0.88);
    float preservation = preservationMask(strength);
    fragColor = vec4(0.0, 0.0, 0.0, shade * (1.0 - preservation));
}
