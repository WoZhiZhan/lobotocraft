package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;

/**
 * 钵海收割者 (WAW) —— 精英海嗣(受击愤怒型)。
 * 血量600、全抗0.8;常态idle/move移动(移速≈行走)不主动攻击;受伤进入愤怒:
 * 每秒-5血、sprint追击(移速≈奔跑2)、攻击7黑伤、每秒对6x6玩家额外5白伤(无视无敌帧)。
 */
public class EntityBasinSeaReaper extends EntityBasinSeaborn {

    private static final EntityDataAccessor<Boolean> ENRAGED =
            SynchedEntityData.defineId(EntityBasinSeaReaper.class, EntityDataSerializers.BOOLEAN);

    private int auraTimer = 0;
    private int drainTimer = 0;

    public EntityBasinSeaReaper(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ENRAGED, false);
    }

    @Override
    public String name() { return "basinsea_reaper"; }

    @Override
    protected float getDamageMultiplier() { return 0.8f; }

    @Override
    protected boolean isAggressiveByDefault() {
        return false;
    }

    public boolean isEnraged() {
        return this.entityData.get(ENRAGED);
    }

    @Override
    protected int getAttackCooldownTicks() {
        return 28;
    }

    private void setEnraged(boolean v) {
        this.entityData.set(ENRAGED, v);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (!this.level().isClientSide && result && !isEnraged()) {
            enrage(source);
        }
        return result;
    }

    private void enrage(DamageSource source) {
        setEnraged(true);
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.28D); // ≈奔跑2
        if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            this.setTarget(attacker);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof Player player) {
            scheduleAttackDamage(15, 8, () -> {
                if (player.isAlive() && this.distanceToSqr(player) <= 9.0) {
                    EntityUtil.clearHurtTime(player, () ->
                            player.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), 7f));
                }
            });
        }
        // 造成伤害音效:剑横扫
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (isEnraged()) {
            drainTimer++;
            if (drainTimer >= 20) {
                drainTimer = 0;
                this.hurt(this.damageSources().generic(), 5f);
            }
            auraTimer++;
            if (auraTimer >= 20) {
                auraTimer = 0;
                List<Player> players = this.level().getEntitiesOfClass(Player.class,
                        this.getBoundingBox().inflate(3, 3, 3), Player::isAlive);
                for (Player p : players) {
                    int prevInvul = p.invulnerableTime;
                    p.invulnerableTime = 0;
                    p.hurt(DamageHelper.getDamage(this, "lobotocraft:white"), 5f);
                    p.invulnerableTime = prevInvul;
                }
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityBasinSeaReaper> event) {
        if (isEnraged()) {
            if (isPlayingAttackAnim()) {
                return event.setAndContinue(RawAnimation.begin().thenPlay("animation.basinsea_reaper.attack"));
            }
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.basinsea_reaper.sprint"));
        }
        if (isMovingForAnimation(event)) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.basinsea_reaper.move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.basinsea_reaper.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)  // ≈行走
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }
}
