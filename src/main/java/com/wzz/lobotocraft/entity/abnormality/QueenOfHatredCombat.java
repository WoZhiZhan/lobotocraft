package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.ai.FacilityAbnormalityTargetPolicy;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

final class QueenOfHatredCombat {
    private QueenOfHatredCombat() {
    }

    static boolean isHeroHostile(LivingEntity entity, EntityQueenOfHatred queen) {
        if (entity == queen || !entity.isAlive()) return false;
        if (entity instanceof AbstractAbnormality abnormality) {
            return abnormality.hasEscape();
        }
        return FacilityCombatRules.isOrdealCombatant(entity)
                || FacilityAbnormalityTargetPolicy.isCoreSuppressionCombatant(entity);
    }

    /**
     * Special compatibility target for escaped abnormalities whose original
     * target matrix does not include arbitrary abnormality-vs-abnormality
     * combat. The Queen is intentionally opt-in; the base escaped flag is not
     * used here so existing target restrictions remain intact.
     */
    static boolean isQueenOfHatredCombatTarget(LivingEntity entity) {
        return entity instanceof EntityQueenOfHatred queen
                && queen.isAbnormalityCombatTarget();
    }

    static boolean isVillainTarget(LivingEntity entity, EntityQueenOfHatred queen) {
        if (entity == queen || !entity.isAlive()) return false;
        if (QueenOfHatredStaff.isAttackableStaff(entity)) return true;
        if (entity instanceof Player) return false;
        if (entity instanceof AbstractAbnormality abnormality) return abnormality.isAbnormalityCombatTarget();
        return FacilityCombatRules.isOrdealCombatant(entity)
                || FacilityAbnormalityTargetPolicy.isCoreSuppressionCombatant(entity);
    }

    static Vec3 clipToBlocks(ServerLevel level, LivingEntity source, Vec3 start, Vec3 desiredEnd) {
        HitResult hit = level.clip(new ClipContext(start, desiredEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
        if (hit.getType() != HitResult.Type.BLOCK) return desiredEnd;
        Vec3 direction = desiredEnd.subtract(start);
        if (direction.lengthSqr() < 1.0E-7D) return hit.getLocation();
        return hit.getLocation().subtract(direction.normalize().scale(0.04D));
    }

    static List<LivingEntity> lineTargets(ServerLevel level, Vec3 start, Vec3 end, double radius,
                                          Predicate<LivingEntity> filter) {
        double safeRadius = Math.max(0.1D, radius);
        AABB bounds = new AABB(
                Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)
        ).inflate(safeRadius);
        Vec3 axis = end.subtract(start);
        double lengthSquared = axis.lengthSqr();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> filter.test(entity)
                        && distanceToSegment(entity.getBoundingBox().getCenter(), start, end) <= safeRadius);
        targets.sort(Comparator.comparingDouble(entity -> projection(
                entity.getBoundingBox().getCenter(), start, axis, lengthSquared)));
        return targets;
    }

    static boolean damageBlack(EntityQueenOfHatred attacker, LivingEntity target, int minimum, int maximum) {
        if (target == null || !target.isAlive()) return false;
        int lower = Math.min(minimum, maximum);
        int upper = Math.max(minimum, maximum);
        float amount = lower + attacker.getRandom().nextInt(upper - lower + 1);
        return target.hurt(DamageHelper.getDamage(attacker, "black"), amount);
    }

    static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 axis = end.subtract(start);
        double lengthSquared = axis.lengthSqr();
        if (lengthSquared < 1.0E-7D) return point.distanceTo(start);
        double t = Mth.clamp(point.subtract(start).dot(axis) / lengthSquared, 0.0D, 1.0D);
        return point.distanceTo(start.add(axis.scale(t)));
    }

    private static double projection(Vec3 point, Vec3 start, Vec3 axis, double lengthSquared) {
        if (lengthSquared < 1.0E-7D) return 0.0D;
        return Mth.clamp(point.subtract(start).dot(axis) / lengthSquared, 0.0D, 1.0D);
    }
}
