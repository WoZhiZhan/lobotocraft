package com.wzz.lobotocraft.entity;

import com.wzz.lobotocraft.entity.abnormality.EntityApprovalBird;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;

public class EntityGallows extends BaseGeoEntity {

    /** 绞刑架存在时长：5 秒（100 tick），完成处决动画后消失 */
    private static final int LIFETIME_TICKS = 100;

    /** 被处决者的实体 ID（-1 代表无） */
    private int victimId = -1;
    /** 是否已完成绑定（将受害者拉到绞刑架位置） */
    private boolean bound  = false;
    private int lifeTick   = 0;

    public EntityGallows(EntityType<? extends TamableAnimal> p_21368_, Level p_21369_) {
        super(p_21368_, p_21369_);
    }

    public void setVictimId(int id) {
        this.victimId = id;
    }

    @Override
    public void tick() {
        super.tick();
        if (!bound && victimId != -1) {
            Entity entity = level().getEntity(victimId);
            if (entity instanceof LivingEntity victim) {
                victim.teleportTo(this.getX(), this.getY() + 1.5, this.getZ());
                victim.getPersistentData().putBoolean("NotMove", true);
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 255));
            }
            bound = true;
        }

        if (bound && victimId != -1 && lifeTick < LIFETIME_TICKS - 10) {
            Entity entity = level().getEntity(victimId);
            if (entity instanceof LivingEntity victim && !victim.isAlive()) {
                victim.teleportTo(this.getX(), this.getY() + 1.5, this.getZ());
                victim.getPersistentData().putBoolean("NotMove", true);
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 255));
            }
        }

        lifeTick++;
        if (lifeTick >= LIFETIME_TICKS) {
            if (victimId != -1) {
                Entity entity = level().getEntity(victimId);
                if (entity instanceof LivingEntity victim) {
                    victim.kill();
                    victim.getPersistentData().putBoolean("NotMove", false);
                    for (Entity e : EntityUtil.findAllEntities(this, 5)) {
                        if (e instanceof EntityApprovalBird approvalBird && approvalBird.hasEscape()) {
                            approvalBird.stopEscape();
                        }
                    }
                }
            }
            this.discard();
        }
    }

    // ── 不可推动、不受伤 ────────────────────────────────────────
    @Override public boolean isPushable()     { return false; }
    @Override public void push(double x, double y, double z) {}
    @Override public void pushEntities()      {}
    @Override public void doPush(Entity e)    {}
    @Override public void push(Entity e)      {}
    @Override public void knockback(double s, double x, double z) {}
    @Override public boolean hurt(DamageSource src, float amt) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ── 存档 ────────────────────────────────────────────────────
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VictimId", victimId);
        tag.putInt("LifeTick", lifeTick);
        tag.putBoolean("Bound",  bound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        victimId = tag.getInt("VictimId");
        lifeTick = tag.getInt("LifeTick");
        bound    = tag.getBoolean("Bound");
    }

    // ── 动画 ────────────────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
    }

    @Override public String name() { return "gallows"; }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mob) {
        return null;
    }
}
