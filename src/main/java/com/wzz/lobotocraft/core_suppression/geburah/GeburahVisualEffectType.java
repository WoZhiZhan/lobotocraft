package com.wzz.lobotocraft.core_suppression.geburah;

public enum GeburahVisualEffectType {
    COMBO_HIT(1, 1),
    WILL_OF_BATTLE_HIT(2, 2),
    BLOOD_MIST_HIT(2, 3),
    BODIES_EVERYWHERE_HIT(2, 4),
    POSTURE_BREAK(3, 5),
    EGO_RELEASE(4, 6),
    EGO_EXHAUSTION(4, 7),
    DEFEAT(5, 8),
    SLASH_HIT(1, 9),
    STAB_HIT(1, 10),
    VERTICAL_SLASH_HIT(1, 11);

    private final int priority;
    private final int shaderKind;

    GeburahVisualEffectType(int priority, int shaderKind) {
        this.priority = priority;
        this.shaderKind = shaderKind;
    }

    public int priority() {
        return priority;
    }

    public int shaderKind() {
        return shaderKind;
    }

    public static GeburahVisualEffectType forMove(GeburahMove move) {
        if (move == null) return COMBO_HIT;
        return switch (move) {
            case SLASH -> SLASH_HIT;
            case STAB -> STAB_HIT;
            case VERTICAL_SLASH -> VERTICAL_SLASH_HIT;
        };
    }

    public static GeburahVisualEffectType byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
