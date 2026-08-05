package com.wzz.lobotocraft.util;

import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Collision-safe placement used when an entity changes to a larger form. */
public final class SafeTransformationUtil {
    private static final double BOUNDS_EPSILON = 1.0E-6D;

    private SafeTransformationUtil() {
    }

    /**
     * Returns the current position when the target dimensions already fit. Otherwise, searches
     * only inside the smallest registered facility area containing the entity.
     */
    @Nullable
    public static Vec3 findPositionForDimensions(ServerLevel level, LivingEntity entity,
                                                  EntityDimensions targetDimensions,
                                                  int horizontalRange, int verticalRange) {
        AABB currentTargetBox = targetDimensions.makeBoundingBox(
                entity.getX(), entity.getY(), entity.getZ());
        if (isValid(level, entity, currentTargetBox, true)) {
            return entity.position();
        }

        FacilityAreaEntry area = findContainingArea(level, entity.position());
        if (area == null) {
            return null;
        }

        AABB areaBounds = new AABB(
                area.minPos.getX(), area.minPos.getY(), area.minPos.getZ(),
                area.maxPos.getX() + 1.0D, area.maxPos.getY() + 1.0D, area.maxPos.getZ() + 1.0D);
        BlockPos origin = entity.blockPosition();
        int maxHorizontal = Math.max(0, horizontalRange);
        int maxVertical = Math.max(0, verticalRange);

        for (int radius = 0; radius <= maxHorizontal; radius++) {
            for (int verticalStep = 0; verticalStep <= maxVertical; verticalStep++) {
                int firstDy = verticalStep == 0 ? 0 : -verticalStep;
                int lastDy = verticalStep == 0 ? 0 : verticalStep;
                for (int dy = firstDy; dy <= lastDy; dy += Math.max(1, verticalStep * 2)) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                            BlockPos candidate = origin.offset(dx, dy, dz);
                            double x = candidate.getX() + 0.5D;
                            double y = candidate.getY();
                            double z = candidate.getZ() + 0.5D;
                            AABB targetBox = targetDimensions.makeBoundingBox(x, y, z);
                            if (!contains(areaBounds, targetBox)) continue;
                            if (!level.getBlockState(candidate.below()).isFaceSturdy(
                                    level, candidate.below(), Direction.UP)) continue;
                            if (!isValid(level, entity, targetBox, true)) continue;
                            return new Vec3(x, y, z);
                        }
                    }
                }
            }
        }
        return null;
    }

    public static boolean isPureShrink(EntityDimensions current, EntityDimensions target) {
        return target.width <= current.width && target.height <= current.height;
    }

    private static boolean isValid(ServerLevel level, LivingEntity entity, AABB box, boolean requireLoaded) {
        if (requireLoaded) {
            BlockPos min = new BlockPos(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ));
            BlockPos max = new BlockPos(
                    Mth.floor(box.maxX - BOUNDS_EPSILON),
                    Mth.floor(box.maxY - BOUNDS_EPSILON),
                    Mth.floor(box.maxZ - BOUNDS_EPSILON));
            if (!level.hasChunksAt(min, max)) return false;
        }
        if (!level.noCollision(entity, box)) return false;
        return level.getEntities(entity, box,
                other -> other.isAlive() && !other.isRemoved() && other.isPickable()).isEmpty();
    }

    @Nullable
    private static FacilityAreaEntry findContainingArea(ServerLevel level, Vec3 position) {
        return FacilityAreaSelection.containing(
                        CompanyRoomData.get(level).getAreas(level.dimension().location()),
                        level.dimension().location(), position)
                .orElse(null);
    }

    private static boolean contains(AABB outer, AABB inner) {
        return inner.minX >= outer.minX - BOUNDS_EPSILON
                && inner.minY >= outer.minY - BOUNDS_EPSILON
                && inner.minZ >= outer.minZ - BOUNDS_EPSILON
                && inner.maxX <= outer.maxX + BOUNDS_EPSILON
                && inner.maxY <= outer.maxY + BOUNDS_EPSILON
                && inner.maxZ <= outer.maxZ + BOUNDS_EPSILON;
    }

}
