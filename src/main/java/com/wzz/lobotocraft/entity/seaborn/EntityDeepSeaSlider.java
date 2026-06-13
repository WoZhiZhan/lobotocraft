package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.util.DamageHelper;
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
 * 底海滑动者 (TETH) —— 普通海嗣。
 * 血量30、全抗1.2、移速≈苦力怕、攻击造成3点黑色伤害,命中后额外造成3点白色伤害。
 */
public class EntityDeepSeaSlider extends EntityBasinSeaborn {

    public EntityDeepSeaSlider(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public String name() { return "deepsea_slider"; }

    @Override
    protected float getDamageMultiplier() { return 1.2f; }

    @Override
    public boolean doHurtTarget(Entity target) {
        // 项11:动画与伤害同步——播放攻击动画,命中帧(第8tick)再结算伤害
        if (target instanceof Player player) {
            scheduleAttackDamage(15, 8, () -> {
                if (player.isAlive() && this.distanceToSqr(player) <= 9.0) {
                    // 3点黑色伤害 + 命中后额外3点白色伤害
                    player.hurt(DamageHelper.getDamage().getDamageSources().mobAttack(this), 3f);
                    player.hurt(DamageHelper.getDamage().getDamageSources().mobAttack(this), 3f);
                }
            });
        }
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityDeepSeaSlider> event) {
        if (isPlayingAttackAnim()) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.deepsea_slider.attack"));
        }
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.deepsea_slider.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.deepsea_slider.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)    // ≈苦力怕
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }
}
