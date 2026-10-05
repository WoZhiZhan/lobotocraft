package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.entity.ordeal.OrdealEntity;
import com.wzz.lobotocraft.entity.ai.FacilityAbnormalityTargetPolicy;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.entity.ai.AbnormalityFearResponse;
import com.wzz.lobotocraft.entity.ai.AbnormalityFearService;
import com.wzz.lobotocraft.entity.ai.AbnormalityFearSource;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.wzz.lobotocraft.work.WorkType;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** CENSORED-1 spawned after CENSORED consumes a victim or tombstone. */
public class EntityCensoredMinion extends AbstractAbnormality implements AbnormalityFearSource {
    // The 21-pixel-wide model at 1.5x spans nearly two Minecraft blocks.
    public static final float MODEL_SCALE = 1.5F;
    private static final int DAMAGE_INTERVAL_TICKS = 50;
    private static final double DAMAGE_RANGE = 2.0D;
    private static final double TARGET_SCAN_RANGE = 24.0D;
    private static final int TARGET_SCAN_INTERVAL = 10;

    private int damageCooldown;
    private int targetScanCooldown;
    private LivingEntity cachedTarget;
    private boolean playedSpawnSound;
    private UUID parentCensoredUuid;

    public EntityCensoredMinion(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
    }

    @Override
    protected void initializeAbnormality() {
        abnormalityCode = "O-03-89-1";
        abnormalityName = "CENSORED-1";
        riskLevel = RiskLevel.HE;
        damageType = "BLACK";
        maxPEOutput = 0;
        initializeWorkPreferences(new float[]{0.0F, 0.0F, 0.0F, 0.0F});
        initializeQliphothCounter();
    }

    @Override
    public String name() {
        return "censored1";
    }

    public void setParentCensoredUuid(UUID parentCensoredUuid) {
        this.parentCensoredUuid = parentCensoredUuid;
    }

    public boolean isChildOf(EntityCensored parent) {
        return parent != null && parentCensoredUuid != null
                && parentCensoredUuid.equals(parent.getUUID());
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        addFacilityRoamingGoals(1, 4, 0.9D);
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (!hasEscape()) {
            setEscape(true);
            EscapeTracker.getInstance().onEscapeStart(this);
        }

        if (!playedSpawnSound && tickCount >= 2) {
            playedSpawnSound = true;
            level().playSound(null, getX(), getY(), getZ(),
                    ModSounds.CENSORED_MINION_SPAWN.get(), SoundSource.HOSTILE, 0.7F, 1.0F);
        }
        if (damageCooldown > 0) damageCooldown--;
        AbnormalityFearService.tickEncounter(this, this);

        LivingEntity target = findTarget();
        if (target == null) {
            cachedTarget = null;
            setTarget(null);
            getNavigation().stop();
            return;
        }
        setTarget(target);
        if (Math.abs(target.getY() - getY()) > 5.0D) return;
        if (distanceToSqr(target) > DAMAGE_RANGE * DAMAGE_RANGE) {
            getNavigation().moveTo(target, 1.0D);
            return;
        }

        getNavigation().stop();
        getLookControl().setLookAt(target);
        if (damageCooldown <= 0) {
            damageCooldown = DAMAGE_INTERVAL_TICKS;
            float damage = 13 + random.nextInt(4);
            EntityUtil.clearHurtTime(target, () ->
                    target.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), damage));
        }
    }

    private LivingEntity findTarget() {
        if (cachedTarget != null
                && isValidCombatTarget(cachedTarget)
                && !cachedTarget.isRemoved()
                && cachedTarget.level() == level()
                && distanceToSqr(cachedTarget) <= TARGET_SCAN_RANGE * TARGET_SCAN_RANGE) {
            if (targetScanCooldown > 0) {
                targetScanCooldown--;
                return cachedTarget;
            }
        } else {
            cachedTarget = null;
        }
        targetScanCooldown = TARGET_SCAN_INTERVAL;

        AABB box = getBoundingBox().inflate(TARGET_SCAN_RANGE);
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, box,
                this::isValidCombatTarget);
        cachedTarget = targets.stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        return cachedTarget;
    }

    boolean isValidCombatTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()
                || entity instanceof EntityCensored || entity instanceof EntityCensoredMinion) return false;
        if (FacilityAbnormalityTargetPolicy.isPersonnel(entity)
                || entity instanceof OrdealEntity
                || FacilityAbnormalityTargetPolicy.isCoreSuppressionCombatant(entity)) return true;
        return entity instanceof AbstractAbnormality abnormality
                && abnormality.isAbnormalityCombatTarget();
    }

    @Override
    public AbnormalityFearResponse encounterFear(net.minecraft.server.level.ServerPlayer employee) {
        return AbnormalityFearService.employeeLevel(employee) < 5
                ? AbnormalityFearResponse.forcedPanic() : AbnormalityFearResponse.level(3);
    }

    @Override
    protected boolean usesContainmentReturnOnDeath() {
        return false;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        // Spawned breach-only children cannot be assigned containment work.
    }

    @Override
    protected AbstractAbnormality createNewInstance(net.minecraft.server.level.ServerLevel serverLevel) {
        return new EntityCensoredMinion((EntityType<? extends TamableAnimal>) getType(), serverLevel);
    }

    @Override
    public void stopEscape() {
        // Spawned children have no containment room; stopping their breach suppresses the entity.
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel && hasEscape()) {
            EscapeTracker.getInstance().onEscapeStop(serverLevel.getServer(), getUUID());
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (parentCensoredUuid != null) tag.putUUID("ParentCensored", parentCensoredUuid);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        parentCensoredUuid = tag.hasUUID("ParentCensored")
                ? tag.getUUID("ParentCensored") : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityCensoredMinion> event) {
        return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, TARGET_SCAN_RANGE);
    }
}
