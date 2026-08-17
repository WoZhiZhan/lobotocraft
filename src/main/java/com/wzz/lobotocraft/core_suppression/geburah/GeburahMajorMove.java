package com.wzz.lobotocraft.core_suppression.geburah;

import java.util.List;

public enum GeburahMajorMove {
    BLOOD_MIST("bloodmist", 92, 75 * 20, List.of(
            new Cue(8, 5, 9, HitKind.BOUND_TARGET, true),
            new Cue(39, 1, 1, HitKind.BOUND_TARGET, false),
            new Cue(45, 1, 1, HitKind.BOUND_TARGET, false),
            new Cue(59, 1, 1, HitKind.BOUND_TARGET, false),
            new Cue(63, 1, 1, HitKind.BOUND_TARGET, false),
            new Cue(67, 16, 20, HitKind.BOUND_TARGET, false),
            new Cue(78, 31, 38, HitKind.BOUND_TARGET, false))),
    EGO_BLOOD_MIST("EGObloodmist", 129, 90 * 20, List.of(
            new Cue(8, 8, 11, HitKind.BOUND_TARGET, true),
            new Cue(39, 1, 1, HitKind.BOUND_TARGET, false),
            new Cue(62, 1, 1, HitKind.BOUND_TARGET, false),
            new Cue(75, 23, 25, HitKind.BOUND_TARGET, false),
            new Cue(96, 32, 50, HitKind.FORWARD_AREA, false))),
    BODIES_EVERYWHERE("bodieseverywhere", 80, 160 * 20, List.of(
            new Cue(46, 42, 60, HitKind.WIDE_AREA, false)));

    private final String animation;
    private final int durationTicks;
    private final int cooldownTicks;
    private final List<Cue> cues;

    GeburahMajorMove(String animation, int durationTicks, int cooldownTicks,
                     List<Cue> cues) {
        this.animation = animation;
        this.durationTicks = durationTicks;
        this.cooldownTicks = cooldownTicks;
        this.cues = List.copyOf(cues);
    }

    public String animation() {
        return animation;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public List<Cue> cues() {
        return cues;
    }

    public boolean bloodMist() {
        return this == BLOOD_MIST || this == EGO_BLOOD_MIST;
    }

    public String displayNameTranslationKey() {
        return switch (this) {
            case BLOOD_MIST, EGO_BLOOD_MIST ->
                    "attack.lobotocraft.geburah.blood_mist";
            case BODIES_EVERYWHERE ->
                    "attack.lobotocraft.geburah.bodies_everywhere";
        };
    }

    public record Cue(int tick, int minDamage, int maxDamage,
                      HitKind hitKind, boolean opening) {
    }

    public enum HitKind {
        BOUND_TARGET,
        FORWARD_AREA,
        WIDE_AREA
    }
}
