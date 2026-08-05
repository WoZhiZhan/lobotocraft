#version 150

uniform sampler2D DiffuseSampler;
uniform float EffectTime;
uniform float Stage;
uniform float GlitchAmount;
uniform float NoiseAmount;

in vec2 texCoord;
out vec4 fragColor;

float randomValue(vec2 value) {
    return fract(sin(dot(value, vec2(12.9898, 78.233))) * 43758.5453);
}

float quantize(float value, float levels) {
    return floor(value * levels) / levels;
}

vec2 quantize(vec2 value, vec2 levels) {
    return floor(value * levels) / levels;
}

vec3 rgbToYuv(vec3 rgb) {
    return vec3(dot(rgb, vec3(0.299, 0.587, 0.114)),
                dot(rgb, vec3(-0.14713, -0.28886, 0.436)),
                dot(rgb, vec3(0.615, -0.51499, -0.10001)));
}

vec3 yuvToRgb(vec3 yuv) {
    return vec3(yuv.x + yuv.z * 1.13983,
                yuv.x + dot(vec2(-0.39465, -0.58060), yuv.yz),
                yuv.x + yuv.y * 2.03211);
}

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    if (Stage < 1.5) {
        fragColor = source;
        return;
    }

    float changeRandom = randomValue(quantize(texCoord.yy, vec2(16.0))
                                     + 150.0 * quantize(EffectTime, 4.0));
    float levels = max(1.0, 16.0 * changeRandom);
    float timeSlice = 5.0 * quantize(EffectTime, levels);
    float vertical = 0.5 * randomValue(quantize(texCoord.yy + timeSlice, vec2(11.0)));
    vertical += 1.5 * randomValue(quantize(texCoord.yy + timeSlice, vec2(7.0)));
    vertical = vertical * 2.0 - 1.0;
    vertical = sign(vertical) * clamp((abs(vertical) - 0.6) / 0.4, 0.0, 1.0);

    vec2 shiftedUv = clamp(texCoord + vec2(0.05 * vertical, 0.0), 0.0, 1.0);
    vec4 shifted = texture(DiffuseSampler, shiftedUv);
    vec3 yuv = rgbToYuv(shifted.rgb);
    float scaledGlitch = vertical * GlitchAmount;
    float denominator = 1.0 - 3.0 * abs(scaledGlitch)
                        * clamp(10.5 - scaledGlitch, 0.0, 1.0);
    yuv.y /= denominator;
    yuv.z += 1.125 * vertical * clamp(scaledGlitch - 0.5, 0.0, 1.0);
    vec4 glitched = vec4(yuvToRgb(yuv), shifted.a);

    float blackNoise = randomValue(texCoord * (EffectTime * 45.0)) * 0.25;
    glitched = mix(glitched, vec4(0.0, 0.0, 0.0, 1.0), blackNoise);
    fragColor = mix(source, glitched * 1.30, NoiseAmount);
}
