package com.wzz.lobotocraft.employee.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Collision-only line test that is safe to execute on the background path planner. */
public final class NavigationLineOfSight {
    private static final double EPSILON = 1.0E-7D;

    private NavigationLineOfSight() {
    }

    public static boolean isClear(NavigationSnapshot snapshot, Vec3 start, Vec3 end) {
        if (snapshot == null || start == null || end == null) return false;
        int minX = Mth.floor(Math.min(start.x, end.x));
        int maxX = Mth.floor(Math.max(start.x, end.x));
        int minY = Mth.floor(Math.min(start.y, end.y));
        int maxY = Mth.floor(Math.max(start.y, end.y));
        int minZ = Mth.floor(Math.min(start.z, end.z));
        int maxZ = Mth.floor(Math.max(start.z, end.z));
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos position = new BlockPos(x, y, z);
                    CellData cell = snapshot.cell(position).orElse(null);
                    if (cell == null) return false;
                    if (!cell.hasCollision()) continue;
                    for (AABB local : cell.collisionShape().toAabbs()) {
                        if (intersects(start, end, local.move(position))) return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean intersects(Vec3 start, Vec3 end, AABB box) {
        double[] interval = {0.0D, 1.0D};
        return clipAxis(start.x, end.x - start.x, box.minX, box.maxX, interval)
                && clipAxis(start.y, end.y - start.y, box.minY, box.maxY, interval)
                && clipAxis(start.z, end.z - start.z, box.minZ, box.maxZ, interval)
                && interval[1] >= interval[0];
    }

    private static boolean clipAxis(double start, double direction, double minimum,
                                    double maximum, double[] interval) {
        if (Math.abs(direction) <= EPSILON) {
            return start > minimum + EPSILON && start < maximum - EPSILON;
        }
        double near = (minimum - start) / direction;
        double far = (maximum - start) / direction;
        if (near > far) {
            double swap = near;
            near = far;
            far = swap;
        }
        interval[0] = Math.max(interval[0], near);
        interval[1] = Math.min(interval[1], far);
        return interval[1] + EPSILON >= interval[0];
    }
}
