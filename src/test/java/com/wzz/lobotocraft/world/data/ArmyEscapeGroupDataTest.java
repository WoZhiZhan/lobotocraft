package com.wzz.lobotocraft.world.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmyEscapeGroupDataTest {
    @Test
    void closedGroupSurvivesRestartUntilEveryMemberAcknowledgesCleanup() {
        UUID group = UUID.randomUUID();
        UUID loaded = UUID.randomUUID();
        UUID unloaded = UUID.randomUUID();
        ArmyEscapeGroupData data = new ArmyEscapeGroupData();
        data.create(group, 2);
        data.register(group, loaded, 2);
        data.register(group, unloaded, 2);
        data.close(group);

        ArmyEscapeGroupData restored = ArmyEscapeGroupData.load(data.save(new CompoundTag()));
        assertTrue(restored.isClosed(group));
        restored.acknowledgeCleanup(group, loaded);
        assertTrue(restored.isClosed(group));
        restored.acknowledgeCleanup(group, unloaded);
        assertFalse(restored.isClosed(group));
        assertEquals(0, restored.groupCount());
    }

    @Test
    void groupOnlyCompletesAfterAllExpectedMembersDetonate() {
        UUID group = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ArmyEscapeGroupData data = new ArmyEscapeGroupData();
        data.create(group, 2);
        data.register(group, first, 2);
        data.register(group, second, 2);
        assertFalse(data.markDetonated(group, first, 2));
        assertTrue(data.markDetonated(group, second, 2));
    }

    @Test
    void legacyClosedGroupWaitsForAnUnloadedMemberThatWasNotKnownAtCloseTime() {
        UUID group = UUID.randomUUID();
        UUID loaded = UUID.randomUUID();
        UUID late = UUID.randomUUID();
        ArmyEscapeGroupData data = new ArmyEscapeGroupData();
        data.create(group, 2);
        data.register(group, loaded, 2);
        data.close(group);
        data.acknowledgeCleanup(group, loaded);
        assertTrue(data.isClosed(group));

        data.register(group, late, 2);
        assertTrue(data.isClosed(group));
        data.acknowledgeCleanup(group, late);
        assertFalse(data.isClosed(group));
    }
}
