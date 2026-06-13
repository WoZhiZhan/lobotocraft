package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.init.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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

import java.util.List;

/**
 * 伊莎玛拉之泪 —— 由伊莎玛拉召唤的固定召唤物。
 * 血量120;受击时播放攻击动画并对周围3x3范围玩家造成8-12点黑色伤害;
 * 区分"被玩家击杀"与"自然(清理)死亡":前者播放被击杀音效,后者播放自然死亡音效。
 * 自然死亡(非玩家击杀)会让伊莎玛拉巨兽形态延长12秒——由伊莎玛拉侧统计。
 */
public class EntityIsharmlaTear extends BaseGeoEntity {

    private int attackAnimTimer = 0;
    // 是否被玩家击杀(用于死亡音效与巨兽延时统计)
    private boolean killedByPlayer = false;

    public EntityIsharmlaTear(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "isharmla_tear";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityIsharmlaTear> event) {
        String anim = getAnimation();
        if ("attack".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla_tear.attack"));
        } else if ("start".equals(anim)) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.isharmla_tear.start"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.isharmla_tear.idle"));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (attackAnimTimer > 0) {
            attackAnimTimer--;
            if (attackAnimTimer == 0) {
                setAnimation("idle");
            }
        }
    }

    /** 机制2:受到攻击时播放攻击动画,对周围3x3范围玩家造成8-12点黑色伤害 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (!this.level().isClientSide && this.isAlive()) {
            // 记录是否被玩家击杀
            if (this.getHealth() - amount <= 0 && source.getEntity() instanceof Player) {
                this.killedByPlayer = true;
            }
            triggerCounterAttack();
        }
        return result;
    }

    private void triggerCounterAttack() {
        setAnimation("attack");
        attackAnimTimer = 20;
        ServerLevel level = (ServerLevel) this.level();
        level.playSound(null, this.blockPosition(), ModSounds.ISHARMLA_TEAR_ATTACK.get(),
                SoundSource.HOSTILE, 1.0f, 1.0f);
        // 3x3 范围内玩家受到 8-12 点黑色伤害
        List<Player> players = level.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(1.5, 1.5, 1.5), Player::isAlive);
        float dmg = 8 + this.random.nextInt(5); // 8-12
        for (Player p : players) {
            p.hurt(this.damageSources().mobAttack(this), dmg);
        }
    }

    public boolean wasKilledByPlayer() {
        return killedByPlayer;
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            ServerLevel level = (ServerLevel) this.level();
            if (killedByPlayer || source.getEntity() instanceof Player) {
                level.playSound(null, this.blockPosition(), ModSounds.ISHARMLA_TEAR_DEATH_KILLED.get(),
                        SoundSource.HOSTILE, 1.0f, 1.0f);
            } else {
                level.playSound(null, this.blockPosition(), ModSounds.ISHARMLA_TEAR_DEATH_NATURAL.get(),
                        SoundSource.HOSTILE, 1.0f, 1.0f);
            }
        }
        super.die(source);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("KilledByPlayer", killedByPlayer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        killedByPlayer = tag.getBoolean("KilledByPlayer");
    }
}
