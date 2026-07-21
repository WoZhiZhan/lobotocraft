#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;        // 实际屏幕像素尺寸（管线自动填充）
uniform float TargetHeight;  // 目标分辨率高度，如 1080 / 720 / 480

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // 屏幕高 2160(4K)、目标 1080 -> scale=2，即每 2x2 物理像素合成一个块
    float scale = max(OutSize.y / TargetHeight, 1.0);

    vec2 lowRes = OutSize / scale;                       // 模拟分辨率下的网格数
    vec2 uv = (floor(texCoord * lowRes) + 0.5) / lowRes; // 吸附到网格中心

    fragColor = vec4(texture(DiffuseSampler, uv).rgb, 1.0);
}