package com.wzz.lobotocraft.init;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.wzz.lobotocraft.client.shader.CCShaderInstance;
import com.wzz.lobotocraft.util.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Vector3f;

import java.util.Objects;

public class ModShaders {
    public static int renderTime;
    public static float renderFrame;

    public static CCShaderInstance SCREEN_DISTORTION_SHADER;
    public static Uniform screenDistortionTime;
    public static Uniform screenDistortionIntensity;

    public static CCShaderInstance PARTICLE_SHADER;
    public static Uniform particleTime;
    public static Uniform particleAge;
    public static Uniform particleLifetime;
    public static Uniform particleStartColor;
    public static Uniform particleEndColor;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        SCREEN_DISTORTION_SHADER = CCShaderInstance.create(
                event.getResourceProvider(),
                ResourceUtil.createInstance("screen_distortion"),
                DefaultVertexFormat.POSITION_TEX
        );
        event.registerShader(SCREEN_DISTORTION_SHADER, ModShaders::shieldShader);
        event.registerShader(CCShaderInstance.create(event.getResourceProvider(),
                        ResourceUtil.createInstance("particle"),
                        DefaultVertexFormat.PARTICLE),
                ModShaders::particleShader
        );
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

    public static void particleShader(ShaderInstance shader) {
        PARTICLE_SHADER = (CCShaderInstance) shader;
        particleTime = Objects.requireNonNull(PARTICLE_SHADER.getUniform("time"));
        particleAge = Objects.requireNonNull(PARTICLE_SHADER.getUniform("particleAge"));
        particleLifetime = Objects.requireNonNull(PARTICLE_SHADER.getUniform("particleLifetime"));
        particleStartColor = Objects.requireNonNull(PARTICLE_SHADER.getUniform("startColor"));
        particleEndColor = Objects.requireNonNull(PARTICLE_SHADER.getUniform("endColor"));

        PARTICLE_SHADER.onApply(() -> {
            if (particleTime != null) {
                float time = (Minecraft.getInstance().level != null) ?
                        Minecraft.getInstance().level.getGameTime() * 0.05f : 0;
                particleTime.set(time);
            }
        });
    }

    public static void updateParticleShader(float age, float lifetime, Vector3f startColor, Vector3f endColor) {
        if (PARTICLE_SHADER != null) {
            if (particleAge != null) {
                particleAge.set(age);
            }
            if (particleLifetime != null) {
                particleLifetime.set(lifetime);
            }
            if (particleStartColor != null) {
                particleStartColor.set(startColor.x, startColor.y, startColor.z);
            }
            if (particleEndColor != null) {
                particleEndColor.set(endColor.x, endColor.y, endColor.z);
            }
        }
    }
}