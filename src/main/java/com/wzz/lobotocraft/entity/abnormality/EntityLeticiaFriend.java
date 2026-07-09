package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.AbnormalityCombatUtil;
import com.wzz.lobotocraft.util.AnimationTimingUtil;
import com.wzz.lobotocraft.util.DamageHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class EntityLeticiaFriend extends BaseGeoEntity {
    private static final EntityDataAccessor<Integer> ANIMATION_VERSION =
            SynchedEntityData.defineId(EntityLeticiaFriend.class, EntityDataSerializers.INT);
    private static final String ANIMATION_FILE = "leticia_friend";
    private static final String SPAWN_ANIMATION = "animation.laetitiafriend.spawn";
    private static final String ATTACK1_ANIMATION = "animation.laetitiafriend.attack1";
    private static final String ATTACK2_ANIMATION = "animation.laetitiafriend.attack2";
    private static final int SPAWN_ANIMATION_TICKS =
            AnimationTimingUtil.getAnimationDurationTicks(ANIMATION_FILE, SPAWN_ANIMATION, 25);
    private static final int ATTACK1_ANIMATION_TICKS =
            AnimationTimingUtil.getAnimationDurationTicks(ANIMATION_FILE, ATTACK1_ANIMATION, 31);
    private static final int ATTACK2_ANIMATION_TICKS =
            AnimationTimingUtil.getAnimationDurationTicks(ANIMATION_FILE, ATTACK2_ANIMATION, 38);
    private static final int ATTACK1_HIT_TICK =
            AnimationTimingUtil.getNearestKeyframeTick(ANIMATION_FILE, ATTACK1_ANIMATION, 0.9583D, 20);
    private static final int ATTACK2_HIT_TICK =
            AnimationTimingUtil.getNearestKeyframeTick(ANIMATION_FILE, ATTACK2_ANIMATION, 1.2917D, 26);
    private static final int ATTACK_INTERVAL_TICKS = ATTACK2_ANIMATION_TICKS;
    private static final int IDLE_SOUND_INTERVAL_TICKS = 80;
    private static final double FRONT_ATTACK_RANGE = 4.0D;
    private static final double FRONT_ATTACK_WIDTH = 2.0D;

    private int spawnAnimationTimer = SPAWN_ANIMATION_TICKS;
    private int attackAnimationTimer = 0;
    private int attackAnimationDuration = 0;
    private int attackHitTick = 0;
    private float pendingAttackDamage = 0.0F;
    private boolean attackDamageApplied = false;
    private LivingEntity pendingAttackTarget = null;
    private int idleSoundCooldown = IDLE_SOUND_INTERVAL_TICKS;
    private String lastClientAnimation = "";
    private int lastClientAnimationVersion = -1;

    public EntityLeticiaFriend(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setAnimation("spawn");
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIMATION_VERSION, 0);
    }

    @Override
    public void setAnimation(String name) {
        super.setAnimation(name);
        if (!this.level().isClientSide) {
            this.entityData.set(ANIMATION_VERSION, this.entityData.get(ANIMATION_VERSION) + 1);
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LeticiaFriendAttackGoal(this, 1.05D, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
                this, LivingEntity.class, 10, true, false, this::isValidAttackTarget));
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (target != null && !isValidAttackTarget(target)) {
            target = null;
        }
        super.setTarget(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) {
            return;
        }

        if (spawnAnimationTimer > 0) {
            spawnAnimationTimer--;
            if (spawnAnimationTimer == 0 && !isAttackAnimating()) {
                setAnimation("idle");
            }
        }

        if (isAttackAnimating()) {
            attackAnimationTimer++;
            if (!attackDamageApplied && attackAnimationTimer >= attackHitTick) {
                attackDamageApplied = true;
                hurtFrontalTargets(pendingAttackTarget, pendingAttackDamage);
            }
            if (attackAnimationTimer >= attackAnimationDuration) {
                clearAttackAnimation();
                setAnimation("idle");
            }
        }

        idleSoundCooldown--;
        if (idleSoundCooldown <= 0) {
            playFriendSound(randomIdleSound());
            idleSoundCooldown = IDLE_SOUND_INTERVAL_TICKS;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        if (!isValidAttackTarget(living)) {
            return false;
        }
        if (isAttackAnimating()) {
            return true;
        }

        boolean attack1 = this.random.nextBoolean();
        attackAnimationTimer = 0;
        attackAnimationDuration = attack1 ? ATTACK1_ANIMATION_TICKS : ATTACK2_ANIMATION_TICKS;
        attackHitTick = attack1 ? ATTACK1_HIT_TICK : ATTACK2_HIT_TICK;
        attackDamageApplied = false;
        pendingAttackTarget = living;
        pendingAttackDamage = 8.0F + this.random.nextInt(3);
        setAnimation(attack1 ? "attack1" : "attack2");
        playFriendSound(randomIdleSound());
        return true;
    }

    private boolean hurtFrontalTargets(LivingEntity directTarget, float damage) {
        Vec3 look = this.getLookAngle().normalize();
        boolean hurt = false;
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(FRONT_ATTACK_RANGE, 2.0D, FRONT_ATTACK_RANGE),
                this::isValidAttackTarget)) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(this.position());
            double forward = toTarget.dot(look);
            if (target != directTarget && (forward < 0.0D || forward > FRONT_ATTACK_RANGE)) {
                continue;
            }
            double side = toTarget.subtract(look.scale(forward)).horizontalDistance();
            if (target != directTarget && side > FRONT_ATTACK_WIDTH) {
                continue;
            }
            hurt |= target.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
        }
        return hurt;
    }

    private boolean isAttackAnimating() {
        return attackAnimationDuration > 0;
    }

    private void clearAttackAnimation() {
        attackAnimationTimer = 0;
        attackAnimationDuration = 0;
        attackHitTick = 0;
        pendingAttackDamage = 0.0F;
        attackDamageApplied = false;
        pendingAttackTarget = null;
    }

    private boolean isValidAttackTarget(LivingEntity target) {
        if (target == null || target == this || !target.isAlive()) {
            return false;
        }
        return !(target instanceof EntityLeticiaFriend)
                && AbnormalityCombatUtil.isValidSuppressorTarget(this, target);
    }

    @Override
    public void die(DamageSource source) {
        setAnimation("dead");
        playFriendSound(ModSounds.LETICIA_FRIEND_DEATH.get());
        super.die(source);
    }

    private SoundEvent randomIdleSound() {
        return switch (this.random.nextInt(3)) {
            case 0 -> ModSounds.LETICIA_FRIEND_IDLE_1.get();
            case 1 -> ModSounds.LETICIA_FRIEND_IDLE_2.get();
            default -> ModSounds.LETICIA_FRIEND_IDLE_3.get();
        };
    }

    private void playFriendSound(SoundEvent sound) {
        if (sound != null && !this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(), sound,
                    SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    @Override
    public String name() {
        return "leticia_friend";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityLeticiaFriend> event) {
        String animation = getAnimation();
        int animationVersion = this.entityData.get(ANIMATION_VERSION);
        if (!animation.equals(lastClientAnimation) || animationVersion != lastClientAnimationVersion) {
            event.getController().forceAnimationReset();
            lastClientAnimation = animation;
            lastClientAnimationVersion = animationVersion;
        }

        return switch (animation) {
            case "spawn" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.laetitiafriend.spawn"));
            case "dead" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.laetitiafriend.dead"));
            case "attack1" -> event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.laetitiafriend.attack1"));
            case "attack2" -> event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.laetitiafriend.attack2"));
            default -> {
                if (event.isMoving()) {
                    yield event.setAndContinue(RawAnimation.begin().thenLoop("animation.laetitiafriend.move"));
                }
                yield event.setAndContinue(RawAnimation.begin().thenLoop("animation.laetitiafriend.idle"));
            }
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 350.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
    }

    private static class LeticiaFriendAttackGoal extends MeleeAttackGoal {
        public LeticiaFriendAttackGoal(EntityLeticiaFriend mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(mob, speedModifier, followingTargetEvenIfNotSeen);
        }

        @Override
        protected int getAttackInterval() {
            return adjustedTickDelay(ATTACK_INTERVAL_TICKS);
        }
    }
}
