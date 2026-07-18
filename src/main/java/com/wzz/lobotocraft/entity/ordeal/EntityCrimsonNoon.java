package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.CrimsonNoonEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.ParticleUtil;
import net.minecraft.core.particles.ParticleTypes;
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

/**
 * 血色的正午 - 汁水大合唱。
 */
public class EntityCrimsonNoon extends BaseGeoEntity {
    private static final EntityDataAccessor<String> ANIM =
            SynchedEntityData.defineId(EntityCrimsonNoon.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIM_VERSION =
            SynchedEntityData.defineId(EntityCrimsonNoon.class, EntityDataSerializers.INT);

    private static final int ATTACK_INTERVAL_TICKS = 4 * 20;
    private static final int ATTACK_ANIM_TICKS = 34;
    private static final int ATTACK_HIT_TICK = 16;
    private static final int DEATH_ANIM_TICKS = 24;
    private static final double ATTACK_REACH = 3.4D;
    private static final double ATTACK_VERTICAL_RANGE = 2.2D;
    private static final float ATTACK_DAMAGE = 5.0F;

    private String lastClientAnim = "";
    private int lastClientAnimVersion = -1;
    private int attackCooldown = ATTACK_INTERVAL_TICKS;
    private int attackTick = 0;
    private int attackVariant = 1;
    private boolean countedDeath = false;
    private boolean deathAnimationStarted = false;
    private int deathAnimationTick = 0;
    private boolean deathFinalized = false;
    private DamageSource delayedDeathSource = null;
    private boolean ordealSpawn = false;

    public EntityCrimsonNoon(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        return "crimson_noon";
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
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false) {
            @Override
            protected double getAttackReachSqr(LivingEntity target) {
                return ATTACK_REACH * ATTACK_REACH;
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
        if (deathAnimationStarted || attackTick > 0 || attackCooldown > 0) {
            return false;
        }
        if (target instanceof LivingEntity living && isValidAttackTarget(living) && isWithinAttackRange(living)) {
            beginAttack(living);
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
            tickDeathAnimation(level);
            return;
        }

        ensureGlowing();

        if (attackTick > 0) {
            tickAttack(level);
            return;
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        LivingEntity target = this.getTarget();
        if (target != null && (!target.isAlive() || !isValidAttackTarget(target))) {
            this.setTarget(null);
            target = null;
        }
        if (target != null && attackCooldown <= 0 && isWithinAttackRange(target)) {
            beginAttack(target);
            return;
        }

        setAnimIfChanged(getMoveAnim());
    }

    private void ensureGlowing() {
        if (!this.hasEffect(MobEffects.GLOWING)) {
            this.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }
        this.setGlowingTag(true);
    }

    private void beginAttack(LivingEntity target) {
        attackTick = 1;
        attackCooldown = ATTACK_INTERVAL_TICKS;
        attackVariant = 1 + this.random.nextInt(3);
        stopMovement();
        faceTarget(target);
        setAnim("attack_" + attackVariant);
        this.level().playSound(null, this.blockPosition(), ModSounds.CRIMSON_NOON_ATTACK.get(),
                SoundSource.HOSTILE, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    private void tickAttack(ServerLevel level) {
        stopMovement();
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            faceTarget(target);
        }

        if (attackTick == ATTACK_HIT_TICK) {
            hurtAttackTargets(level);
        }

        attackTick++;
        if (attackTick > ATTACK_ANIM_TICKS) {
            attackTick = 0;
            setAnimIfChanged(getMoveAnim());
        }
    }

    private void hurtAttackTargets(ServerLevel level) {
        double reach = switch (attackVariant) {
            case 2 -> ATTACK_REACH + 0.6D;
            case 3 -> ATTACK_REACH + 1.0D;
            default -> ATTACK_REACH;
        };
        AABB range = this.getBoundingBox().inflate(reach, ATTACK_VERTICAL_RANGE, reach);
        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, range, this::isValidAttackTarget)) {
            if (this.distanceToSqr(target) > reach * reach) {
                continue;
            }
            EntityUtil.clearHurtTime(target);
            if (target.hurt(DamageHelper.getDamage(this, "red"), ATTACK_DAMAGE)) {
                spawnHitParticles(level, target);
                hit = true;
            }
            EntityUtil.clearHurtTime(target);
        }
        if (hit) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                    8, 0.8D, 0.6D, 0.8D, 0.02D);
        }
    }

    private void spawnHitParticles(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleUtil.getDustParticle(0.8F, 0.0F, 0.0F, 1.4F),
                target.getX(), target.getY() + target.getBbHeight() * 0.45D, target.getZ(),
                14, 0.3D, 0.35D, 0.3D, 0.03D);
    }

    private boolean isValidAttackTarget(@Nullable LivingEntity target) {
        if (target == null || target == this || !target.isAlive()) {
            return false;
        }
        if (target instanceof EntityCrimsonNoon || target instanceof EntityBloodySmall) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return true;
    }

    private boolean isWithinAttackRange(LivingEntity target) {
        return this.distanceToSqr(target) <= ATTACK_REACH * ATTACK_REACH;
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

    private String getMoveAnim() {
        return this.getNavigation().isInProgress() || this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D
                ? "walk"
                : "idle";
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
    public void die(DamageSource source) {
        if (this.level().isClientSide || deathAnimationStarted) {
            return;
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
    }

    private void tickDeathAnimation(ServerLevel level) {
        stopMovement();
        deathAnimationTick++;
        if (deathFinalized || deathAnimationTick < DEATH_ANIM_TICKS) {
            return;
        }
        deathFinalized = true;
        if (!countedDeath) {
            countedDeath = true;
            CrimsonNoonEvent.onCrimsonNoonKilled(level, this);
        }
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

    private PlayState predicate(AnimationState<EntityCrimsonNoon> event) {
        String anim = getAnim();
        int animVersion = getAnimVersion();
        if (!anim.equals(lastClientAnim) || animVersion != lastClientAnimVersion) {
            event.getController().forceAnimationReset();
            lastClientAnim = anim;
            lastClientAnimVersion = animVersion;
        }
        return switch (anim) {
            case "walk" -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.crimson_noon.walk"));
            case "attack_1" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.crimson_noon.attack_1"));
            case "attack_2" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.crimson_noon.attack_2"));
            case "attack_3" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.crimson_noon.attack_3"));
            case "death" -> event.setAndContinue(RawAnimation.begin().thenPlay("animation.crimson_noon.death"));
            default -> event.setAndContinue(RawAnimation.begin().thenLoop("animation.crimson_noon.idle"));
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 550.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.5D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putInt("AttackTick", attackTick);
        tag.putInt("AttackVariant", attackVariant);
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
        attackCooldown = tag.contains("AttackCooldown") ? tag.getInt("AttackCooldown") : ATTACK_INTERVAL_TICKS;
        attackTick = tag.getInt("AttackTick");
        attackVariant = tag.contains("AttackVariant") ? tag.getInt("AttackVariant") : 1;
        countedDeath = tag.getBoolean("CountedDeath");
        deathAnimationStarted = tag.getBoolean("DeathAnimationStarted");
        deathAnimationTick = tag.getInt("DeathAnimationTick");
        ordealSpawn = tag.getBoolean("OrdealSpawn");
        if (deathAnimationStarted) {
            this.setHealth(1.0F);
            this.setNoAi(true);
        }
        if (tag.contains("Anim")) this.entityData.set(ANIM, tag.getString("Anim"));
        this.entityData.set(ANIM_VERSION, tag.getInt("AnimVersion"));
    }
}
