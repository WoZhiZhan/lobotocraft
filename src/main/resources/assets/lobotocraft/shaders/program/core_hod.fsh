#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float EffectTime;
uniform float Stage;

in vec2 texCoord;
out vec4 fragColor;

float randomValue(vec2 value) {
    return fract(sin(dot(value, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float phase = clamp(floor(Stage + 0.5), 0.0, 2.0);
    float split = 0.001 + phase * 0.0005;
    vec2 offset = vec2(split, 0.0);

    vec3 color = vec3(
        texture(DiffuseSampler, clamp(texCoord + offset, 0.0, 1.0)).r,
        texture(DiffuseSampler, texCoord).g,
        texture(DiffuseSampler, clamp(texCoord - offset, 0.0, 1.0)).b
    );

    float scanWave = sin((texCoord.y * OutSize.y + EffectTime * 22.0) * 3.14159265);
    color *= 0.945 + scanWave * 0.055;

    color = mix(color, color * vec3(1.10, 0.99, 0.86), 0.30);
    color = (color - 0.5) * 0.97 + 0.5 + vec3(0.010, 0.004, 0.0);

    float frame = floor(EffectTime * 14.0);
    vec2 pixel = floor(texCoord * OutSize);
    vec3 coloredNoise = vec3(
        randomValue(pixel + vec2(frame, 17.0)),
        randomValue(pixel + vec2(43.0, frame * 1.37)),
        randomValue(pixel + vec2(frame * 0.73, 91.0))
    ) - 0.5;
    color += coloredNoise * (0.050 + phase * 0.020);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
