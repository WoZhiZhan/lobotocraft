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

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float lineField(float distanceToLine, float sharpness) {
    return exp(-abs(distanceToLine) * sharpness);
}

void main() {
    float aspect = OutSize.x / max(1.0, OutSize.y);
    vec2 direction = length(EffectDirection) < 0.01
        ? vec2(1.0, 0.0) : normalize(EffectDirection);
    vec2 perpendicular = vec2(-direction.y, direction.x);
    vec2 centerDelta = texCoord - EffectOrigin;
    centerDelta.x *= aspect;
    float radius = length(centerDelta);
    float along = dot(centerDelta, direction);
    float across = dot(centerDelta, perpendicular);
    vec2 screenDelta = texCoord - vec2(0.5);
    float edge = smoothstep(0.24, 0.72, length(screenDelta));
    float progress = clamp(EffectProgress, 0.0, 1.0);
    float effectPower = EffectIntensity * (1.0 + EgoAttack * 0.68);
    float envelope = sin(progress * 3.14159265) * effectPower;
    float attackHit = ((EffectKind > 0.5 && EffectKind < 4.5)
        || (EffectKind > 8.5 && EffectKind < 11.5)) ? 1.0 : 0.0;
    float egoActive = 1.0 - smoothstep(0.18, 0.62, abs(Stage - 1.0));

    vec2 displacement = vec2(0.0);
    float energy = 0.0;
    float fractureEnergy = 0.0;
    float goldEnergy = 0.0;
    vec2 blurAxis = direction;
    float blurStrength = 0.0;

    float persistent = edge * (0.00055
        + egoActive * (0.0012 + GeburahProximity * 0.0024));
    displacement += perpendicular * sin(EffectTime * 1.7 + texCoord.y * 28.0)
        * persistent;

    if (EffectKind > 0.5 && EffectKind < 1.5) {
        float primary = lineField(across, 92.0);
        float echoA = lineField(across - 0.025, 74.0);
        float echoB = lineField(across + 0.041, 62.0);
        float slash = (primary + echoA * 0.52 + echoB * 0.34) * envelope;
        displacement += direction * slash * 0.009;
        displacement += perpendicular * (echoA - echoB) * envelope * 0.0035;
        energy = slash * 0.62;
    } else if (EffectKind > 1.5 && EffectKind < 2.5) {
        float corridor = lineField(across, 48.0)
            * (1.0 - smoothstep(0.03, 0.62, abs(along)));
        float tunnel = exp(-abs(radius - (1.0 - progress) * 0.48) * 38.0);
        float compression = (corridor * 0.72 + tunnel) * envelope;
        displacement -= normalize(centerDelta + vec2(0.0001)) * compression * 0.014;
        displacement += direction * corridor * envelope * 0.008;
        energy = compression * 0.58;
    } else if (EffectKind > 2.5 && EffectKind < 3.5) {
        float tear = lineField(across, 78.0);
        float secondary = lineField(across - 0.052, 54.0) * 0.5;
        float firstRing = exp(-abs(radius - progress * 0.46) * 42.0);
        float secondRing = effectPower > 0.72
            ? exp(-abs(radius - max(0.0, progress - 0.18) * 0.62) * 48.0) : 0.0;
        float bloodField = (tear + secondary + firstRing * 0.7 + secondRing) * envelope;
        displacement += direction * (tear + secondary) * envelope * 0.014;
        displacement += normalize(centerDelta + vec2(0.0001))
            * (firstRing + secondRing) * envelope * 0.011;
        energy = bloodField * 0.82;
    } else if (EffectKind > 3.5 && EffectKind < 4.5) {
        float bands = pow(1.0 - abs(fract((texCoord.y + progress * 0.08) * 7.0) - 0.5) * 2.0, 9.0);
        float sliceNoise = step(0.58, hash21(vec2(floor(texCoord.y * 15.0), EffectProgress)));
        float slices = bands * sliceNoise * envelope;
        displacement += direction * slices * 0.024;
        displacement -= perpendicular * sin(texCoord.y * 53.0) * slices * 0.003;
        energy = slices * 0.92;
    } else if (EffectKind > 4.5 && EffectKind < 5.5) {
        float cells = abs(fract((atan(centerDelta.y, centerDelta.x) * 8.0
                     + radius * 34.0)) - 0.5);
        float fractures = (1.0 - smoothstep(0.0, 0.075, cells)) * envelope;
        float shockRadius = 0.05 + progress * 0.64;
        float aftershock = exp(-abs(radius - shockRadius) * 42.0) * envelope;
        vec2 radial = normalize(centerDelta + vec2(0.0001));
        displacement += radial * (fractures * 0.008 + aftershock * 0.014);
        fractureEnergy = fractures + aftershock * 0.45;
        goldEnergy = fractureEnergy * 0.92;
        energy = aftershock * 0.24;
    } else if (EffectKind > 5.5 && EffectKind < 6.5) {
        float wave = progress < 0.42 ? (0.42 - progress) * 0.72
                                    : (progress - 0.42) * 1.18;
        float ring = exp(-abs(radius - wave) * 46.0) * envelope;
        displacement += normalize(centerDelta + vec2(0.0001)) * ring * 0.019;
        energy = ring;
    } else if (EffectKind > 6.5 && EffectKind < 7.5) {
        float collapse = (1.0 - progress) * 0.62;
        float ring = exp(-abs(radius - collapse) * 38.0) * envelope;
        displacement -= normalize(centerDelta + vec2(0.0001)) * ring * 0.014;
        goldEnergy = ring;
    } else if (EffectKind > 7.5 && EffectKind < 8.5) {
        float collapse = (1.0 - progress) * 0.78;
        float ring = exp(-abs(radius - collapse) * 52.0) * envelope;
        float fragments = step(0.72, hash21(floor((texCoord + direction * progress * 0.06)
            * OutSize / 10.0)));
        displacement -= normalize(centerDelta + vec2(0.0001)) * ring * 0.021;
        displacement += direction * fragments * envelope * 0.006;
        energy = ring + fragments * envelope * 0.18;
    } else if (EffectKind > 8.5 && EffectKind < 9.5) {
        float recovery = (1.0 - progress) * effectPower;
        float blade = lineField(across, 42.0)
            * (1.0 - smoothstep(0.08, 0.78, abs(along)));
        float trailingEdge = lineField(across + 0.038, 31.0) * 0.62;
        displacement += direction * (blade + trailingEdge) * recovery * 0.012;
        displacement += perpendicular * (blade - trailingEdge) * recovery * 0.0045;
        blurAxis = direction;
        blurStrength = (blade * 0.010 + trailingEdge * 0.006) * recovery;
        energy = (blade * 0.54 + trailingEdge * 0.32) * recovery;
    } else if (EffectKind > 9.5 && EffectKind < 10.5) {
        float punch = exp(-progress * 4.7) * effectPower;
        float corridor = lineField(across, 34.0)
            * (1.0 - smoothstep(0.06, 0.70, abs(along)));
        float pressure = exp(-radius * 5.2) * punch;
        displacement += direction * corridor * punch * 0.019;
        displacement -= normalize(centerDelta + vec2(0.0001)) * pressure * 0.009;
        blurAxis = direction;
        blurStrength = corridor * punch * 0.014;
        energy = corridor * punch * 0.66 + pressure * 0.30;
    } else if (EffectKind > 10.5 && EffectKind < 11.5) {
        float impact = exp(-progress * 6.4) * effectPower;
        float crush = lineField(across, 30.0)
            * (1.0 - smoothstep(0.10, 0.82, abs(along)));
        float shockRadius = 0.045 + progress * 0.74;
        float shock = exp(-abs(radius - shockRadius) * 38.0) * impact;
        vec2 radial = normalize(centerDelta + vec2(0.0001));
        displacement += direction * crush * impact * 0.016;
        displacement += radial * shock * 0.025;
        blurAxis = normalize(direction + perpendicular * 0.18);
        blurStrength = crush * impact * 0.015 + shock * 0.007;
        energy = crush * impact * 0.75 + shock * 0.96;
    }

    if (attackHit > 0.5 && blurStrength <= 0.0001) {
        float recovery = (1.0 - progress) * effectPower;
        float directionalTrail = lineField(across, 31.0)
            * (1.0 - smoothstep(0.10, 0.86, abs(along)));
        displacement += direction * directionalTrail * recovery * 0.008;
        blurAxis = direction;
        blurStrength = directionalTrail * recovery * 0.0085;
        energy += directionalTrail * recovery * 0.24;
    }

    float hitEdge = edge * sin(progress * 3.14159265) * clamp(HitSeverity, 0.0, 1.25);
    displacement += perpendicular * sin(texCoord.y * 41.0 + EffectTime * 5.0)
        * hitEdge * 0.0018;

    vec2 sampleUv = clamp(texCoord + displacement, vec2(0.001), vec2(0.999));
    vec2 blurOffset = blurAxis * blurStrength;
    vec3 color;
    if (blurStrength > 0.0001) {
        color = texture(DiffuseSampler, sampleUv).rgb * 0.44;
        color += texture(DiffuseSampler,
            clamp(sampleUv - blurOffset, vec2(0.001), vec2(0.999))).rgb * 0.20;
        color += texture(DiffuseSampler,
            clamp(sampleUv + blurOffset, vec2(0.001), vec2(0.999))).rgb * 0.20;
        color += texture(DiffuseSampler,
            clamp(sampleUv - blurOffset * 2.0, vec2(0.001), vec2(0.999))).rgb * 0.08;
        color += texture(DiffuseSampler,
            clamp(sampleUv + blurOffset * 2.0, vec2(0.001), vec2(0.999))).rgb * 0.08;
    } else {
        color = texture(DiffuseSampler, sampleUv).rgb;
    }
    color += vec3(0.27, 0.004, 0.012) * energy;
    color += vec3(0.72, 0.42, 0.06) * goldEnergy;
    fragColor = vec4(color, 1.0);
}
