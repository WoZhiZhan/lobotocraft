#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D VignetteSampler;
uniform float Vignetting;
uniform float VignettingFull;
uniform float VignettingDirt;
uniform vec4 VignettingColor;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    vec4 mask = texture(VignetteSampler, texCoord) * VignettingColor.a;
    color = mix(color, VignettingColor, mask.r * Vignetting);
    color = mix(color, VignettingColor, mask.g * VignettingDirt * 2.0);
    color = mix(color, VignettingColor, mask.b * VignettingFull);
    color.a = 1.0;
    fragColor = color;
}
