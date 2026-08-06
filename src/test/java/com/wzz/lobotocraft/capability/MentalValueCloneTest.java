package com.wzz.lobotocraft.capability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MentalValueCloneTest {
    @Test
    void cloneCopiesPersistedExtraMentalWithoutEquipmentAttributeTotal() {
        MentalValue source = new MentalValue() {
            @Override
            public float getExtraMentalValue() {
                return 45.0F;
            }
        };
        source.setExtraMentalValue(15.0F);

        MentalValue replacement = new MentalValue();
        replacement.copyFrom(source);

        assertEquals(15.0F, replacement.getExtraMentalValue());
    }
}
