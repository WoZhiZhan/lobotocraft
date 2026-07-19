package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.listener.VioletDawnEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 紫罗兰的黎明 - 理解的果实。
 */
public class EntityVioletDawn extends BaseGeoEntity {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(EntityVioletDawn.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIM_VERSION =
            SynchedEntityData.defineId(EntityVioletDawn.class, EntityDataSerializers.INT);

    private static final int SELF_DESTRUCT_TICKS = 65 * 20;
    private static final int ATTACK_COOLDOWN_TICKS = 30;
    private static final int DEATH_ANIM_TICKS = 50;
    private static final int MIN_CHASE_TICKS = 10 * 20;
    private static final int MAX_CHASE_TICKS = 15 * 20;
    private static final int SEARCH_LIMIT = 30_000_000;
    private static final double ATTACK_RANGE_SQR = 1.6D;
    private static final double SELF_DESTRUCT_RANGE = 10.0D;

    private String lastClientAnim = "";
    private int lastClientAnimVersion = -1;
    private int lifeTicks = 0;
    private int attackCooldown = 0;
    private int chaseTicksRemaining = 0;
    private boolean countedDeath = false;
    private boolean deathAnimationStarted = false;
    private int deathAnimationTick = 0;
    private boolean deathFinalized = false;
    private DamageSource delayedDeathSource = null;
    private boolean ordealSpawn = false;

    public EntityVioletDawn(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "violet_dawn";
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIM, "idle");
        this.entityData.define(ANIM_VERSION, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.45D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        return super.finalizeSpawn(level, difficulty, spawnType, data, tag);
    }

    public boolean isOrdealSpawn() {
        return ordealSpawn;
    }

    public void setOrdealSpawn(boolean ordealSpawn) {
        this.ordealSpawn = ordealSpawn;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (deathAnimationStarted) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !deathAnimationStarted && !this.level().isClientSide) {
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity living && living != this && isValidTarget(living)) {
                this.setTarget(living);
                this.chaseTicksRemaining = MIN_CHASE_TICKS + this.random.nextInt(MAX_CHASE_TICKS - MIN_CHASE_TICKS + 1);
                this.getNavigation().moveTo(living, 1.0D);
            }
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (!(this.level() instanceof ServerLevel level)) return;

        if (deathAnimationStarted) {
            tickDeathAnimation();
            return;
        }

        if (!this.hasEffect(MobEffects.GLOWING)) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }

        lifeTicks++;
        if (attackCooldown > 0) attackCooldown--;

        if (lifeTicks >= SELF_DESTRUCT_TICKS) {
            selfDestruct(level);
            return;
        }

        tickTarget(level);
    }

    private void tickTarget(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (!isValidTarget(target)) {
            this.setTarget(null);
            chaseTicksRemaining = 0;
            setAnimIfChanged("idle");
            return;
        }

        if (chaseTicksRemaining <= 0) {
            this.setTarget(null);
            this.getNavigation().stop();
            setAnimIfChanged("idle");
            return;
        }
        chaseTicksRemaining--;

        if (isInAttackRange(target)) {
            this.getNavigation().stop();
            setAnimIfChanged("idle");
            tryAttack(level, target);
            return;
        }

        this.getNavigation().moveTo(target, 1.0D);
        setAnimIfChanged("walk");
    }

    private void tryAttack(ServerLevel level, LivingEntity target) {
        if (attackCooldown > 0) {
            return;
        }
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        spawnAttackParticles(level, target);
        if (target.hurt(DamageHelper.getDamage(this, "black"), 1.0F + this.random.nextInt(3))) {
            this.level().playSound(null, this.blockPosition(), ModSounds.VIOLET_DAWN_ATTACK.get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    private boolean isValidTarget(@Nullable LivingEntity target) {
        if (target == null || target == this || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return target instanceof EntityClerk;
    }

    private boolean isInAttackRange(LivingEntity target) {
        return this.getBoundingBox().inflate(0.35D).intersects(target.getBoundingBox())
                || this.distanceToSqr(target) <= ATTACK_RANGE_SQR;
    }

    private void spawnAttackParticles(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleUtil.getDustParticle(0.55F, 0.08F, 0.9F, 1.2F),
                target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                18, 0.25D, 0.35D, 0.25D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD,
                target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                8, 0.25D, 0.25D, 0.25D, 0.01D);
    }

    private void selfDestruct(ServerLevel level) {
        if (deathAnimationStarted) {
            return;
        }
        notifyRemoved(level);
        this.level().playSound(null, this.blockPosition(), ModSounds.VIOLET_DAWN_DEATH.get(),
                SoundSource.HOSTILE, 1.2F, 1.0F);
        spawnSelfDestructParticles(level);
        hurtSelfDestructTargets(level);
        zeroNearbyAbnormalityCounters(level);
        beginDeathAnimation(this.damageSources().generic());
    }

    private void hurtSelfDestructTargets(ServerLevel level) {
        AABB range = this.getBoundingBox().inflate(SELF_DESTRUCT_RANGE, 5.0D, SELF_DESTRUCT_RANGE);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, range, this::isValidTarget)) {
            target.hurt(DamageHelper.getDamage(this, "white"), 10.0F + this.random.nextInt(6));
        }
    }

    private void zeroNearbyAbnormalityCounters(ServerLevel level) {
        AABB whole = new AABB(-SEARCH_LIMIT, level.getMinBuildHeight(), -SEARCH_LIMIT,
                SEARCH_LIMIT, level.getMaxBuildHeight(), SEARCH_LIMIT);
        List<AbstractAbnormality> candidates = new ArrayList<>(level.getEntitiesOfClass(AbstractAbnormality.class,
                whole, abnormality -> abnormality.isAlive()
                        && abnormality.canEscape()
                        && !abnormality.hasEscape()
                        && abnormality.getQliphothCounter() > 0));
        candidates.sort(Comparator.comparingDouble(this::distanceToSqr));

        int poolSize = Math.min(candidates.size(), 6);
        List<AbstractAbnormality> nearestPool = new ArrayList<>(candidates.subList(0, poolSize));
        Collections.shuffle(nearestPool, new java.util.Random(level.getRandom().nextLong()));
        for (int i = 0; i < Math.min(3, nearestPool.size()); i++) {
            AbstractAbnormality abnormality = nearestPool.get(i);
            abnormality.decreaseQliphothCounter(abnormality.getQliphothCounter());
        }
    }

    private void spawnSelfDestructParticles(ServerLevel level) {
        double y = this.getY() + this.getBbHeight() * 0.5D;
        level.sendParticles(ParticleUtil.getDustParticle(0.6F, 0.0F, 0.95F, 1.8F),
                this.getX(), y, this.getZ(), 180, 4.5D, 2.5D, 4.5D, 0.04D);
        level.sendParticles(ParticleTypes.END_ROD,
                this.getX(), y, this.getZ(), 90, 4.0D, 2.0D, 4.0D, 0.03D);
        level.sendParticles(ParticleTypes.WITCH,
                this.getX(), y, this.getZ(), 70, 4.0D, 2.0D, 4.0D, 0.02D);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || deathAnimationStarted) {
            return;
        }
        if (this.level() instanceof ServerLevel level) {
            notifyRemoved(level);
        }
        beginDeathAnimation(source);
    }

    private void notifyRemoved(ServerLevel level) {
        if (ordealSpawn && !countedDeath) {
            countedDeath = true;
            VioletDawnEvent.onVioletDawnRemoved(level);
        }
    }

    private void beginDeathAnimation(DamageSource source) {
        deathAnimationStarted = true;
        deathAnimationTick = 0;
        deathFinalized = false;
        delayedDeathSource = source;
        this.setHealth(1.0F);
        this.setTarget(null);
        this.setNoAi(true);
        stopMovement();
        setAnim("death");
    }

    private void tickDeathAnimation() {
        stopMovement();
        deathAnimationTick++;
        if (deathFinalized || deathAnimationTick < DEATH_ANIM_TICKS) {
            return;
        }
        deathFinalized = true;
        this.setHealth(0.0F);
        super.die(delayedDeathSource == null ? damageSources().generic() : delayedDeathSource);
        this.remove(Entity.RemovalReason.KILLED);
    }

    private void stopMovement() {
        this.getNavigation().stop();
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        this.hurtMarked = true;
    }

    private void setAnim(String animation) {
        if (this.level().isClientSide) return;
        this.entityData.set(ANIM, animation);
        this.entityData.set(ANIM_VERSION, this.entityData.get(ANIM_VERSION) + 1);
    }

    private void setAnimIfChanged(String animation) {
        if (!animation.equals(getAnim())) {
            setAnim(animation);
        }
    }

    private String getAnim() {
        return this.entityData.get(ANIM);
    }

    private int getAnimVersion() {
        return this.entityData.get(ANIM_VERSION);
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityVioletDawn> event) {
        String anim = getAnim();
        int animVersion = getAnimVersion();
        if (!anim.equals(lastClientAnim) || animVersion != lastClientAnimVersion) {
            event.getController().forceAnimationReset();
            lastClientAnim = anim;
            lastClientAnimVersion = animVersion;
        }
        return switch (anim) {
            case "death" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.violet_dawn.death"));
            case "walk" -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.violet_dawn.walk"));
            default -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.violet_dawn.idle"));
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 190.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.05D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.5D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.0D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LifeTicks", lifeTicks);
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putInt("ChaseTicksRemaining", chaseTicksRemaining);
        tag.putBoolean("CountedDeath", countedDeath);
        tag.putBoolean("DeathAnimationStarted", deathAnimationStarted);
        tag.putInt("DeathAnimationTick", deathAnimationTick);
        tag.putBoolean("OrdealSpawn", ordealSpawn);
        tag.putString("Anim", getAnim());
        tag.putInt("AnimVersion", getAnimVersion());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifeTicks = tag.getInt("LifeTicks");
        attackCooldown = tag.getInt("AttackCooldown");
        chaseTicksRemaining = tag.getInt("ChaseTicksRemaining");
        countedDeath = tag.getBoolean("CountedDeath");
        deathAnimationStarted = tag.getBoolean("DeathAnimationStarted");
        deathAnimationTick = tag.getInt("DeathAnimationTick");
        ordealSpawn = tag.getBoolean("OrdealSpawn");
        if (deathAnimationStarted) {
            this.setHealth(1.0F);
            setNoAi(true);
        }
        if (tag.contains("Anim")) this.entityData.set(ANIM, tag.getString("Anim"));
        this.entityData.set(ANIM_VERSION, tag.getInt("AnimVersion"));
    }
}
