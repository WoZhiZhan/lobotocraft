package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class FacilityDayMeltdownIdentityTest {
    @Test
    void targetCodeSurvivesSaveReloadForUnloadedEntityCleanup() {
        UUID target = UUID.randomUUID();
        FacilityDayData data = new FacilityDayData();
        data.addMeltdown(target, 1200L, 3, MeltdownPolicy.DARK_SKADI_CODE);

        FacilityDayData reloaded = FacilityDayData.load(data.save(new CompoundTag()));

        assertEquals(MeltdownPolicy.DARK_SKADI_CODE,
            reloaded.getActiveMeltdowns().get(target).targetCode());
    }

    @Test
    void legacyEntryWithoutIdentityRemainsRecognizableAsLegacy() {
        UUID target = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        ListTag meltdowns = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Target", target);
        entry.putLong("Deadline", 1200L);
        entry.putInt("Level", 2);
        meltdowns.add(entry);
        root.put("ActiveMeltdowns", meltdowns);

        FacilityDayData reloaded = FacilityDayData.load(root);

        assertEquals("", reloaded.getActiveMeltdowns().get(target).targetCode());
    }
}
