package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 脊海喷吐者 (TETH) —— 普通海嗣(远程)。
 * 血量25、全抗1.2、移速≈苦力怕、从口中喷出蓝色粒子(抛物线、可躲、射程10),命中造成6点红色伤害。
 */
public class EntityRidgeSeaSpitter extends EntityBasinSeaborn implements RangedAttackMob {

    private int attackAnimTimer = 0;

    public EntityRidgeSeaSpitter(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public String name() { return "ridgesea_spitter"; }

    @Override
    protected float getDamageMultiplier() { return 1.2f; }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 远程攻击:射程10格,每40tick一次
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.0D, 40, 10.0F));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide) return;
        attackAnimTimer = 15;
        // 攻击音效:羊驼吐口水
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.0f, 1.0f);

        // 抛物线投射(类似弓箭、飞行较慢可躲开)
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333) - (this.getEyeY() - 0.1);
        double dz = target.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        Arrow arrow = new Arrow(this.level(), this);
        arrow.setBaseDamage(0); // 伤害由命中事件处理,这里设0避免双重计算
        arrow.shoot(dx, dy + dist * 0.15, dz, 0.8f, 6.0f); // 速度较慢
        arrow.setOwner(this);
        // 标记为喷吐者的投射物,用于命中判定
        arrow.getPersistentData().putFloat("ridgesea_spit_damage", 6f);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        this.level().addFreshEntity(arrow);

        // 蓝色粒子拖尾(从口部喷出)
        if (this.level() instanceof ServerLevel server) {
            Vec3 mouth = this.position().add(0, this.getEyeHeight(), 0);
            server.sendParticles(ParticleTypes.SPLASH,
                    mouth.x, mouth.y, mouth.z, 12, 0.2, 0.2, 0.2, 0.02);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (attackAnimTimer > 0) attackAnimTimer--;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityRidgeSeaSpitter> event) {
        if (attackAnimTimer > 0) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.ridgesea_spitter.attack"));
        }
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.ridgesea_spitter.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.ridgesea_spitter.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }
}
