package com.wzz.lobotocraft.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;

class PanicTypeTest {
    @Test
    void effectiveMaximaSelectExactlyOneTypeIncludingEquipmentBonuses() {
        assertEquals(PanicType.BERSERK, PanicType.select(121.0F, 120.0F));
        assertEquals(PanicType.PRUDENCE, PanicType.select(120.0F, 121.0F));
        assertEquals(PanicType.PRUDENCE, PanicType.select(120.0F, 120.0F));
    }

    @Test
    void activeTypeIsFrozenEvenIfEquipmentChangesMidPanic() {
        assertEquals(PanicType.BERSERK,
                PanicType.resolve(PanicType.BERSERK, true, 1.0F, 1000.0F));
        assertEquals(PanicType.PRUDENCE,
                PanicType.resolve(PanicType.PRUDENCE, true, 1000.0F, 1.0F));
        assertEquals(PanicType.NONE,
                PanicType.resolve(PanicType.BERSERK, false, 1000.0F, 1.0F));
    }

    @Test
    void corruptedOrCheatedNumbersRemainDeterministic() {
        assertEquals(PanicType.PRUDENCE, PanicType.select(Float.NaN, Float.NaN));
        assertEquals(PanicType.BERSERK,
                PanicType.select(Float.POSITIVE_INFINITY, -100.0F));
        assertEquals(PanicType.PRUDENCE,
                PanicType.select(-100.0F, Float.POSITIVE_INFINITY));
        assertEquals(PanicType.NONE, PanicType.byName("hacked-value"));
        assertEquals(PanicType.NONE, PanicType.byName(null));
    }

    @Test
    void panicTypePersistsUntilMentalValueExceedsHalfAfterReload() {
        CompoundTag activeTag = new CompoundTag();
        activeTag.putFloat("MentalValue", 0.0F);
        activeTag.putFloat("MaxMentalValue", 100.0F);
        activeTag.putString("PanicType", PanicType.BERSERK.name());
        MentalValue active = new MentalValue();
        active.loadNBTData(activeTag);
        assertEquals(PanicType.BERSERK, active.getPanicType());

        CompoundTag recoveringTag = activeTag.copy();
        recoveringTag.putFloat("MentalValue", 50.0F);
        MentalValue recovering = new MentalValue();
        recovering.loadNBTData(recoveringTag);
        assertEquals(PanicType.BERSERK, recovering.getPanicType());

        CompoundTag recoveredTag = activeTag.copy();
        recoveredTag.putFloat("MentalValue", 50.01F);
        MentalValue recovered = new MentalValue();
        recovered.loadNBTData(recoveredTag);
        assertEquals(PanicType.NONE, recovered.getPanicType());

        activeTag.putString("PanicType", "not-an-enum");
        MentalValue corrupted = new MentalValue();
        corrupted.loadNBTData(activeTag);
        assertEquals(PanicType.NONE, corrupted.getPanicType());
    }

    @Test
    void recoveryThresholdIsStrictlyAboveHalf() {
        assertEquals(false, PanicType.hasRecovered(50.0F, 100.0F));
        assertEquals(true, PanicType.hasRecovered(50.01F, 100.0F));
        assertEquals(true, PanicType.isActive(PanicType.PRUDENCE, 50.0F, 100.0F));
        assertEquals(false, PanicType.isActive(PanicType.PRUDENCE, 50.01F, 100.0F));
        assertEquals(false, PanicType.isActive(PanicType.NONE, 40.0F, 100.0F));
    }
}
