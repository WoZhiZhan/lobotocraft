#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D VhsSampler;
uniform sampler2D Vhs2Sampler;
uniform float EffectTime;
uniform float Tracking;
uniform float Contrast;
uniform float Jitter;
uniform float Glitch;
uniform float Noise;
uniform float Brightness;

in vec2 texCoord;
out vec4 fragColor;

vec3 yuvToRgb(vec3 yuv) {
    return vec3(yuv.x + 1.140 * yuv.z,
                yuv.x - 0.395 * yuv.y - 0.581 * yuv.z,
                yuv.x + 2.032 * yuv.y);
}

vec3 rgbToYuv(vec3 rgb) {
    return vec3(0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b,
                -0.147 * rgb.r - 0.289 * rgb.g + 0.436 * rgb.b,
                0.615 * rgb.r - 0.515 * rgb.g - 0.100 * rgb.b);
}

float hardLight(float source, float destination) {
    return source < 0.5
            ? 2.0 * source * destination
            : 1.0 - 2.0 * (1.0 - source) * (1.0 - destination);
}

vec3 hardLight(vec3 source, vec3 destination) {
    return vec3(hardLight(source.r, destination.r),
                hardLight(source.g, destination.g),
                hardLight(source.b, destination.b));
}

float randomValue(vec2 value) {
    float phase = mod(dot(value, vec2(12.9898, 78.233)), 3.14);
    return fract(sin(phase) * 43758.5453);
}

void main() {
    vec2 sourceUv = texCoord;
    vec2 uv = sourceUv;
    float time = EffectTime * 0.05;

    if (uv.y < 0.025) uv.x += (uv.y - 0.05) * sin(uv.y * 512.0 + time * 12.0);
    if (uv.y < 0.015) uv.x += (uv.y - 0.05) * sin(uv.y * 512.0 + time * 64.0);
    float horizontalWave = sin(uv.y * 150.0 + time * 48.0) / 64.0;
    float randomBand = randomValue(vec2(time * 20.0)) * 15.0;
    float band = smoothstep(randomBand, randomBand + 0.03, uv.y)
                 - smoothstep(randomBand + 0.06, randomBand + 0.09, uv.y);
    uv.x += mix(0.0, horizontalWave, band);

    float quantizedY = floor(uv.y * 288.0) / 288.0;
    float jitterX = randomValue(vec2(time * 0.013, quantizedY * 0.42)) * 0.004;
    jitterX += sin(randomValue(vec2(time * 0.4, quantizedY))) * 0.005;
    uv.x += jitterX * (1.0 - uv.x);
    uv = mix(sourceUv, uv, Jitter);

    vec3 firstSample = clamp(texture(DiffuseSampler, uv).rgb, 0.08, 0.95);
    vec3 firstYuv = rgbToYuv(firstSample);
    float sineOffset = sin(time * 128.0) / 128.0;
    float cosineOffset = cos(time * 128.0) / 128.0;
    uv = floor(uv * vec2(52.0, 288.0)) / vec2(52.0, 288.0);
    vec3 chromaSample = texture(DiffuseSampler,
                                uv + vec2(-0.01 + sineOffset, cosineOffset) * sineOffset).rgb;
    vec3 secondYuv = rgbToYuv(chromaSample) / (Contrast + 1.0);
    float wave = max(sin(uv.y * 24.0 + time * 64.0), 0.0);
    wave += max(sin(uv.y * 14.0 + time * 16.0), 0.0);
    wave = wave * 0.5 + 0.5;
    vec3 color = clamp(yuvToRgb(vec3(firstYuv.x, secondYuv.yz * wave)), 0.08, 0.95) * 1.05;

    float frameTime = time * 30.0;
    vec2 vhsUv = sourceUv / 8.0;
    vhsUv.x += floor(mod(frameTime, 1.0) * 8.0) / 8.0;
    vhsUv.y += 1.0 - floor(mod(frameTime / 8.0, 1.0) * 8.0) / 8.0;
    vec3 tapeNoise = texture(VhsSampler, vhsUv).rgb;
    tapeNoise = (tapeNoise + Brightness - 0.5) * Noise + 0.5;
    color = hardLight(color, tapeNoise);

    vec2 trackingUv = sourceUv / 8.0;
    trackingUv.y = 1.0 - trackingUv.y;
    trackingUv.x += floor(mod(frameTime, 1.0) * 8.0) / 8.0;
    trackingUv.y += 1.0 - floor(mod(frameTime / 8.0, 1.0) * 8.0) / 8.0;
    vec3 tracking = texture(Vhs2Sampler, mix(sourceUv, trackingUv, Glitch)).rgb;
    color += tracking * Tracking * (1.0 - sourceUv.y);
    fragColor = vec4(color, 1.0);
}
