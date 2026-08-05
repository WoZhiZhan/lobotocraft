#version 150

uniform sampler2D DiffuseSampler;
uniform float EffectTime;
uniform float Transition;

in vec2 texCoord;
out vec4 fragColor;

float smoothRange(float edge0, float edge1, float value) {
    float t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float malkuthHoleSize() {
    float rate = clamp(Transition, 0.0, 1.0);
    if (rate < 0.5) {
        return mix(0.75, 0.20, smoothRange(0.0, 0.5, rate));
    }
    return mix(0.20, 0.75, smoothRange(0.5, 1.0, rate));
}

void main() {
    vec2 uv = texCoord;
    float k = 0.0;
    vec3 direction = vec3(uv, 1.0) - 0.5;
    vec3 orbit = direction;
    vec3 color = vec3(0.0);
    vec3 point;
    direction += texture(DiffuseSampler, vec2(0.1, 0.5)).rgb * 0.01;

    for (int outer = 0; outer < 12; ++outer) {
        point = orbit + cos(EffectTime);
        for (int inner = 0; inner < 10; ++inner) {
            point = abs(point.zyx - 0.2) - 0.7;
        }
        k += exp(-2.0 * abs(dot(point, orbit)));
        k /= 3.0;
        orbit += direction * 0.5 * k;
        color = 0.97 * color + 0.1 * k * vec3(k * k * k * 0.2, k * k * 0.9, 0.9);
    }

    float hole = malkuthHoleSize();
    float mask = 1.0 - smoothRange(hole, hole - 0.35, distance(vec2(0.5), uv));
    color = 0.4 * log(vec3(1.0) + color);
    vec2 feedbackUv = color.r * uv * 0.5;
    color += texture(DiffuseSampler, feedbackUv).rgb;
    color = mix(texture(DiffuseSampler, uv).rgb, color, mask);
    fragColor = vec4(color, 1.0);
}
