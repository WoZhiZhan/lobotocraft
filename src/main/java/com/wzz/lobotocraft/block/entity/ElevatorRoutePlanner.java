package com.wzz.lobotocraft.block.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure route ordering used before any destination chunk is loaded. */
public final class ElevatorRoutePlanner {
    private ElevatorRoutePlanner() {
    }

    public static List<Stop> rank(Stop origin, Iterable<Stop> candidates,
                                  boolean up, int horizontalRadius) {
        if (origin == null || candidates == null || horizontalRadius < 0) return List.of();
        List<Stop> result = new ArrayList<>();
        for (Stop candidate : candidates) {
            if (candidate == null || candidate.equals(origin)) continue;
            long dy = (long) candidate.y - origin.y;
            if ((up && dy <= 0L) || (!up && dy >= 0L)) continue;
            long dx = Math.abs((long) candidate.x - origin.x);
            long dz = Math.abs((long) candidate.z - origin.z);
            if (dx > horizontalRadius || dz > horizontalRadius) continue;
            result.add(candidate);
        }
        result.sort(Comparator
                .comparingLong((Stop stop) -> Math.abs((long) stop.y - origin.y))
                .thenComparingLong(stop -> horizontalDistanceSquared(origin, stop))
                .thenComparingInt(Stop::x)
                .thenComparingInt(Stop::z)
                .thenComparingInt(Stop::y));
        return List.copyOf(result);
    }

    private static long horizontalDistanceSquared(Stop first, Stop second) {
        long dx = (long) first.x - second.x;
        long dz = (long) first.z - second.z;
        return dx * dx + dz * dz;
    }

    public record Stop(int x, int y, int z) {
    }
}
