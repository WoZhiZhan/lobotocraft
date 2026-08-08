#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float EffectTime;
uniform float Fade;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec3 original = texture(DiffuseSampler, uv).rgb;
    float x = sin(0.3 * EffectTime + uv.y * 21.0)
              * sin(0.7 * EffectTime + uv.y * 29.0)
              * sin(0.3 + 0.33 * EffectTime + uv.y * 31.0) * 0.0017;

    vec3 color = texture(DiffuseSampler, uv + vec2(x + 0.001, 0.001)).rgb + 0.05;
    vec3 echo = texture(DiffuseSampler,
                        0.75 * vec2(x + 0.025, -0.02) + uv + vec2(0.001)).rgb;
    color += echo * vec3(0.08, 0.05, 0.08);
    color = clamp(color * 0.6 + 0.4 * color * color, 0.0, 1.0);
    float vignette = 16.0 * uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
    color *= pow(vignette, 0.3);
    color *= vec3(3.66, 2.94, 2.66);

    float scan = clamp(0.35 + 0.35
                       * sin(3.5 * EffectTime + uv.y * OutSize.y * 1.5), 0.0, 1.0);
    color *= 0.4 + 0.7 * pow(scan, 1.7);
    float bar = clamp(floor(sin(uv.y * 6.0 + EffectTime * 2.6) + 1.95), 0.0, 1.1);
    color *= 1.0 + 0.01 * sin(110.0 * EffectTime) + bar * 0.25;
    fragColor = vec4(mix(original, color, Fade), 1.0);
}
