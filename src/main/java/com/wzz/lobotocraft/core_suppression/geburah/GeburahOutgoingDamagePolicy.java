package com.wzz.lobotocraft.core_suppression.geburah;

public final class GeburahOutgoingDamagePolicy {
    private GeburahOutgoingDamagePolicy() {
    }

    public static float apply(float rolledBaseDamage, boolean ego, boolean playerTarget,
                              int abnormalityKillBonus, boolean hasSpeed,
                              int slownessAmplifier, boolean exhausted) {
        if (!Float.isFinite(rolledBaseDamage) || rolledBaseDamage <= 0.0F) return 0.0F;
        float damage = rolledBaseDamage;
        if (ego && playerTarget) damage += 2.0F;
        if (ego) damage += Math.max(0, Math.min(5, abnormalityKillBonus));
        if (hasSpeed) damage *= 1.1F;
        if (slownessAmplifier >= 0) damage *= 1.0F + 0.1F * (slownessAmplifier + 1);
        if (exhausted) damage *= 0.3F;
        return damage;
    }
}
