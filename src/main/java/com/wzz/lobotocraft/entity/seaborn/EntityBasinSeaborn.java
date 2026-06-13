package com.wzz.lobotocraft.entity.seaborn;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 海嗣怪物基类(深蓝色正午——大群的意志 考验生成的怪物)。
 * 统一处理:全局伤害抗性倍率、受击音效(河豚膨胀)、目标选择。
 * 子类提供各自的抗性倍率、攻击逻辑与动画。
 */
public abstract class EntityBasinSeaborn extends BaseGeoEntity {

    public EntityBasinSeaborn(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
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

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
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

    /** 子类在 doHurtTarget 中调用:延迟 delay tick 后执行伤害结算,并驱动攻击动画 */
    protected void scheduleAttackDamage(int animDuration, int hitDelay, Runnable damageAction) {
        this.attackAnimTimer = animDuration;
        this.pendingDamageDelay = hitDelay;
        this.pendingDamageAction = damageAction;
    }

    /** 攻击动画是否正在播放(供子类 predicate 替代 this.swinging 判定) */
    public boolean isPlayingAttackAnim() {
        return attackAnimTimer > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (attackAnimTimer > 0) attackAnimTimer--;
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
}
