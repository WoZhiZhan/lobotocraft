#version 150

uniform sampler2D DiffuseSampler;
uniform float Stage;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float blockSize = Stage < 0.5 ? 2.0 : Stage < 1.5 ? 4.0 : 8.0;
    vec2 safeSize = max(OutSize, vec2(1.0));
    vec2 uv = (floor(texCoord * safeSize / blockSize) + 0.5)
            * blockSize / safeSize;
    fragColor = vec4(texture(DiffuseSampler, uv).rgb, 1.0);
}
