package com.wzz.lobotocraft.entity.ai;

import com.wzz.lobotocraft.employee.navigation.FacilityNavigationCache;
import com.wzz.lobotocraft.employee.navigation.NavigationCandidatePolicy;
import com.wzz.lobotocraft.employee.navigation.NavigationNode;
import com.wzz.lobotocraft.employee.navigation.NavigationSnapshot;
import com.wzz.lobotocraft.employee.navigation.NodeEvaluator;
import com.wzz.lobotocraft.employee.navigation.TransitionGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Validates facility patrol destinations against the escaped mob's real dimensions. */
public final class FacilityMobNavigationTarget {
    private FacilityMobNavigationTarget() {
    }

    public static boolean isNavigable(ServerLevel level, PathfinderMob mob, BlockPos candidate) {
        Double y = standingY(level, mob, candidate);
        if (y == null) return false;
        BlockPos minimum = candidate.offset(-2, -2, -2);
        BlockPos maximum = candidate.offset(2, 3 + (int) Math.ceil(mob.getBbHeight()), 2);
        NavigationSnapshot snapshot = FacilityNavigationCache.snapshot(level, minimum, maximum);
        if (snapshot == null) snapshot = NavigationSnapshot.capture(level, minimum, maximum, 250);
        NodeEvaluator evaluator = new NodeEvaluator(snapshot, mob.getBbWidth(), mob.getBbHeight());
        NavigationNode node = new NavigationNode(candidate, y);
        if (!evaluator.evaluate(node, false).traversable()) return false;
        TransitionGenerator transitions = new TransitionGenerator(evaluator, 1, 1, false);
        return NavigationCandidatePolicy.hasLocalMobility(node, transitions::generate);
    }

    private static Double standingY(ServerLevel level, PathfinderMob mob, BlockPos candidate) {
        if (!level.getWorldBorder().isWithinBounds(candidate) || !level.hasChunkAt(candidate)) {
            return null;
        }
        double standingY = candidate.getY();
        VoxelShape localShape = level.getBlockState(candidate).getCollisionShape(level, candidate);
        if (!localShape.isEmpty()) {
            double surface = localShape.bounds().maxY;
            if (surface <= 0.25D) standingY += surface;
        }
        Vec3 standing = new Vec3(candidate.getX() + 0.5D, standingY,
                candidate.getZ() + 0.5D);
        AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(
                standing.x, standing.y, standing.z);
        if (!level.noCollision(mob, box)) return null;
        double halfWidth = mob.getBbWidth() / 2.0D;
        AABB support = new AABB(standing.x - halfWidth, standing.y - 0.0625D,
                standing.z - halfWidth, standing.x + halfWidth,
                standing.y + 1.0E-6D, standing.z + halfWidth);
        return level.getBlockCollisions(mob, support).iterator().hasNext() ? standingY : null;
    }
}
