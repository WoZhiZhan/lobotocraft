#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D WaterSampler;
uniform float EffectTime;
uniform float Transition;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float rate = Transition < 0.0 ? 0.0 : clamp(Transition, 0.0, 1.0);
    float distortion = mix(40.0, 64.0, rate);
    float speed = mix(0.35, 0.12, rate);
    vec2 uv = texCoord;
    vec3 rain1 = texture(WaterSampler,
                         vec2(uv.x * 1.3 * 0.3,
                              uv.y * 0.5 * 1.4 + EffectTime * speed * 0.125)).rgb / distortion;
    vec3 rain2 = texture(WaterSampler,
                         vec2(uv.x * 1.15 * 0.3 - 0.1,
                              uv.y * 0.5 * 1.1 + EffectTime * speed * 0.225)).rgb / distortion;
    vec3 rain3 = texture(WaterSampler,
                         vec2(uv.x * 0.3 - 0.2,
                              uv.y * 0.5 + EffectTime * speed * 0.025)).rgb / distortion;
    vec2 distortedUv = uv - (rain1.xy - rain2.xy - rain3.xy) / 3.0;
    fragColor = vec4(texture(DiffuseSampler, distortedUv).rgb, 1.0);
}
