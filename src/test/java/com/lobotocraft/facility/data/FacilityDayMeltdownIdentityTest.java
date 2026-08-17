package com.lobotocraft.facility.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class FacilityDayMeltdownIdentityTest {
    @Test
    void targetCodeSurvivesSaveReloadForUnloadedEntityCleanup() {
        UUID target = UUID.randomUUID();
        FacilityDayData data = new FacilityDayData();
        data.addMeltdown(target, 1200L, 3, MeltdownPolicy.DARK_SKADI_CODE,
            MeltdownCause.QLIPHOTH_OVERLOAD);

        FacilityDayData reloaded = FacilityDayData.load(data.save(new CompoundTag()));

        assertEquals(MeltdownPolicy.DARK_SKADI_CODE,
            reloaded.getActiveMeltdowns().get(target).targetCode());
        assertEquals(MeltdownCause.QLIPHOTH_OVERLOAD,
            reloaded.getActiveMeltdowns().get(target).cause());
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
        assertEquals(MeltdownCause.LEGACY_OR_UNKNOWN,
            reloaded.getActiveMeltdowns().get(target).cause());
    }

    @Test
    void birdEggCauseSurvivesSaveReloadAndIsIndependentOfSuppression() {
        UUID target = UUID.randomUUID();
        FacilityDayData data = new FacilityDayData();
        data.addMeltdown(target, 1200L, 3, "O-03-03", MeltdownCause.APOCALYPSE_BIRD_EGG);

        FacilityDayData reloaded = FacilityDayData.load(data.save(new CompoundTag()));

        FacilityDayData.ActiveMeltdown meltdown = reloaded.getActiveMeltdowns().get(target);
        assertEquals(MeltdownCause.APOCALYPSE_BIRD_EGG, meltdown.cause());
        assertTrue(MeltdownLifecycle.survivesWithoutSuppression(meltdown));
    }

    @Test
    void qliphothOverloadSurvivesWithoutCoreSuppression() {
        UUID target = UUID.randomUUID();
        FacilityDayData data = new FacilityDayData();
        data.addMeltdown(target, 1200L, 3, "O-03-03", MeltdownCause.QLIPHOTH_OVERLOAD);

        assertTrue(MeltdownLifecycle.survivesWithoutSuppression(
            data.getActiveMeltdowns().get(target)));
    }
}
