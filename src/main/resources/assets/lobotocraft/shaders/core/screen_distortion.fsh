#version 150

uniform sampler2D Sampler0;
uniform float time;
uniform float intensity;

in vec2 texCoord;
out vec4 fragColor;

// 基础哈希噪声
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// 平滑噪声
float smoothNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f); // smoothstep

    return mix(
    mix(hash(i),               hash(i + vec2(1,0)), f.x),
    mix(hash(i + vec2(0,1)),   hash(i + vec2(1,1)), f.x),
    f.y
    );
}

void main() {
    vec2 uv = texCoord;

    // 热气向上漂移
    vec2 flow = vec2(0.0, -time * 0.12);

    // 三层不同频率噪声叠加，模拟湍流
    float n1 = smoothNoise(uv * vec2(3.0, 7.0) + flow);
    float n2 = smoothNoise(uv * vec2(6.0, 4.0) + flow * 1.4 + vec2(3.7, 1.5));
    float n3 = smoothNoise(uv * vec2(2.0, 11.0) + flow * 0.6 + vec2(1.2, 8.4));

    // 合成扰动方向，x横向轻，y纵向稍强（热浪主要竖向）
    vec2 distort;
    distort.x = ((n1 - 0.5) + (n2 - 0.5) * 0.4) * 0.018;
    distort.y = ((n2 - 0.5) + (n3 - 0.5) * 0.4) * 0.025;

    distort *= intensity;

    vec2 bentUV = clamp(uv + distort, 0.001, 0.999);
    fragColor = texture(Sampler0, bentUV);
}