package com.wzz.lobotocraft.core_suppression.geburah;

import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

final class GeburahSpawnLocator {
    private static final String ROOM_ID = "disciplinary/upper-main";
    private static final String ROOM_GROUP = "dept/disciplinary/passage/dept-geburah-1";
    private static final BlockPos DEBUG_CONTROL_ANCHOR = new BlockPos(202, 273, 44);
    private static final BlockPos DEBUG_FLAT_ANCHOR = new BlockPos(0, 4, 0);

    private GeburahSpawnLocator() {
    }

    static Optional<BlockPos> find(ServerLevel level, Entity entity) {
        List<FacilityWorldLayout.AreaDefinition> areas = FacilityWorldLayout.areasForGroup(
                level.dimension().location(), ROOM_GROUP);
        FacilityWorldLayout.AreaDefinition room = areas.stream()
                .filter(area -> ROOM_ID.equals(area.id()))
                .findFirst().orElse(null);
        if (room == null) return Optional.empty();

        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        addExitSeeds(level, entity, room, open, visited);
        if (open.isEmpty()) return Optional.empty();

        BlockPos center = new BlockPos(
                (room.minPos().getX() + room.maxPos().getX()) / 2,
                (room.minPos().getY() + room.maxPos().getY()) / 2,
                (room.minPos().getZ() + room.maxPos().getZ()) / 2);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        while (!open.isEmpty()) {
            BlockPos current = open.remove();
            double distance = current.distSqr(center);
            if (distance < bestDistance) {
                best = current;
                bestDistance = distance;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.relative(direction).offset(0, dy, 0);
                    if (!room.contains(next) || visited.contains(next)
                            || !isSafe(level, entity, next)) continue;
                    visited.add(next);
                    open.add(next);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    static Optional<BlockPos> findDebugFallback(ServerLevel level, Entity entity) {
        Optional<BlockPos> control = findNear(level, entity, DEBUG_CONTROL_ANCHOR, 16, 12);
        if (control.isPresent()) return control;

        Optional<BlockPos> sharedSpawn = findNear(level, entity, level.getSharedSpawnPos(), 16, 12);
        return sharedSpawn.isPresent()
                ? sharedSpawn
                : findNear(level, entity, DEBUG_FLAT_ANCHOR, 16, 6);
    }

    private static Optional<BlockPos> findNear(ServerLevel level, Entity entity, BlockPos anchor,
                                                int horizontalRange, int verticalRange) {
        for (int radius = 0; radius <= horizontalRange; radius++) {
            for (int verticalDistance = 0; verticalDistance <= verticalRange; verticalDistance++) {
                int variants = verticalDistance == 0 ? 1 : 2;
                for (int variant = 0; variant < variants; variant++) {
                    int dy = variant == 0 ? -verticalDistance : verticalDistance;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                            BlockPos candidate = anchor.offset(dx, dy, dz);
                            if (isSafe(level, entity, candidate)) return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static void addExitSeeds(ServerLevel level, Entity entity,
                                     FacilityWorldLayout.AreaDefinition room,
                                     Queue<BlockPos> open, Set<BlockPos> visited) {
        for (int y = room.minPos().getY(); y <= room.maxPos().getY(); y++) {
            for (int z = room.minPos().getZ(); z <= room.maxPos().getZ(); z++) {
                addSeed(level, entity, room, new BlockPos(room.minPos().getX(), y, z),
                        Direction.WEST, open, visited);
                addSeed(level, entity, room, new BlockPos(room.maxPos().getX(), y, z),
                        Direction.EAST, open, visited);
            }
            for (int x = room.minPos().getX(); x <= room.maxPos().getX(); x++) {
                addSeed(level, entity, room, new BlockPos(x, y, room.minPos().getZ()),
                        Direction.NORTH, open, visited);
                addSeed(level, entity, room, new BlockPos(x, y, room.maxPos().getZ()),
                        Direction.SOUTH, open, visited);
            }
        }
    }

    private static void addSeed(ServerLevel level, Entity entity,
                                FacilityWorldLayout.AreaDefinition room, BlockPos inside,
                                Direction outward, Queue<BlockPos> open, Set<BlockPos> visited) {
        if (!isSafe(level, entity, inside)) return;
        BlockPos outside = inside.relative(outward);
        boolean exitsToFacility = FacilityWorldLayout.areaAt(level.dimension().location(), outside)
                .filter(area -> !room.id().equals(area.id()))
                .isPresent();
        if (exitsToFacility && visited.add(inside)) open.add(inside);
    }

    private static boolean isSafe(ServerLevel level, Entity entity, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP)
                || floor.getBlock() instanceof AbstractGlassBlock) return false;
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        if (feetState.getBlock() instanceof AbstractGlassBlock
                || headState.getBlock() instanceof AbstractGlassBlock
                || !feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) {
            return false;
        }
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(
                feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        return level.noCollision(entity, box);
    }
}
