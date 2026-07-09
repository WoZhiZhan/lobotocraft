package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.entity.abnormality.EntityCleaner;
import com.wzz.lobotocraft.entity.abnormality.EntityLeticiaFriend;
import com.wzz.lobotocraft.entity.abnormality.EntityWorkerBee;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class AbnormalityCombatUtil {
    private AbnormalityCombatUtil() {
    }

    public static boolean isValidSuppressorTarget(LivingEntity attacker, LivingEntity target) {
        if (target == null || target == attacker || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        if (target instanceof AbstractAbnormality abnormality) {
            return abnormality.canEscape() && abnormality.hasEscape();
        }
        return true;
    }

    public static boolean isAbnormalitySuppressor(Entity entity) {
        return entity instanceof EntityLeticiaFriend
                || entity instanceof EntityWorkerBee
                || entity instanceof EntityCleaner;
    }
}
