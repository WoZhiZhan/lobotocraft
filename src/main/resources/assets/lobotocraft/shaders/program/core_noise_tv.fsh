#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D NoiseSampler;
uniform float EffectTime;
uniform float Transition;
uniform float Stage;

in vec2 texCoord;
out vec4 fragColor;

float yesodFade() {
    float rate = clamp(Transition, 0.0, 1.0);
    float pulse = rate < 0.5 ? rate * 2.0 : (1.0 - rate) * 2.0;
    float stage = clamp(Stage, 0.0, 2.0);
    float baseAmount = 0.05 + stage * 0.03;
    float peakAmount = 0.18 + stage * 0.10;
    return mix(baseAmount, peakAmount, pulse);
}

void main() {
    vec2 noiseUv = texCoord + vec2(EffectTime * 0.75, EffectTime * 0.20);
    vec4 source = texture(DiffuseSampler, texCoord);
    vec4 noiseColor = texture(NoiseSampler, noiseUv);
    fragColor = mix(source, noiseColor, yesodFade());
    fragColor.a = 1.0;
}
