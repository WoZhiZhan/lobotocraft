#version 150

uniform float time;
uniform float particleAge;
uniform float particleLifetime;
uniform vec3 startColor;
uniform vec3 endColor;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// ---- Hash / Noise ----------------------------------------------------------

float noise(vec2 p) {
    return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453);
}

float smoothNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f*f*(3.0 - 2.0*f);

    float a = noise(i);
    float b = noise(i + vec2(1.0, 0.0));
    float c = noise(i + vec2(0.0, 1.0));
    float d = noise(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// ---- Main ------------------------------------------------------------------

void main() {
    vec2 uv = texCoord0 * 2.0 - 1.0;

    // 箭矢拖尾方向渐变（假设沿x轴飞行，从左到右）
    float trailFade = smoothstep(-1.0, 1.0, uv.x) * 0.7 + 0.3;

    float r = length(uv);
    float progress = particleAge / particleLifetime;

    // 更强的光晕效果
    float halo = smoothstep(1.5, 0.0, r);

    // 扭曲效果（减少一点让拖尾更清晰）
    float distort = smoothNoise(vec2(uv.x * 6.0, uv.y * 6.0 + time * 3.0)) * 0.6;
    distort += smoothNoise(vec2(uv.x * 15.0, uv.y * 15.0 + time * 6.0)) * 0.4;

    // 能量脉动
    float pulse = sin(time * 8.0 + r * 10.0) * 0.3 + 0.7;

    // 颜色渐变：更明亮的过渡
    vec3 baseColor = mix(startColor, endColor, progress * 0.6 + r * 0.3);

    // 增强核心光
    float core = smoothstep(0.5, 0.0, r) * 2.0;

    // 添加能量火花效果
    float spark = smoothNoise(vec2(uv.x * 25.0 + time * 15.0, uv.y * 25.0)) *
    smoothstep(0.8, 0.0, r);

    // 边缘发光
    float edgeGlow = smoothstep(0.3, 0.8, r) * smoothstep(1.2, 0.8, r) * 0.8;

    // 组合颜色 - 更明亮的混合
    vec3 color =
    baseColor * (0.8 * halo) +                    // 基础光晕
    baseColor * (distort * 0.6) +                 // 扭曲
    vec3(1.0, 0.95, 0.9) * core * 1.5 +          // 核心白光
    baseColor * spark * 1.2 +                     // 能量火花
    baseColor * edgeGlow * pulse;                 // 边缘脉动

    // 裂纹效果（减弱一点）
    float crack =
    1.0 - smoothstep(0.8, 1.4, r + distort * 0.25) +
    sin((uv.x + uv.y) * 15.0 + time * 8.0) * 0.05;

    color *= crack;

    // 更强的透明度
    float alpha = clamp(
    halo * 1.5 +
    distort * 0.4 +
    core * 1.2 +
    spark * 0.3,
    0.0, 1.0
    );

    // 箭矢方向的透明度衰减
    alpha *= trailFade;

    // 生命周期淡入淡出
    if (progress < 0.15) {
        alpha *= smoothstep(0.0, 0.15, progress);
    }
    if (progress > 0.7) {
        alpha *= (1.0 - smoothstep(0.7, 1.0, progress));
    }

    // 顶点颜色调制
    color *= vertexColor.rgb;
    alpha *= vertexColor.a;

    // 提高亮度 - 从2.5提升到4.0
    float brightness = 4.0;
    color *= brightness;

    // 添加一点饱和度提升
    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luminance), color, 1.3);

    fragColor = vec4(color, alpha);
}