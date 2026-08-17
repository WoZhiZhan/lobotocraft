package com.wzz.lobotocraft.entity.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class MoveToBlockPosWithElevatorGoalTest {
    @Test
    void supportsBossesAndOtherPathfindingMobs() {
        assertDoesNotThrow(() -> MoveToBlockPosWithElevatorGoal.class.getConstructor(
                PathfinderMob.class, double.class, Supplier.class,
                BooleanSupplier.class, Runnable.class));
    }
}
