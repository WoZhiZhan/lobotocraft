package com.wzz.lobotocraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


@OnlyIn(value=Dist.CLIENT)
public class AttackParticle extends TextureSheetParticle {
    private final SpriteSet spriteSet;

    public static StParticleProvider provider(SpriteSet spriteSet) {
        return new StParticleProvider(spriteSet);
    }
    public static class StParticleProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public StParticleProvider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new AttackParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, spriteSet);
        }
    }

    public AttackParticle(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.setSize(0.0f, 0.0f);
        this.quadSize *= 1.0f;
        this.lifetime = 15;
        this.gravity = -0.1f;
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.pickSprite(spriteSet);
    }

    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    public void tick() {
        super.tick();
    }
}