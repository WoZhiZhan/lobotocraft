#version 150

uniform sampler2D DiffuseSampler;
uniform float EffectTime;

in vec2 texCoord;
out vec4 fragColor;

float voronoi(vec2 uv) {
    vec2 lattice = abs(uv) * 10.0;
    vec2 local = fract(lattice) - 0.5;
    lattice = floor(lattice);
    float distanceValue = 1.0;

    for (int x = -1; x < 2; ++x) {
        for (int y = -1; y < 2; ++y) {
            vec2 offset = vec2(float(x), float(y));
            vec2 point = lattice + offset;
            vec2 animated = (cos(point.x + EffectTime) + cos(point.y + EffectTime)) * vec2(0.3);
            distanceValue = min(distanceValue, length(local + animated - offset));
        }
    }
    return distanceValue;
}

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float angle = atan(centered.y, centered.x);
    float radial = length(centered) * 0.12;
    float fade = clamp(radial * 40.0 - 3.0
                       + cos(angle + cos(angle * 6.0) + EffectTime * 2.0) * 0.68,
                       0.0, 1.0);
    float aura = 0.0;

    for (int index = 3; index < 6; ++index) {
        float frequency = float(index);
        vec2 local = centered + cos((angle - radial) * frequency + EffectTime + centered + frequency) * 0.2;
        aura += voronoi(local) * (0.7 + (cos(local.x * 14.234) + cos(local.y * 16.234)) * 0.4);
    }

    vec3 auraColor = vec3(0.16, 0.57, 0.19) * aura * fade;
    float displacement = (auraColor.r + auraColor.g + auraColor.b) / 3.0;
    vec3 color = texture(DiffuseSampler, texCoord + vec2(displacement)).rgb;
    fragColor = vec4(color, 1.0);
}
