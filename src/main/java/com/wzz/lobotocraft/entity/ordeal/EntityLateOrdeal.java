package com.wzz.lobotocraft.entity.ordeal;

import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.event.listener.LateOrdealEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.ParticleUtil;
import com.lobotocraft.facility.data.OrdealTier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Comparator;

/**
 * Save-compatible placeholder for late-tier entities created by older development builds.
 * Natural Dusk/Midnight schedules do not spawn this class.
 */
public class EntityLateOrdeal extends BaseGeoEntity implements OrdealEntity {
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(EntityLateOrdeal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PILLAR_DAMAGE =
            SynchedEntityData.defineId(EntityLateOrdeal.class, EntityDataSerializers.INT);

    private final LateOrdealVariant configuredVariant;
    private int attackCooldown;
    private int specialCooldown;
    private int reinforcementBudget;
    private boolean countedDeath;
    private boolean primarySpawn = true;

    public EntityLateOrdeal(EntityType<? extends TamableAnimal> type, Level level,
                            LateOrdealVariant configuredVariant) {
        super(type, level);
        this.configuredVariant = configuredVariant;
        this.entityData.set(VARIANT, configuredVariant.ordinal());
        this.setTexture(configuredVariant.assetName());
        this.specialCooldown = initialSpecialCooldown(configuredVariant);
        this.reinforcementBudget = initialReinforcementBudget(configuredVariant);
        if (configuredVariant.stationary()) this.setNoAi(true);
        this.setPersistenceRequired();
    }

    @Override
    public String name() {
        // Entity's base constructor asks for a name before configuredVariant is assigned.
        return configuredVariant == null ? "crimson_noon" : configuredVariant.assetName();
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANT, LateOrdealVariant.CRIMSON_DUSK.ordinal());
        this.entityData.define(PILLAR_DAMAGE, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.85D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class,
                10, true, false, this::isValidTarget));
    }

    public LateOrdealVariant getVariant() {
        int ordinal = this.entityData.get(VARIANT);
        LateOrdealVariant[] variants = LateOrdealVariant.values();
        return ordinal >= 0 && ordinal < variants.length ? variants[ordinal] : LateOrdealVariant.CRIMSON_DUSK;
    }

    public void setPillarDamage(int index) {
        this.entityData.set(PILLAR_DAMAGE, Mth.clamp(index, 0, 3));
    }

    public void setPrimarySpawn(boolean primarySpawn) {
        this.primarySpawn = primarySpawn;
        if (!primarySpawn) reinforcementBudget = 0;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target == null || isValidTarget(target) ? target : null);
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
    public boolean doHurtTarget(Entity target) {
        if (attackCooldown > 0 || !(target instanceof LivingEntity living) || !isValidTarget(living)) {
            return false;
        }
        attackCooldown = getVariant().tier() == OrdealTier.MIDNIGHT ? 30 : 20;
        float damage = switch (getVariant()) {
            case CRIMSON_DUSK -> 10.0F;
            case AMBER_DUSK -> 12.0F;
            case AMBER_MIDNIGHT -> 18.0F;
            default -> 8.0F;
        };
        return living.hurt(DamageHelper.getDamage(this, getVariant().damageType()), damage);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel serverLevel) || !isAlive()) return;

        LateOrdealVariant variant = getVariant();
        if (attackCooldown > 0) attackCooldown--;
        if (variant.stationary()) {
            getNavigation().stop();
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        }

        if (specialCooldown > 0) specialCooldown--;
        if (specialCooldown <= 0) {
            runSpecial(serverLevel, variant);
            specialCooldown = nextSpecialCooldown(variant);
        }
    }

    private void runSpecial(ServerLevel level, LateOrdealVariant variant) {
        switch (variant) {
            case GREEN_DUSK -> spawnReinforcement(level, false);
            case AMBER_DUSK -> spawnReinforcement(level, true);
            case GREEN_MIDNIGHT -> damageNearby(level, 26.0D, 7.0D, "black", 16.0F, 0.15F, 0.8F, 0.25F);
            case VIOLET_MIDNIGHT -> violetPillarAttack(level);
            case AMBER_MIDNIGHT -> {
                damageNearby(level, 9.0D, 5.0D, "red", 24.0F, 0.95F, 0.65F, 0.1F);
                spawnReinforcement(level, true);
            }
            case CRIMSON_DUSK -> damageNearby(level, 5.0D, 3.0D, "red", 14.0F, 0.9F, 0.05F, 0.05F);
        }
    }

    private void spawnReinforcement(ServerLevel level, boolean amber) {
        if (!primarySpawn || reinforcementBudget <= 0) return;
        if (LateOrdealEvent.spawnReinforcement(level, this, amber)) {
            reinforcementBudget--;
        }
    }

    private void violetPillarAttack(ServerLevel level) {
        int kind = this.entityData.get(PILLAR_DAMAGE);
        String damageType = switch (kind) {
            case 0 -> "red";
            case 1 -> "white";
            case 2 -> "black";
            default -> "blue";
        };
        float[][] colors = {{0.95F, 0.1F, 0.1F}, {0.95F, 0.95F, 0.95F},
                {0.5F, 0.1F, 0.75F}, {0.15F, 0.5F, 0.95F}};
        damageNearby(level, 18.0D, 9.0D, damageType, 18.0F,
                colors[kind][0], colors[kind][1], colors[kind][2]);
    }

    private void damageNearby(ServerLevel level, double horizontal, double vertical, String damageType,
                              float damage, float red, float green, float blue) {
        AABB area = getBoundingBox().inflate(horizontal, vertical, horizontal);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, this::isValidTarget)) {
            target.hurt(DamageHelper.getDamage(this, damageType), damage);
        }
        level.sendParticles(ParticleUtil.getDustParticle(red, green, blue, 1.6F),
                getX(), getY() + getBbHeight() * 0.5D, getZ(),
                80, horizontal * 0.55D, Math.min(3.0D, vertical), horizontal * 0.55D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                18, horizontal * 0.3D, Math.min(2.0D, vertical), horizontal * 0.3D, 0.01D);
    }

    private boolean isValidTarget(@Nullable LivingEntity target) {
        if (target == null || target == this || !target.isAlive() || target instanceof OrdealEntity) return false;
        if (target instanceof Player player) return !player.isCreative() && !player.isSpectator();
        return target instanceof EntityClerk;
    }

    @Nullable
    private LivingEntity nearestTarget(double range) {
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
                        this::isValidTarget).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide && !countedDeath && level() instanceof ServerLevel serverLevel) {
            countedDeath = true;
            LateOrdealEvent.onTrackedEntityKilled(serverLevel, this);
        }
        super.die(source);
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 3, this::animation));
    }

    private PlayState animation(AnimationState<EntityLateOrdeal> state) {
        LateOrdealVariant variant = getVariant();
        boolean moving = getNavigation().isInProgress() || getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
        return switch (variant) {
            case CRIMSON_DUSK -> state.setAndContinue(RawAnimation.begin().thenLoop(
                    moving ? "animation.crimson_noon.walk" : "animation.crimson_noon.idle"));
            case GREEN_DUSK, GREEN_MIDNIGHT -> state.setAndContinue(RawAnimation.begin().thenLoop(
                    moving ? "animation.green_noon.walk" : "animation.green_noon.idle"));
            case VIOLET_MIDNIGHT -> state.setAndContinue(
                    RawAnimation.begin().thenLoop("animation.violet_noon.idle"));
            case AMBER_DUSK, AMBER_MIDNIGHT -> moving
                    ? state.setAndContinue(RawAnimation.begin().thenLoop("animation.amber_dawn.move"))
                    : PlayState.STOP;
        };
    }

    public static AttributeSupplier.Builder crimsonDuskAttributes() {
        return attributes(900.0D, 0.22D, 0.5D, 1.1D, 1.0D, 1.3D);
    }

    public static AttributeSupplier.Builder greenDuskAttributes() {
        return attributes(850.0D, 0.0D, 0.8D, 1.2D, 1.5D, 1.0D);
    }

    public static AttributeSupplier.Builder amberDuskAttributes() {
        return attributes(700.0D, 0.28D, 0.6D, 1.0D, 1.5D, 1.2D);
    }

    public static AttributeSupplier.Builder greenMidnightAttributes() {
        return attributes(3200.0D, 0.0D, 0.6D, 1.0D, 0.8D, 1.0D);
    }

    public static AttributeSupplier.Builder violetMidnightAttributes() {
        return attributes(1800.0D, 0.0D, 0.8D, 0.8D, 0.8D, 0.8D);
    }

    public static AttributeSupplier.Builder amberMidnightAttributes() {
        return attributes(2400.0D, 0.18D, 0.5D, 1.0D, 1.2D, 1.0D);
    }

    private static AttributeSupplier.Builder attributes(double health, double speed, double red,
                                                        double white, double black, double blue) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), red)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), white)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), black)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), blue);
    }

    private static int initialSpecialCooldown(LateOrdealVariant variant) {
        return switch (variant) {
            case GREEN_DUSK, AMBER_DUSK -> 8 * 20;
            case GREEN_MIDNIGHT, VIOLET_MIDNIGHT -> 12 * 20;
            case AMBER_MIDNIGHT -> 15 * 20;
            case CRIMSON_DUSK -> 6 * 20;
        };
    }

    private static int nextSpecialCooldown(LateOrdealVariant variant) {
        return switch (variant) {
            case GREEN_DUSK -> 45 * 20;
            case AMBER_DUSK -> 20 * 20;
            case GREEN_MIDNIGHT -> 10 * 20;
            case VIOLET_MIDNIGHT -> 25 * 20;
            case AMBER_MIDNIGHT -> 20 * 20;
            case CRIMSON_DUSK -> 8 * 20;
        };
    }

    private static int initialReinforcementBudget(LateOrdealVariant variant) {
        return switch (variant) {
            case GREEN_DUSK -> 3;
            case AMBER_DUSK -> 4;
            case AMBER_MIDNIGHT -> 3;
            default -> 0;
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LateVariant", getVariant().ordinal());
        tag.putInt("PillarDamage", this.entityData.get(PILLAR_DAMAGE));
        tag.putInt("AttackCooldown", attackCooldown);
        tag.putInt("SpecialCooldown", specialCooldown);
        tag.putInt("ReinforcementBudget", reinforcementBudget);
        tag.putBoolean("CountedDeath", countedDeath);
        tag.putBoolean("PrimarySpawn", primarySpawn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("LateVariant")) {
            this.entityData.set(VARIANT, Mth.clamp(tag.getInt("LateVariant"), 0,
                    LateOrdealVariant.values().length - 1));
        } else if (configuredVariant != null) {
            this.entityData.set(VARIANT, configuredVariant.ordinal());
        }
        setTexture(getVariant().assetName());
        this.entityData.set(PILLAR_DAMAGE, Mth.clamp(tag.getInt("PillarDamage"), 0, 3));
        attackCooldown = tag.getInt("AttackCooldown");
        specialCooldown = tag.contains("SpecialCooldown") ? tag.getInt("SpecialCooldown")
                : initialSpecialCooldown(getVariant());
        reinforcementBudget = tag.contains("ReinforcementBudget") ? tag.getInt("ReinforcementBudget")
                : initialReinforcementBudget(getVariant());
        countedDeath = tag.getBoolean("CountedDeath");
        primarySpawn = !tag.contains("PrimarySpawn") || tag.getBoolean("PrimarySpawn");
        if (getVariant().stationary()) this.setNoAi(true);
    }
}
