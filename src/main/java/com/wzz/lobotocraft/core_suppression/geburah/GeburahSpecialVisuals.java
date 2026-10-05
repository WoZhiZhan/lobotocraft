package com.wzz.lobotocraft.core_suppression.geburah;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Cosmetic event vocabulary; all attack timings still come from the production controllers. */
public final class GeburahSpecialVisuals {
    public static final int MAX_CASTS = 8;
    public static final int MAX_ACCENTS = 24;
    public static final int SYNC_INTERVAL = 8;
    public static final double VIEW_DISTANCE = 64;
    public static final double BODIES_LENGTH = GeburahMajorMove.BODIES_AREA.length();
    public static final double BODIES_WIDTH = GeburahMajorMove.BODIES_AREA.width();
    public static final double BLOOD_LENGTH = GeburahMajorMove.BLOOD_AREA.length();
    public static final double BLOOD_WIDTH = GeburahMajorMove.BLOOD_AREA.width();

    private GeburahSpecialVisuals() {}

    public enum Skill {
        WILL, EGO_WILL, BLOOD, EGO_BLOOD, BODIES;
        public GeburahMajorMove major() {
            return switch (this) {
                case BLOOD -> GeburahMajorMove.BLOOD_MIST;
                case EGO_BLOOD -> GeburahMajorMove.EGO_BLOOD_MIST;
                case BODIES -> GeburahMajorMove.BODIES_EVERYWHERE;
                default -> null;
            };
        }
        public int duration() {
            return major() == null ? GeburahSpecialController.WINDUP_TICKS
                    + GeburahSpecialController.STRIKE_TICKS + GeburahSpecialController.RECOVERY_TICKS
                    : major().durationTicks();
        }
        public boolean ego() { return this == EGO_WILL || this == EGO_BLOOD || this == BODIES; }
        public static Skill of(GeburahMajorMove move) {
            return switch (move) {
                case BLOOD_MIST -> BLOOD;
                case EGO_BLOOD_MIST -> EGO_BLOOD;
                case BODIES_EVERYWHERE -> BODIES;
            };
        }
    }

    public enum Event { START, SYNC, DASH, CUE, STOP }

    public record Snapshot(UUID caster, int entityId, long castId, Skill skill, Event event,
                           int elapsed, int cueIndex, boolean connected,
                           Vec3 origin, Vec3 target, Vec3 forward) {
        public Snapshot {
            if (caster == null || entityId < 0 || castId <= 0 || skill == null || event == null
                    || elapsed < 0 || elapsed > skill.duration() + 1
                    || !worldPoint(origin) || !worldPoint(target) || !finite(forward)
                    || Math.abs(forward.y) > 0.001 || forward.lengthSqr() < 0.5
                    || forward.lengthSqr() > 1.5) throw new IllegalArgumentException("Invalid special visual snapshot");
            if (event == Event.CUE) {
                if (skill.major() == null ? cueIndex != 0
                        : cueIndex < 0 || cueIndex >= skill.major().cues().size()) {
                    throw new IllegalArgumentException("Invalid special visual cue");
                }
                int expected = skill.major() == null ? GeburahSpecialController.WINDUP_TICKS
                        + GeburahSpecialController.HIT_TICK : skill.major().cues().get(cueIndex).tick();
                if (elapsed != expected) throw new IllegalArgumentException("Cue does not match attack timing");
            } else if (cueIndex != -1 || connected) throw new IllegalArgumentException("Unexpected special cue data");
            if (event == Event.DASH && skill.major() != null) throw new IllegalArgumentException("Major attack cannot dash");
            if (event == Event.DASH && elapsed != GeburahSpecialController.WINDUP_TICKS
                    || event == Event.START && elapsed != 0) throw new IllegalArgumentException("Invalid special start timing");
        }
    }

    public static boolean finite(Vec3 p) {
        return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }
    public static boolean worldPoint(Vec3 p) {
        return finite(p) && Math.abs(p.x) <= 30_000_000 && Math.abs(p.y) <= 30_000_000
                && Math.abs(p.z) <= 30_000_000;
    }
    public static boolean bladeWindow(Skill skill, double elapsed) {
        if (skill == Skill.BODIES) {
            int hit = skill.major().cues().get(0).tick();
            return elapsed >= hit - 5 && elapsed <= hit + 8;
        }
        if (skill.major() != null) {
            return elapsed >= skill.major().cues().get(0).tick()
                    && elapsed <= skill.major().cues().get(skill.major().cues().size() - 1).tick() + 7;
        }
        return elapsed >= GeburahSpecialController.WINDUP_TICKS
                && elapsed <= GeburahSpecialController.WINDUP_TICKS + GeburahSpecialController.STRIKE_TICKS;
    }
    public static int qualitySegments(int quality, double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0 || distanceSquared > VIEW_DISTANCE * VIEW_DISTANCE) return 0;
        int count = quality <= 0 ? 20 : quality == 1 ? 12 : 6;
        return distanceSquared > 32 * 32 ? Math.max(4, count / 2) : count;
    }
}
