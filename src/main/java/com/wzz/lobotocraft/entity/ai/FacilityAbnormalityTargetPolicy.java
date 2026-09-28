package com.wzz.lobotocraft.entity.ai;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.EntityGeburah;
import com.wzz.lobotocraft.entity.EntityRedShoesClerk;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Opt-in target identities; each abnormality retains its own hostility rules. */
public final class FacilityAbnormalityTargetPolicy {
    private FacilityAbnormalityTargetPolicy() {
    }

    public static boolean isCoreSuppressionCombatant(LivingEntity entity) {
        return entity instanceof EntityGeburah && entity.isAlive() && !entity.isRemoved();
    }

    public static boolean isPersonnel(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) return false;
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return entity instanceof EntityClerk || entity instanceof EntityRedShoesClerk;
    }
}
