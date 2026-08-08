package com.wzz.lobotocraft.entity.abnormality;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackForestDoorPolicyTest {
    @Test
    void duplicateBirdsNeverSatisfyTheThreeRoleRequirement() {
        assertEquals(1, BlackForestDoorPolicy.capturedRoleCount(Set.of("large_bird")));
        assertEquals(2, BlackForestDoorPolicy.capturedRoleCount(Set.of("large_bird", "punishing_bird")));
        assertEquals(3, BlackForestDoorPolicy.capturedRoleCount(
                Set.of("large_bird", "approval_bird", "punishing_bird")));
    }

    @Test
    void completedDoorUsesPersistableAbsoluteDeadline() {
        assertFalse(BlackForestDoorPolicy.shouldRemoveCompletedDoor(true, 500L, 499L));
        assertTrue(BlackForestDoorPolicy.shouldRemoveCompletedDoor(true, 500L, 500L));
        assertFalse(BlackForestDoorPolicy.shouldRemoveCompletedDoor(false, 500L, 600L));
    }
}
