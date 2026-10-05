package com.wzz.lobotocraft.entity.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkPreferenceDisplayTest {
    @Test void originalHelperNegativePreferencesAreVisibleWithoutNegativeBars() {
        for (float rate : new float[]{-0.3F, -0.6F, -0.9F}) {
            assertFalse(WorkPreferenceDisplay.isMissing(rate));
            assertEquals(Math.round(rate * 100) + "%", WorkPreferenceDisplay.text(rate));
            assertEquals(0, WorkPreferenceDisplay.barWidth(rate, 60));
        }
    }

    @Test void sentinelAndMissingRowsRemainUnavailable() {
        for (float rate : new float[]{-1.0F, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertTrue(WorkPreferenceDisplay.isMissing(rate));
            assertEquals("/", WorkPreferenceDisplay.text(rate));
            assertEquals(0, WorkPreferenceDisplay.barWidth(rate, 60));
        }
        assertTrue(Float.isNaN(WorkPreferenceDisplay.valueAt(null, 0, 0)));
        assertTrue(Float.isNaN(WorkPreferenceDisplay.valueAt(new float[][]{null}, 0, 0)));
        assertTrue(Float.isNaN(WorkPreferenceDisplay.valueAt(new float[][]{{0.5F}}, 0, 4)));
    }

    @Test void ordinaryRatesStillFormatAndBarsClamp() {
        assertEquals("0%", WorkPreferenceDisplay.text(0));
        assertEquals("50%", WorkPreferenceDisplay.text(0.5F));
        assertEquals(30, WorkPreferenceDisplay.barWidth(0.5F, 60));
        assertEquals(60, WorkPreferenceDisplay.barWidth(1.2F, 60));
        assertEquals(-0.3F, WorkPreferenceDisplay.valueAt(new float[][]{{-0.3F}}, 0, 0));
    }
}
