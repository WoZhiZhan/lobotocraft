package com.wzz.lobotocraft.entity.projectile;

import com.wzz.lobotocraft.init.ModItems;
import com.wzz.lobotocraft.item.ego.must_open_path.MustOpenPathMechanics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public final class MustOpenPathAnchorProjectile extends ThrowableItemProjectile {
    private static final String ORIGIN_DIMENSION = "OriginDimension";
    private static final String ORIGIN_X = "OriginX";
    private static final String ORIGIN_Y = "OriginY";
    private static final String ORIGIN_Z = "OriginZ";
    private static final double MAX_DISTANCE_SQUARED = 25.0D * 25.0D;

    private ResourceKey<Level> originDimension = Level.OVERWORLD;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 flightStart = Vec3.ZERO;
    private boolean resolved;
    private boolean fullyCharged;

    public MustOpenPathAnchorProjectile(EntityType<? extends MustOpenPathAnchorProjectile> type,
                                        Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public MustOpenPathAnchorProjectile(EntityType<? extends MustOpenPathAnchorProjectile> type,
                                        LivingEntity owner, Level level,
                                        ResourceKey<Level> originDimension, Vec3 origin) {
        super(type, owner, level);
        this.originDimension = originDimension;
        this.origin = origin;
        this.flightStart = owner.getEyePosition();
        setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                level().addParticle(ParticleTypes.BUBBLE,
                        getX() + random.nextGaussian() * 0.08D,
                        getY() + random.nextGaussian() * 0.08D,
                        getZ() + random.nextGaussian() * 0.08D,
                        random.nextGaussian() * 0.035D,
                        random.nextGaussian() * 0.035D,
                        random.nextGaussian() * 0.035D);
            }
            for (int i = 0; i < 2; i++) {
                level().addParticle(ParticleTypes.SPLASH,
                        getX() + random.nextGaussian() * 0.06D,
                        getY() + random.nextGaussian() * 0.06D,
                        getZ() + random.nextGaussian() * 0.06D,
                        random.nextGaussian() * 0.08D,
                        random.nextGaussian() * 0.08D,
                        random.nextGaussian() * 0.08D);
            }
            return;
        }
        if (flightStart == Vec3.ZERO) flightStart = position();
        if (flightStart.distanceToSqr(position()) >= MAX_DISTANCE_SQUARED) {
            resolve(position());
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!level().isClientSide) resolve(hitResult.getLocation());
    }

    private void resolve(Vec3 landing) {
        if (resolved) return;
        resolved = true;
        if (getOwner() instanceof ServerPlayer player) {
            MustOpenPathMechanics.activateAt(player, originDimension, origin, landing, fullyCharged);
        }
        discard();
    }

    public void setFullyCharged(boolean fullyCharged) {
        this.fullyCharged = fullyCharged;
    }

    public boolean isFullyCharged() {
        return fullyCharged;
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return ModItems.MUST_OPEN_PATH_WEAPON.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(ORIGIN_DIMENSION, originDimension.location().toString());
        tag.putDouble(ORIGIN_X, origin.x);
        tag.putDouble(ORIGIN_Y, origin.y);
        tag.putDouble(ORIGIN_Z, origin.z);
        tag.putBoolean("FullyCharged", fullyCharged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(ORIGIN_DIMENSION));
        if (id != null) {
            originDimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
        }
        origin = new Vec3(tag.getDouble(ORIGIN_X), tag.getDouble(ORIGIN_Y), tag.getDouble(ORIGIN_Z));
        fullyCharged = tag.getBoolean("FullyCharged");
        flightStart = position();
        setNoGravity(true);
    }
}
