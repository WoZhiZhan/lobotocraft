package com.wzz.lobotocraft.effect;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.EntityWorkerBee;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

public class QueenBeeSporeEffect extends MobEffect {
    public static final String WORKER_SPAWNED_TAG = "lobotocraft_queen_bee_worker_spawned";

    public QueenBeeSporeEffect() {
        super(MobEffectCategory.HARMFUL, 0xE6C334);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                "d67b7fb6-3ad9-4f7c-a0e6-69e50d3210df",
                -0.5D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration > 0 && duration % 40 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        if (living.level().isClientSide || living.isDeadOrDying()) {
            return;
        }
        ParticleUtil.spawnParticles(living, ParticleUtil.getDustParticle(1.0f, 0.78f, 0.08f, 1.2f), 8, 0.02D);
        boolean markedNoTombstone = false;
        if (living instanceof EntityClerk clerk && clerk.getHealth() <= 8.0F
                && !clerk.getPersistentData().getBoolean(EntityClerk.NO_TOMBSTONE_TAG)) {
            EntityClerk.markNoTombstone(clerk);
            markedNoTombstone = true;
        }

        living.hurt(DamageHelper.getDamage(living, "lobotocraft:red"), 8.0F);
        if (markedNoTombstone && living.isAlive()) {
            living.getPersistentData().remove(EntityClerk.NO_TOMBSTONE_TAG);
        }
    }

    public static boolean canInfect(LivingEntity living) {
        if (living == null || !living.isAlive()) {
            return false;
        }
        if (living instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return living instanceof Villager || living instanceof EntityClerk;
    }

    public static boolean shouldSpawnWorkerFrom(LivingEntity dead) {
        return dead instanceof Player || dead instanceof Villager || dead instanceof EntityClerk;
    }

    public static boolean spawnWorkerFromCorpse(LivingEntity dead) {
        if (!shouldSpawnWorkerFrom(dead) || !(dead.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        CompoundTag data = dead.getPersistentData();
        if (data.getBoolean(WORKER_SPAWNED_TAG)) {
            return false;
        }
        data.putBoolean(WORKER_SPAWNED_TAG, true);

        EntityWorkerBee worker = ModEntities.worker_bee.get().create(serverLevel);
        if (worker == null) {
            return false;
        }
        worker.moveTo(dead.getX(), dead.getY(), dead.getZ(),
                serverLevel.random.nextFloat() * 360.0F, 0.0F);
        worker.finalizeSpawn(
                serverLevel,
                serverLevel.getCurrentDifficultyAt(worker.blockPosition()),
                MobSpawnType.MOB_SUMMONED,
                null,
                null);
        serverLevel.addFreshEntity(worker);
        serverLevel.playSound(null, worker.blockPosition(), ModSounds.WORKER_BEE_SPAWN.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
        ParticleUtil.spawnParticles(worker, ParticleUtil.getDustParticle(1.0f, 0.78f, 0.08f, 1.5f), 20, 0.04D);
        return true;
    }
}
