package com.wzz.lobotocraft.init;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.wzz.lobotocraft.client.shader.CCShaderInstance;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.util.Objects;

public class ModShaders {
    public static int renderTime;
    public static float renderFrame;

    public static CCShaderInstance SCREEN_DISTORTION_SHADER;
    public static Uniform screenDistortionTime;
    public static Uniform screenDistortionIntensity;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        SCREEN_DISTORTION_SHADER = CCShaderInstance.create(
                event.getResourceProvider(),
                ResourceUtil.createInstance("screen_distortion"),
                DefaultVertexFormat.POSITION_TEX
        );
        event.registerShader(SCREEN_DISTORTION_SHADER, ModShaders::shieldShader);
    }

    public static void shieldShader(ShaderInstance shader) {
        SCREEN_DISTORTION_SHADER = (CCShaderInstance) shader;
        screenDistortionTime = Objects.requireNonNull(
                SCREEN_DISTORTION_SHADER.getUniform("time"));
        screenDistortionIntensity = Objects.requireNonNull(
                SCREEN_DISTORTION_SHADER.getUniform("intensity"));

        SCREEN_DISTORTION_SHADER.onApply(() ->
                screenDistortionTime.set((float) renderTime + renderFrame)
        );
    }
}