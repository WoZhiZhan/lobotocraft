package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.control.HostileControlState;
import com.wzz.lobotocraft.entity.ordeal.OrdealEntity;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

public final class FacilityCombatRules {
    private FacilityCombatRules() {
    }

    public static boolean isProtectedCombatant(Entity entity) {
        return entity instanceof AbstractAbnormality || isOrdealCombatant(entity);
    }

    public static boolean isOrdealCombatant(Entity entity) {
        return entity instanceof OrdealEntity
                || entity != null && entity.getPersistentData().getBoolean("lobotocraft_blue_midday_spawn");
    }

    public static boolean isImmuneEnvironment(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.CACTUS);
    }

    public static boolean canDamageProtectedCombatant(DamageSource source) {
        if (isImmuneEnvironment(source)) return false;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity living)) return true;
        if (living instanceof Player) {
            return living.getMainHandItem().getItem() instanceof BaseEgoWeapon
                    || DamageHelper.isMyModDamage(DamageHelper.getDamageResourceKey(source));
        }
        return living instanceof AbstractAbnormality
                || isOrdealCombatant(living)
                || AbnormalityCombatUtil.isAbnormalitySuppressor(living);
    }

    public static boolean ignoresHurtCooldown(DamageSource source) {
        return source.getEntity() != null
                || DamageHelper.isMyModDamage(DamageHelper.getDamageResourceKey(source));
    }

    public static boolean blocksEmployeeFriendlyFire(Entity victim, DamageSource source) {
        // Controlled employees are rescue targets; normal employee friendly-fire
        // protection must not prevent players or other employees from hitting them.
        if (victim instanceof LivingEntity living && HostileControlState.isActive(living)) {
            return false;
        }
        Player attacker = resolvePlayerAttacker(source);
        CombatActor victimKind = combatActor(victim);
        CombatActor attackerKind = combatActor(attacker);
        if (!(victim instanceof net.minecraft.server.level.ServerPlayer target)
                || !(attacker instanceof net.minecraft.server.level.ServerPlayer sourcePlayer)) return false;
        boolean attackerHostile = MentalValueUtil.isPanic(sourcePlayer)
                || HostileControlState.isActive(sourcePlayer);
        return blocksEmployeeFriendlyFire(victimKind, attackerKind, target == sourcePlayer,
                attackerHostile, MentalValueUtil.isPanic(target));
    }

    public static Player resolvePlayerAttacker(DamageSource source) {
        if (source == null) return null;
        Entity causing = source.getEntity();
        if (causing instanceof Player player) return player;
        if (causing instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player player) return player;
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    static boolean blocksEmployeeFriendlyFire(boolean attackerHostile, boolean victimPanicking) {
        return !attackerHostile && !victimPanicking;
    }

    static boolean blocksEmployeeFriendlyFire(CombatActor victim, CombatActor attacker,
                                              boolean samePlayer, boolean attackerHostile,
                                              boolean victimPanicking) {
        return victim.isEmployeePlayer() && attacker.isEmployeePlayer() && !samePlayer
                && blocksEmployeeFriendlyFire(attackerHostile, victimPanicking);
    }

    private static CombatActor combatActor(Entity entity) {
        if (entity instanceof com.wzz.lobotocraft.employee.EmployeeServerPlayer) {
            return CombatActor.AI_EMPLOYEE;
        }
        if (entity instanceof net.minecraft.server.level.ServerPlayer) return CombatActor.HUMAN_PLAYER;
        return CombatActor.NON_PLAYER;
    }

    enum CombatActor {
        HUMAN_PLAYER,
        AI_EMPLOYEE,
        NON_PLAYER;

        boolean isEmployeePlayer() {
            return this != NON_PLAYER;
        }
    }
}
