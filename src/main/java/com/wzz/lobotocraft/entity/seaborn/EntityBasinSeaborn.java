package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.util.AnimationTimingUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimationState;

import java.util.EnumSet;

/**
 * 海嗣怪物基类(深蓝色正午——大群的意志 考验生成的怪物)。
 * 统一处理:全局伤害抗性倍率、受击音效(河豚膨胀)、目标选择。
 * 子类提供各自的抗性倍率、攻击逻辑与动画。
 */
public abstract class EntityBasinSeaborn extends BaseGeoEntity {
    private static final EntityDataAccessor<Integer> ATTACK_ANIM_TIMER =
            SynchedEntityData.defineId(EntityBasinSeaborn.class, EntityDataSerializers.INT);

    public EntityBasinSeaborn(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ATTACK_ANIM_TIMER, 0);
    }

    /** 伤害抗性倍率(按伤害源,默认全局固定);子类可覆写按伤害类型区分 */
    protected float getDamageMultiplier(DamageSource source) {
        return getDamageMultiplier();
    }

    /** 全局伤害抗性倍率(1.0=不变,>1受到伤害更多,<1更少) */
    protected abstract float getDamageMultiplier();

    /** 是否主动攻击(孽生者/收割者初始不主动攻击) */
    protected boolean isAggressiveByDefault() {
        return true;
    }

    /** 远程海嗣覆写为 false,避免同时安装近战 AI */
    protected boolean usesMeleeAttackGoal() {
        return true;
    }

    /** 近战攻击间隔,子类按动画长度覆写 */
    protected int getAttackCooldownTicks() {
        return 30;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (usesMeleeAttackGoal()) {
            this.goalSelector.addGoal(2, new SeabornMeleeAttackGoal(this, 1.0D, true));
        }
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        if (isAggressiveByDefault()) {
            this.targetSelector.addGoal(2,
                    new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 抗性倍率(按伤害源)
        float multiplier = getDamageMultiplier(source);
        boolean result = super.hurt(source, amount * multiplier);
        if (!this.level().isClientSide && result) {
            // 受伤音效:河豚膨胀
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        return result;
    }

    /** 攻击命中音效,子类可覆写 */
    protected SoundEvent getAttackSound() {
        return null;
    }

    // ==================== 项11:攻击动画与伤害同步 ====================
    // 近战海嗣的 doHurtTarget 会被 MeleeAttackGoal 立即调用造成伤害,
    // 但攻击动画(swinging)此时刚开始播放,导致"伤害先于动画命中帧"的不同步。
    // 这里提供统一的延迟机制:伤害推迟到动画命中帧再结算。
    protected int attackAnimTimer = 0;      // >0 时正在播放攻击动画(供 predicate 使用)
    private int pendingDamageDelay = 0;     // 伤害结算倒计时
    private Runnable pendingDamageAction = null;

    protected void startAttackAnimation(int animDuration) {
        this.attackAnimTimer = animDuration;
        if (!this.level().isClientSide) {
            this.entityData.set(ATTACK_ANIM_TIMER, animDuration);
        }
    }

    protected void startAttackAnimation(String animationFileName, String animationName, int fallbackDuration) {
        int duration = AnimationTimingUtil.getAnimationDurationTicks(
                animationFileName, animationName, fallbackDuration);
        startAttackAnimation(duration);
    }

    /** 子类在 doHurtTarget 中调用:延迟 delay tick 后执行伤害结算,并驱动攻击动画 */
    protected void scheduleAttackDamage(int animDuration, int hitDelay, Runnable damageAction) {
        startAttackAnimation(animDuration);
        this.pendingDamageDelay = hitDelay;
        this.pendingDamageAction = damageAction;
    }

    protected void scheduleAttackDamage(String animationFileName, String animationName,
                                        int fallbackDuration, int fallbackHitDelay, Runnable damageAction) {
        int duration = AnimationTimingUtil.getAnimationDurationTicks(
                animationFileName, animationName, fallbackDuration);
        int hitDelay = AnimationTimingUtil.getNearestKeyframeTick(
                animationFileName, animationName, fallbackHitDelay);
        scheduleAttackDamage(duration, Math.min(hitDelay, duration), damageAction);
    }

    /** 攻击动画是否正在播放(供子类 predicate 替代 this.swinging 判定) */
    public boolean isPlayingAttackAnim() {
        return this.entityData.get(ATTACK_ANIM_TIMER) > 0;
    }

    protected boolean isMovingForAnimation(AnimationState<?> event) {
        Vec3 movement = this.getDeltaMovement();
        return event.isMoving()
                || movement.x * movement.x + movement.z * movement.z > 1.0E-6D
                || !this.getNavigation().isDone();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (attackAnimTimer > 0) {
            attackAnimTimer--;
            this.entityData.set(ATTACK_ANIM_TIMER, attackAnimTimer);
        }
        if (pendingDamageDelay > 0) {
            pendingDamageDelay--;
            if (pendingDamageDelay == 0 && pendingDamageAction != null) {
                pendingDamageAction.run();
                pendingDamageAction = null;
            }
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean result = super.doHurtTarget(target);
        SoundEvent atk = getAttackSound();
        if (atk != null && !this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(), atk, SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        return result;
    }

    private static class SeabornMeleeAttackGoal extends Goal {
        private final EntityBasinSeaborn mob;
        private final double speedModifier;
        private final boolean followingTargetEvenIfNotSeen;
        private Path path;
        private int ticksUntilNextPathRecalculation;
        private int ticksUntilNextAttack;
        private long lastCanUseCheck;

        private SeabornMeleeAttackGoal(EntityBasinSeaborn mob, double speedModifier,
                                       boolean followingTargetEvenIfNotSeen) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            long gameTime = this.mob.level().getGameTime();
            if (gameTime - this.lastCanUseCheck < 20L) {
                return false;
            }
            this.lastCanUseCheck = gameTime;
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            this.path = this.mob.getNavigation().createPath(target, 0);
            if (this.path != null) {
                return true;
            }
            return this.mob.distanceToSqr(target) <= getAttackReachSqr(target);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (!this.followingTargetEvenIfNotSeen) {
                return !this.mob.getNavigation().isDone();
            }
            if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
                return false;
            }
            return !(target.distanceToSqr(this.mob) > 1024.0D);
        }

        @Override
        public void start() {
            PathNavigation navigation = this.mob.getNavigation();
            navigation.moveTo(this.path, this.speedModifier);
            this.mob.setAggressive(true);
            this.ticksUntilNextPathRecalculation = 0;
            this.ticksUntilNextAttack = 0;
        }

        @Override
        public void stop() {
            LivingEntity target = this.mob.getTarget();
            if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
                this.mob.setTarget(null);
            }
            this.mob.setAggressive(false);
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }

            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            if (--this.ticksUntilNextPathRecalculation <= 0) {
                this.ticksUntilNextPathRecalculation = adjustedTickDelay(4 + this.mob.getRandom().nextInt(7));
                if (!this.mob.getNavigation().moveTo(target, this.speedModifier)) {
                    Vec3 pos = DefaultRandomPos.getPosTowards(this.mob, 8, 7, target.position(), Math.PI / 2);
                    if (pos != null) {
                        this.mob.getNavigation().moveTo(pos.x, pos.y, pos.z, this.speedModifier);
                    }
                }
            }

            if (this.ticksUntilNextAttack > 0) {
                this.ticksUntilNextAttack--;
            }
            checkAndPerformAttack(target, distance);
        }

        private void checkAndPerformAttack(LivingEntity target, double distanceSqr) {
            if (distanceSqr > getAttackReachSqr(target)
                    || this.ticksUntilNextAttack > 0
                    || this.mob.isPlayingAttackAnim()) {
                return;
            }
            this.ticksUntilNextAttack = adjustedTickDelay(this.mob.getAttackCooldownTicks());
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(target);
        }

        private double getAttackReachSqr(LivingEntity target) {
            double width = this.mob.getBbWidth() * 2.0F;
            return Math.max(9.0D, width * width + target.getBbWidth());
        }
    }
}
