package com.wzz.lobotocraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 蝴蝶贴图特效粒子(亡蝶葬仪)。
 * 大小与常规掉落物相当;无初速时为"绽放"形态(原地漂浮2秒消失),
 * 有初速时为"飞行"形态(直线飞行,接触方块后消失,最长15秒)。
 */
@OnlyIn(value = Dist.CLIENT)
public class ButterflyParticle extends TextureSheetParticle {

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet spriteSet) {
        return (type, level, x, y, z, vx, vy, vz) ->
                new ButterflyParticle(level, x, y, z, vx, vy, vz, spriteSet);
    }

    public ButterflyParticle(ClientLevel world, double x, double y, double z,
                             double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.setSize(0.25f, 0.25f);
        this.quadSize = 0.25f; // 约等于掉落物大小
        boolean flying = (vx * vx + vy * vy + vz * vz) > 1.0E-4;
        if (flying) {
            // 飞行形态:直线冲向前方,碰到方块消失,最长15秒
            this.lifetime = 15 * 20;
            this.hasPhysics = true;
            this.gravity = 0.0f;
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
        } else {
            // 绽放形态:原地轻微漂浮,2秒后消失
            this.lifetime = 2 * 20;
            this.hasPhysics = false;
            this.gravity = -0.02f;
            this.xd = (this.random.nextDouble() - 0.5) * 0.02;
            this.yd = this.random.nextDouble() * 0.03;
            this.zd = (this.random.nextDouble() - 0.5) * 0.02;
        }
        this.pickSprite(spriteSet);
    }

    @Override
    public void tick() {
        super.tick();
        // 飞行形态碰撞到方块后立即消失
        if (this.hasPhysics && (this.stoppedByCollision || this.onGround)) {
            this.remove();
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0; // 全亮
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
