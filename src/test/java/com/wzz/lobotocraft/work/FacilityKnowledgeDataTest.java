package com.wzz.lobotocraft.work;

import com.wzz.lobotocraft.capability.PlayerAbnormalityData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FacilityKnowledgeDataTest {
    @Test
    void oldPlayersContributeKnowledgeWithoutSharingPersonalResearch() {
        PlayerAbnormalityData alice = new PlayerAbnormalityData();
        PlayerAbnormalityData bob = new PlayerAbnormalityData();
        alice.unlockManual("O-03-89", 0);
        alice.addEquipmentDevelopmentCount("O-03-89", "weapon", 1);
        bob.unlockManual("O-03-89", 100);
        FacilityKnowledgeData facility = new FacilityKnowledgeData();
        assertTrue(facility.merge(alice));
        assertTrue(facility.merge(bob));
        assertTrue(facility.applyTo(alice));
        assertTrue(facility.applyTo(bob));
        assertTrue(alice.isManualUnlocked("O-03-89", 100));
        assertTrue(bob.isManualUnlocked("O-03-89", 0));
        assertEquals(0, bob.getEquipmentDevelopmentCount("O-03-89", "weapon"));
        assertEquals(1, alice.getEquipmentDevelopmentCount("O-03-89", "weapon"));
        assertFalse(facility.merge(alice));
        assertFalse(facility.applyTo(bob));
    }

    @Test
    void savedKnowledgeSurvivesRestartAndIsAvailableToNewPlayers() {
        FacilityKnowledgeData original = new FacilityKnowledgeData();
        assertTrue(original.unlock("O-03-89", 3));
        assertFalse(original.unlock("O-03-89", 3));
        CompoundTag saved = original.save(new CompoundTag());
        assertFalse(saved.contains("PersonalEgoResearch"));
        assertFalse(saved.contains("EquipmentCount"));
        FacilityKnowledgeData restored = FacilityKnowledgeData.load(saved);
        PlayerAbnormalityData newcomer = new PlayerAbnormalityData();
        assertTrue(restored.applyTo(newcomer));
        assertTrue(newcomer.isManualUnlocked("O-03-89", 3));
        assertFalse(restored.unlock("O-03-89", 3));
    }
}
