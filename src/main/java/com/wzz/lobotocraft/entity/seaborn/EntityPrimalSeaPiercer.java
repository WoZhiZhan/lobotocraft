package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
 * 始海穿刺者 (WAW) —— 精英海嗣(远程)。
 * 血量260、黑色抗性0.8其他1.0、移速≈奔跑玩家、
 * 攻击射出小喙同款追踪弹(EntityLightOrb)造成15点红色伤害。
 */
public class EntityPrimalSeaPiercer extends EntityBasinSeaborn implements RangedAttackMob {
    private static final String ATTACK_ANIMATION = "animation.primalsea_piercer.attack";

    public EntityPrimalSeaPiercer(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public String name() { return "primalsea_piercer"; }

    @Override
    protected float getDamageMultiplier() { return 1.0f; }

    /** 黑色抗性0.8、其他抗性1.0 */
    @Override
    protected float getDamageMultiplier(DamageSource source) {
        return DamageHelper.isBlackDamage(source) ? 0.8f : 1.0f;
    }

    @Override
    protected boolean usesMeleeAttackGoal() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 远程攻击:射程16格,冷却30~60tick;速度1.0使其在射程外会主动靠近
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.0D, 30, 60, 16.0F));
        // 无目标时水中/陆地漫游,使其会自行移动
        this.goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 1.0D));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide) return;
        startAttackAnimation("primalsea_piercer", ATTACK_ANIMATION, 19);
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0f, 1.0f);

        // 弓箭(带射出拖尾特效):暴击箭会产生拖尾粒子
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333) - (this.getEyeY() - 0.1);
        double dz = target.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(this.level(), this);
        arrow.setBaseDamage(0);
        arrow.shoot(dx, dy + dist * 0.07, dz, 2.4f, 1.0f); // 速度快、近乎直线
        arrow.setOwner(this);
        arrow.setCritArrow(true); // 暴击箭=带拖尾粒子特效
        arrow.getPersistentData().putFloat("ridgesea_spit_damage", 15f); // 复用命中事件,15点伤害
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        this.level().addFreshEntity(arrow);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityPrimalSeaPiercer> event) {
        if (this.isDeadOrDying()) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.primalsea_piercer.die"));
        }
        if (isPlayingAttackAnim()) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold(ATTACK_ANIMATION));
        }
        if (isMovingForAnimation(event)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.primalsea_piercer.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.primalsea_piercer.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 260.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }
}
