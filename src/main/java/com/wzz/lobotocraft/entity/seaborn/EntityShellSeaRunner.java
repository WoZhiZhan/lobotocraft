package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 壳海狂奔者 (TETH) —— 普通海嗣。
 * 血量20、全抗1.2、移速≈奔跑玩家、攻击造成4点红色伤害。
 */
public class EntityShellSeaRunner extends EntityBasinSeaborn {

    public EntityShellSeaRunner(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public String name() { return "shell_sea_runner"; }

    @Override
    protected float getDamageMultiplier() { return 1.2f; }

    @Override
    protected SoundEvent getAttackSound() {
        // 攻击:唤魔者地刺闭合
        return SoundEvents.EVOKER_FANGS_ATTACK;
    }

    @Override
    protected int getAttackCooldownTicks() {
        return 24;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof Player player) {
            scheduleAttackDamage(15, 7, () -> {
                if (player.isAlive() && this.distanceToSqr(player) <= 9.0) {
                    player.hurt(DamageHelper.getDamage(this, "red"), 4f);
                }
            });
        }
        SoundEvent atk = getAttackSound();
        if (atk != null && !this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(), atk,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityShellSeaRunner> event) {
        if (isPlayingAttackAnim()) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.shell_sea_runner.attack"));
        }
        if (isMovingForAnimation(event)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.shell_sea_runner.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.shell_sea_runner.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)   // ≈奔跑玩家
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }
}
