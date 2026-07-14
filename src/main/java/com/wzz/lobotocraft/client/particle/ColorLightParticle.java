package com.wzz.lobotocraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wzz.lobotocraft.color.ExtendedColor;
import com.wzz.lobotocraft.init.ModParticleRenderTypes;
import com.wzz.lobotocraft.init.ModShaders;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class ColorLightParticle extends TextureSheetParticle {
    private final float startR;
    private final float startG;
    private final float startB;
    private final float endR;
    private final float endG;
    private final float endB;

    protected ColorLightParticle(ClientLevel level, double x, double y, double z,
                                 double xSpeed, double ySpeed, double zSpeed,
                                 float startR, float startG, float startB,
                                 float endR, float endG, float endB) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.startR = startR;
        this.startG = startG;
        this.startB = startB;
        this.endR = endR;
        this.endG = endG;
        this.endB = endB;

        this.xd = xSpeed * 0.2;
        this.yd = ySpeed * 0.2;
        this.zd = zSpeed * 0.2;
        this.lifetime = 20 + this.random.nextInt(80);
        this.hasPhysics = false;
        this.friction = 0.96F;
        this.scale(0.55F + this.random.nextFloat() * 0.15F);
        this.setColor(startR, startG, startB);
        this.setAlpha(1.0F);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float currentAge = this.age + partialTicks;
        ModShaders.updateParticleShader(currentAge, this.lifetime,
                new Vector3f(startR, startG, startB),
                new Vector3f(endR, endG, endB)
        );
        super.render(buffer, camera, partialTicks);
    }

    @Override
    public void tick() {
        super.tick();
        float lifeProgress = (float) this.age / this.lifetime;
        float r = this.lerp(startR, endR, lifeProgress);
        float g = this.lerp(startG, endG, lifeProgress);
        float b = this.lerp(startB, endB, lifeProgress);
        this.setColor(r, g, b);
        float twinkle = (float) (0.7f + 0.3f * Math.sin(this.age * 0.1));
        this.setAlpha(twinkle * 0.9f);
        this.yd += Math.sin(this.age * 0.03) * 3.0E-4;
        if (this.age > this.lifetime * 0.7) {
            float fadeAlpha = (float) (1.0f - (this.age - this.lifetime * 0.7) / (this.lifetime * 0.3f));
            this.setAlpha(this.alpha * fadeAlpha);
        }
    }

    private float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    @Override
    public int getLightColor(float partialTicks) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ModParticleRenderTypes.SHADER_PARTICLE_SPHERE;
    }

    public static Provider createProvider(SpriteSet sprites, ExtendedColor start, ExtendedColor end) {
        float sr = start.getRed() / 255.0f;
        float sg = start.getGreen() / 255.0f;
        float sb = start.getBlue() / 255.0f;
        float er = end.getRed() / 255.0f;
        float eg = end.getGreen() / 255.0f;
        float eb = end.getBlue() / 255.0f;
        return new Provider(sprites, sr, sg, sb, er, eg, eb);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float startR;
        private final float startG;
        private final float startB;
        private final float endR;
        private final float endG;
        private final float endB;

        public Provider(SpriteSet sprites) {
            this(sprites, 1.0F, 0.85F, 0.1F, 0.8F, 0.6F, 0.0F); // 默认金色
        }

        public Provider(SpriteSet sprites,
                        float startR, float startG, float startB,
                        float endR, float endG, float endB) {
            this.sprites = sprites;
            this.startR = startR;
            this.startG = startG;
            this.startB = startB;
            this.endR = endR;
            this.endG = endG;
            this.endB = endB;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            ColorLightParticle particle = new ColorLightParticle(
                    level, x, y, z, xSpeed, ySpeed, zSpeed,
                    startR, startG, startB, endR, endG, endB
            );
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}