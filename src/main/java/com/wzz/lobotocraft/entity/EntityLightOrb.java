package com.wzz.lobotocraft.entity;

import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class EntityLightOrb extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(EntityLightOrb.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(EntityLightOrb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE =
            SynchedEntityData.defineId(EntityLightOrb.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(EntityLightOrb.class, EntityDataSerializers.OPTIONAL_UUID);

    public static final int MAX_LIFE = 200;
    private static final float MAX_SPEED   = 0.8f;
    private static final float STEER_FORCE = 0.20f;   // 转向力，越大转弯越急
    private static final double HIT_DIST   = 0.7;

    private Vec3 velocity = Vec3.ZERO;

    private final java.util.Random random = new java.util.Random();
    private double offsetX, offsetY, offsetZ;

    public EntityLightOrb(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;

        // 生成随机种子，用于计算平滑偏移
        offsetX = random.nextDouble() * Math.PI * 2;
        offsetY = random.nextDouble() * Math.PI * 2;
        offsetZ = random.nextDouble() * Math.PI * 2;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TARGET_UUID, Optional.empty());
        this.entityData.define(LIFETIME, MAX_LIFE);
        this.entityData.define(DAMAGE, 0f);
        this.entityData.define(OWNER_UUID, Optional.empty());
    }

    public void setTarget(@Nullable LivingEntity target) {
        this.entityData.set(TARGET_UUID,
                target == null ? Optional.empty() : Optional.of(target.getUUID()));
    }

    public void setDamage(float damage) {
        this.entityData.set(DAMAGE, damage);
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.entityData.set(OWNER_UUID,
                owner == null ? Optional.empty() : Optional.of(owner.getUUID()));
    }

    @Nullable
    public LivingEntity getOwner() {
        Optional<UUID> uuidOpt = entityData.get(OWNER_UUID);
        if (uuidOpt.isEmpty() || level().isClientSide) return null;
        Entity entity = level().getEntities().get(uuidOpt.get());
        return entity instanceof LivingEntity ? (LivingEntity) entity : null;
    }

    @Override
    public void tick() {
        super.tick();

        int life = entityData.get(LIFETIME);
        if (life <= 0) { discard(); return; }
        entityData.set(LIFETIME, life - 1);

        if (level().isClientSide) return;

        Optional<UUID> uuidOpt = entityData.get(TARGET_UUID);
        if (uuidOpt.isEmpty()) { discard(); return; }

        Entity found = level().getEntities().get(uuidOpt.get());
        if (!(found instanceof LivingEntity target) || !target.isAlive()) { discard(); return; }

        Vec3 currentPos = position();
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);

        if (currentPos.distanceTo(targetCenter) < HIT_DIST) {
            float damage = entityData.get(DAMAGE);
            LivingEntity owner = getOwner();
            if (owner != null) {
                EntityUtil.clearHurtTime(target, () -> {target.hurt(DamageHelper.getDamage(owner, "black"), damage);});
            }
            discard();
            return;
        }

        double distToTarget = currentPos.distanceTo(targetCenter);
        double offsetScale = Mth.clamp((distToTarget - 1.5) / 4.0, 0.0, 1.0);
        Vec3 dest = targetCenter.add(getSmoothOffset(life).scale(offsetScale));

        Vec3 toTarget = dest.subtract(currentPos);
        double distToDest = toTarget.length();

        // 接近时降速，避免冲过头
        float approachSpeed = (float) Math.min(MAX_SPEED, distToDest * 0.5f + 0.05f);
        Vec3 desiredVelocity = toTarget.normalize().scale(approachSpeed);

        // velocity = lerp(velocity, desiredVelocity, STEER_FORCE)
        velocity = velocity.scale(1.0 - STEER_FORCE)
                .add(desiredVelocity.scale(STEER_FORCE));

        // 限制最大速度
        if (velocity.length() > MAX_SPEED) {
            velocity = velocity.normalize().scale(MAX_SPEED);
        }

        Vec3 newPos = currentPos.add(velocity);
        setPos(newPos.x, newPos.y, newPos.z);
    }

    private Vec3 getSmoothOffset(int life) {
        double freq = 0.04;
        double amplitude = 1.0; // 振幅保持，但靠近时会被 offsetScale 压到 0

        double x = Math.sin(life * freq + offsetX) * amplitude;
        double y = Math.sin(life * freq * 1.4 + offsetY) * amplitude * 0.4;
        double z = Math.cos(life * freq + offsetZ) * amplitude;

        return new Vec3(x, y, z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(LIFETIME, tag.getInt("Lifetime"));
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        if (tag.hasUUID("TargetUUID")) {
            entityData.set(TARGET_UUID, Optional.of(tag.getUUID("TargetUUID")));
        }
        if (tag.hasUUID("OwnerUUID")) {
            entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        if (tag.contains("OffsetX")) {
            offsetX = tag.getDouble("OffsetX");
            offsetY = tag.getDouble("OffsetY");
            offsetZ = tag.getDouble("OffsetZ");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Lifetime", entityData.get(LIFETIME));
        tag.putFloat("Damage", entityData.get(DAMAGE));
        entityData.get(TARGET_UUID).ifPresent(u -> tag.putUUID("TargetUUID", u));
        entityData.get(OWNER_UUID).ifPresent(u -> tag.putUUID("OwnerUUID", u));
        tag.putDouble("OffsetX", offsetX);
        tag.putDouble("OffsetY", offsetY);
        tag.putDouble("OffsetZ", offsetZ);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override public boolean hurt(DamageSource s, float f) { return false; }
    @Override public boolean isPickable()                   { return false; }
    @Override public boolean isPushable()                   { return false; }
}