package com.wzz.lobotocraft.client.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientVisualConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_BORDERS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FEAR_VISUALS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FEAR_CAMERA_SHAKE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FEAR_AUDIO_ENABLED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("visuals");
        DAMAGE_BORDERS_ENABLED = builder
                .comment("Show the colored full-screen border when taking Lobotocraft damage.")
                .define("damageBordersEnabled", true);
        FEAR_VISUALS_ENABLED = builder
                .comment("Show fear vignettes and overhead fear text.")
                .define("fearVisualsEnabled", true);
        FEAR_CAMERA_SHAKE_ENABLED = builder
                .comment("Allow short camera impulses from high-level fear.")
                .define("fearCameraShakeEnabled", true);
        FEAR_AUDIO_ENABLED = builder
                .comment("Allow fear audio and temporary environmental sound ducking.")
                .define("fearAudioEnabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientVisualConfig() {
    }
}
