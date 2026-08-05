package com.wzz.lobotocraft.core_suppression;

/** Maps the shared facility meltdown level to Hod's three stat-reduction stages. */
public final class HodPenaltyPolicy {
    private HodPenaltyPolicy() {
    }

    public static int visualStage(int meltdownLevel) {
        return meltdownLevel >= 4 ? 2 : meltdownLevel >= 2 ? 1 : 0;
    }

    public static int penalty(int meltdownLevel) {
        return (visualStage(meltdownLevel) + 1) * 10;
    }
}
