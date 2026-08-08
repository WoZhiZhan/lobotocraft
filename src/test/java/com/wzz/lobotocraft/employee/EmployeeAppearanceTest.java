package com.wzz.lobotocraft.employee;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EmployeeAppearanceTest {
    @Test
    void malformedOrUnknownSelectionsFallBackToDefault() {
        assertEquals(EmployeeAppearance.DEFAULT,
                new EmployeeAppearance("unknown", "id", "path", "texture").sanitized());
        assertEquals(EmployeeAppearance.DEFAULT,
                new EmployeeAppearance(EmployeeAppearance.MODERN_YSM_PROVIDER,
                        "id", "path", "bad\u00a7texture").sanitized());
    }

    @Test
    void appearanceNbtRoundTripsWithoutYsmClasses() {
        EmployeeAppearance appearance = new EmployeeAppearance(
                EmployeeAppearance.LEGACY_YSM_PROVIDER, "yes_steve_model:clerk",
                "clerk", "yes_steve_model:clerk/default.png");

        EmployeeAppearance loaded = EmployeeAppearance.load(appearance.save());

        assertEquals(appearance, loaded);
        assertTrue(loaded.usesYsm());
        assertFalse(loaded.isDefault());
        assertEquals(EmployeeAppearance.DEFAULT, EmployeeAppearance.load(new CompoundTag()));
    }
}
