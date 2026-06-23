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
 * 元核孽生者 (HE) —— 精英海嗣(受击愤怒型)。
 * 血量450、全抗1.0;常态idle不动不攻击;受伤进入愤怒:每秒-9血、swim追击(移速≈奔跑3)、
 * 攻击6黑伤、每秒对6x6玩家额外5白伤(无视无敌帧)。
 */
public class EntityNucleicMaleficent extends EntityBasinSeaborn {

    private static final EntityDataAccessor<Boolean> ENRAGED =
            SynchedEntityData.defineId(EntityNucleicMaleficent.class, EntityDataSerializers.BOOLEAN);

    private int auraTimer = 0;
    private int drainTimer = 0;

    public EntityNucleicMaleficent(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ENRAGED, false);
    }

    @Override
    public String name() { return "nucleic_maleficent"; }

    @Override
    protected float getDamageMultiplier() { return 1.0f; }

    @Override
    protected boolean isAggressiveByDefault() {
        return false; // 初始不主动攻击,受击后才愤怒
    }

    public boolean isEnraged() {
        return this.entityData.get(ENRAGED);
    }

    @Override
    protected int getAttackCooldownTicks() {
        return 30;
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
        // 提速到≈奔跑3
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.32D);
        // 锁定攻击者仇恨
        if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            this.setTarget(attacker);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof Player player) {
            scheduleAttackDamage(19, 9, () -> {
                if (player.isAlive() && this.distanceToSqr(player) <= 9.0) {
                    EntityUtil.clearHurtTime(player, () ->
                            player.hurt(DamageHelper.getDamage(this, "black"), 6f));
                    EntityUtil.clearHurtTime(player, () ->
                            player.hurt(DamageHelper.getDamage(this, "white"), 4f + this.random.nextInt(3)));
                }
            });
        }
        // 攻击音效:剑横扫
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
            // 每秒损失9点生命值
            drainTimer++;
            if (drainTimer >= 20) {
                drainTimer = 0;
                this.hurt(this.damageSources().generic(), 9f);
            }
            // 每秒对周围6x6范围玩家额外造成5点白色伤害,无视无敌帧
            auraTimer++;
            if (auraTimer >= 20) {
                auraTimer = 0;
                List<Player> players = this.level().getEntitiesOfClass(Player.class,
                        this.getBoundingBox().inflate(3, 3, 3), Player::isAlive);
                for (Player p : players) {
                    int prevInvul = p.invulnerableTime;
                    p.invulnerableTime = 0;
                    p.hurt(DamageHelper.getDamage(this, "white"), 5f);
                    p.invulnerableTime = prevInvul;
                }
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityNucleicMaleficent> event) {
        if (this.isDeadOrDying()) {
            return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.nucleic_maleficent.die"));
        }
        if (isEnraged()) {
            if (isPlayingAttackAnim()) {
                return event.setAndContinue(RawAnimation.begin().thenPlay("animation.nucleic_maleficent.attack"));
            }
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.nucleic_maleficent.swim"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.nucleic_maleficent.idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 450.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)  // 初始不动
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }
}
