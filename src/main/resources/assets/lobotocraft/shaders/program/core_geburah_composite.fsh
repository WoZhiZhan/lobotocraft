#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Stage;
uniform float EffectTime;
uniform float EffectKind;
uniform float EffectProgress;
uniform float EffectIntensity;
uniform float HitSeverity;
uniform float EgoAttack;
uniform float GeburahProximity;
uniform vec2 EffectOrigin;
uniform vec2 EffectDirection;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 direction = length(EffectDirection) < 0.01
        ? vec2(1.0, 0.0) : normalize(EffectDirection);
    vec2 perpendicular = vec2(-direction.y, direction.x);
    vec2 centerDelta = texCoord - EffectOrigin;
    centerDelta.x *= OutSize.x / max(1.0, OutSize.y);
    float radius = length(centerDelta);
    float across = dot(centerDelta, perpendicular);
    vec2 screenDelta = texCoord - vec2(0.5);
    float edge = smoothstep(0.22, 0.72, length(screenDelta));
    float progress = clamp(EffectProgress, 0.0, 1.0);
    float effectPower = EffectIntensity * (1.0 + EgoAttack * 0.68);
    float envelope = sin(progress * 3.14159265) * effectPower;
    float attackHit = ((EffectKind > 0.5 && EffectKind < 4.5)
        || (EffectKind > 8.5 && EffectKind < 11.5)) ? 1.0 : 0.0;
    float egoActive = 1.0 - smoothstep(0.18, 0.62, abs(Stage - 1.0));

    float persistentSplit = edge * (egoActive * (0.00075 + GeburahProximity * 0.0015));
    float attackRecovery = (1.0 - progress) * effectPower * attackHit;
    float slashExtra = (EffectKind > 8.5 && EffectKind < 9.5) ? 0.0025 : 0.0;
    float split = persistentSplit + attackRecovery * (0.0075 + slashExtra);
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float red = texture(DiffuseSampler,
        clamp(texCoord + direction * split, vec2(0.001), vec2(0.999))).r;
    float blue = texture(DiffuseSampler,
        clamp(texCoord - direction * split, vec2(0.001), vec2(0.999))).b;
    vec3 color = vec3(red, base.g, blue);

    float universalRedTrail = exp(-abs(across + 0.030) * 37.0);
    float universalBlueTrail = exp(-abs(across - 0.038) * 34.0);
    float universalCore = exp(-abs(across) * 68.0);
    color += vec3(0.30, 0.003, 0.008) * universalRedTrail * attackRecovery * 0.72;
    color += vec3(0.008, 0.10, 0.34) * universalBlueTrail * attackRecovery * 0.62;
    color += vec3(0.18, 0.11, 0.13) * universalCore * attackRecovery * 0.30;

    if (EffectKind > 0.5 && EffectKind < 1.5) {
        float echoes = exp(-abs(across) * 84.0)
            + exp(-abs(across - 0.032) * 62.0) * 0.45;
        color += vec3(0.22, 0.008, 0.018) * echoes * envelope;
    } else if (EffectKind > 1.5 && EffectKind < 2.5) {
        float focus = exp(-radius * 5.5) * envelope;
        float luminance = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(color, mix(vec3(luminance), color * vec3(1.16, 0.72, 0.76), 0.58),
            focus * 0.46);
    } else if (EffectKind > 2.5 && EffectKind < 3.5) {
        float tear = exp(-abs(across) * 72.0) * envelope;
        color = mix(color, color * vec3(1.26, 0.47, 0.52), tear * 0.62);
    } else if (EffectKind > 3.5 && EffectKind < 4.5) {
        float band = pow(1.0 - abs(fract(texCoord.y * 7.0) - 0.5) * 2.0, 10.0)
            * envelope;
        color = mix(color, color.bgr * vec3(0.72, 0.24, 0.32), band * 0.38);
    } else if (EffectKind > 4.5 && EffectKind < 5.5) {
        float cells = abs(fract((atan(centerDelta.y, centerDelta.x) * 8.0
                     + radius * 34.0)) - 0.5);
        float fractures = (1.0 - smoothstep(0.0, 0.075, cells)) * envelope;
        float shockRadius = 0.05 + progress * 0.64;
        float aftershock = exp(-abs(radius - shockRadius) * 38.0) * envelope;
        float luminance = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(color, vec3(luminance) * vec3(1.18, 0.94, 0.56),
            fractures * 0.48);
        color += vec3(0.42, 0.22, 0.025) * fractures * 0.72;
        color = mix(color, color * vec3(0.62, 0.16, 0.13), aftershock * 0.34);
    } else if (EffectKind > 8.5 && EffectKind < 9.5) {
        float redTrail = exp(-abs(across + 0.036) * 43.0);
        float blueTrail = exp(-abs(across - 0.044) * 38.0);
        float coreTrail = exp(-abs(across) * 74.0);
        color += vec3(0.30, 0.004, 0.008) * redTrail * attackRecovery;
        color += vec3(0.01, 0.10, 0.28) * blueTrail * attackRecovery * 0.78;
        color += vec3(0.20, 0.22, 0.18) * coreTrail * attackRecovery * 0.28;
    } else if (EffectKind > 9.5 && EffectKind < 10.5) {
        float thrust = exp(-abs(across) * 52.0)
            * exp(-progress * 4.7) * effectPower;
        float luminance = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(color, vec3(luminance) * vec3(1.12, 0.70, 0.76), thrust * 0.38);
        color += vec3(0.22, 0.004, 0.01) * thrust;
    } else if (EffectKind > 10.5 && EffectKind < 11.5) {
        float impact = exp(-progress * 6.4) * effectPower;
        float shockRadius = 0.045 + progress * 0.74;
        float shock = exp(-abs(radius - shockRadius) * 34.0) * impact;
        color = mix(color, color * vec3(1.16, 0.48, 0.42), shock * 0.52);
        color = mix(color, color * vec3(0.52, 0.20, 0.18), edge * impact * 0.22);
    }

    if (EffectKind > 5.5 && EffectKind < 6.5 && progress < 0.1875) {
        float gray = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(color, vec3(gray), (1.0 - progress / 0.1875) * 0.88);
    }
    if (EffectKind > 6.5 && EffectKind < 7.5) {
        color += vec3(0.18, 0.10, 0.01) * edge * envelope;
    }

    float hit = edge * sin(progress * 3.14159265) * clamp(HitSeverity, 0.0, 1.25);
    color = mix(color, color * vec3(1.18, 0.46, 0.52), hit * 0.28);
    color = mix(color, color * vec3(0.84, 0.19, 0.22),
        edge * egoActive * (0.045 + GeburahProximity * 0.075));
    fragColor = vec4(color, 1.0);
}
