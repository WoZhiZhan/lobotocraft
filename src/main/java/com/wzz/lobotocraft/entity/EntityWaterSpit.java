package com.wzz.lobotocraft.entity;

import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EntityWaterSpit extends Entity {
    private static final int MAX_LIFE = 80;
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(EntityWaterSpit.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(EntityWaterSpit.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(EntityWaterSpit.class, EntityDataSerializers.OPTIONAL_UUID);

    public EntityWaterSpit(EntityType<? extends EntityWaterSpit> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
    }

    public EntityWaterSpit(Level level, LivingEntity owner, float damage) {
        this(com.wzz.lobotocraft.init.ModEntities.water_spit.get(), level);
        setOwner(owner);
        this.entityData.set(DAMAGE, damage);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIFETIME, MAX_LIFE);
        this.entityData.define(DAMAGE, 0.0F);
        this.entityData.define(OWNER_UUID, Optional.empty());
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.entityData.set(OWNER_UUID, owner == null ? Optional.empty() : Optional.of(owner.getUUID()));
    }

    @Nullable
    public LivingEntity getOwnerLiving() {
        Optional<UUID> uuid = this.entityData.get(OWNER_UUID);
        if (uuid.isEmpty() || this.level().isClientSide) return null;
        Entity owner = this.level().getEntities().get(uuid.get());
        return owner instanceof LivingEntity living ? living : null;
    }

    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        Vec3 direction = new Vec3(x, y, z).normalize()
                .add(this.random.triangle(0.0D, 0.0172275D * inaccuracy),
                        this.random.triangle(0.0D, 0.0172275D * inaccuracy),
                        this.random.triangle(0.0D, 0.0172275D * inaccuracy))
                .normalize()
                .scale(velocity);
        this.setDeltaMovement(direction);
        double horizontal = direction.horizontalDistance();
        this.setYRot((float) (Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    public void tick() {
        super.tick();

        int life = this.entityData.get(LIFETIME);
        if (life <= 0) {
            discard();
            return;
        }
        this.entityData.set(LIFETIME, life - 1);

        spawnTrailParticles();

        Vec3 movement = this.getDeltaMovement();
        if (movement.lengthSqr() < 1.0E-7D) {
            discard();
            return;
        }

        Vec3 current = this.position();
        Vec3 next = current.add(movement);
        HitResult hit = this.level().clip(new ClipContext(
                current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hit.getLocation();
            this.setPos(hitPos.x, hitPos.y, hitPos.z);
            spawnImpactParticles();
            discard();
            return;
        }

        this.setPos(next.x, next.y, next.z);
        if (!this.level().isClientSide) {
            tryHitLivingTarget();
        }
        this.setDeltaMovement(movement.scale(0.98D).add(0.0D, -0.02D, 0.0D));
    }

    private void tryHitLivingTarget() {
        LivingEntity owner = getOwnerLiving();
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(0.35D),
                entity -> entity.isAlive()
                        && (entity instanceof Player || entity instanceof EntityClerk)
                        && entity != owner
                        && !(entity instanceof Player player && (player.isCreative() || player.isSpectator())));
        if (targets.isEmpty()) return;

        LivingEntity target = targets.get(0);
        DamageSource source = owner != null ? DamageHelper.getDamage(owner, "red") : this.damageSources().generic();
        target.hurt(source, this.entityData.get(DAMAGE));
        spawnImpactParticles();
        discard();
    }

    private void spawnTrailParticles() {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH,
                    this.getX(), this.getY(), this.getZ(), 4, 0.08D, 0.08D, 0.08D, 0.01D);
            server.sendParticles(ParticleTypes.BUBBLE_POP,
                    this.getX(), this.getY(), this.getZ(), 2, 0.05D, 0.05D, 0.05D, 0.0D);
        } else if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnImpactParticles() {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH,
                    this.getX(), this.getY(), this.getZ(), 18, 0.25D, 0.25D, 0.25D, 0.05D);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(LIFETIME, tag.getInt("Lifetime"));
        this.entityData.set(DAMAGE, tag.getFloat("Damage"));
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        this.setDeltaMovement(tag.getDouble("MotionX"), tag.getDouble("MotionY"), tag.getDouble("MotionZ"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Lifetime", this.entityData.get(LIFETIME));
        tag.putFloat("Damage", this.entityData.get(DAMAGE));
        this.entityData.get(OWNER_UUID).ifPresent(uuid -> tag.putUUID("OwnerUUID", uuid));
        Vec3 motion = this.getDeltaMovement();
        tag.putDouble("MotionX", motion.x);
        tag.putDouble("MotionY", motion.y);
        tag.putDouble("MotionZ", motion.z);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
}
