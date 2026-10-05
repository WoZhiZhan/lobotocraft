package com.wzz.lobotocraft.client.core;

import com.wzz.lobotocraft.client.particle.GeburahGroundShardParticle;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahGroundImpact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/** ParticleEngine owns movement/rendering; this class only reserves a bounded visual budget. */
public final class GeburahGroundImpactEffects {
    private static final List<ActiveShard> ACTIVE = new ArrayList<>();
    private static ClientLevel owner;

    private GeburahGroundImpactEffects() {
    }

    public static void spawn(ResourceLocation dimension, Vec3 impact, float radius, int seed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(dimension)) return;
        tick();
        int count = GeburahGroundImpact.count(mc.options.particles().get().getId(),
                mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(impact), ACTIVE.size());
        var sampledFloors = new HashSet<BlockPos>();
        for (GeburahGroundImpact.Shard shard : GeburahGroundImpact.layout(seed, count, radius)) {
            BlockPos sample = BlockPos.containing(impact.x + shard.offsetX(), impact.y + 0.2D,
                    impact.z + shard.offsetZ());
            // Bounded local sampling: don't create chunks, cross floors, or turn machine models into debris.
            for (int depth = 0; depth < 3; depth++, sample = sample.below()) {
                if (!mc.level.hasChunkAt(sample)) break;
                var state = mc.level.getBlockState(sample);
                if (state.isAir()) continue;
                if (state.getRenderShape() != RenderShape.MODEL || state.hasBlockEntity()
                        || !state.isCollisionShapeFullBlock(mc.level, sample)) break;
                if (!sampledFloors.add(sample)) break;
                GeburahGroundShardParticle particle = new GeburahGroundShardParticle(
                        mc.level, sample.getX() + 0.5D, sample.getY() + 1.005D,
                        sample.getZ() + 0.5D, state, sample, shard);
                ACTIVE.add(new ActiveShard(particle, mc.level.getGameTime() + shard.lifetime() + 1));
                mc.particleEngine.add(particle);
                break;
            }
        }
    }

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (owner != level) {
            clear();
            owner = level;
        }
        if (level == null) return;
        // Expiry also handles particles evicted by ParticleEngine before ever being ticked.
        ACTIVE.removeIf(active -> {
            if (active.particle().isAlive() && level.getGameTime() < active.expires()) return false;
            active.particle().remove();
            return true;
        });
    }

    public static void clear() {
        ACTIVE.forEach(active -> active.particle().remove());
        ACTIVE.clear();
        owner = null;
    }

    private record ActiveShard(GeburahGroundShardParticle particle, long expires) {
    }
}
