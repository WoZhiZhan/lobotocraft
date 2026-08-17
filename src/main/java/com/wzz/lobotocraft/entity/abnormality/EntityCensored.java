package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Comparator;
import java.util.List;

/** O-03-89. Containment and escape forms share one authoritative lifecycle entity. */
public class EntityCensored extends AbstractAbnormality {
    private static final int CONTAINED_SACRIFICE_TICKS = 91;
    private static final int ATTACK_ANIMATION_TICKS = 40;
    private static final int ATTACK_HIT_TICK = 24;
    private static final int SPECIAL_ANIMATION_TICKS = 60;
    private static final int MENTAL_HIT_COUNT = 6;
    private static final int MENTAL_HIT_INTERVAL = SPECIAL_ANIMATION_TICKS / MENTAL_HIT_COUNT;
    private static final double MENTAL_AOE_HALF_XZ = 6.0D;
    private static final double MENTAL_AOE_HALF_Y = 3.0D;
    private static final int TOMBSTONE_SCAN_RANGE_XZ = 20;
    private static final int TOMBSTONE_SCAN_RANGE_Y = 6;
    private static final int TOMBSTONE_SCAN_INTERVAL = 10;
    private static final double TOMBSTONE_TRIGGER_DISTANCE = 2.0D;
    private static final double TOMBSTONE_TRIGGER_DISTANCE_Y = 1.5D;

    private int containedSacrificeTimer;
    private int attackTimer;
    private LivingEntity attackTarget;
    private int specialAttackTimer;
    private BlockPos specialAttackSpawnPos;
    private int mentalHitsDealt;
    private int targetReevaluationCooldown;
    private BlockPos cachedTombstone;
    private int lastTombstoneScan;
    private boolean seekingTombstone;
    private String lastClientAnimation = "";

    public EntityCensored(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void initializeAbnormality() {
        abnormalityCode = "O-03-89";
        abnormalityName = "CENSORED";
        riskLevel = RiskLevel.ALEPH;
        damageType = "BLACK";
        maxPEOutput = 28;
        initializeWorkPreferences(new float[]{0.35F, 0.40F, 0.25F, 0.10F});
        initializeQliphothCounter(2);
    }

    @Override
    public String name() {
        return "censored";
    }

    @Override
    public SoundEvent getAttackSound() {
        return ModSounds.CENSORED_ATTACK.get();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!hasEscape() || specialAttackTimer > 0 || !(target instanceof LivingEntity living)) {
            return false;
        }
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (attackTimer > 0) return true;
        attackTimer = ATTACK_ANIMATION_TICKS;
        attackTarget = living;
        setAnimation("attack");
        playAttackSound();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!hasEscape()) {
            tickContained();
            return;
        }
        setNoAi(false);
        tickEscaped();
    }

    private void tickContained() {
        setNoAi(true);
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        clearEscapeCombatState();
        if (containedSacrificeTimer > 0) {
            containedSacrificeTimer--;
            if (containedSacrificeTimer == 0) setAnimation("idle");
        } else if (!"idle".equals(getAnimation())) {
            setAnimation("idle");
        }
    }

    private void tickEscaped() {
        if (attackTimer > 0) {
            attackTimer--;
            int elapsed = ATTACK_ANIMATION_TICKS - attackTimer;
            if (elapsed == ATTACK_HIT_TICK && canHitAttackTarget()) {
                float damage = 23 + random.nextInt(4);
                EntityUtil.clearHurtTime(attackTarget, () ->
                        attackTarget.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), damage));
                if (!attackTarget.isAlive() && !(attackTarget instanceof AbstractAbnormality)) {
                    startSpecialAttack(attackTarget.blockPosition());
                }
            }
            if (attackTimer == 0) {
                attackTarget = null;
                if (specialAttackTimer == 0) setAnimation("idle");
            }
        }

        if (specialAttackTimer > 0) {
            specialAttackTimer--;
            int elapsed = SPECIAL_ANIMATION_TICKS - specialAttackTimer;
            if (elapsed > 0 && elapsed % MENTAL_HIT_INTERVAL == 0
                    && mentalHitsDealt < MENTAL_HIT_COUNT) {
                mentalHitsDealt++;
                applyMentalAreaDamage();
            }
            if (specialAttackTimer == 0) {
                spawnMinion(specialAttackSpawnPos);
                specialAttackSpawnPos = null;
                setAnimation("idle");
            }
            return;
        }

        if (attackTimer == 0) {
            boolean moving = getNavigation().isInProgress()
                    || getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D;
            String animation = moving ? "move" : "idle";
            if (!animation.equals(getAnimation())) setAnimation(animation);
        }

        BlockPos tombstone = findNearestTombstone();
        if (tombstone != null) {
            if (isAdjacentToTombstone(tombstone)) {
                startSpecialAttack(tombstone);
                return;
            }
            if (!seekingTombstone || getNavigation().isDone()) {
                seekingTombstone = getNavigation().moveTo(
                        tombstone.getX() + 0.5D, tombstone.getY(), tombstone.getZ() + 0.5D, 1.0D);
            }
            getLookControl().setLookAt(
                    tombstone.getX() + 0.5D, tombstone.getY(), tombstone.getZ() + 0.5D);
            setTarget(null);
            return;
        }
        seekingTombstone = false;

        if (targetReevaluationCooldown > 0) {
            targetReevaluationCooldown--;
            return;
        }
        targetReevaluationCooldown = 20;
        if (getTarget() != null && getTarget().isAlive()) return;

        AbstractAbnormality nearestAbnormality = level().getEntitiesOfClass(
                        AbstractAbnormality.class,
                        getBoundingBox().inflate(32.0D),
                        abnormality -> abnormality != this
                                && abnormality.isAbnormalityCombatTarget()
                                && !abnormality.isBirdOfBlackForest()
                                && abnormality.isAlive())
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (nearestAbnormality != null) {
            setTarget(nearestAbnormality);
            return;
        }

        LivingEntity nearestVictim = level().getEntitiesOfClass(
                        LivingEntity.class, getBoundingBox().inflate(32.0D), this::isValidVictim)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        setTarget(nearestVictim);
    }

    private boolean isValidVictim(LivingEntity entity) {
        if (entity == this || !entity.isAlive() || entity instanceof EntityCensoredMinion) return false;
        if (entity instanceof AbstractAbnormality abnormality) return abnormality.isAbnormalityCombatTarget();
        if (entity instanceof Player player) return !player.isCreative() && !player.isSpectator();
        return true;
    }

    private boolean canHitAttackTarget() {
        return attackTarget != null && attackTarget.isAlive() && distanceToSqr(attackTarget) <= 16.0D;
    }

    private void startSpecialAttack(BlockPos spawnPos) {
        if (spawnPos == null || specialAttackTimer > 0) return;
        specialAttackTimer = SPECIAL_ANIMATION_TICKS;
        mentalHitsDealt = 0;
        specialAttackSpawnPos = spawnPos.immutable();
        seekingTombstone = false;
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        setAnimation("special_attack");
        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.CENSORED_SACRIFICE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);

        BlockState state = level().getBlockState(spawnPos);
        if (state.is(ModBlocks.TOMBSTONE.get())) level().destroyBlock(spawnPos, false);
        cachedTombstone = null;
        lastTombstoneScan = tickCount - TOMBSTONE_SCAN_INTERVAL;
    }

    private void applyMentalAreaDamage() {
        float damage = 12 + random.nextInt(6);
        DamageSource white = DamageHelper.getDamage(this, "lobotocraft:white");
        AABB box = getBoundingBox().inflate(
                MENTAL_AOE_HALF_XZ, MENTAL_AOE_HALF_Y, MENTAL_AOE_HALF_XZ);
        List<LivingEntity> victims = level().getEntitiesOfClass(LivingEntity.class, box, entity ->
                entity != this
                        && entity.isAlive()
                        && !(entity instanceof AbstractAbnormality)
                        && !(entity instanceof EntityCensoredMinion)
                        && (!(entity instanceof Player player)
                        || (!player.isCreative() && !player.isSpectator())));
        for (LivingEntity victim : victims) {
            EntityUtil.clearHurtTime(victim, () -> victim.hurt(white, damage));
        }
    }

    private void spawnMinion(BlockPos spawnPos) {
        if (!(level() instanceof ServerLevel serverLevel) || spawnPos == null) return;
        EntityCensoredMinion child = ModEntities.censored_minion.get().create(serverLevel);
        if (child == null) return;
        child.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                random.nextFloat() * 360.0F, 0.0F);
        child.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED, null, null);
        child.setPersistenceRequired();
        serverLevel.addFreshEntity(child);
    }

    private BlockPos findNearestTombstone() {
        if (cachedTombstone != null) {
            if (level().getBlockState(cachedTombstone).is(ModBlocks.TOMBSTONE.get())
                    && tickCount - lastTombstoneScan < TOMBSTONE_SCAN_INTERVAL) {
                return cachedTombstone;
            }
            if (!level().getBlockState(cachedTombstone).is(ModBlocks.TOMBSTONE.get())) {
                cachedTombstone = null;
            }
        }
        if (tickCount - lastTombstoneScan < TOMBSTONE_SCAN_INTERVAL) return cachedTombstone;
        lastTombstoneScan = tickCount;

        BlockPos origin = blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -TOMBSTONE_SCAN_RANGE_XZ; dx <= TOMBSTONE_SCAN_RANGE_XZ; dx++) {
            for (int dz = -TOMBSTONE_SCAN_RANGE_XZ; dz <= TOMBSTONE_SCAN_RANGE_XZ; dz++) {
                for (int dy = -TOMBSTONE_SCAN_RANGE_Y; dy <= TOMBSTONE_SCAN_RANGE_Y; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level().getBlockState(cursor).is(ModBlocks.TOMBSTONE.get())) continue;
                    double distance = cursor.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        cachedTombstone = best;
        return best;
    }

    private boolean isAdjacentToTombstone(BlockPos tombstone) {
        double dx = getX() - (tombstone.getX() + 0.5D);
        double dy = getY() - tombstone.getY();
        double dz = getZ() - (tombstone.getZ() + 0.5D);
        return dx * dx + dz * dz <= TOMBSTONE_TRIGGER_DISTANCE * TOMBSTONE_TRIGGER_DISTANCE
                && Math.abs(dy) <= TOMBSTONE_TRIGGER_DISTANCE_Y;
    }

    private void playContainedSacrifice() {
        containedSacrificeTimer = CONTAINED_SACRIFICE_TICKS;
        setAnimation("sacrifice");
        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.CENSORED_SACRIFICE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        if (!level().isClientSide) playContainedSacrifice();
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), 23 + random.nextInt(4));
    }

    @Override
    public void onQliphothMeltdown() {
        triggerEscape();
    }

    @Override
    public void triggerEscape() {
        super.triggerEscape();
        if (!hasEscape()) return;
        setNoAi(false);
        containedSacrificeTimer = 0;
        clearEscapeCombatState();
        setAnimation("idle");
    }

    @Override
    public void stopEscape() {
        boolean wasEscaped = hasEscape();
        super.stopEscape();
        if (!wasEscaped || hasEscape()) return;
        resetContainmentState();
    }

    @Override
    protected void prepareForContainmentReturn() {
        clearEscapeCombatState();
        setAnimation("idle");
    }

    private void resetContainmentState() {
        containedSacrificeTimer = 0;
        clearEscapeCombatState();
        setNoAi(true);
        setAnimation("idle");
    }

    private void clearEscapeCombatState() {
        attackTimer = 0;
        attackTarget = null;
        specialAttackTimer = 0;
        specialAttackSpawnPos = null;
        mentalHitsDealt = 0;
        targetReevaluationCooldown = 0;
        cachedTombstone = null;
        seekingTombstone = false;
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityCensored((EntityType<? extends TamableAnimal>) getType(), serverLevel);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<EntityCensored> event) {
        String animation = getAnimation();
        if (!animation.equals(lastClientAnimation)) {
            event.getController().forceAnimationReset();
            lastClientAnimation = animation;
        }
        if (!hasEscape()) {
            if ("sacrifice".equals(animation)) {
                return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("sacrifice"));
            }
            return event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }
        return switch (animation) {
            case "attack", "special_attack" ->
                    event.setAndContinue(RawAnimation.begin().thenPlayAndHold(animation));
            case "move" -> event.setAndContinue(RawAnimation.begin().thenLoop("move"));
            default -> event.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1500.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 24.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(com.wzz.lobotocraft.init.ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.5D)
                .add(com.wzz.lobotocraft.init.ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.4D)
                .add(com.wzz.lobotocraft.init.ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(com.wzz.lobotocraft.init.ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 0.8D);
    }
}
