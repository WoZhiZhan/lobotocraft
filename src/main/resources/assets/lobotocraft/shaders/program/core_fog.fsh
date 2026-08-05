#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform float FarPlane;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    const float nearPlane = 0.05;
    float rawDepth = texture(DepthSampler, texCoord).r;
    float linearDepth = nearPlane / (FarPlane - rawDepth * (FarPlane - nearPlane));
    float fogDepth = smoothstep(0.0, 0.5, clamp(linearDepth, 0.0, 1.0));
    vec3 source = texture(DiffuseSampler, texCoord).rgb;
    vec3 fogColor = vec3(91.0, 212.0, 23.0) / 255.0;
    fragColor = vec4(mix(source, fogColor, fogDepth * 0.135), 1.0);
}
