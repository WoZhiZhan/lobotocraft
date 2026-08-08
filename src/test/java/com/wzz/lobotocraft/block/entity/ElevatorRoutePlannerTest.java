package com.wzz.lobotocraft.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElevatorRoutePlannerTest {
    @Test
    void ranksOnlyTheRequestedDirectionAndShaftRadius() {
        var origin = new ElevatorRoutePlanner.Stop(10, 100, 10);
        var ranked = ElevatorRoutePlanner.rank(origin, List.of(
                new ElevatorRoutePlanner.Stop(10, 90, 10),
                new ElevatorRoutePlanner.Stop(11, 120, 10),
                new ElevatorRoutePlanner.Stop(10, 110, 10),
                new ElevatorRoutePlanner.Stop(14, 105, 10)), true, 3);
        assertEquals(List.of(
                new ElevatorRoutePlanner.Stop(10, 110, 10),
                new ElevatorRoutePlanner.Stop(11, 120, 10)), ranked);
    }

    @Test
    void duplicateOriginAndNullInputsCannotCreateRoutes() {
        var origin = new ElevatorRoutePlanner.Stop(0, 0, 0);
        assertTrue(ElevatorRoutePlanner.rank(origin, List.of(origin), true, 3).isEmpty());
        assertTrue(ElevatorRoutePlanner.rank(null, List.of(origin), true, 3).isEmpty());
        assertTrue(ElevatorRoutePlanner.rank(origin, null, true, 3).isEmpty());
        assertTrue(ElevatorRoutePlanner.rank(origin, List.of(origin), true, -1).isEmpty());
    }

    @Test
    void extremeCoordinatesDoNotOverflowDirectionOrDistanceOrdering() {
        var origin = new ElevatorRoutePlanner.Stop(Integer.MIN_VALUE, 0, Integer.MIN_VALUE);
        var exact = new ElevatorRoutePlanner.Stop(Integer.MIN_VALUE, 1, Integer.MIN_VALUE);
        var wrapped = new ElevatorRoutePlanner.Stop(Integer.MAX_VALUE, 2, Integer.MAX_VALUE);
        assertEquals(List.of(exact), ElevatorRoutePlanner.rank(
                origin, List.of(wrapped, exact), true, 3));
    }

    @Test
    void tiesAreStableRegardlessOfRegistryIterationOrder() {
        var origin = new ElevatorRoutePlanner.Stop(0, 10, 0);
        var left = new ElevatorRoutePlanner.Stop(-1, 20, 0);
        var right = new ElevatorRoutePlanner.Stop(1, 20, 0);
        assertEquals(List.of(left, right), ElevatorRoutePlanner.rank(
                origin, List.of(right, left), true, 3));
    }
}
