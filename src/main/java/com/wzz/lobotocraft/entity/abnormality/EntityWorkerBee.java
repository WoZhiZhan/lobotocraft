package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.effect.QueenBeeSporeEffect;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class EntityWorkerBee extends BaseGeoEntity {
    private static final int ATTACK_ANIMATION_TICKS = 16;
    private int attackAnimationTimer = 0;

    public EntityWorkerBee(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, EntityClerk.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Villager.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true,
                entity -> entity instanceof Player player && !player.isCreative() && !player.isSpectator()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && attackAnimationTimer > 0) {
            attackAnimationTimer--;
            if (attackAnimationTimer == 0) {
                setAnimation("idle");
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        attackAnimationTimer = ATTACK_ANIMATION_TICKS;
        setAnimation("attack");
        float damage = 6.0F + this.random.nextFloat() * 4.0F;
        boolean hurt = living.hurt(DamageHelper.getDamage(this, "lobotocraft:red"), damage);
        playWorkerSound(ModSounds.WORKER_BEE_ATTACK.get());
        if (hurt && !living.isAlive()) {
            QueenBeeSporeEffect.spawnWorkerFromCorpse(living);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        playWorkerSound(this.random.nextBoolean()
                ? ModSounds.WORKER_BEE_DEATH.get()
                : ModSounds.WORKER_BEE_DEATH2.get());
        super.die(source);
    }

    private void playWorkerSound(SoundEvent sound) {
        if (sound != null && !this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(), sound,
                    SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    @Override
    public String name() {
        return "worker_bee";
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityWorkerBee> event) {
        if ("attack".equals(getAnimation())) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("attack"));
        }
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("move"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.5D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 2.0D);
    }
}
