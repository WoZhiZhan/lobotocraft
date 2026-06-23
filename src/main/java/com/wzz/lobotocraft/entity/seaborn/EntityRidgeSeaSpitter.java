package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.entity.EntityWaterSpit;
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
import net.minecraft.world.level.Level;
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

    public EntityRidgeSeaSpitter(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public String name() { return "ridgesea_spitter"; }

    @Override
    protected float getDamageMultiplier() { return 1.2f; }

    @Override
    protected boolean usesMeleeAttackGoal() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 远程攻击:射程10格,每40tick一次
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.0D, 40, 10.0F));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide) return;
        startAttackAnimation(13);
        // 攻击音效:羊驼吐口水
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.0f, 1.0f);

        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333) - (this.getEyeY() - 0.1);
        double dz = target.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        EntityWaterSpit spit = new EntityWaterSpit(this.level(), this, 6f);
        spit.moveTo(this.getX(), this.getEyeY() - 0.1D, this.getZ(), this.getYRot(), this.getXRot());
        spit.shoot(dx, dy + dist * 0.15, dz, 0.8f, 6.0f);
        this.level().addFreshEntity(spit);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityRidgeSeaSpitter> event) {
        if (isPlayingAttackAnim()) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.ridgesea_spitter.attack"));
        }
        if (isMovingForAnimation(event)) {
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
