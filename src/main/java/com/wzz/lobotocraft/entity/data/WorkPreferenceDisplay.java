package com.wzz.lobotocraft.entity.data;

import java.util.Locale;

/** Presentation of the raw LC preference matrix, separate from the final work-roll chance. */
public final class WorkPreferenceDisplay {
    private WorkPreferenceDisplay() {}

    public static float valueAt(float[][] preferences, int work, int level) {
        if (preferences == null || work < 0 || work >= preferences.length
                || preferences[work] == null || level < 0 || level >= preferences[work].length)
            return Float.NaN;
        return preferences[work][level];
    }

    public static boolean isMissing(float rate) {
        // -1 is the existing "unavailable" sentinel. Other negative values
        // (Helper's -0.3/-0.6/-0.9) are real raw preference penalties.
        return !Float.isFinite(rate) || rate == -1.0F;
    }

    public static String text(float rate) {
        return isMissing(rate) ? "/" : String.format(Locale.ROOT, "%.0f%%", rate * 100.0F);
    }

    public static int barWidth(float rate, int width) {
        return isMissing(rate) ? 0 : Math.max(0, Math.min(Math.max(0, width), (int)(width * rate)));
    }
}
