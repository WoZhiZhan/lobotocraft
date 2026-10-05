package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.ordeal.OrdealEntity;
import com.wzz.lobotocraft.entity.ai.AbnormalityFearResponse;
import com.wzz.lobotocraft.entity.ai.AbnormalityFearService;
import com.wzz.lobotocraft.entity.ai.FacilityAbnormalityTargetPolicy;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.init.ModBlocks;
import com.wzz.lobotocraft.init.ModEntities;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import java.util.ArrayList;
import java.util.List;

/** O-03-89. Containment and escape forms share one authoritative lifecycle entity. */
public class EntityCensored extends AbstractAbnormality {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> UNIQUE_WORK_BUSY =
            net.minecraft.network.syncher.SynchedEntityData.defineId(EntityCensored.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(UNIQUE_WORK_BUSY, false);
    }

    public boolean isUniqueWorkBusy() { return entityData.get(UNIQUE_WORK_BUSY); }
    public void setUniqueWorkBusy(boolean busy) { entityData.set(UNIQUE_WORK_BUSY, busy); }
    public void beginUniqueSacrificeAnimation() { playContainedSacrifice(); }
    private static final int CONTAINED_SACRIFICE_TICKS = 91;
    private static final int ATTACK_ANIMATION_TICKS = 40;
    private static final int SPECIAL_ANIMATION_TICKS = 60;
    private static final double MENTAL_AOE_HALF_XZ = 6.0D;
    private static final double MENTAL_AOE_HALF_Y = 3.0D;
    private static final int TOMBSTONE_SCAN_RANGE_XZ = 20;
    private static final int TOMBSTONE_SCAN_RANGE_Y = 6;
    private static final int TOMBSTONE_SCAN_INTERVAL = 10;
    private static final double TOMBSTONE_TRIGGER_DISTANCE = 2.0D;
    private static final double TOMBSTONE_TRIGGER_DISTANCE_Y = 1.5D;
    private static final double TOMBSTONE_BODY_REACH = 3.0D;

    private int containedSacrificeTimer;
    private int attackTimer;
    private int attackAnimationTick;
    private LivingEntity attackTarget;
    private int specialAttackTimer;
    private int specialAnimationTick;
    private BlockPos specialAttackSpawnPos;
    private int targetReevaluationCooldown;
    private BlockPos cachedTombstone;
    private int lastTombstoneScan;
    private boolean seekingTombstone;
    private String lastClientAnimation = "";

    /** Must stay aligned with censored.animation.json's server-relevant timeline events. */
    private enum AnimationEvent {
        ATTACK_HIT("censored.attack.hit", 25),
        ATTACK_END("censored.attack.end", ATTACK_ANIMATION_TICKS),
        SPECIAL_HEAL("censored.special.heal", 15),
        SPECIAL_WHITE_DAMAGE("censored.special.white_damage", 45),
        SPECIAL_CREATE_CHILD("censored.special.create_child", 50),
        SPECIAL_END("censored.special.end", SPECIAL_ANIMATION_TICKS);

        private final String id;
        private final int tick;

        AnimationEvent(String id, int tick) {
            this.id = id;
            this.tick = tick;
        }

        private boolean occursAt(int animationTick) {
            return tick == animationTick;
        }
    }

    public EntityCensored(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void initializeAbnormality() {
        abnormalityCode = "O-03-89";
        abnormalityName = "CENSORED";
        riskLevel = RiskLevel.ALEPH;
        damageType = "BLACK";
        // BaseMod/BaseCreatures/Censored_stat.txt: last cube bound 32.
        maxPEOutput = 32;
        initializeWorkPreferences(new float[]{0.8F, 0.9F, 0.7F, 0.0F});
        initializeQliphothCounter(2);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[]{0.0F, 0.0F, 0.0F, 0.0F};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        // R/W/B/P in Censored_stat.txt map to Instinct/Insight/Attachment/Repression.
        return new float[][]{
                {0.0F, -0.1F, -0.2F, -0.3F, -0.4F},
                {0.0F, -0.1F, -0.2F, -0.3F, -0.4F},
                {0.0F, -0.1F, -0.2F, -0.3F, -0.4F},
                {0.0F, 0.0F, 0.0F, 0.0F, 0.0F}
        };
    }

    @Override
    public int getGoodWorkResultMin() {
        // Original has only two feeling bounds: BAD 0..16, NORMAL 17..32.
        return 33;
    }

    @Override
    public int getNormalWorkResultMin() {
        return 17;
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
        addFacilityRoamingGoals(1, 5, 1.0D);
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected boolean isFacilityMovementBlocked() {
        return attackTimer > 0 || specialAttackTimer > 0
                || seekingTombstone || cachedTombstone != null;
    }

    private void playCensoredSound(SoundEvent sound, float volume, float pitch) {
        if (sound == null || level().isClientSide) return;
        // HOSTILE positional sounds use vanilla distance attenuation from this entity's coordinates.
        level().playSound(null, getX(), getY(), getZ(), sound,
                SoundSource.HOSTILE, volume, pitch);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!hasEscape() || specialAttackTimer > 0 || !(target instanceof LivingEntity living)) {
            return false;
        }
        if (!isValidCombatTarget(living)) return false;
        if (attackTimer > 0) return true;
        attackTimer = ATTACK_ANIMATION_TICKS;
        attackAnimationTick = 0;
        attackTarget = living;
        face(living.getX(), living.getZ());
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        setAnimation("attack");
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (isUniqueWorkBusy() && com.lobotocraft.facility.FacilityPauseState.isPaused(getServer())) return;
        if (!hasEscape()) {
            tickContained();
            return;
        }
        setNoAi(false);
        AbnormalityFearService.tickEncounter(this, this);
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
            if (attackTarget != null && attackTarget.isAlive()) face(attackTarget.getX(), attackTarget.getZ());
            attackTimer--;
            attackAnimationTick++;
            dispatchAnimationEvent(AnimationEvent.ATTACK_HIT, attackAnimationTick);
            if (attackTimer == 0) {
                dispatchAnimationEvent(AnimationEvent.ATTACK_END, attackAnimationTick);
                attackTarget = null;
                if (specialAttackTimer == 0) setAnimation("idle");
            }
        }

        if (specialAttackTimer > 0) {
            specialAttackTimer--;
            specialAnimationTick++;
            dispatchAnimationEvent(AnimationEvent.SPECIAL_HEAL, specialAnimationTick);
            dispatchAnimationEvent(AnimationEvent.SPECIAL_WHITE_DAMAGE, specialAnimationTick);
            dispatchAnimationEvent(AnimationEvent.SPECIAL_CREATE_CHILD, specialAnimationTick);
            if (specialAttackTimer == 0) {
                dispatchAnimationEvent(AnimationEvent.SPECIAL_END, specialAnimationTick);
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
                seekingTombstone = moveTowardTombstone(tombstone);
            }
            getLookControl().setLookAt(
                    tombstone.getX() + 0.5D, tombstone.getY(), tombstone.getZ() + 0.5D);
            face(tombstone.getX() + 0.5D, tombstone.getZ() + 0.5D);
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

        LivingEntity nearestVictim = level().getEntitiesOfClass(
                        LivingEntity.class, getBoundingBox().inflate(32.0D), this::isValidCombatTarget)
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        setTarget(nearestVictim);
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (target != null && !isValidCombatTarget(target)) target = null;
        super.setTarget(target);
    }

    public static boolean isEmployeeTarget(LivingEntity entity) {
        if (!entity.isAlive() || entity instanceof EntityCensored || entity instanceof EntityCensoredMinion) {
            return false;
        }
        if (entity instanceof Player player) return !player.isCreative() && !player.isSpectator();
        return entity instanceof EntityClerk;
    }

    /** Minecraft adaptation: panic inside the bound containment room acts immediately. */
    public static void onEmployeePanic(net.minecraft.server.level.ServerPlayer employee) {
        if (employee == null || !(employee.level() instanceof ServerLevel level)) return;
        for (EntityCensored censored : level.getEntitiesOfClass(EntityCensored.class,
                employee.getBoundingBox().inflate(24.0D),
                entity -> entity.isAlive() && !entity.hasEscape() && !entity.isRemoved())) {
            BlockPos markerPos = censored.getHomeRoomMarker();
            if (markerPos == null || !level.dimension().location().equals(censored.getHomeRoomDimension())
                    || !(level.getBlockEntity(markerPos) instanceof RoomMarkerBlockEntity marker)
                    || !censored.getUUID().equals(marker.getBoundAbnormality())
                    || !marker.getScanBox().contains(employee.position())) continue;
            censored.decreaseQliphothCounter(1);
        }
    }

    /** Targets that CENSORED may attack after escaping; its own children remain excluded. */
    boolean isValidCombatTarget(LivingEntity entity) {
        if (entity == this || !entity.isAlive()
                || entity instanceof EntityCensoredMinion child && child.isChildOf(this)) return false;
        if (entity instanceof Player player) return !player.isCreative() && !player.isSpectator();
        if (entity instanceof EntityClerk || entity instanceof EntityRedHoodMercenary
                || entity instanceof OrdealEntity
                || FacilityAbnormalityTargetPolicy.isCoreSuppressionCombatant(entity)) return true;
        return entity instanceof AbstractAbnormality abnormality
                && abnormality.isAbnormalityCombatTarget();
    }

    private boolean canHitAttackTarget() {
        return attackTarget != null && attackTarget.isAlive() && distanceToSqr(attackTarget) <= 16.0D;
    }

    private void face(double x, double z) {
        double dx = x - getX();
        double dz = z - getZ();
        if (dx * dx + dz * dz < 1.0E-6D) return;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    private void dispatchAnimationEvent(AnimationEvent event, int animationTick) {
        if (!event.occursAt(animationTick)) return;
        switch (event) {
            case ATTACK_HIT -> {
                if (!canHitAttackTarget()) return;
                float damage = 23 + random.nextInt(4);
                LivingEntity victim = attackTarget;
                EntityUtil.clearHurtTime(victim, () -> {
                    if (!hasEscape() || isRemoved() || !victim.isAlive()
                            || distanceToSqr(victim) > 16.0D) return;
                    if (victim.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), damage)) {
                        playAttackSound();
                    }
                });
            }
            case SPECIAL_HEAL -> heal(getMaxHealth() * 0.05F);
            case SPECIAL_WHITE_DAMAGE -> applyMentalAreaDamage();
            case SPECIAL_CREATE_CHILD -> consumeTombstoneAndSpawnMinion();
            case ATTACK_END, SPECIAL_END -> {
                // Animation end only releases the state in the caller after all prior events fire.
            }
        }
    }

    private void startSpecialAttack(BlockPos spawnPos) {
        if (spawnPos == null || specialAttackTimer > 0) return;
        specialAttackTimer = SPECIAL_ANIMATION_TICKS;
        specialAnimationTick = 0;
        specialAttackSpawnPos = spawnPos.immutable();
        seekingTombstone = false;
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        setAnimation("special_attack");
        playCensoredSound(ModSounds.CENSORED_SACRIFICE.get(), 0.8F, 1.0F);

    }

    private void applyMentalAreaDamage() {
        float damage = 12 + random.nextInt(6);
        DamageSource white = DamageHelper.getDamage(this, "lobotocraft:white");
        AABB box = getBoundingBox().inflate(
                MENTAL_AOE_HALF_XZ, MENTAL_AOE_HALF_Y, MENTAL_AOE_HALF_XZ);
        List<LivingEntity> victims = level().getEntitiesOfClass(LivingEntity.class, box,
                this::isValidCombatTarget);
        for (LivingEntity victim : victims) {
            EntityUtil.clearHurtTime(victim, () -> victim.hurt(white, damage));
        }
    }

    private void spawnMinion(BlockPos spawnPos) {
        if (!(level() instanceof ServerLevel serverLevel) || spawnPos == null) return;
        EntityCensoredMinion child = ModEntities.censored_minion.get().create(serverLevel);
        if (child == null) return;
        child.setParentCensoredUuid(getUUID());
        child.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                random.nextFloat() * 360.0F, 0.0F);
        child.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED, null, null);
        child.setPersistenceRequired();
        serverLevel.addFreshEntity(child);
    }

    private void consumeTombstoneAndSpawnMinion() {
        if (specialAttackSpawnPos == null) return;
        BlockState state = level().getBlockState(specialAttackSpawnPos);
        if (state.is(ModBlocks.TOMBSTONE.get())) {
            level().destroyBlock(specialAttackSpawnPos, false);
            spawnMinion(specialAttackSpawnPos);
        }
        cachedTombstone = null;
        lastTombstoneScan = tickCount - TOMBSTONE_SCAN_INTERVAL;
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
        double dz = getZ() - (tombstone.getZ() + 0.5D);
        boolean centerClose = dx * dx + dz * dz
                <= TOMBSTONE_TRIGGER_DISTANCE * TOMBSTONE_TRIGGER_DISTANCE;
        if (Math.abs(getY() - tombstone.getY()) > TOMBSTONE_TRIGGER_DISTANCE_Y
                || !(centerClose || getBoundingBox().inflate(
                        TOMBSTONE_BODY_REACH, 0.0D, TOMBSTONE_BODY_REACH)
                        .intersects(new AABB(tombstone)))) return false;
        // The tombstone conversion is a proximity interaction. A wide CENSORED
        // body may be blocked by a one-block doorway before its center reaches
        // the block center; collision must not prevent the conversion event.
        return true;
    }

    private boolean moveTowardTombstone(BlockPos tombstone) {
        if (getNavigation().moveTo(tombstone.getX() + 0.5D,
                tombstone.getY(), tombstone.getZ() + 0.5D, 1.0D)) return true;
        // A wide body may not fit at the tombstone's center. Approach a reachable
        // side instead, then let the body-reach and unobstructed-ray check trigger it.
        List<BlockPos> approaches = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            approaches.add(tombstone.relative(direction, 3));
        }
        approaches.sort(Comparator.comparingDouble(pos -> pos.distSqr(blockPosition())));
        for (BlockPos approach : approaches) {
            if (getNavigation().moveTo(approach.getX() + 0.5D,
                    approach.getY(), approach.getZ() + 0.5D, 1.0D)) return true;
        }
        return false;
    }

    private void playContainedSacrifice() {
        containedSacrificeTimer = CONTAINED_SACRIFICE_TICKS;
        setAnimation("sacrifice");
        playCensoredSound(ModSounds.CENSORED_SACRIFICE.get(), 0.8F, 1.0F);
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        // Ordinary work damage does not start the unique corpse-conversion sequence.
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:black"), 5 + random.nextInt(6));
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
        AbnormalityFearService.resetSourceEncounters(this);
        containedSacrificeTimer = 0;
        clearEscapeCombatState();
        setNoAi(true);
        setAnimation("idle");
    }

    private void clearEscapeCombatState() {
        attackTimer = 0;
        attackAnimationTick = 0;
        attackTarget = null;
        specialAttackTimer = 0;
        specialAnimationTick = 0;
        specialAttackSpawnPos = null;
        targetReevaluationCooldown = 0;
        cachedTombstone = null;
        seekingTombstone = false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Original CENSORED cannot take damage while it processes a death scene.
        if (specialAttackTimer > 0) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean onWorkStart(net.minecraft.server.level.ServerPlayer player, WorkType workType) {
        if (isUniqueWorkBusy()) return false;
        if (!super.onWorkStart(player, workType)) return false;
        AbnormalityFearService.apply(player, encounterFear(player));
        return !com.wzz.lobotocraft.util.MentalValueUtil.isPanic(player);
    }

    @Override
    public AbnormalityFearResponse encounterFear(net.minecraft.server.level.ServerPlayer employee) {
        return AbnormalityFearService.employeeLevel(employee) < 5
                ? AbnormalityFearResponse.forcedPanic() : AbnormalityFearResponse.level(4);
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
        .add(com.wzz.lobotocraft.init.ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.6D)
        .add(com.wzz.lobotocraft.init.ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.8D)
        .add(com.wzz.lobotocraft.init.ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.4D)
        .add(com.wzz.lobotocraft.init.ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.0D);
    }
}
