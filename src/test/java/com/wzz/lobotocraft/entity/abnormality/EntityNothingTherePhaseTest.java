package com.wzz.lobotocraft.entity.abnormality;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EntityNothingTherePhaseTest {
    @Test
    void canonicalPhaseTextureMappingIsStableAndClamped() {
        assertEquals("no_things_have", NothingTherePhaseTextures.forPhase(0));
        assertEquals("nothings_egg", NothingTherePhaseTextures.forPhase(1));
        assertEquals("no_things_have_man", NothingTherePhaseTextures.forPhase(2));
        assertEquals("no_things_have", NothingTherePhaseTextures.forPhase(-100));
        assertEquals("no_things_have", NothingTherePhaseTextures.forPhase(100));
    }
}
