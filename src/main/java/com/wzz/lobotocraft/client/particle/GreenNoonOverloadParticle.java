package com.wzz.lobotocraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GreenNoonOverloadParticle extends TextureSheetParticle {
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet spriteSet) {
        return (type, level, x, y, z, vx, vy, vz) ->
                new GreenNoonOverloadParticle(level, x, y, z, vx, vy, vz, spriteSet);
    }

    private GreenNoonOverloadParticle(ClientLevel level, double x, double y, double z,
                                      double vx, double vy, double vz, SpriteSet spriteSet) {
        super(level, x, y, z);
        this.setSize(0.2F, 0.2F);
        this.quadSize = 0.22F + this.random.nextFloat() * 0.18F;
        this.lifetime = 8 + this.random.nextInt(7);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.82F;
        this.xd = vx;
        this.yd = vy + 0.01D;
        this.zd = vz;
        this.pickSprite(spriteSet);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setAlpha(1.0F - (float) this.age / this.lifetime);
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
