package com.lobotocraft.facility.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FacilityServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue DAY_VOTE_APPROVAL_RATIO;
    public static final ForgeConfigSpec.IntValue DAY_VOTE_DURATION_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("day_transition");
        DAY_VOTE_APPROVAL_RATIO = builder
                .comment("Required approval ratio. Abstentions count as approval; AI employees never vote.")
                .defineInRange("approvalRatio", 1.0D, 0.0D, 1.0D);
        DAY_VOTE_DURATION_TICKS = builder
                .comment("Multiplayer vote duration in server ticks.")
                .defineInRange("voteDurationTicks", 200, 40, 1200);
        builder.pop();
        SPEC = builder.build();
    }

    private FacilityServerConfig() {
    }
}
