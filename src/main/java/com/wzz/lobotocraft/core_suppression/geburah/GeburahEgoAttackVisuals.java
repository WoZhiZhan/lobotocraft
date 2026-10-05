package com.wzz.lobotocraft.core_suppression.geburah;

/** Cosmetic trial tuning. The legacy path is selected by one client-side switch. */
public final class GeburahEgoAttackVisuals {
    public static final int MAX_CONTACTS = 4;
    public static final int MAX_ACTIVE_HITS = 12;
    public static final double VIEW_DISTANCE = 48.0D;

    private GeburahEgoAttackVisuals() {
    }

    public static boolean supports(GeburahMove move) {
        return move == GeburahMove.SLASH || move == GeburahMove.STAB;
    }

    public static boolean enabled(boolean switchOn, boolean egoMove, boolean egoManifested,
                                  GeburahMove move) {
        return switchOn && egoMove && egoManifested && supports(move);
    }

    public static boolean animationEligible(String animation, boolean egoManifested) {
        return egoManifested && ("EGOslash".equals(animation) || "EGOstab".equals(animation));
    }

    public static boolean validHit(boolean hurt, float healthLoss, boolean suppressed) {
        return hurt && (suppressed || Float.isFinite(healthLoss) && healthLoss > 0.0F);
    }

    public static double trailLifetime(boolean enhanced, GeburahMove move) {
        return enhanced && move == GeburahMove.SLASH ? 8.0D : 6.0D;
    }

    public static float trailFade(float age) {
        float remaining = Math.max(0.0F, 1.0F - age);
        return remaining * remaining * remaining; // Darker extended tail, not a solid lingering hitbox.
    }

    public static double widthScale(GeburahMove move, float age) {
        return move == GeburahMove.SLASH ? 1.0D + 0.45D * Math.max(0.0F, 1.0F - age * 3.0F)
                : 0.65D + 0.35D * Math.max(0.0F, 1.0F - age);
    }

    public static int hitSegments(int quality, double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0
                || distanceSquared > VIEW_DISTANCE * VIEW_DISTANCE) return 0;
        int segments = quality <= 0 ? 16 : quality == 1 ? 8 : 4;
        return distanceSquared > 24 * 24 ? Math.max(4, segments / 2) : segments;
    }
}
