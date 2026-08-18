#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float EffectTime;

in vec2 texCoord;
out vec4 fragColor;

float hash11(float value) {
    return fract(sin(value * 127.1) * 43758.5453123);
}

vec2 hash22(vec2 value) {
    return fract(sin(vec2(
        dot(value, vec2(127.1, 311.7)),
        dot(value, vec2(269.5, 183.3)))) * 43758.5453123);
}

float segmentDistance(vec2 point, vec2 start, vec2 end) {
    vec2 line = end - start;
    float rate = clamp(dot(point - start, line) / max(dot(line, line), 0.00001), 0.0, 1.0);
    return length(point - start - line * rate);
}

float fallingRain(vec2 point, float time) {
    float rain = 0.0;
    for (int index = 0; index < 18; ++index) {
        float seed = float(index) + 17.0;
        float randomA = hash11(seed);
        float randomB = hash11(seed + 43.0);
        float cycle = mix(1.7, 4.2, randomA);
        float phase = fract((time + randomB * cycle) / cycle);
        float x = (randomA - 0.5) * 0.96 * (OutSize.x / max(OutSize.y, 1.0));
        float headY = 1.10 - phase * 1.24;
        float trailLength = mix(0.035, 0.105, randomB);
        float width = mix(0.0015, 0.0030, hash11(seed + 9.0));
        float distanceToTrail = segmentDistance(
                point, vec2(x, headY), vec2(x, headY + trailLength));
        float trail = 1.0 - smoothstep(width, width * 2.8, distanceToTrail);
        float headDistance = length((point - vec2(x, headY)) * vec2(0.72, 1.45));
        float head = 1.0 - smoothstep(width * 2.0, width * 4.8, headDistance);
        float life = smoothstep(0.0, 0.07, phase) * (1.0 - smoothstep(0.86, 1.0, phase));
        rain += (trail * 0.36 + head * 0.72) * life;
    }
    return rain;
}

float impactRipples(vec2 point, float time) {
    float ripples = 0.0;
    for (int index = 0; index < 5; ++index) {
        float seed = float(index) + 71.0;
        float period = mix(2.2, 4.8, hash11(seed));
        float timeline = (time + seed * 1.731) / period;
        float eventIndex = floor(timeline);
        float age = fract(timeline);
        vec2 randomPoint = hash22(vec2(eventIndex, seed));
        vec2 center = vec2(
                (randomPoint.x - 0.5) * 0.88 * (OutSize.x / max(OutSize.y, 1.0)),
                mix(0.12, 0.88, randomPoint.y));
        float radius = age * mix(0.085, 0.145, hash11(seed + eventIndex));
        float ringDistance = abs(length(point - center) - radius);
        float ringWidth = mix(0.0025, 0.0045, hash11(seed + 8.0));
        float ring = 1.0 - smoothstep(ringWidth, ringWidth * 2.5, ringDistance);
        float echoAge = max(age - 0.15, 0.0);
        float echoRadius = echoAge * radius / max(age, 0.001);
        float echoDistance = abs(length(point - center) - echoRadius);
        float echo = (1.0 - smoothstep(ringWidth, ringWidth * 2.3, echoDistance))
                * step(0.15, age);
        float fade = smoothstep(0.0, 0.05, age) * (1.0 - smoothstep(0.48, 0.95, age));
        float impact = (1.0 - smoothstep(0.006, 0.022, length(point - center)))
                * (1.0 - smoothstep(0.0, 0.10, age));
        ripples += (ring + echo * 0.42) * fade + impact * 0.8;
    }
    return ripples;
}

void main() {
    vec3 source = texture(DiffuseSampler, texCoord).rgb;
    vec3 blueFiltered = source * vec3(0.76, 0.87, 1.02) + vec3(0.0, 0.012, 0.030);
    vec3 color = mix(source, blueFiltered, 0.14);

    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 point = vec2((texCoord.x - 0.5) * aspect, texCoord.y);
    float rain = fallingRain(point, EffectTime);
    float ripple = impactRipples(point, EffectTime);
    float waterMask = clamp(rain * 0.18 + ripple * 0.34, 0.0, 0.52);
    vec3 waterColor = vec3(0.34, 0.67, 0.88);
    color = mix(color, waterColor, waterMask);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
