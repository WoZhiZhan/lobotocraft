package com.wzz.lobotocraft.entity.abnormality;

import java.util.Set;

/** Pure role/deadline rules used by the black-forest door state machine. */
final class BlackForestDoorPolicy {
    private BlackForestDoorPolicy() {
    }

    static int capturedRoleCount(Set<String> codes) {
        int count = 0;
        if (codes.contains("large_bird") || codes.contains("O-02-40")) count++;
        if (codes.contains("approval_bird")) count++;
        if (codes.contains("punishing_bird") || codes.contains("O-02-56")) count++;
        return count;
    }

    static boolean shouldRemoveCompletedDoor(boolean encounterStarted, long deadline, long now) {
        return encounterStarted && deadline > 0L && now >= deadline;
    }
}
