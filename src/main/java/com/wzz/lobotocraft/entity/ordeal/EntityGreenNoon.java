package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.listener.GreenNoonEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModParticleTypes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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

import java.util.Comparator;

/**
 * 绿色的正午 - 理解的过程。
 */
public class EntityGreenNoon extends BaseGeoEntity {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(EntityGreenNoon.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIM_VERSION =
            SynchedEntityData.defineId(EntityGreenNoon.class, EntityDataSerializers.INT);

    private static final int SHOOTS_PER_SECOND = 6;
    private static final int SHOOT_RATE_SCALE = 20;
    private static final int SHOOT_ANIM_TICKS = 12;
    private static final int CHAINSAW_ATTACK_TICKS = 40;
    private static final int CHAINSAW_COOLDOWN_TICKS = 30;
    private static final int OVERLOAD_INTERVAL_TICKS = 22 * 20;
    private static final int OVERLOAD_DURATION_TICKS = 6 * 20;
    private static final int DEATH_ANIM_TICKS = 10;
    private static final int[] CHAINSAW_HIT_TICKS = {10, 12, 14, 16, 18, 20, 22, 24, 26};
    private static final double SHOOT_RANGE = 30.0D;
    private static final double CHAINSAW_RANGE = 2.0D;

    private String lastClientAnim = "";
    private int lastClientAnimVersion = -1;
    private int shootRateAccumulator = SHOOT_RATE_SCALE;
    private int shootAnimationTick = 0;
    private int chainsawAttackTick = 0;
    private int chainsawCooldown = 0;
    private int overloadCooldown = OVERLOAD_INTERVAL_TICKS;
    private int overloadTick = 0;
    private boolean countedDeath = false;
    private boolean deathAnimationStarted = false;
    private int deathAnimationTick = 0;
    private boolean deathFinalized = false;
    private DamageSource delayedDeathSource = null;
    private boolean ordealSpawn = false;

    public EntityGreenNoon(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "green_noon";
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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, false) {
            @Override
            protected double getAttackReachSqr(LivingEntity target) {
                return CHAINSAW_RANGE * CHAINSAW_RANGE;
            }
        });
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                this::isValidAttackTarget));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        this.setGlowingTag(true);
        return super.finalizeSpawn(level, difficulty, spawnType, data, tag);
    }

    public boolean isOrdealSpawn() {
        return ordealSpawn;
    }

    public void setOrdealSpawn(boolean ordealSpawn) {
        this.ordealSpawn = ordealSpawn;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target == null || isValidAttackTarget(target) ? target : null);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (deathAnimationStarted || overloadTick > 0 || chainsawAttackTick > 0) {
            return false;
        }
        if (target instanceof LivingEntity living
                && isValidAttackTarget(living)
                && isWithinChainsawRange(living)
                && chainsawCooldown <= 0) {
            beginChainsawAttack(living);
            return true;
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (deathAnimationStarted) {
            return false;
        }
        return super.hurt(source, amount);
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

        ensureGlowing();

        if (overloadTick > 0) {
            tickOverload(level);
            return;
        }
        if (this.isNoAi()) {
            this.setNoAi(false);
        }

        if (overloadCooldown > 0) {
            overloadCooldown--;
        }
        if (overloadCooldown <= 0) {
            beginOverload();
            return;
        }

        if (chainsawCooldown > 0) {
            chainsawCooldown--;
        }
        if (chainsawAttackTick > 0) {
            tickChainsawAttack(level);
            return;
        }

        if (shootAnimationTick > 0) {
            shootAnimationTick--;
        }

        LivingEntity closeTarget = findNearestTarget(CHAINSAW_RANGE);
        if (closeTarget != null && chainsawCooldown <= 0) {
            this.setTarget(closeTarget);
            beginChainsawAttack(closeTarget);
            return;
        }

        tickShooting(level);
        if (shootAnimationTick <= 0) {
            setAnimIfChanged(getMoveAnim());
        }
    }

    private void ensureGlowing() {
        if (!this.hasEffect(MobEffects.GLOWING)) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }
        this.setGlowingTag(true);
    }

    private void tickShooting(ServerLevel level) {
        LivingEntity target = findNearestTarget(SHOOT_RANGE);
        if (target == null) {
            shootRateAccumulator = SHOOT_RATE_SCALE;
            return;
        }

        shootRateAccumulator += SHOOTS_PER_SECOND;
        if (shootRateAccumulator < SHOOT_RATE_SCALE) {
            return;
        }

        shootRateAccumulator -= SHOOT_RATE_SCALE;
        shootAnimationTick = SHOOT_ANIM_TICKS;
        this.setTarget(target);
        faceTarget(target);
        setAnimIfChanged("shoot");

        EntityUtil.clearHurtTime(target);
        if (target.hurt(DamageHelper.getDamage(this, "red"), 1.0F)) {
            level.playSound(null, this.blockPosition(), ModSounds.GREEN_NOON_SHOOT.get(),
                    SoundSource.HOSTILE, 0.7F, 1.0F);
        }
        EntityUtil.clearHurtTime(target);
    }

    private void beginChainsawAttack(LivingEntity target) {
        chainsawAttackTick = 1;
        chainsawCooldown = CHAINSAW_COOLDOWN_TICKS;
        shootAnimationTick = 0;
        stopMovement();
        faceTarget(target);
        setAnim("attack");
        this.level().playSound(null, this.blockPosition(), ModSounds.GREEN_NOON_CHAINSAW.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void tickChainsawAttack(ServerLevel level) {
        stopMovement();
        for (int hitTick : CHAINSAW_HIT_TICKS) {
            if (chainsawAttackTick == hitTick) {
                hurtChainsawTargets(level);
                break;
            }
        }

        chainsawAttackTick++;
        if (chainsawAttackTick > CHAINSAW_ATTACK_TICKS) {
            chainsawAttackTick = 0;
            setAnimIfChanged(getMoveAnim());
        }
    }

    private void hurtChainsawTargets(ServerLevel level) {
        AABB range = this.getBoundingBox().inflate(CHAINSAW_RANGE, 2.5D, CHAINSAW_RANGE);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, range,
                target -> isValidAttackTarget(target) && isWithinChainsawRange(target))) {
            EntityUtil.clearHurtTime(target);
            target.hurt(DamageHelper.getDamage(this, "red"), 1.0F + this.random.nextFloat());
            EntityUtil.clearHurtTime(target);
        }
    }

    private void beginOverload() {
        overloadTick = OVERLOAD_DURATION_TICKS;
        chainsawAttackTick = 0;
        shootAnimationTick = 0;
        shootRateAccumulator = 0;
        this.setNoAi(true);
        stopMovement();
        setAnim("overload");
        this.level().playSound(null, this.blockPosition(), ModSounds.GREEN_NOON_OVERLOAD.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void tickOverload(ServerLevel level) {
        stopMovement();
        if (overloadTick % 4 == 0) {
            level.sendParticles(ModParticleTypes.GREEN_NOON_OVERLOAD.get(),
                    this.getX(), this.getY() + this.getBbHeight() * 0.55D, this.getZ(),
                    3, 0.65D, this.getBbHeight() * 0.35D, 0.65D, 0.015D);
        }

        overloadTick--;
        if (overloadTick <= 0) {
            this.setNoAi(false);
            overloadCooldown = OVERLOAD_INTERVAL_TICKS;
            setAnimIfChanged(getMoveAnim());
        }
    }

    private LivingEntity findNearestTarget(double horizontalRange) {
        if (!(this.level() instanceof ServerLevel level)) {
            return null;
        }
        AABB range = this.getBoundingBox().inflate(horizontalRange, 8.0D, horizontalRange);
        return level.getEntitiesOfClass(LivingEntity.class, range, this::isValidAttackTarget).stream()
                .filter(target -> this.distanceToSqr(target) <= horizontalRange * horizontalRange)
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private boolean isWithinChainsawRange(LivingEntity target) {
        return this.distanceToSqr(target) <= CHAINSAW_RANGE * CHAINSAW_RANGE;
    }

    private boolean isValidAttackTarget(@Nullable LivingEntity target) {
        if (target == null || target == this || !target.isAlive()) {
            return false;
        }
        if (isGreenOrdealUnit(target)) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return true;
    }

    private boolean isGreenOrdealUnit(LivingEntity target) {
        return target instanceof EntityGreenDawn || target instanceof EntityGreenNoon;
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0F / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
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

    private String getMoveAnim() {
        if (this.getNavigation().isInProgress() || this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D) {
            return "walk";
        }
        LivingEntity target = this.getTarget();
        return target != null && target.isAlive() && this.distanceToSqr(target) > CHAINSAW_RANGE * CHAINSAW_RANGE
                ? "walk"
                : "idle";
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide || deathAnimationStarted) {
            return;
        }
        if (this.level() instanceof ServerLevel level && ordealSpawn && !countedDeath) {
            countedDeath = true;
            GreenNoonEvent.onGreenNoonKilled(level);
        }
        beginDeathAnimation(source);
    }

    private void beginDeathAnimation(DamageSource source) {
        deathAnimationStarted = true;
        deathAnimationTick = 0;
        deathFinalized = false;
        delayedDeathSource = source;
        this.setHealth(1.0F);
        this.setNoAi(true);
        stopMovement();
        setAnim("death");
        this.level().playSound(null, this.blockPosition(), ModSounds.GREEN_NOON_DEATH.get(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
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

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityGreenNoon> event) {
        String anim = getAnim();
        int animVersion = getAnimVersion();
        if (!anim.equals(lastClientAnim) || animVersion != lastClientAnimVersion) {
            event.getController().forceAnimationReset();
            lastClientAnim = anim;
            lastClientAnimVersion = animVersion;
        }
        return switch (anim) {
            case "shoot" -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.green_noon.shoot"));
            case "attack" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.green_noon.attack"));
            case "overload" -> event.setAndContinue(
                    RawAnimation.begin().thenPlayAndHold("animation.green_noon.overload"));
            case "death" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.green_noon.death"));
            case "walk" -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.green_noon.walk"));
            default -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.green_noon.idle"));
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.13D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.3D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 2.0D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ShootRateAccumulator", shootRateAccumulator);
        tag.putInt("ShootAnimationTick", shootAnimationTick);
        tag.putInt("ChainsawAttackTick", chainsawAttackTick);
        tag.putInt("ChainsawCooldown", chainsawCooldown);
        tag.putInt("OverloadCooldown", overloadCooldown);
        tag.putInt("OverloadTick", overloadTick);
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
        shootRateAccumulator = tag.contains("ShootRateAccumulator")
                ? tag.getInt("ShootRateAccumulator")
                : SHOOT_RATE_SCALE;
        shootAnimationTick = tag.getInt("ShootAnimationTick");
        chainsawAttackTick = tag.getInt("ChainsawAttackTick");
        chainsawCooldown = tag.getInt("ChainsawCooldown");
        overloadCooldown = tag.contains("OverloadCooldown")
                ? tag.getInt("OverloadCooldown")
                : OVERLOAD_INTERVAL_TICKS;
        overloadTick = tag.getInt("OverloadTick");
        countedDeath = tag.getBoolean("CountedDeath");
        deathAnimationStarted = tag.getBoolean("DeathAnimationStarted");
        deathAnimationTick = tag.getInt("DeathAnimationTick");
        ordealSpawn = tag.getBoolean("OrdealSpawn");
        if (deathAnimationStarted || overloadTick > 0) {
            this.setNoAi(true);
        }
        if (tag.contains("Anim")) this.entityData.set(ANIM, tag.getString("Anim"));
        this.entityData.set(ANIM_VERSION, tag.getInt("AnimVersion"));
    }
}
