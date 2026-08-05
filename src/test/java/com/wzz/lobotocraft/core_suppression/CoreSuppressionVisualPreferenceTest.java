package com.wzz.lobotocraft.core_suppression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class CoreSuppressionVisualPreferenceTest {
    @Test
    void visualPreferencesArePerPlayerPersistentAndDefaultToColorOnly() {
        UUID full = UUID.randomUUID();
        UUID colorOnly = UUID.randomUUID();
        UUID lateJoiner = UUID.randomUUID();
        CoreSuppressionData data = new CoreSuppressionData();

        data.start(CoreSuppressionType.HOD, full, "owner", 26, 0, 0,
                Map.of(full, true, colorOnly, false));

        assertTrue(data.usesFullVisualEffects(full));
        assertFalse(data.usesFullVisualEffects(colorOnly));
        assertFalse(data.usesFullVisualEffects(lateJoiner));

        CoreSuppressionData loaded = CoreSuppressionData.load(data.save(new CompoundTag()));
        assertTrue(loaded.usesFullVisualEffects(full));
        assertFalse(loaded.usesFullVisualEffects(colorOnly));

        loaded.finish(false);
        assertFalse(loaded.usesFullVisualEffects(full));
    }
}
