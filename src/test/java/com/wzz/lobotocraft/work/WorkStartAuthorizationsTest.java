package com.wzz.lobotocraft.work;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkStartAuthorizationsTest {
    private static final UUID ABNORMALITY = UUID.fromString("4a2b093c-cb5d-4a3b-aeb9-9a7b03059691");
    private static final String FACILITY = "lobotocraft:facility";

    @Test
    void screenAuthorizationAllowsTwoMinutesForAPlayerDecision() {
        assertEquals(2400L, WorkStartAuthorizations.TTL_TICKS);
    }

    @Test
    void acceptsOnlyTheExactFreshInteraction() {
        assertTrue(WorkStartAuthorizations.matches(ABNORMALITY, FACILITY, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK, ABNORMALITY, FACILITY, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK));
    }

    @Test
    void rejectsExpiredCrossDimensionAndCrossModeRequests() {
        assertFalse(WorkStartAuthorizations.matches(ABNORMALITY, FACILITY, 119,
                WorkStartAuthorizations.Interaction.NORMAL_WORK, ABNORMALITY, FACILITY, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK));
        assertFalse(WorkStartAuthorizations.matches(ABNORMALITY, FACILITY, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK, ABNORMALITY, "minecraft:overworld", 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK));
        assertFalse(WorkStartAuthorizations.matches(ABNORMALITY, FACILITY, 120,
                WorkStartAuthorizations.Interaction.NORMAL_WORK, ABNORMALITY, FACILITY, 120,
                WorkStartAuthorizations.Interaction.TOOL_USE));
    }
}
