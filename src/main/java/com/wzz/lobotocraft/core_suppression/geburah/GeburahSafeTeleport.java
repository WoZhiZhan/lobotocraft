package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GeburahSafeTeleport {
    private static final int SEARCH_RADIUS = 4;
    private static final int VERTICAL_RANGE = 2;
    private static final int CONNECTIVITY_LIMIT = 640;

    private GeburahSafeTeleport() {
    }

    public static Optional<Vec3> findWillOfBattle(ServerLevel level, Entity entity,
                                                   LivingEntity target, boolean ego) {
        Vec3 targetLook = target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (targetLook.lengthSqr() < 1.0E-6D) {
            targetLook = target.position().subtract(entity.position()).multiply(1.0D, 0.0D, 1.0D);
        }
        if (targetLook.lengthSqr() < 1.0E-6D) targetLook = new Vec3(0.0D, 0.0D, 1.0D);
        targetLook = targetLook.normalize();
        double signedDistance = ego ? -6.0D : 2.0D;
        BlockPos preferred = BlockPos.containing(target.position().add(targetLook.scale(signedDistance)));
        return findNearTarget(level, entity, target, preferred);
    }

    public static Optional<Vec3> findBehindTarget(ServerLevel level, Entity entity,
                                                   LivingEntity target, double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0D) return Optional.empty();
        Vec3 targetLook = target.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (targetLook.lengthSqr() < 1.0E-6D) {
            targetLook = target.position().subtract(entity.position())
                    .multiply(1.0D, 0.0D, 1.0D);
        }
        if (targetLook.lengthSqr() < 1.0E-6D) targetLook = new Vec3(0.0D, 0.0D, 1.0D);
        BlockPos preferred = BlockPos.containing(
                target.position().subtract(targetLook.normalize().scale(distance)));
        return findNearTarget(level, entity, target, preferred);
    }

    private static Optional<Vec3> findNearTarget(ServerLevel level, Entity entity,
                                                  LivingEntity target, BlockPos preferred) {
        BlockPos targetFeet = target.blockPosition();

        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (int verticalDistance = 0; verticalDistance <= VERTICAL_RANGE; verticalDistance++) {
                int variants = verticalDistance == 0 ? 1 : 2;
                for (int variant = 0; variant < variants; variant++) {
                    int dy = variant == 0 ? -verticalDistance : verticalDistance;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                            BlockPos candidate = preferred.offset(dx, dy, dz);
                            if (!isLandingSafe(level, entity, candidate)
                                    || !hasSight(level, entity, candidate, target)
                                    || !hasLocalConnection(level, entity, candidate, targetFeet)) continue;
                            return Optional.of(Vec3.atBottomCenterOf(candidate));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<Vec3> findPursuitStep(ServerLevel level, Entity entity,
                                                 LivingEntity target, Vec3 predictedTarget,
                                                 double maximumDistance) {
        if (maximumDistance <= 0.0D || Math.abs(target.getY() - entity.getY()) > 3.0D) {
            return Optional.empty();
        }
        Vec3 direction = predictedTarget.subtract(entity.position()).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 1.0E-6D) return Optional.empty();
        direction = direction.normalize();

        for (double distance = maximumDistance; distance >= 0.5D; distance -= 0.25D) {
            Vec3 desired = entity.position().add(direction.scale(distance));
            BlockPos horizontal = BlockPos.containing(desired.x, entity.getY(), desired.z);
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos feet = horizontal.offset(0, dy, 0);
                Vec3 destination = Vec3.atBottomCenterOf(feet);
                if (!isLandingSafe(level, entity, feet)
                        || !hasSight(level, entity, feet, target)
                        || !isSweptPathSafe(level, entity, entity.position(), destination)) continue;
                return Optional.of(destination);
            }
        }
        return Optional.empty();
    }

    public static Optional<Vec3> findSafeDisplacement(ServerLevel level, Entity entity,
                                                       Vec3 displacement) {
        return findSafeDisplacement(level, entity, displacement, null);
    }

    public static Optional<Vec3> findSafeDisplacement(ServerLevel level, Entity entity,
                                                       Vec3 displacement, Entity ignoredEntity) {
        Vec3 horizontal = displacement.multiply(1.0D, 0.0D, 1.0D);
        double maximumDistance = horizontal.length();
        if (maximumDistance < 0.25D) return Optional.empty();
        Vec3 direction = horizontal.normalize();
        for (double distance = maximumDistance; distance >= 0.25D; distance -= 0.25D) {
            Vec3 desired = entity.position().add(direction.scale(distance));
            BlockPos horizontalFeet = BlockPos.containing(desired.x, entity.getY(), desired.z);
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos feet = horizontalFeet.offset(0, dy, 0);
                Vec3 destination = Vec3.atBottomCenterOf(feet);
                if (!isLandingSafe(level, entity, feet, ignoredEntity)
                        || !isSweptPathSafe(level, entity, entity.position(),
                        destination, ignoredEntity)) continue;
                return Optional.of(destination);
            }
        }
        return Optional.empty();
    }

    private static boolean isLandingSafe(ServerLevel level, Entity entity, BlockPos feet) {
        return isLandingSafe(level, entity, feet, null);
    }

    private static boolean isLandingSafe(ServerLevel level, Entity entity,
                                         BlockPos feet, Entity ignoredEntity) {
        if (FacilityWorldLayout.areaAt(level.dimension().location(), feet).isEmpty()) return false;
        if (!isBlockSafe(level, entity, feet)) return false;
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(
                feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        return level.getEntities(entity, box,
                other -> other != ignoredEntity && other.isAlive() && !other.isRemoved()).isEmpty();
    }

    private static boolean isBlockSafe(ServerLevel level, Entity entity, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below());
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP)
                || floor.getBlock() instanceof AbstractGlassBlock
                || feetState.getBlock() instanceof AbstractGlassBlock
                || headState.getBlock() instanceof AbstractGlassBlock
                || isDangerous(floor) || isDangerous(feetState) || isDangerous(headState)
                || !feetState.getFluidState().isEmpty()
                || !headState.getFluidState().isEmpty()) return false;
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(
                feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        return level.noCollision(entity, box);
    }

    private static boolean isDangerous(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE);
    }

    private static boolean hasSight(ServerLevel level, Entity entity, BlockPos feet,
                                    LivingEntity target) {
        Vec3 start = Vec3.atBottomCenterOf(feet).add(0.0D, entity.getEyeHeight(), 0.0D);
        return level.clip(new ClipContext(start, target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType()
                == HitResult.Type.MISS;
    }

    private static boolean isSweptPathSafe(ServerLevel level, Entity entity,
                                           Vec3 start, Vec3 end) {
        return isSweptPathSafe(level, entity, start, end, null);
    }

    private static boolean isSweptPathSafe(ServerLevel level, Entity entity,
                                           Vec3 start, Vec3 end, Entity ignoredEntity) {
        double distance = start.distanceTo(end);
        int samples = Math.max(1, (int) Math.ceil(distance / 0.25D));
        for (int i = 1; i <= samples; i++) {
            Vec3 point = start.lerp(end, i / (double) samples);
            AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(
                    point.x, point.y, point.z);
            if (!level.noCollision(entity, box)
                    || !level.getEntities(entity, box,
                    other -> other != ignoredEntity && other.isAlive()
                            && !other.isRemoved()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasLocalConnection(ServerLevel level, Entity entity,
                                              BlockPos start, BlockPos targetFeet) {
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        open.add(start);
        visited.add(start);
        int checked = 0;
        while (!open.isEmpty() && checked++ < CONNECTIVITY_LIMIT) {
            BlockPos current = open.remove();
            if (Math.abs(current.getY() - targetFeet.getY()) <= 1
                    && current.distManhattan(targetFeet) <= 2) return true;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.relative(direction).offset(0, dy, 0);
                    if (visited.contains(next)
                            || Math.abs(next.getX() - start.getX()) > 10
                            || Math.abs(next.getZ() - start.getZ()) > 10
                            || Math.abs(next.getY() - start.getY()) > 3
                            || !isBlockSafe(level, entity, next)) continue;
                    visited.add(next);
                    open.add(next);
                }
            }
        }
        return false;
    }
}
