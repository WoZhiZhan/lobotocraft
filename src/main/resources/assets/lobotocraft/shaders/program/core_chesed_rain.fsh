#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D RainSampler;
uniform vec2 OutSize;
uniform float EffectTime;
uniform float Transition;

in vec2 texCoord;
out vec4 fragColor;

vec4 sampleRain(vec2 uv) {
    return texture(RainSampler, fract(uv));
}

float sceneEdge(vec2 uv) {
    vec2 texel = 3.0 / max(OutSize, vec2(1.0));
    vec3 upperRight = texture(DiffuseSampler,
            clamp(uv + texel, vec2(0.0), vec2(1.0))).rgb;
    vec3 lowerRight = texture(DiffuseSampler,
            clamp(uv + vec2(texel.x, -texel.y), vec2(0.0), vec2(1.0))).rgb;
    vec3 upperLeft = texture(DiffuseSampler,
            clamp(uv + vec2(-texel.x, texel.y), vec2(0.0), vec2(1.0))).rgb;
    vec3 difference = (abs(upperRight - lowerRight) + abs(upperRight - upperLeft)) * 0.5;
    return length(difference);
}

void main() {
    float rate = clamp(Transition, 0.0, 1.0);

    // ChesedBossBase.UpdateEffect: the Rain_Pro burst settles over exactly three seconds.
    float speed = mix(0.95, 0.55, rate);
    float size = mix(0.40, 1.80, rate);
    float intensity = mix(2.0, 0.5, rate);
    float dropOnOff = mix(0.90, 0.15, rate);
    float directionX = 0.12;
    float time = EffectTime * speed;

    float timeStep = floor(time * 32.0);
    float steppedTime = timeStep * 0.0625;
    vec2 dropUv = vec2(
            texCoord.x * 2.0 + steppedTime - time * directionX * 0.55,
            texCoord.y + timeStep * 0.11 + time * 0.45);
    float drop = sceneEdge(texCoord) * sampleRain(dropUv).b * dropOnOff;

    vec2 rainUv = texCoord * size;
    rainUv.x += directionX * rainUv.y;
    vec2 rainUv1 = rainUv * 3.0 + vec2(0.10, time * 0.80);
    vec2 rainUv2 = rainUv1 * 0.65 + vec2(0.10, time);
    vec2 rainUv3 = rainUv2 * 0.65 + vec2(0.10, time * 1.20);
    vec2 rainUv4 = rainUv3 * 0.50 + vec2(0.10, time * 1.20);
    vec2 rainUv5 = rainUv4 * 0.40 + vec2(0.10, time * 1.20);
    float rain = intensity * 0.25 * (
            sampleRain(rainUv1).r * 0.30
            + sampleRain(rainUv2).r * 0.50
            + sampleRain(rainUv3).r * 0.70
            + sampleRain(rainUv4).r * 0.90
            + sampleRain(rainUv5).r * 0.90);

    float storm = sampleRain(vec2(texCoord.x * 0.001 + time * 0.20,
            time * 0.01)).g * intensity * 0.50;
    vec3 source = texture(DiffuseSampler, texCoord).rgb;
    vec3 rainLayer = source + vec3(rain + drop + storm);
    vec3 blueFiltered = rainLayer * vec3(0.64, 0.78, 1.12)
            + vec3(0.008, 0.020, 0.070);
    vec3 color = mix(rainLayer, blueFiltered, 0.38);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
