package com.wzz.lobotocraft.client.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientVisualConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_BORDERS_ENABLED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("visuals");
        DAMAGE_BORDERS_ENABLED = builder
                .comment("Show the colored full-screen border when taking Lobotocraft damage.")
                .define("damageBordersEnabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientVisualConfig() {
    }
}
