package com.wzz.lobotocraft.entity;

import com.wzz.lobotocraft.core_suppression.geburah.GeburahActionWindow;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahAttackEffects;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahBleedSync;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahComboController;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahChaseWatchdog;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahComboPlanner;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahComboTactics;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahCombatState;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahCounterPassive;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahDamageResponsibility;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahDamageTuning;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEgoReleasePulse;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEgoAttackVisuals;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahEncounterService;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahHitbox;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMajorAttackController;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMajorAttackAudioPolicy;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMajorAttackEffects;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMajorHitFeedback;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMajorMove;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahMove;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahOutgoingDamagePolicy;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahPhase;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahPosturePolicy;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahPursuitPolicy;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahSafeTeleport;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahSoundscape;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahSpecialPolicy;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahSpecialController;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahStatusEffectPolicy;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahVisualEffects;
import com.wzz.lobotocraft.core_suppression.geburah.GeburahVisualEffectType;
import com.wzz.lobotocraft.core_suppression.geburah.CombatControlLockService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.BaseGeoEntity;
import com.wzz.lobotocraft.entity.ai.goal.MoveToBlockPosWithElevatorGoal;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.GeburahCameraShakePacket;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class EntityGeburah extends BaseGeoEntity {
    private static final double COMBO_START_RANGE = 5.0D;
    private static final String WILL_OF_BATTLE = "willofbattle";
    private static final String EGO_WILL_OF_BATTLE = "ego_willofbattle";
    private static final int WILL_OF_BATTLE_COOLDOWN = 35 * 20;
    private static final int EGO_WILL_OF_BATTLE_COOLDOWN = 30 * 20;
    private static final int EGO_RELEASE_ANIMATION_TICKS = 59;
    private static final int EGO_RELEASE_LEAD_TICKS = 10;
    private static final String BLOOD_MIST = "bloodmist";
    private static final String EGO_BLOOD_MIST = "egobloodmist";
    private static final String BODIES_EVERYWHERE = "bodieseverywhere";
    private static final Set<String> DEBUG_TRAIL_ANIMATIONS = Set.of(
            "slash", "stab", "verticalslash", "willofbattle",
            "EGOslash", "EGOstab", "EGOverticalslash", "EGOwillofbattle");
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(
            EntityGeburah.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> POSTURE = SynchedEntityData.defineId(
            EntityGeburah.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EGO = SynchedEntityData.defineId(
            EntityGeburah.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ANIMATION_SPEED = SynchedEntityData.defineId(
            EntityGeburah.class, EntityDataSerializers.FLOAT);

    private final GeburahCombatState combatState = new GeburahCombatState();
    private GeburahActionWindow actionWindow = GeburahActionWindow.OTHER;
    private boolean debugDamage;
    private int targetScanTicks;
    private int phaseTransitionTicks;
    private boolean egoReleaseManifestStarted;
    private int debugComboDepth;
    private final Map<String, Integer> debugCooldowns = new HashMap<>();
    private final GeburahComboController combo = new GeburahComboController();
    private LivingEntity comboTarget;
    private boolean comboEgo;
    private boolean comboPostureSpent;
    private final GeburahSpecialController special = new GeburahSpecialController();
    private LivingEntity specialTarget;
    private Vec3 specialDestination;
    private boolean specialEgo;
    private boolean willKillPriority;
    private int specialRetryTicks;
    private final GeburahMajorAttackController major = new GeburahMajorAttackController();
    private LivingEntity majorTarget;
    private ServerPlayer majorBoundPlayer;
    private final Set<java.util.UUID> majorPostureTargets = new HashSet<>();
    private boolean majorSoundPlayed;
    private int majorLastWindupSoundTick;
    private boolean bodiesOpeningPending;
    private final GeburahCounterPassive counterPassive = new GeburahCounterPassive();
    private LivingEntity counterattackTarget;
    private boolean counterattackArmed;
    private int counterPursuitTicks;
    private long suppressEffectConversionUntilTick = Long.MIN_VALUE;
    private int egoAbnormalityKillBonus;
    private LivingEntity pendingPriorityTarget;
    private final GeburahChaseWatchdog chaseWatchdog = new GeburahChaseWatchdog();
    private String debugTrailAnimation;
    private float debugTrailYaw;
    private int debugTrailRestartTicks;

    public EntityGeburah(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
        setAnimation("idle");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, GeburahCombatState.DEFAULT_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, GeburahPursuitPolicy.movementSpeed(false))
                .add(Attributes.FOLLOW_RANGE, 256.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.6D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 0.6D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new MoveToBlockPosWithElevatorGoal(
                this, 1.0D, this::pursuitTargetPosition,
                this::shouldUseElevatorPursuit, () -> { }));
    }

    @Override
    public String name() {
        return "geburah";
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, GeburahPhase.NORMAL.ordinal());
        entityData.define(POSTURE, GeburahCombatState.MAX_POSTURE);
        entityData.define(EGO, 0.0F);
        entityData.define(ANIMATION_SPEED, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || combatState.phase() == GeburahPhase.DEFEATED) return;
        if (debugTrailAnimation != null) {
            tickDebugTrailAnimation();
            return;
        }
        tickMoveCooldowns();
        if (specialRetryTicks > 0) specialRetryTicks--;
        if (counterPursuitTicks > 0) counterPursuitTicks--;

        if (--targetScanTicks <= 0) {
            targetScanTicks = 10;
            selectFacilityTarget();
        }
        LivingEntity target = getTarget();
        boolean hasValidTarget = isValidTarget(target);
        GeburahPhase previousPhase = combatState.phase();
        combatState.tick(hasValidTarget);
        if (previousPhase != combatState.phase()) {
            if (combatState.phase() == GeburahPhase.EGO) beginEgoRelease();
            if (combatState.phase() == GeburahPhase.EXHAUSTED) {
                egoAbnormalityKillBonus = 0;
                GeburahVisualEffects.broadcastStage(this,
                        GeburahVisualEffectType.EGO_EXHAUSTION, 1.0F, 20);
            }
        }
        if (phaseTransitionTicks > 0) {
            cancelCombatActions();
            if (!egoReleaseManifestStarted
                    && phaseTransitionTicks <= EGO_RELEASE_ANIMATION_TICKS) {
                startEgoReleaseManifestation();
            }
            phaseTransitionTicks--;
            getNavigation().stop();
            setAnimation(egoReleaseManifestStarted ? "EGOrelease" : "idle");
            applyPhaseAttributes();
            syncCombatState();
            GeburahEncounterService.INSTANCE.updateBossBars(this);
            return;
        }
        if (combatState.postureBreakTicks() > 0) {
            cancelCombatActions();
            getNavigation().stop();
            setAnimation(combatState.phase() == GeburahPhase.EGO ? "EGOidle" : "idle");
            applyPhaseAttributes();
            syncCombatState();
            GeburahEncounterService.INSTANCE.updateBossBars(this);
            return;
        }
        if (tickCombatAction(target)) {
            applyPhaseAttributes();
            syncCombatState();
            GeburahEncounterService.INSTANCE.updateBossBars(this);
            return;
        }
        if (hasValidTarget) {
            double pursuitSpeed = counterPursuitTicks > 0 && target == counterattackTarget
                    ? 1.65D : 1.0D;
            boolean pursuitTeleported = false;
            if (shouldUseElevatorPursuit()) {
                chaseWatchdog.reset();
            } else {
                getNavigation().moveTo(target, pursuitSpeed);
                pursuitTeleported = recoverStuckChase(target);
            }
            if (!pursuitTeleported) faceMovementIfFree();
            setAnimation(combatState.phase() == GeburahPhase.EGO ? "EGOmove" : "move");
        } else {
            chaseWatchdog.reset();
            getNavigation().stop();
            setAnimation(combatState.phase() == GeburahPhase.EGO ? "EGOidle" : "idle");
        }
        applyPhaseAttributes();
        syncCombatState();
        GeburahEncounterService.INSTANCE.updateBossBars(this);
    }

    private BlockPos pursuitTargetPosition() {
        LivingEntity target = getTarget();
        return isValidTarget(target) ? target.blockPosition() : null;
    }

    private boolean shouldUseElevatorPursuit() {
        LivingEntity target = getTarget();
        return GeburahPursuitPolicy.shouldUseElevator(getY(),
                target == null ? getY() : target.getY(),
                isValidTarget(target), isCombatActionActive());
    }

    private boolean recoverStuckChase(LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)
                || !chaseWatchdog.observe(target.getUUID(), position())) return false;
        Optional<Vec3> destination = GeburahSafeTeleport.findBehindTarget(
                serverLevel, this, target, combatState.phase() == GeburahPhase.EGO ? 5.0D : 3.5D);
        if (destination.isEmpty()) return false;
        Vec3 start = position();
        Vec3 end = destination.get();
        moveTo(end.x, end.y, end.z, getYRot(), getXRot());
        faceTarget(target, 180.0F);
        GeburahAttackEffects.spawnPursuitDash(serverLevel, start, end,
                combatState.phase() == GeburahPhase.EGO);
        return true;
    }

    private void faceMovementIfFree() {
        double deltaX = getX() - xo;
        double deltaZ = getZ() - zo;
        if (!GeburahPursuitPolicy.shouldFaceMovement(
                isCombatActionActive(), deltaX, deltaZ)) return;
        float yaw = GeburahPursuitPolicy.movementYaw(deltaX, deltaZ);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private boolean tickCombatAction(LivingEntity target) {
        if (major.isActive()) return tickMajorAttack();
        if (special.isActive()) return tickWillOfBattle();
        if (combo.isAttacking()) return tickComboAttack(target, false, false);
        applyPendingPriorityTarget();
        target = getTarget();
        if (!isValidTarget(target)) return false;

        boolean ego = combatState.phase() == GeburahPhase.EGO;
        boolean fullHealth = target.getHealth() >= target.getMaxHealth() - 0.001F;
        String cooldownKey = ego ? EGO_WILL_OF_BATTLE : WILL_OF_BATTLE;
        boolean willReady = debugCooldown(cooldownKey) <= 0 && specialRetryTicks <= 0;
        Optional<Vec3> landing = Optional.empty();
        if (willReady) {
            landing = GeburahSafeTeleport.findWillOfBattle(
                    (ServerLevel) level(), this, target, ego);
            if (landing.isEmpty()) specialRetryTicks = 20;
        }
        int nearbyEnemies = ((ServerLevel) level()).getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(12.0D, 5.0D, 12.0D), this::isValidTarget).size();
        boolean bloodMistReady = debugCooldown(ego ? EGO_BLOOD_MIST : BLOOD_MIST) <= 0;
        boolean bodiesReady = debugCooldown(BODIES_EVERYWHERE) <= 0;
        if (counterattackArmed && counterattackTarget == target) {
            if (ego && bodiesReady && nearbyEnemies >= 2) {
                counterattackArmed = false;
                return startMajorAttack(GeburahMajorMove.BODIES_EVERYWHERE, target);
            }
            if (target instanceof ServerPlayer && bloodMistReady) {
                counterattackArmed = false;
                return startMajorAttack(ego ? GeburahMajorMove.EGO_BLOOD_MIST
                        : GeburahMajorMove.BLOOD_MIST, target);
            }
            if (willReady && landing.isPresent()) {
                counterattackArmed = false;
                return startWillOfBattle(target, landing.orElseThrow(), ego);
            }
            boolean started = tickComboAttack(target, true, true);
            if (started) counterattackArmed = false;
            return started;
        }
        if (willKillPriority && willReady && landing.isPresent()) {
            return startWillOfBattle(target, landing.orElseThrow(), ego);
        }
        GeburahSpecialPolicy.Action action = GeburahSpecialPolicy.choose(
                new GeburahSpecialPolicy.Snapshot(ego, target instanceof ServerPlayer,
                        fullHealth, distanceTo(target), nearbyEnemies, bodiesOpeningPending,
                        bodiesReady, bloodMistReady, willReady, landing.isPresent()));
        return switch (action) {
            case BODIES_EVERYWHERE -> startMajorAttack(
                    GeburahMajorMove.BODIES_EVERYWHERE, target);
            case BLOOD_MIST -> startMajorAttack(ego
                    ? GeburahMajorMove.EGO_BLOOD_MIST
                    : GeburahMajorMove.BLOOD_MIST, target);
            case WILL_OF_BATTLE -> startWillOfBattle(target, landing.orElseThrow(), ego);
            case COMBO -> tickComboAttack(target, true,
                    !fullHealth || combatState.posture()
                            < GeburahCombatState.MAX_POSTURE * 0.75F);
        };
    }

    private boolean startMajorAttack(GeburahMajorMove move, LivingEntity target) {
        if (!major.start(move)) return false;
        majorTarget = target;
        majorBoundPlayer = null;
        majorPostureTargets.clear();
        majorSoundPlayed = false;
        majorLastWindupSoundTick = 0;
        bodiesOpeningPending = false;
        debugComboDepth = 0;
        debugCooldowns.put(move.animation().toLowerCase(java.util.Locale.ROOT),
                move.cooldownTicks());
        entityData.set(ANIMATION_SPEED, 1.0F);
        faceTarget(target, 180.0F);
        setAnimation(move.animation());
        getNavigation().stop();
        actionWindow = GeburahActionWindow.WINDUP;
        announceSpecialAttack(move.displayNameTranslationKey());
        level().playSound(null, blockPosition(), move == GeburahMajorMove.BODIES_EVERYWHERE
                        ? ModSounds.GEBURAH_BODIES_EVERYWHERE_WINDUP.get()
                        : ModSounds.GEBURAH_BLOOD_MIST_WINDUP.get(),
                SoundSource.HOSTILE, 1.45F, 0.94F);
        return true;
    }

    private boolean tickMajorAttack() {
        GeburahMajorMove move = major.move();
        if (move == null) return false;
        if (move.bloodMist() && !isValidTarget(majorTarget)) {
            abortMajorAttack();
            return false;
        }

        GeburahMajorAttackController.Event event = major.tick();
        if (level() instanceof ServerLevel serverLevel) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
            Vec3 targetPosition = isValidTarget(majorTarget) ? majorTarget.position() : null;
            GeburahMajorAttackEffects.spawnFrame(serverLevel, move, major.elapsedTicks(),
                    position(), targetPosition, forward);
            if (event == GeburahMajorAttackController.Event.HIT) {
                GeburahMajorMove.Cue cue = major.currentCue();
                GeburahMajorAttackEffects.spawnImpact(serverLevel, move, cue,
                        position(), targetPosition, forward);
                playMajorHitSound(move, cue);
                boolean connected = performMajorHit(move, cue, forward);
                if (cue.opening() && !connected) {
                    abortMajorAttack();
                    return false;
                }
            }
        }

        if (event == GeburahMajorAttackController.Event.FINISHED) {
            finishMajorAttack();
            return false;
        }
        getNavigation().stop();
        actionWindow = switch (major.stage()) {
            case WINDUP -> GeburahActionWindow.WINDUP;
            case ACTIVE -> GeburahActionWindow.ACTIVE;
            case RECOVERY -> GeburahActionWindow.RECOVERY;
            case READY -> GeburahActionWindow.OTHER;
        };
        return true;
    }

    private boolean performMajorHit(GeburahMajorMove move, GeburahMajorMove.Cue cue,
                                    Vec3 forward) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        if (cue.hitKind() == GeburahMajorMove.HitKind.BOUND_TARGET) {
            if (!isValidTarget(majorTarget)) return false;
            if (cue.opening() && (distanceTo(majorTarget) > 8.0D
                    || !hasLineOfSight(majorTarget))) return false;
            float healthBefore = majorTarget.getHealth();
            boolean hit = hurtWithoutVanillaReaction(majorTarget,
                    outgoingDamage(majorTarget, rollDamage(cue)));
            if (!hit) return false;
            float healthLoss = Math.max(0.0F, healthBefore - majorTarget.getHealth());
            restoreMajorPosture(majorTarget);
            if (cue.opening() && majorTarget instanceof ServerPlayer player) {
                majorBoundPlayer = player;
                CombatControlLockService.lock(player, this,
                        Math.max(1, move.durationTicks() - major.elapsedTicks() + 2));
            }
            if (majorTarget instanceof ServerPlayer player) {
                sendMajorShake(player, move, cue, healthLoss);
            }
            return true;
        }

        GeburahHitbox hitbox = cue.hitKind() == GeburahMajorMove.HitKind.WIDE_AREA
                ? GeburahHitbox.forwardBox(position(), forward, 12.0D, 15.0D, 5.0D)
                : GeburahHitbox.forwardBox(position(), forward, 8.0D, 3.0D, 8.0D);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
                LivingEntity.class, hitbox.searchBounds(), target ->
                        isValidTarget(target) && hitbox.intersects(target.getBoundingBox())
                                && hasLineOfSight(target));
        targets.sort(Comparator.comparingDouble(this::distanceToSqr));
        boolean connected = false;
        for (LivingEntity target : targets) {
            float healthBefore = target.getHealth();
            if (!hurtWithoutVanillaReaction(target,
                    outgoingDamage(target, rollDamage(cue)))) continue;
            float healthLoss = Math.max(0.0F, healthBefore - target.getHealth());
            connected = true;
            restoreMajorPosture(target);
            if (move == GeburahMajorMove.BODIES_EVERYWHERE) {
                GeburahBleedSync.apply(this, target, 5);
            }
            if (target instanceof ServerPlayer player) {
                sendMajorShake(player, move, cue, healthLoss);
            }
        }
        return connected;
    }

    private void playMajorHitSound(GeburahMajorMove move, GeburahMajorMove.Cue cue) {
        GeburahMajorAttackAudioPolicy.HitSound sound =
                GeburahMajorAttackAudioPolicy.forCue(move, cue,
                        major.elapsedTicks(), majorLastWindupSoundTick);
        if (sound == GeburahMajorAttackAudioPolicy.HitSound.WINDUP) {
            majorLastWindupSoundTick = major.elapsedTicks();
            level().playSound(null, blockPosition(), ModSounds.GEBURAH_BLOOD_MIST_WINDUP.get(),
                    SoundSource.HOSTILE, 1.45F, 0.94F);
            GeburahSoundscape.playMajorImpact(level(), blockPosition(), move, false);
        } else if (sound == GeburahMajorAttackAudioPolicy.HitSound.IMPACT
                && !majorSoundPlayed) {
            majorSoundPlayed = true;
            level().playSound(null, blockPosition(),
                    move == GeburahMajorMove.BODIES_EVERYWHERE
                            ? ModSounds.GEBURAH_BODIES_EVERYWHERE.get()
                            : ModSounds.GEBURAH_BLOOD_MIST.get(),
                    SoundSource.HOSTILE, 1.6F, 0.96F);
            GeburahSoundscape.playMajorImpact(level(), blockPosition(), move, true);
        }
    }

    private float rollDamage(GeburahMajorMove.Cue cue) {
        return cue.minDamage() + random.nextInt(cue.maxDamage() - cue.minDamage() + 1);
    }

    private void restoreMajorPosture(LivingEntity target) {
        if (target instanceof AbstractAbnormality) return;
        if (!majorPostureTargets.add(target.getUUID())) return;
        int distinctTargets = majorPostureTargets.size();
        float restore = distinctTargets == 1 ? 12.0F
                : distinctTargets <= 4 ? 2.0F : 0.0F;
        combatState.restorePosture(restore);
    }

    private void sendMajorShake(ServerPlayer player, GeburahMajorMove move,
                                GeburahMajorMove.Cue cue, float healthLoss) {
        GeburahMajorHitFeedback.Profile feedback =
                GeburahMajorHitFeedback.forCue(move, cue);
        MessageLoader.getLoader().sendToPlayer(player,
                new GeburahCameraShakePacket(feedback.durationTicks(),
                        feedback.amplitude(), random.nextInt()));
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        GeburahVisualEffectType type = move == GeburahMajorMove.BODIES_EVERYWHERE
                ? GeburahVisualEffectType.BODIES_EVERYWHERE_HIT
                : GeburahVisualEffectType.BLOOD_MIST_HIT;
        GeburahVisualEffects.sendHit(player, this, type, forward,
                feedback.visualIntensity(), healthLoss, feedback.durationTicks());
    }

    private void finishMajorAttack() {
        releaseMajorTarget();
        major.reset();
        majorTarget = null;
        majorPostureTargets.clear();
        majorSoundPlayed = false;
        majorLastWindupSoundTick = 0;
        actionWindow = GeburahActionWindow.OTHER;
    }

    private void abortMajorAttack() {
        releaseMajorTarget();
        major.abort();
        major.reset();
        majorTarget = null;
        majorPostureTargets.clear();
        majorSoundPlayed = false;
        majorLastWindupSoundTick = 0;
        actionWindow = GeburahActionWindow.OTHER;
    }

    private void releaseMajorTarget() {
        if (majorBoundPlayer != null) CombatControlLockService.clear(majorBoundPlayer);
        majorBoundPlayer = null;
    }

    private void faceTarget(LivingEntity target, float maximumTurn) {
        if (!isValidTarget(target)) return;
        Vec3 direction = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 1.0E-6D) return;
        float desiredYaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0D);
        float yaw = GeburahPursuitPolicy.turnToward(getYRot(), desiredYaw, maximumTurn >= 180.0F);
        if (maximumTurn >= 180.0F) yaw = desiredYaw;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private boolean tickComboAttack(LivingEntity target, boolean allowStart, boolean preferLong) {
        boolean canStart = allowStart && isValidTarget(target)
                && distanceTo(target) <= COMBO_START_RANGE
                && hasLineOfSight(target);
        GeburahComboController.Event event;
        if (!combo.isAttacking() && canStart) {
            comboEgo = combatState.phase() == GeburahPhase.EGO;
            GeburahComboPlanner.AttackPlan attackPlan = GeburahComboPlanner.planForPosture(
                    comboEgo, preferLong, combatState.posture(), random::nextInt);
            comboPostureSpent = GeburahPosturePolicy.spendsComboPosture(
                    target instanceof AbstractAbnormality)
                    && attackPlan.spendsComboPosture()
                    && combatState.spendComboPosture();
            event = combo.start(attackPlan.moves(), comboEgo);
        } else {
            event = combo.tick();
        }

        if (event == GeburahComboController.Event.START_WINDUP) {
            comboTarget = target;
            debugComboDepth = 1;
            entityData.set(ANIMATION_SPEED, (float) combo.animationSpeed());
            setAnimation(combo.move().beginAnimation(comboEgo));
        } else if (event == GeburahComboController.Event.START_STRIKE
                || event == GeburahComboController.Event.START_CHAIN_STRIKE) {
            prepareComboStrike(event == GeburahComboController.Event.START_CHAIN_STRIKE);
            debugComboDepth = combo.moveIndex() + 1;
            setAnimation(combo.move().strikeAnimation(comboEgo));
            playMoveSound(combo.move());
        } else if (event == GeburahComboController.Event.HIT) {
            performComboMove(combo.move());
        } else if (event == GeburahComboController.Event.START_RECOVERY) {
            setAnimation(combo.move().recoveryAnimation(comboEgo));
        } else if (event == GeburahComboController.Event.FINISHED) {
            if (combo.consumeCleanRecovery() && comboPostureSpent) {
                combatState.refundCleanRecovery();
            }
            comboPostureSpent = false;
            comboTarget = null;
            debugComboDepth = 0;
        }

        if (!combo.isAttacking()) {
            actionWindow = GeburahActionWindow.OTHER;
            return false;
        }
        getNavigation().stop();
        if (isValidTarget(comboTarget) && combo.stage() == GeburahComboController.Stage.WINDUP) {
            faceTarget(comboTarget, 90.0F);
        }
        actionWindow = switch (combo.stage()) {
            case WINDUP -> GeburahActionWindow.WINDUP;
            case STRIKE -> GeburahActionWindow.ACTIVE;
            case RECOVERY -> GeburahActionWindow.RECOVERY;
            case READY -> GeburahActionWindow.OTHER;
        };
        return true;
    }

    private void prepareComboStrike(boolean chained) {
        LivingEntity currentTarget = isValidTarget(comboTarget) ? comboTarget : getTarget();
        if (!isValidTarget(currentTarget) || !(level() instanceof ServerLevel serverLevel)) return;
        comboTarget = currentTarget;

        Vec3 relative = currentTarget.position().subtract(position());
        Vec3 horizontal = relative.multiply(1.0D, 0.0D, 1.0D);
        double distance = horizontal.length();
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        double lateral = Math.abs(horizontal.dot(side));
        double awaySpeed = distance < 1.0E-6D ? 0.0D
                : currentTarget.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D)
                .dot(horizontal.normalize());
        int nearbyEnemies = serverLevel.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(8.0D, 4.0D, 8.0D), this::isValidTarget).size();

        if (chained) {
            GeburahMove next = GeburahComboTactics.chooseNext(combo.previousMove(),
                    new GeburahComboTactics.Snapshot(distance, lateral, awaySpeed,
                            nearbyEnemies, hasLineOfSight(currentTarget)), random::nextInt);
            combo.replaceCurrentMove(next);
        }

        Vec3 targetVelocity = currentTarget.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        Vec3 predictedTarget = currentTarget.position().add(targetVelocity.scale(4.0D));
        Vec3 predictedDirection = predictedTarget.subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        if (predictedDirection.lengthSqr() > 1.0E-6D) {
            float desiredYaw = (float) (Math.toDegrees(Math.atan2(
                    predictedDirection.z, predictedDirection.x)) - 90.0D);
            float adjustedYaw = GeburahPursuitPolicy.turnToward(getYRot(), desiredYaw, comboEgo);
            setYRot(adjustedYaw);
            setYBodyRot(adjustedYaw);
            setYHeadRot(adjustedYaw);
        }

        double stepDistance = GeburahPursuitPolicy.stepDistance(distance, comboEgo);
        Optional<Vec3> destination = GeburahSafeTeleport.findPursuitStep(
                serverLevel, this, currentTarget, predictedTarget, stepDistance);
        if (destination.isPresent()) {
            Vec3 start = position();
            Vec3 end = destination.get();
            moveTo(end.x, end.y, end.z, getYRot(), getXRot());
            GeburahAttackEffects.spawnPursuitDash(serverLevel, start, end, comboEgo);
        }
    }

    private boolean startWillOfBattle(LivingEntity target, Vec3 destination, boolean ego) {
        specialTarget = target;
        specialDestination = destination;
        specialEgo = ego;
        debugComboDepth = 0;
        special.start();
        willKillPriority = false;
        entityData.set(ANIMATION_SPEED, 1.0F);
        debugCooldowns.put(ego ? EGO_WILL_OF_BATTLE : WILL_OF_BATTLE,
                ego ? EGO_WILL_OF_BATTLE_COOLDOWN : WILL_OF_BATTLE_COOLDOWN);
        setAnimation(ego ? "EGOwillofbattle_begin" : "willofbattle_begin");
        getNavigation().stop();
        actionWindow = GeburahActionWindow.WINDUP;
        announceSpecialAttack("attack.lobotocraft.geburah.will_of_battle");
        return true;
    }

    private void announceSpecialAttack(String attackNameKey) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Component attackName = Component.translatable(attackNameKey).withStyle(ChatFormatting.RED);
        Component warning = Component.translatable(
                "message.lobotocraft.core_suppression.geburah.special_attack_warning", attackName)
                .withStyle(ChatFormatting.DARK_RED);
        for (ServerPlayer player : serverLevel.players()) {
            player.displayClientMessage(warning, true);
        }
    }

    private boolean tickWillOfBattle() {
        GeburahSpecialController.Event event = special.tick();
        if (event == GeburahSpecialController.Event.START_STRIKE) {
            Optional<Vec3> refreshed = isValidTarget(specialTarget)
                    ? GeburahSafeTeleport.findWillOfBattle(
                            (ServerLevel) level(), this, specialTarget, specialEgo)
                    : Optional.empty();
            if (refreshed.isEmpty()) {
                event = special.abortToRecovery();
            } else {
                Vec3 origin = position();
                specialDestination = refreshed.get();
                moveTo(specialDestination.x, specialDestination.y, specialDestination.z,
                        getYRot(), getXRot());
                faceTarget(specialTarget, 180.0F);
                spawnWillOfBattleParticles((ServerLevel) level(), origin, specialDestination);
                setAnimation(specialEgo ? "EGOwillofbattle" : "willofbattle");
                level().playSound(null, blockPosition(), ModSounds.GEBURAH_SLASH.get(),
                        SoundSource.HOSTILE, 1.35F, specialEgo ? 0.88F : 0.96F);
                GeburahSoundscape.playAttackMotion(level(), blockPosition(),
                        GeburahMove.STAB, specialEgo);
            }
        }
        if (event == GeburahSpecialController.Event.HIT) {
            performWillOfBattleHit();
        } else if (event == GeburahSpecialController.Event.START_RECOVERY) {
            setAnimation(specialEgo ? "EGOwillofbattle_end" : "willofbattle_end");
        } else if (event == GeburahSpecialController.Event.FINISHED) {
            specialTarget = null;
            specialDestination = null;
        }

        if (!special.isActive()) {
            actionWindow = GeburahActionWindow.OTHER;
            return false;
        }
        getNavigation().stop();
        if (isValidTarget(specialTarget)
                && special.stage() == GeburahSpecialController.Stage.WINDUP) {
            faceTarget(specialTarget, 180.0F);
        }
        actionWindow = switch (special.stage()) {
            case WINDUP -> GeburahActionWindow.WINDUP;
            case STRIKE -> GeburahActionWindow.ACTIVE;
            case RECOVERY -> GeburahActionWindow.RECOVERY;
            case READY -> GeburahActionWindow.OTHER;
        };
        return true;
    }

    private void performWillOfBattleHit() {
        LivingEntity target = specialTarget;
        if (!isValidTarget(target) || distanceTo(target) > 8.0D || !hasLineOfSight(target)) return;
        float damage = 19.0F + random.nextInt(8);
        float healthBefore = target.getHealth();
        if (!hurtWithoutVanillaReaction(target,
                outgoingDamage(target, damage))) return;
        GeburahSoundscape.playAttackImpact(level(), blockPosition(),
                GeburahMove.STAB, specialEgo);
        float healthLoss = Math.max(0.0F, healthBefore - target.getHealth());
        if (GeburahPosturePolicy.restoresPostureOnHit(target instanceof AbstractAbnormality)) {
            combatState.restorePosture(12.0F);
        }
        if (!target.isAlive()
                || target instanceof AbstractAbnormality abnormality && !abnormality.hasEscape()) {
            willKillPriority = true;
        }
        if (target instanceof ServerPlayer player) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
            GeburahVisualEffects.sendHit(player, this,
                    GeburahVisualEffectType.WILL_OF_BATTLE_HIT,
                    forward, specialEgo ? 1.0F : 0.82F,
                    healthLoss, specialEgo ? 9 : 7);
        }
    }

    private void spawnWillOfBattleParticles(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 axis = end.subtract(start);
        int samples = specialEgo ? 30 : 24;
        for (int i = 0; i < samples; i++) {
            double progress = i / (double) Math.max(1, samples - 1);
            Vec3 point = start.add(axis.scale(progress)).add(0.0D,
                    0.35D + (i % 3) * 0.32D, 0.0D);
            level.sendParticles(DustParticleOptions.REDSTONE, point.x, point.y, point.z,
                    1, 0.035D, 0.055D, 0.035D, 0.018D);
            if (i % 6 == 0) {
                level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z,
                        1, 0.08D, 0.08D, 0.08D, 0.04D);
            }
        }
        Vec3 lineCenter = start.lerp(end, 0.5D).add(0.0D, getBbHeight() * 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                lineCenter.x, lineCenter.y, lineCenter.z,
                specialEgo ? 6 : 4, Math.abs(axis.x) * 0.2D, 0.25D,
                Math.abs(axis.z) * 0.2D, 0.0D);
    }

    private void playMoveSound(GeburahMove move) {
        SoundEvent sound = switch (move) {
            case SLASH -> ModSounds.GEBURAH_SLASH.get();
            case STAB -> ModSounds.GEBURAH_STAB.get();
            case VERTICAL_SLASH -> comboEgo
                    ? ModSounds.GEBURAH_EGO_VERTICAL_SLASH.get()
                    : ModSounds.GEBURAH_VERTICAL_SLASH.get();
        };
        level().playSound(null, blockPosition(), sound, SoundSource.HOSTILE,
                1.2F, comboEgo ? 0.92F : 1.0F);
        GeburahSoundscape.playAttackMotion(level(), blockPosition(), move, comboEgo);
    }

    private void performComboMove(GeburahMove move) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
        GeburahHitbox hitbox = GeburahHitbox.forMove(move, comboEgo, position(), forward);
        GeburahAttackEffects.spawn(serverLevel, move, comboEgo,
                combatState.phase() == GeburahPhase.EGO, position(), forward);
        GeburahSoundscape.playAttackImpact(level(), blockPosition(), move, comboEgo);

        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
                LivingEntity.class, hitbox.searchBounds(), target ->
                        isValidTarget(target) && hitbox.intersects(target.getBoundingBox())
                                && hasLineOfSight(target));
        targets.sort(Comparator.<LivingEntity>comparingInt(target -> target == comboTarget ? 0 : 1)
                .thenComparingDouble(this::distanceToSqr));

        int postureEligibleHits = 0;
        boolean primaryTargetHit = false;
        boolean enhancedContacts = GeburahEgoAttackVisuals.enabled(true, comboEgo,
                combatState.phase() == GeburahPhase.EGO, move);
        List<Vec3> visualContacts = enhancedContacts
                ? new ArrayList<>(GeburahEgoAttackVisuals.MAX_CONTACTS) : List.of();
        for (LivingEntity target : targets) {
            float damage = move.minDamage(comboEgo)
                    + random.nextInt(move.maxDamage(comboEgo) - move.minDamage(comboEgo) + 1);
            float healthBefore = target.getHealth();
            boolean wantsContact = enhancedContacts
                    && visualContacts.size() < GeburahEgoAttackVisuals.MAX_CONTACTS;
            boolean escapedBefore = wantsContact && target instanceof AbstractAbnormality abnormality
                    && abnormality.hasEscape();
            // Snapshot before suppression can return the entity to its containment home.
            Vec3 contact = null;
            if (wantsContact) {
                var bounds = target.getBoundingBox();
                contact = new Vec3(net.minecraft.util.Mth.clamp(getX(), bounds.minX, bounds.maxX),
                        bounds.minY + bounds.getYsize() * 0.55D,
                        net.minecraft.util.Mth.clamp(getZ(), bounds.minZ, bounds.maxZ));
            }
            if (!hurtWithoutVanillaReaction(target,
                    outgoingDamage(target, damage))) continue;
            float healthLoss = Math.max(0.0F, healthBefore - target.getHealth());
            boolean suppressed = escapedBefore && target instanceof AbstractAbnormality abnormality
                    && !abnormality.hasEscape();
            if (wantsContact
                    && GeburahEgoAttackVisuals.validHit(true, healthLoss, suppressed)) {
                visualContacts.add(contact);
            }
            if (target == comboTarget) primaryTargetHit = true;

            if (GeburahPosturePolicy.restoresPostureOnHit(
                    target instanceof AbstractAbnormality)) {
                postureEligibleHits++;
            }
            int bleedStacks = move.bleedStacks(comboEgo);
            if (bleedStacks > 0) GeburahBleedSync.apply(this, target, bleedStacks);
            if (move == GeburahMove.VERTICAL_SLASH && target instanceof ServerPlayer player) {
                CombatControlLockService.lock(player, this, comboEgo ? 10 : 6);
            }
            if (target instanceof ServerPlayer player) sendMoveShake(player, move, healthLoss);
        }
        if (!visualContacts.isEmpty()) {
            // Visual randomness must not consume the entity's damage/AI random stream.
            var packet = new com.wzz.lobotocraft.network.packet.GeburahEgoContactPacket(
                    serverLevel.dimension().location(), move, forward, visualContacts,
                    (int) serverLevel.getGameTime() ^ getId());
            for (var viewer : serverLevel.players()) {
                if (viewer.distanceToSqr(this) <= GeburahEgoAttackVisuals.VIEW_DISTANCE
                        * GeburahEgoAttackVisuals.VIEW_DISTANCE) {
                    MessageLoader.getLoader().sendToPlayer(viewer, packet);
                }
            }
        }
        combatState.restorePostureFromAttackHits(postureEligibleHits);
        if (primaryTargetHit && isValidTarget(comboTarget)) {
            applyMoveDisplacement(serverLevel, move, comboTarget, forward);
        }
    }

    private void applyMoveDisplacement(ServerLevel level, GeburahMove move,
                                       LivingEntity target, Vec3 forward) {
        if (move == GeburahMove.SLASH) {
            boolean passThrough = !comboEgo ? random.nextBoolean() : random.nextBoolean();
            if (passThrough) {
                GeburahSafeTeleport.findBehindTarget(level, this, target, 3.0D)
                        .ifPresent(destination -> moveEntitySafely(level, this, destination));
            } else if (comboEgo) {
                Vec3 away = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
                if (away.lengthSqr() > 1.0E-6D) {
                    GeburahSafeTeleport.findSafeDisplacement(
                                    level, target, away.normalize().scale(3.0D))
                            .ifPresent(destination -> moveEntitySafely(level, target, destination));
                }
            }
            return;
        }
        if (move != GeburahMove.STAB || !comboEgo) return;

        Vec3 retreat = forward.multiply(-2.0D, 0.0D, -2.0D);
        Optional<Vec3> geburahDestination = GeburahSafeTeleport.findSafeDisplacement(
                level, this, retreat, target);
        Optional<Vec3> targetDestination = GeburahSafeTeleport.findSafeDisplacement(
                level, target, retreat, this);
        if (geburahDestination.isEmpty() || targetDestination.isEmpty()) return;
        moveEntitySafely(level, target, targetDestination.get());
        moveEntitySafely(level, this, geburahDestination.get());
    }

    private void moveEntitySafely(ServerLevel level, LivingEntity entity, Vec3 destination) {
        Vec3 start = entity.position();
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(destination.x, destination.y, destination.z,
                    player.getYRot(), player.getXRot());
        } else {
            entity.moveTo(destination.x, destination.y, destination.z,
                    entity.getYRot(), entity.getXRot());
        }
        GeburahAttackEffects.spawnPursuitDash(level, start, destination,
                combatState.phase() == GeburahPhase.EGO);
    }

    private boolean hurtWithoutVanillaReaction(LivingEntity target, float damage) {
        boolean wasEscapedAbnormality = target instanceof AbstractAbnormality abnormality
                && abnormality.hasEscape();
        Vec3 movement = target.getDeltaMovement();
        EntityUtil.clearHurtTime(target);
        boolean hurt = target instanceof ServerPlayer
                && GeburahDamageTuning.shouldNeutralizePlayerRed(combatState.phase())
                ? GeburahDamageTuning.withNeutralPlayerRed(() ->
                        target.hurt(DamageHelper.getDamage(this, "red"), damage))
                : target.hurt(DamageHelper.getDamage(this, "red"), damage);
        target.setDeltaMovement(movement);
        EntityUtil.clearHurtTime(target);
        if (hurt && wasEscapedAbnormality && target instanceof AbstractAbnormality abnormality
                && !abnormality.hasEscape() && combatState.phase() == GeburahPhase.EGO) {
            egoAbnormalityKillBonus = Math.min(5, egoAbnormalityKillBonus + 1);
        }
        return hurt;
    }

    private float outgoingDamage(LivingEntity target, float rolledDamage) {
        MobEffectInstance slowness = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        int slownessAmplifier = slowness == null ? -1 : slowness.getAmplifier();
        return GeburahOutgoingDamagePolicy.apply(rolledDamage,
                combatState.phase() == GeburahPhase.EGO,
                target instanceof ServerPlayer, egoAbnormalityKillBonus,
                hasEffect(MobEffects.MOVEMENT_SPEED), slownessAmplifier,
                combatState.phase() == GeburahPhase.EXHAUSTED);
    }

    private void sendMoveShake(ServerPlayer player, GeburahMove move, float healthLoss) {
        int duration = switch (move) {
            case SLASH -> comboEgo ? 7 : 5;
            case STAB -> comboEgo ? 6 : 4;
            case VERTICAL_SLASH -> comboEgo ? 8 : 6;
        };
        float amplitude = switch (move) {
            case SLASH -> comboEgo ? 0.85F : 0.62F;
            case STAB -> comboEgo ? 0.46F : 0.32F;
            case VERTICAL_SLASH -> comboEgo ? 1.35F : 1.02F;
        };
        MessageLoader.getLoader().sendToPlayer(player,
                new GeburahCameraShakePacket(duration, amplitude, random.nextInt()));
        boolean finalHit = combo.moveIndex() + 1 >= combo.moveCount();
        float visualIntensity = switch (move) {
            case SLASH -> comboEgo ? 0.76F : 0.58F;
            case STAB -> comboEgo ? 0.90F : 0.68F;
            case VERTICAL_SLASH -> comboEgo ? 1.12F : 0.84F;
        };
        if (finalHit) {
            visualIntensity = Math.max(visualIntensity,
                    Math.min(1.15F, 0.55F + combo.moveCount() * 0.1F));
        }
        GeburahVisualEffects.sendHit(player, this, GeburahVisualEffectType.forMove(move),
                Vec3.directionFromRotation(0.0F, getYRot()), visualIntensity, healthLoss,
                finalHit ? duration + 3 : Math.max(3, duration - 1));
    }

    private void cancelCombatActions() {
        releaseMajorTarget();
        major.reset();
        combo.reset();
        special.reset();
        comboTarget = null;
        comboPostureSpent = false;
        specialTarget = null;
        specialDestination = null;
        majorTarget = null;
        majorPostureTargets.clear();
        majorSoundPlayed = false;
        majorLastWindupSoundTick = 0;
        debugComboDepth = 0;
        actionWindow = GeburahActionWindow.OTHER;
    }

    private void tickMoveCooldowns() {
        debugCooldowns.replaceAll((move, ticks) -> Math.max(0, ticks - 1));
        debugCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private void selectFacilityTarget() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        LivingEntity current = getTarget();
        if (counterattackArmed && !isValidTarget(counterattackTarget)) {
            counterattackArmed = false;
            counterattackTarget = null;
            counterPursuitTicks = 0;
        }
        if (counterattackArmed && isValidTarget(counterattackTarget)) {
            if (current != counterattackTarget) setTarget(counterattackTarget);
            return;
        }

        LivingEntity nearestAbnormality = null;
        LivingEntity nearestPlayer = null;
        double abnormalityDistance = Double.MAX_VALUE;
        double playerDistance = Double.MAX_VALUE;
        for (Entity entity : serverLevel.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == this) continue;
            double distance = distanceToSqr(living);
            if (living instanceof AbstractAbnormality abnormality && abnormality.isAbnormalityCombatTarget()) {
                if (distance < abnormalityDistance) {
                    nearestAbnormality = abnormality;
                    abnormalityDistance = distance;
                }
            } else if (living instanceof ServerPlayer player && !player.isSpectator()
                    && !player.isCreative()
                    && GeburahEncounterService.INSTANCE.isParticipant(player)
                    && distance < playerDistance) {
                nearestPlayer = player;
                playerDistance = distance;
            }
        }
        if (nearestAbnormality != null) {
            if (current == nearestAbnormality) return;
            if (isCombatActionActive()) pendingPriorityTarget = nearestAbnormality;
            else setTarget(nearestAbnormality);
            return;
        }
        if (!isValidTarget(current)) setTarget(nearestPlayer);
    }

    private boolean isCombatActionActive() {
        return major.isActive() || special.isActive() || combo.isAttacking();
    }

    private void applyPendingPriorityTarget() {
        if (!isValidTarget(pendingPriorityTarget)) {
            pendingPriorityTarget = null;
            return;
        }
        setTarget(pendingPriorityTarget);
        pendingPriorityTarget = null;
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level()) return false;
        if (target instanceof AbstractAbnormality abnormality) return abnormality.isAbnormalityCombatTarget();
        return target instanceof ServerPlayer player && !player.isSpectator() && !player.isCreative()
                && GeburahEncounterService.INSTANCE.isParticipant(player);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (phaseTransitionTicks > 0) return false;
        if (!debugDamage && !FacilityCombatRules.canDamageProtectedCombatant(source)) return false;
        if (!debugDamage && !isLegitimateSource(source)) return false;

        LivingEntity responsible = GeburahDamageResponsibility.resolve(source);
        if (!debugDamage && responsible != null && distanceTo(responsible) > 5.0F) {
            suppressEffectConversionUntilTick = level().getGameTime();
            combatState.restorePosture(33.0F);
            GeburahCounterPassive.Result counter = counterPassive.record(
                    responsible.getUUID(), level().getGameTime());
            if (counter == GeburahCounterPassive.Result.COUNTER) {
                armCounterattack(responsible);
            }
            syncCombatState();
            return false;
        }
        if (!debugDamage && responsible instanceof ServerPlayer player
                && DamageHelper.isMeleeAttack(source)
                && player.getAttackStrengthScale(0.5F) < 0.9F) {
            return false;
        }
        EntityUtil.clearHurtTime(this);
        invulnerableTime = 0;
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            combo.markRecoveryHit();
            EntityUtil.clearHurtTime(this);
            invulnerableTime = 0;
        }
        return hurt;
    }

    private void armCounterattack(LivingEntity responsible) {
        if (!isValidTarget(responsible)) return;
        counterattackTarget = responsible;
        counterattackArmed = true;
        setTarget(responsible);
        Optional<Vec3> destination = level() instanceof ServerLevel serverLevel
                ? GeburahSafeTeleport.findBehindTarget(serverLevel, this, responsible, 2.0D)
                : Optional.empty();
        if (destination.isPresent()) {
            Vec3 start = position();
            Vec3 end = destination.get();
            moveTo(end.x, end.y, end.z, getYRot(), getXRot());
            GeburahAttackEffects.spawnPursuitDash((ServerLevel) level(), start, end,
                    combatState.phase() == GeburahPhase.EGO);
            faceTarget(responsible, 180.0F);
        } else {
            counterPursuitTicks = 3 * 20;
        }
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect != null && effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            if (!level().isClientSide
                    && level().getGameTime() > suppressEffectConversionUntilTick) {
                MobEffectInstance current = getEffect(MobEffects.MOVEMENT_SPEED);
                int currentAmplifier = current == null ? -1 : current.getAmplifier();
                int nextAmplifier = GeburahStatusEffectPolicy.nextSpeedAmplifier(currentAmplifier);
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                        GeburahStatusEffectPolicy.SPEED_DURATION_TICKS,
                        nextAmplifier, false, true, true));
            }
            return false;
        }
        return super.canBeAffected(effect);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void doPush(Entity entity) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    private boolean isLegitimateSource(DamageSource source) {
        LivingEntity attacker = GeburahDamageResponsibility.resolve(source);
        if (attacker instanceof ServerPlayer player) {
            return GeburahEncounterService.INSTANCE.isParticipant(player);
        }
        return attacker instanceof AbstractAbnormality abnormality && abnormality.isAbnormalityCombatTarget();
    }

    public GeburahCombatState.DamageResult applyFinalDamage(float amount, boolean damagePosture) {
        int previousPostureBreakTicks = combatState.postureBreakTicks();
        GeburahCombatState.DamageResult result = combatState.applyHealthDamage(amount);
        if (damagePosture) {
            combatState.applyPostureDamage(result.appliedDamage(), actionWindow);
        }
        if (!result.defeated()) setHealth(combatState.health());
        if (result.changedPhase()) {
            if (result.currentPhase() == GeburahPhase.EGO) beginEgoRelease();
            else setAnimation("idle");
            applyPhaseAttributes();
        }
        syncCombatState();
        GeburahEncounterService.INSTANCE.updateBossBars(this);
        if (previousPostureBreakTicks <= 0 && combatState.postureBreakTicks() > 0) {
            GeburahVisualEffects.broadcastStage(this,
                    GeburahVisualEffectType.POSTURE_BREAK, 1.0F, 10);
        }
        if (result.defeated()) {
            GeburahVisualEffects.broadcastStage(this,
                    GeburahVisualEffectType.DEFEAT, 1.0F, 24);
            GeburahEncounterService.INSTANCE.onLegitimateDefeat(this);
        }
        return result;
    }

    public boolean applyDebugDamage(float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F || combatState.phase() == GeburahPhase.DEFEATED) {
            return false;
        }
        debugDamage = true;
        int previousInvulnerability = invulnerableTime;
        try {
            invulnerableTime = 0;
            return hurt(damageSources().generic(), amount);
        } finally {
            invulnerableTime = previousInvulnerability;
            debugDamage = false;
        }
    }

    private void beginEgoRelease() {
        bodiesOpeningPending = true;
        phaseTransitionTicks = EGO_RELEASE_ANIMATION_TICKS + EGO_RELEASE_LEAD_TICKS;
        egoReleaseManifestStarted = false;
        getNavigation().stop();
        setAnimation("idle");
        if (level() instanceof ServerLevel serverLevel) {
            GeburahEgoReleasePulse.trigger(serverLevel, this);
        }
    }

    private void startEgoReleaseManifestation() {
        egoReleaseManifestStarted = true;
        setAnimation("EGOrelease");
        level().playSound(null, blockPosition(), ModSounds.GEBURAH_EGO_MANIFEST.get(),
                SoundSource.HOSTILE, 2.4F, 1.0F);
        GeburahVisualEffects.broadcastStage(this,
                GeburahVisualEffectType.EGO_RELEASE, 1.0F, 16);
    }

    public void debugSetHealth(float value) {
        combatState.setHealth(value);
        setHealth(Math.max(1.0F, combatState.health()));
        syncCombatState();
        GeburahEncounterService.INSTANCE.updateBossBars(this);
    }

    public void debugSetPosture(float value) {
        combatState.setPosture(value);
        syncCombatState();
        GeburahEncounterService.INSTANCE.updateBossBars(this);
    }

    public void debugSetEgo(float value) {
        combatState.setEgo(value);
        syncCombatState();
        GeburahEncounterService.INSTANCE.updateBossBars(this);
    }

    public void debugSetPhase(GeburahPhase phase) {
        combatState.setPhase(phase);
        if (phase != GeburahPhase.EGO) egoAbnormalityKillBonus = 0;
        if (phase != GeburahPhase.DEFEATED) setHealth(Math.max(1.0F, combatState.health()));
        applyPhaseAttributes();
        syncCombatState();
        GeburahEncounterService.INSTANCE.updateBossBars(this);
    }

    public void debugSetEgoCooldown(int ticks) {
        combatState.setEgoExhaustionTicks(ticks);
        syncCombatState();
        GeburahEncounterService.INSTANCE.updateBossBars(this);
    }

    public void debugSetComboDepth(int depth) {
        debugComboDepth = Math.max(0, Math.min(6, depth));
    }

    public int debugComboDepth() {
        return debugComboDepth;
    }

    public void debugSetCooldown(String move, int ticks) {
        if (move == null || move.isBlank()) return;
        String key = move.toLowerCase(java.util.Locale.ROOT);
        if (ticks <= 0) debugCooldowns.remove(key);
        else debugCooldowns.put(key, ticks);
    }

    public int debugCooldown(String move) {
        return move == null ? 0 : debugCooldowns.getOrDefault(
                move.toLowerCase(java.util.Locale.ROOT), 0);
    }

    public String debugAction() {
        if (major.isActive()) return major.move().animation().toLowerCase(java.util.Locale.ROOT)
                + "/" + major.stage().name().toLowerCase(java.util.Locale.ROOT)
                + "@" + major.elapsedTicks();
        if (special.isActive()) return "willofbattle/"
                + special.stage().name().toLowerCase(java.util.Locale.ROOT);
        if (combo.isAttacking()) {
            GeburahMove move = combo.move();
            return (move == null ? "combo" : move.name().toLowerCase(java.util.Locale.ROOT))
                    + "/" + combo.stage().name().toLowerCase(java.util.Locale.ROOT);
        }
        return "idle";
    }

    public static boolean isDebugTrailAnimation(String animation) {
        return DEBUG_TRAIL_ANIMATIONS.contains(animation);
    }

    public void debugPlayTrailAnimation(String animation, float yaw, float speed) {
        if (!isDebugTrailAnimation(animation) || !Float.isFinite(yaw)
                || !Float.isFinite(speed) || speed <= 0.0F) return;
        cancelCombatActions();
        debugTrailAnimation = animation;
        debugTrailYaw = yaw;
        debugTrailRestartTicks = 2;
        combatState.setPhase(animation.startsWith("EGO")
                ? GeburahPhase.EGO : GeburahPhase.NORMAL);
        entityData.set(ANIMATION_SPEED, speed);
        setNoAi(true);
        setNoGravity(true);
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        setAnimation(animation.startsWith("EGO") ? "EGOidle" : "idle");
        applyPhaseAttributes();
        syncCombatState();
    }

    public void debugClearTrailAnimation() {
        debugTrailAnimation = null;
        debugTrailRestartTicks = 0;
        entityData.set(ANIMATION_SPEED, 1.0F);
        setNoAi(false);
        setNoGravity(false);
        setAnimation(combatState.phase() == GeburahPhase.EGO ? "EGOidle" : "idle");
    }

    private void tickDebugTrailAnimation() {
        getNavigation().stop();
        setTarget(null);
        setDeltaMovement(Vec3.ZERO);
        setYRot(debugTrailYaw);
        setYBodyRot(debugTrailYaw);
        setYHeadRot(debugTrailYaw);
        if (debugTrailRestartTicks > 0) {
            debugTrailRestartTicks--;
            setAnimation(debugTrailAnimation.startsWith("EGO") ? "EGOidle" : "idle");
        } else {
            setAnimation(debugTrailAnimation);
        }
    }

    public GeburahCombatState combatState() {
        return combatState;
    }

    public int debugEgoKillBonus() {
        return egoAbnormalityKillBonus;
    }

    public void debugSetEgoKillBonus(int value) {
        egoAbnormalityKillBonus = Math.max(0, Math.min(5, value));
    }

    public boolean isPhaseTransitioning() {
        return phaseTransitionTicks > 0;
    }

    public GeburahPhase syncedPhase() {
        return GeburahPhase.values()[Math.max(0, Math.min(
                GeburahPhase.values().length - 1, entityData.get(PHASE)))];
    }

    public float syncedPosture() {
        return entityData.get(POSTURE);
    }

    public float syncedEgo() {
        return entityData.get(EGO);
    }

    private void syncCombatState() {
        entityData.set(PHASE, combatState.phase().ordinal());
        entityData.set(POSTURE, combatState.posture());
        entityData.set(EGO, combatState.ego());
    }

    private void applyPhaseAttributes() {
        setResistance(ModAttributes.RED_DAMAGE_RESISTANCE.get(), combatState.resistance("red"));
        setResistance(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), combatState.resistance("white"));
        setResistance(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), combatState.resistance("black"));
        setResistance(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), combatState.resistance("blue"));
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(GeburahPursuitPolicy.movementSpeed(
                combatState.phase() == GeburahPhase.EGO));
    }

    private void setResistance(net.minecraft.world.entity.ai.attributes.Attribute attribute,
                               double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("GeburahPhase", combatState.phase().name());
        tag.putFloat("GeburahHealth", combatState.health());
        tag.putFloat("GeburahPosture", combatState.posture());
        tag.putFloat("GeburahEgo", combatState.ego());
        tag.putBoolean("GeburahWillKillPriority", willKillPriority);
        tag.putInt("GeburahEgoAbnormalityKillBonus", egoAbnormalityKillBonus);
        CompoundTag cooldowns = new CompoundTag();
        debugCooldowns.forEach(cooldowns::putInt);
        tag.put("GeburahMoveCooldowns", cooldowns);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GeburahPhase")) {
            try {
                combatState.setPhase(GeburahPhase.valueOf(tag.getString("GeburahPhase")));
            } catch (IllegalArgumentException ignored) {
                combatState.setPhase(GeburahPhase.NORMAL);
            }
        }
        if (tag.contains("GeburahHealth")) combatState.setHealth(tag.getFloat("GeburahHealth"));
        if (tag.contains("GeburahPosture")) combatState.setPosture(tag.getFloat("GeburahPosture"));
        if (tag.contains("GeburahEgo")) combatState.setEgo(tag.getFloat("GeburahEgo"));
        willKillPriority = tag.getBoolean("GeburahWillKillPriority");
        egoAbnormalityKillBonus = Math.max(0, Math.min(5,
                tag.getInt("GeburahEgoAbnormalityKillBonus")));
        if (combatState.phase() != GeburahPhase.EGO) egoAbnormalityKillBonus = 0;
        debugCooldowns.clear();
        if (tag.contains("GeburahMoveCooldowns")) {
            CompoundTag cooldowns = tag.getCompound("GeburahMoveCooldowns");
            for (String move : cooldowns.getAllKeys()) {
                int ticks = cooldowns.getInt(move);
                if (ticks > 0) debugCooldowns.put(move, ticks);
            }
        }
        setHealth(Math.max(1.0F, combatState.health()));
        applyPhaseAttributes();
        syncCombatState();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new software.bernie.geckolib.core.animation.AnimationController<>(
                this, "geburah_controller", 1, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityGeburah> event) {
        String animation = getAnimation();
        String resource = "animation.geburah." + animation;
        boolean fixedSpeed = animation.equals("idle") || animation.equals("move")
                || animation.equals("EGOidle") || animation.equals("EGOmove")
                || animation.equals("default") || animation.equals("EGO_default")
                || animation.equals("EGOrelease");
        event.getController().setAnimationSpeed(
                fixedSpeed ? 1.0D : Math.max(0.1D, entityData.get(ANIMATION_SPEED)));
        return switch (animation) {
            case "idle", "move", "EGOidle", "EGOmove", "default", "EGO_default" ->
                    event.setAndContinue(RawAnimation.begin().thenLoop(resource));
            case "EGOrelease" -> event.setAndContinue(RawAnimation.begin().thenPlayAndHold(resource));
            default -> event.setAndContinue(RawAnimation.begin().thenPlay(resource));
        };
    }
}
