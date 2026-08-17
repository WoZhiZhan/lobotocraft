package com.wzz.lobotocraft.core_suppression.geburah;

public enum GeburahMove {
    SLASH("slash", "EGOslash", 14, 14, 4, 4, 17,
            6, 9, 7, 10, 1, 2),
    STAB("stab", "EGOstab", 16, 18, 5, 6, 17,
            4, 9, 4, 9, 0, 0),
    VERTICAL_SLASH("verticalslash", "EGOverticalslash", 18, 20, 2, 2, 20,
            7, 10, 8, 12, 0, 0);

    public static final int WINDUP_TICKS = 17;

    private final String normalAnimation;
    private final String egoAnimation;
    private final int normalStrikeTicks;
    private final int egoStrikeTicks;
    private final int normalHitTick;
    private final int egoHitTick;
    private final int recoveryTicks;
    private final int normalMinDamage;
    private final int normalMaxDamage;
    private final int egoMinDamage;
    private final int egoMaxDamage;
    private final int normalBleedStacks;
    private final int egoBleedStacks;

    GeburahMove(String normalAnimation, String egoAnimation,
                int normalStrikeTicks, int egoStrikeTicks,
                int normalHitTick, int egoHitTick, int recoveryTicks,
                int normalMinDamage, int normalMaxDamage,
                int egoMinDamage, int egoMaxDamage,
                int normalBleedStacks, int egoBleedStacks) {
        this.normalAnimation = normalAnimation;
        this.egoAnimation = egoAnimation;
        this.normalStrikeTicks = normalStrikeTicks;
        this.egoStrikeTicks = egoStrikeTicks;
        this.normalHitTick = normalHitTick;
        this.egoHitTick = egoHitTick;
        this.recoveryTicks = recoveryTicks;
        this.normalMinDamage = normalMinDamage;
        this.normalMaxDamage = normalMaxDamage;
        this.egoMinDamage = egoMinDamage;
        this.egoMaxDamage = egoMaxDamage;
        this.normalBleedStacks = normalBleedStacks;
        this.egoBleedStacks = egoBleedStacks;
    }

    public String beginAnimation(boolean ego) {
        return baseAnimation(ego) + "_begin";
    }

    public String strikeAnimation(boolean ego) {
        return baseAnimation(ego);
    }

    public String recoveryAnimation(boolean ego) {
        return baseAnimation(ego) + "_end";
    }

    public int strikeTicks(boolean ego) {
        return ego ? egoStrikeTicks : normalStrikeTicks;
    }

    public int hitTick(boolean ego) {
        return ego ? egoHitTick : normalHitTick;
    }

    public int recoveryTicks() {
        return recoveryTicks;
    }

    public int minDamage(boolean ego) {
        return ego ? egoMinDamage : normalMinDamage;
    }

    public int maxDamage(boolean ego) {
        return ego ? egoMaxDamage : normalMaxDamage;
    }

    public int bleedStacks(boolean ego) {
        return ego ? egoBleedStacks : normalBleedStacks;
    }

    private String baseAnimation(boolean ego) {
        return ego ? egoAnimation : normalAnimation;
    }
}
