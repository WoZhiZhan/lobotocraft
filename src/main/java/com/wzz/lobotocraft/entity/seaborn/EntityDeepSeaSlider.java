package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
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
    private static final String ATTACK_ANIMATION = "animation.deepsea_slider.attack";

    public EntityDeepSeaSlider(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public String name() { return "deepsea_slider"; }

    @Override
    protected float getDamageMultiplier() { return 1.2f; }

    @Override
    protected int getAttackCooldownTicks() {
        return 26;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof Player player) {
            scheduleAttackDamage("deepsea_slider", ATTACK_ANIMATION, 16, 8, () -> {
                if (player.isAlive() && this.distanceToSqr(player) <= 9.0) {
                    EntityUtil.clearHurtTime(player, () ->
                            player.hurt(DamageHelper.getDamage(this, "black"), 3f));
                    if (player instanceof ServerPlayer serverPlayer) {
                        MentalValueUtil.reduceMentalValue(serverPlayer, 2 + this.random.nextInt(2));
                    }
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
        if (this.isDeadOrDying()) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.deepsea_slider.die"));
        }
        if (isPlayingAttackAnim()) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold(ATTACK_ANIMATION));
        }
        if (isMovingForAnimation(event)) {
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
