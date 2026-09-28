package com.wzz.lobotocraft.entity.abnormality;

import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.debug.prototype.queenlaser.QueenLaserPrototypePacket;
import com.wzz.lobotocraft.debug.prototype.queenstar.QueenStarPrototypePacket;
import com.wzz.lobotocraft.entity.ai.FacilityAbnormalityTargetPolicy;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.base.EscapeTracker;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeStopEvent;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.IMessage;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EntityQueenOfHatred extends AbstractAbnormality {
    private enum State {
        CONTAINED,
        CONTAINED_GREETING,
        CONTAINED_KISS,
        CONTAINED_HYSTERIA_START,
        CONTAINED_HYSTERIC,
        CONTAINED_RESTORE,
        HERO_IDLE,
        HERO_ATTACK,
        HERO_LASER_CAST,
        HERO_LASER_FIRE,
        HERO_WEAK,
        HERO_HYSTERIA_START,
        HERO_HYSTERIC,
        HERO_DYING,
        VILLAIN_IDLE,
        VILLAIN_CHARGE_1,
        VILLAIN_CHARGE_2,
        VILLAIN_CHARGE_3,
        VILLAIN_LASER,
        VILLAIN_WEAK,
        VILLAIN_RETURN_SNAKE,
        VILLAIN_RETURN_HUMAN
    }

    private record CompletedWork(WorkResult result, int peOutput, boolean forcedEnd) {
    }

    private static final EntityDataAccessor<Boolean> DATA_SNAKE_FORM =
            SynchedEntityData.defineId(EntityQueenOfHatred.class, EntityDataSerializers.BOOLEAN);

    private static final int GREETING_TICKS = 40;
    private static final int KISS_TICKS = 50;
    private static final int HYSTERIA_START_TICKS = 25;
    private static final int RESTORE_TICKS = 50;
    private static final int HERO_ATTACK_TICKS = 25;
    private static final int HERO_ATTACK_FIRE_TICK = 16;
    private static final int HERO_LASER_CAST_TICKS = 90;
    private static final int HERO_LASER_FIRE_TICKS = 300;
    private static final int HERO_DYING_TICKS = 70;
    private static final int VILLAIN_CHARGE_1_TICKS = 10;
    private static final int VILLAIN_CHARGE_2_TICKS = 27;
    private static final int VILLAIN_CHARGE_3_TICKS = 27;
    private static final int VILLAIN_LASER_TICKS = 200;
    private static final int VILLAIN_RETURN_SNAKE_TICKS = 45;
    private static final int VILLAIN_RETURN_HUMAN_TICKS = 90;
    private static final int LASER_PULSE_INTERVAL = 10;
    private static final double HERO_STAR_RANGE = 28.0D;
    private static final double LASER_RANGE = 72.0D;
    private static final double HERO_ATTACK_RANGE = 10.0D;
    private static final double TARGET_SCAN_RANGE = 96.0D;
    private static final double STAFF_GREETING_RANGE = 4.0D;

    private State state = State.CONTAINED;
    private int actionTicks;
    private int actionElapsed;
    private int heroNormalCooldown;
    private int heroLaserCooldown;
    private int heroTeleportCooldown;
    private int villainAttackCooldown;
    private int stageStaffDeaths;
    private int heroStaffDeaths;
    private int heroStartingStaff;
    private int villainTeleportStage;
    private int lastFacilityDay = -1;
    private boolean helpedThisStage;
    private boolean playerWasNearby;
    private boolean returnWasKilled;
    private Vec3 containmentOrigin;
    private Vec3 beamStart;
    private Vec3 beamEnd;
    private Vec3 pendingStarStart;
    private Vec3 pendingStarEnd;
    private int pendingStarTicks;
    private UUID kissTarget;
    private CompletedWork kissWork;
    private final Map<UUID, CompletedWork> completedWork = new HashMap<>();
    private String lastClientAnimation = "";
    private boolean lastClientSnake;
    private boolean visualResyncPending;

    public EntityQueenOfHatred(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
        setAnimation("idle_inside");
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-01-04";
        this.abnormalityName = "憎恶女王";
        this.riskLevel = RiskLevel.WAW;
        this.damageType = "BLACK";
        this.maxPEOutput = 22;
        initializeQliphothCounter(2);
        this.workPreferences = new float[]{0.4F, 0.45F, 0.55F, 0.2F};
        this.fullWorkPreferences = new float[][]{
                {0.30F, 0.40F, 0.40F, 0.50F, 0.50F},
                {0.45F, 0.45F, 0.45F, 0.45F, 0.45F},
                {0.50F, 0.50F, 0.55F, 0.55F, 0.60F},
                {0.20F, 0.20F, 0.20F, 0.00F, 0.00F}
        };
    }

    @Override
    protected void registerGoals() {
        // Movement and target selection are owned by the explicit Queen state machine.
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SNAKE_FORM, false);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SNAKE_FORM.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isSnakeForm() ? EntityDimensions.scalable(2.2F, 3.4F)
                : EntityDimensions.scalable(0.9F, 2.7F);
    }

    @Override
    public String name() {
        return "the_queen_of_hatred_human";
    }

    public boolean isSnakeForm() {
        return this.entityData.get(DATA_SNAKE_FORM);
    }

    private void setSnakeForm(boolean snake) {
        if (this.entityData.get(DATA_SNAKE_FORM) == snake) return;
        this.entityData.set(DATA_SNAKE_FORM, snake);
        refreshDimensions();
    }

    @Override
    public int getGoodWorkResultMin() {
        return 15;
    }

    @Override
    public int getNormalWorkResultMin() {
        return 9;
    }

    @Override
    public int getBasicInfoCost() {
        return 20;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 20;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 7;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 5;
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[]{
                new ObservationLevelBonus(0.05F, 0),
                new ObservationLevelBonus(0.00F, 3),
                new ObservationLevelBonus(0.03F, 0),
                new ObservationLevelBonus(0.00F, 5)
        };
    }

    @Override
    public void attackPlayerOnFailure(net.minecraft.world.entity.player.Player player, WorkType workType) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.hurt(DamageHelper.getDamage(this, "black"), 3.0F + random.nextInt(2));
        }
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        // Exact PE output is applied from onWorkComplete after the optional kiss animation.
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        return canAcceptWork() && super.onWorkStart(player, workType);
    }

    @Override
    public boolean onOpenWorkScreen(ServerPlayer player) {
        return canAcceptWork() && super.onOpenWorkScreen(player);
    }

    private boolean canAcceptWork() {
        return state == State.CONTAINED || state == State.CONTAINED_GREETING
                || state == State.CONTAINED_HYSTERIC;
    }

    @Override
    public Float modifyWorkSuccessRate(ServerPlayer player, WorkType workType, float baseRate) {
        return getQliphothCounter() == 1
                ? QueenOfHatredPolicy.applyHystericWorkPenalty(baseRate) : baseRate;
    }

    void recordCompletedWork(ServerPlayer player, WorkResult result, int peOutput, boolean forcedEnd) {
        if (player == null) return;
        completedWork.put(player.getUUID(), new CompletedWork(result, peOutput, forcedEnd));
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        CompletedWork work = completedWork.remove(player.getUUID());
        if (work == null) work = new CompletedWork(result, result == WorkResult.GOOD ? 15 : 0, false);
        if (work.forcedEnd()) return;

        if (getQliphothCounter() == 2 && player.isAlive() && !MentalValueUtil.isPanic(player)) {
            kissTarget = player.getUUID();
            kissWork = work;
            transition(State.CONTAINED_KISS, KISS_TICKS, "kiss");
            play(ModSounds.QOH_HERO_KISS_1.get(), 1.3F, 1.0F);
            return;
        }
        applyCompletedWork(work);
    }

    private void applyCompletedWork(CompletedWork work) {
        if (getQliphothCounter() == 1) {
            if (QueenOfHatredPolicy.overcomesHysteria(work.peOutput())) {
                increaseQliphothCounter(1);
            } else {
                decreaseQliphothCounter(1);
            }
        } else if (getQliphothCounter() == 2 && work.result() == WorkResult.BAD) {
            decreaseQliphothCounter(1);
        }
    }

    @Override
    public void decreaseQliphothCounter(int amount) {
        int old = getQliphothCounter();
        super.decreaseQliphothCounter(amount);
        if (old > 1 && getQliphothCounter() == 1 && isContainedState(state)) {
            beginContainedHysteria();
        }
    }

    @Override
    public void increaseQliphothCounter(int amount) {
        int old = getQliphothCounter();
        super.increaseQliphothCounter(amount);
        if (old == 1 && getQliphothCounter() >= 2 && isContainedState(state)) {
            transition(State.CONTAINED_RESTORE, RESTORE_TICKS, "restore");
        }
    }

    @Override
    public void onQliphothMeltdown() {
        beginVillainEscape();
    }

    @Override
    public void triggerEscape() {
        beginVillainEscape();
    }

    void onStaffDeath(LivingEntity staff) {
        if (!QueenOfHatredStaff.isStaff(staff)) return;
        stageStaffDeaths++;
        if (isHeroCombatState(state)) {
            heroStaffDeaths++;
            if (QueenOfHatredPolicy.shouldBecomeHysteric(heroStaffDeaths, heroStartingStaff)) {
                beginHeroHysteria();
            }
        }
    }

    void onFacilityQliphothStage(int triggeredLevel) {
        if (!isContainedState(state)) return;
        if (QueenOfHatredPolicy.shouldReduceCounterAtStageChange(stageStaffDeaths)) {
            decreaseQliphothCounter(1);
        }
        stageStaffDeaths = 0;
        helpedThisStage = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel serverLevel = (ServerLevel) level();
        tickDayBoundary(serverLevel);
        if (visualResyncPending) {
            syncVisualsToPlayers(serverLevel);
            visualResyncPending = false;
        }
        tickPendingStar(serverLevel);
        tickCooldowns();

        actionElapsed++;
        if (actionTicks > 0) actionTicks--;

        switch (state) {
            case CONTAINED -> tickContained(serverLevel);
            case CONTAINED_GREETING -> {
                stopActionMovement();
                if (actionTicks <= 0) transition(State.CONTAINED, 0, "idle_inside");
            }
            case CONTAINED_KISS -> tickKiss(serverLevel);
            case CONTAINED_HYSTERIA_START -> {
                stopActionMovement();
                if (actionTicks <= 0) transition(State.CONTAINED_HYSTERIC, 0, "hysteria2");
            }
            case CONTAINED_HYSTERIC -> {
                stopActionMovement();
                playLoopAtInterval(ModSounds.QOH_HERO_PANIC_LOOP.get(), 129, 1.4F);
            }
            case CONTAINED_RESTORE -> {
                stopActionMovement();
                if (actionTicks <= 0) transition(State.CONTAINED, 0, "idle_inside");
            }
            case HERO_IDLE -> tickHeroIdle(serverLevel);
            case HERO_ATTACK -> tickHeroAttack(serverLevel);
            case HERO_LASER_CAST -> tickHeroLaserCast();
            case HERO_LASER_FIRE -> tickHeroLaserFire(serverLevel);
            case HERO_WEAK -> {
                stopActionMovement();
                if (actionTicks <= 0) transition(State.HERO_IDLE, 0, "idle_outside");
            }
            case HERO_HYSTERIA_START -> {
                stopActionMovement();
                if (actionTicks <= 0) transition(State.HERO_HYSTERIC,
                        randomBetween(25 * 20, 30 * 20), "hysteria2");
            }
            case HERO_HYSTERIC -> {
                stopActionMovement();
                playLoopAtInterval(ModSounds.QOH_HERO_PANIC_LOOP.get(), 129, 1.4F);
                if (actionTicks <= 0) beginVillainEscape();
            }
            case HERO_DYING -> tickHeroDying(serverLevel);
            case VILLAIN_IDLE -> tickVillainIdle(serverLevel);
            case VILLAIN_CHARGE_1 -> {
                stopActionMovement();
                if (actionTicks <= 0) {
                    transition(State.VILLAIN_CHARGE_2, VILLAIN_CHARGE_2_TICKS, "charge2");
                    play(ModSounds.QOH_VILLAIN_CAST_1.get(), 1.5F, 1.0F);
                }
            }
            case VILLAIN_CHARGE_2 -> {
                stopActionMovement();
                if (actionTicks <= 0) {
                    transition(State.VILLAIN_CHARGE_3, VILLAIN_CHARGE_3_TICKS, "charge3");
                    play(ModSounds.QOH_VILLAIN_CAST_2.get(), 1.5F, 1.0F);
                }
            }
            case VILLAIN_CHARGE_3 -> {
                stopActionMovement();
                if (actionTicks <= 0) {
                    transition(State.VILLAIN_LASER, VILLAIN_LASER_TICKS, "charge4");
                }
            }
            case VILLAIN_LASER -> tickVillainLaser(serverLevel);
            case VILLAIN_WEAK -> {
                stopActionMovement();
                if (actionTicks <= 0) {
                    villainAttackCooldown = randomBetween(5 * 20, 7 * 20);
                    transition(State.VILLAIN_IDLE, 0, "idle");
                }
            }
            case VILLAIN_RETURN_SNAKE -> tickVillainReturnSnake();
            case VILLAIN_RETURN_HUMAN -> tickVillainReturnHuman(serverLevel);
        }
    }

    private void tickDayBoundary(ServerLevel level) {
        int currentDay = FacilityDayData.get(level).getDay();
        if (lastFacilityDay < 0) {
            lastFacilityDay = currentDay;
            return;
        }
        if (currentDay == lastFacilityDay) return;
        lastFacilityDay = currentDay;
        stageStaffDeaths = 0;
        heroStaffDeaths = 0;
        helpedThisStage = false;
        if (isHeroState(state)) {
            finishHeroReturn(level, false);
            return;
        }
        if (isVillainState(state) || state == State.VILLAIN_RETURN_SNAKE
                || state == State.VILLAIN_RETURN_HUMAN) {
            finishVillainReturn(level, false);
            return;
        }
        clearPendingWork();
        clearVisuals();
        setSnakeForm(false);
        setEscape(false);
        setQliphothCounter(getMaxQliphothCounter());
        setHealth(getMaxHealth());
        transition(State.CONTAINED, 0, "idle_inside");
    }

    private void tickCooldowns() {
        if (isHeroCombatState(state)) {
            if (heroNormalCooldown > 0) heroNormalCooldown--;
            if (heroLaserCooldown > 0) heroLaserCooldown--;
        }
        if (state == State.VILLAIN_IDLE && villainAttackCooldown > 0) villainAttackCooldown--;
    }

    private void tickContained(ServerLevel level) {
        stopActionMovement();
        boolean nearby = !level.getEntitiesOfClass(ServerPlayer.class,
                getBoundingBox().inflate(STAFF_GREETING_RANGE),
                player -> player.isAlive() && !player.isSpectator()).isEmpty();
        if (nearby && !playerWasNearby && !WorkManager.isAbnormalityBeingWorked(this)) {
            transition(State.CONTAINED_GREETING, GREETING_TICKS, "idle_inside2");
            playerWasNearby = true;
            return;
        }
        playerWasNearby = nearby;
        if (tickCount % 20 == 0) tryBeginHeroSortie(level);
    }

    private void tickKiss(ServerLevel level) {
        stopActionMovement();
        if (actionElapsed == 34) play(ModSounds.QOH_HERO_KISS_2.get(), 1.3F, 1.0F);
        if (actionTicks > 0) return;
        ServerPlayer target = QueenOfHatredStaff.findPlayer(level.getServer(), kissTarget);
        if (target != null && target.level() == level && target.isAlive()) {
            QueenOfHatredStaff.healFully(target);
        }
        CompletedWork work = kissWork;
        kissTarget = null;
        kissWork = null;
        transition(State.CONTAINED, 0, "idle_inside");
        if (work != null) applyCompletedWork(work);
    }

    private void tryBeginHeroSortie(ServerLevel level) {
        if (helpedThisStage || getQliphothCounter() != 2
                || WorkManager.isAbnormalityBeingWorked(this)
                || EscapeTracker.getInstance().getCurrentAlertLevel(level.getServer()) < 1) return;
        LivingEntity target = nearestTarget(level, entity -> QueenOfHatredCombat.isHeroHostile(entity, this));
        if (target == null) return;

        captureContainmentOrigin();
        helpedThisStage = true;
        heroStartingStaff = QueenOfHatredStaff.liveStaffCount(level);
        heroStaffDeaths = 0;
        heroNormalCooldown = randomBetween(12 * 20, 16 * 20);
        heroLaserCooldown = randomBetween(30 * 20, 35 * 20);
        heroTeleportCooldown = randomBetween(10 * 20, 15 * 20);
        setHealth(getMaxHealth());
        setNoAi(false);
        transition(State.HERO_IDLE, 0, "idle_outside");
        teleportNear(level, target, false);
    }

    private void tickHeroIdle(ServerLevel level) {
        LivingEntity target = nearestTarget(level, entity -> QueenOfHatredCombat.isHeroHostile(entity, this));
        if (target == null) {
            finishHeroReturn(level, false);
            return;
        }

        if (heroTeleportCooldown > 0) heroTeleportCooldown--;
        if (heroTeleportCooldown <= 0) {
            teleportNear(level, target, false);
            heroTeleportCooldown = randomBetween(10 * 20, 15 * 20);
        }

        face(target);
        double distance = distanceTo(target);
        if (heroLaserCooldown <= 0) {
            beginHeroLaser(level, target);
            return;
        }
        if (heroNormalCooldown <= 0 && distance <= HERO_ATTACK_RANGE) {
            transition(State.HERO_ATTACK, HERO_ATTACK_TICKS, "attack");
            stopActionMovement();
            return;
        }
        moveToward(target, 1.05D, "move", "idle_outside");
    }

    private void tickHeroAttack(ServerLevel level) {
        stopActionMovement();
        if (actionElapsed == HERO_ATTACK_FIRE_TICK) fireHeroStar(level);
        if (actionTicks <= 0) {
            heroNormalCooldown = randomBetween(8 * 20, 12 * 20);
            transition(State.HERO_IDLE, 0, "idle_outside");
        }
    }

    private void fireHeroStar(ServerLevel level) {
        LivingEntity target = nearestTarget(level, entity -> QueenOfHatredCombat.isHeroHostile(entity, this));
        if (target == null) return;
        Vec3 start = beamOrigin();
        Vec3 desiredEnd = target.getBoundingBox().getCenter();
        Vec3 end = QueenOfHatredCombat.clipToBlocks(level, this, start, desiredEnd);
        int travelTicks = Mth.clamp((int) Math.ceil(start.distanceTo(end) / 0.55D), 8, 80);
        pendingStarStart = start;
        pendingStarEnd = end;
        pendingStarTicks = travelTicks;
        sendVisual(level, QueenStarPrototypePacket.start(start, end, travelTicks));
        play(ModSounds.QOH_HERO_ATTACK.get(), 1.5F, 1.0F);
    }

    private void tickPendingStar(ServerLevel level) {
        if (pendingStarTicks <= 0 || pendingStarStart == null || pendingStarEnd == null) return;
        pendingStarTicks--;
        if (pendingStarTicks > 0) return;
        List<LivingEntity> targets = QueenOfHatredCombat.lineTargets(level,
                pendingStarStart, pendingStarEnd, 0.85D,
                entity -> QueenOfHatredStaff.isStaff(entity)
                        || QueenOfHatredCombat.isHeroHostile(entity, this));
        for (LivingEntity target : targets) {
            if (QueenOfHatredStaff.isStaff(target)) {
                QueenOfHatredStaff.heal(target, randomBetween(13, 15), randomBetween(13, 15));
                continue;
            }
            QueenOfHatredCombat.damageBlack(this, target, 13, 16);
            break;
        }
        pendingStarStart = null;
        pendingStarEnd = null;
    }

    private void beginHeroLaser(ServerLevel level, LivingEntity target) {
        lockBeam(level, target);
        transition(State.HERO_LASER_CAST, HERO_LASER_CAST_TICKS, "attack2");
        stopActionMovement();
        sendVisual(level, QueenLaserPrototypePacket.start(beamStart, beamEnd,
                QueenLaserPrototypePacket.MODE_MAX, HERO_LASER_CAST_TICKS,
                HERO_LASER_CAST_TICKS + HERO_LASER_FIRE_TICKS));
        play(ModSounds.QOH_HERO_SKILL_START.get(), 1.6F, 1.0F);
    }

    private void tickHeroLaserCast() {
        stopActionMovement();
        if (actionElapsed == 30) play(ModSounds.QOH_HERO_SKILL_PORTAL_1.get(), 1.5F, 1.0F);
        if (actionElapsed == 60) play(ModSounds.QOH_HERO_SKILL_PORTAL_2.get(), 1.5F, 1.0F);
        if (actionTicks <= 0) {
            transition(State.HERO_LASER_FIRE, HERO_LASER_FIRE_TICKS, "magic_power1");
            play(ModSounds.QOH_HERO_SKILL_CAST_END.get(), 1.7F, 1.0F);
        }
    }

    private void tickHeroLaserFire(ServerLevel level) {
        stopActionMovement();
        if (actionElapsed == 1 || actionElapsed % LASER_PULSE_INTERVAL == 0) {
            boolean secondPhase = actionElapsed >= HERO_LASER_FIRE_TICKS / 2;
            for (LivingEntity target : QueenOfHatredCombat.lineTargets(level, beamStart, beamEnd, 2.1D,
                    entity -> QueenOfHatredStaff.isStaff(entity)
                            || QueenOfHatredCombat.isHeroHostile(entity, this))) {
                if (QueenOfHatredStaff.isStaff(target)) {
                    QueenOfHatredStaff.heal(target, randomBetween(45, 50) / 10.0F,
                            randomBetween(70, 80) / 10.0F);
                } else {
                    QueenOfHatredCombat.damageBlack(this, target,
                            secondPhase ? 15 : 12, secondPhase ? 18 : 15);
                }
                EntityUtil.clearHurtTime(target);
            }
        }
        playLoopAtInterval(ModSounds.QOH_HERO_SKILL_LOOP.get(), 158, 1.6F);
        if (actionTicks <= 0) {
            heroLaserCooldown = randomBetween(20 * 20, 25 * 20);
            transition(State.HERO_WEAK, randomBetween(5 * 20, 8 * 20), "weak");
        }
    }

    private void beginHeroHysteria() {
        if (!isHeroCombatState(state)) return;
        clearVisuals();
        transition(State.HERO_HYSTERIA_START, HYSTERIA_START_TICKS, "hysteria");
        play(ModSounds.QOH_HERO_PANIC_DOWN.get(), 1.7F, 1.0F);
    }

    private void beginContainedHysteria() {
        clearPendingWork();
        transition(State.CONTAINED_HYSTERIA_START, HYSTERIA_START_TICKS, "hysteria");
        play(ModSounds.QOH_HERO_PANIC_DOWN.get(), 1.4F, 1.0F);
    }

    private void beginVillainEscape() {
        if (isVillainState(state) || state == State.VILLAIN_RETURN_SNAKE
                || state == State.VILLAIN_RETURN_HUMAN) return;
        boolean transformingFromHero = isHeroState(state);
        Vec3 transformationPosition = position();
        captureContainmentOrigin();
        clearPendingWork();
        clearVisuals();
        setSnakeForm(true);
        setNoAi(false);
        state = State.VILLAIN_IDLE;
        actionTicks = 0;
        actionElapsed = 0;
        setAnimation("idle");
        play(ModSounds.QOH_HERO_TRANSFORM_1.get(), 1.7F, 1.0F);
        if (!hasEscape() && !beginEscape(null)) {
            setEscape(true);
            EscapeTracker.getInstance().onEscapeStart(this);
        }
        if (transformingFromHero) {
            moveTo(transformationPosition.x, transformationPosition.y, transformationPosition.z,
                    getYRot(), getXRot());
            setDeltaMovement(Vec3.ZERO);
            getNavigation().stop();
        }
        villainAttackCooldown = randomBetween(10 * 20, 14 * 20);
        villainTeleportStage = 0;
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1.2D, getZ(),
                    42, 1.0D, 1.3D, 1.0D, 0.08D);
        }
    }

    private void tickVillainIdle(ServerLevel level) {
        LivingEntity target = nearestTarget(level, entity -> QueenOfHatredCombat.isVillainTarget(entity, this));
        if (target == null) {
            setTarget(null);
            if (!getNavigation().isInProgress() && random.nextInt(80) == 0) {
                Vec3 destination = DefaultRandomPos.getPos(this, 12, 6);
                if (destination != null) getNavigation().moveTo(destination.x, destination.y, destination.z, 0.95D);
            }
            setAnimation(getNavigation().isInProgress() ? "move" : "idle");
            return;
        }
        face(target);
        if (villainAttackCooldown <= 0 && distanceTo(target) <= LASER_RANGE) {
            beginVillainLaser(level, target);
            return;
        }
        moveToward(target, 1.0D, "move", "idle");
    }

    private void beginVillainLaser(ServerLevel level, LivingEntity target) {
        lockBeam(level, target);
        transition(State.VILLAIN_CHARGE_1, VILLAIN_CHARGE_1_TICKS, "charge");
        stopActionMovement();
        int castTicks = VILLAIN_CHARGE_1_TICKS + VILLAIN_CHARGE_2_TICKS + VILLAIN_CHARGE_3_TICKS;
        sendVisual(level, QueenLaserPrototypePacket.start(beamStart, beamEnd,
                QueenLaserPrototypePacket.MODE_MAX, castTicks, castTicks + VILLAIN_LASER_TICKS));
        play(ModSounds.QOH_VILLAIN_CAST_START.get(), 1.7F, 1.0F);
    }

    private void tickVillainLaser(ServerLevel level) {
        stopActionMovement();
        if (actionElapsed == 1 || actionElapsed % LASER_PULSE_INTERVAL == 0) {
            int phase = actionElapsed < 65 ? 1 : actionElapsed < 135 ? 2 : 3;
            int minimum = phase == 1 ? 4 : phase == 2 ? 6 : 8;
            int maximum = phase == 1 ? 6 : phase == 2 ? 8 : 10;
            for (LivingEntity target : QueenOfHatredCombat.lineTargets(level, beamStart, beamEnd, 2.0D,
                    entity -> QueenOfHatredCombat.isVillainTarget(entity, this))) {
                if (QueenOfHatredCombat.damageBlack(this, target, minimum, maximum)) {
                    heal(randomBetween(5, 8));
                }
                if (target instanceof AbstractAbnormality || FacilityCombatRules.isOrdealCombatant(target)) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                }
                EntityUtil.clearHurtTime(target);
            }
        }
        playLoopAtInterval(ModSounds.QOH_VILLAIN_CAST_LOOP.get(), 158, 1.6F);
        if (actionTicks <= 0) {
            transition(State.VILLAIN_WEAK, randomBetween(5 * 20, 7 * 20), "charge5");
            play(ModSounds.QOH_VILLAIN_DOWN.get(), 1.5F, 1.0F);
        }
    }

    private void tickHeroDying(ServerLevel level) {
        stopActionMovement();
        if (actionElapsed == 1) play(ModSounds.QOH_HERO_DELAY_1.get(), 1.5F, 1.0F);
        if (actionElapsed == HERO_DYING_TICKS / 2) play(ModSounds.QOH_HERO_DELAY_2.get(), 1.5F, 1.0F);
        if (actionTicks <= 0) {
            QueenOfHatredStaff.healAllFully(level);
            finishHeroReturn(level, true);
        }
    }

    private void tickVillainReturnSnake() {
        stopActionMovement();
        if (actionElapsed == 1) play(ModSounds.QOH_VILLAIN_DEAD_1.get(), 1.7F, 1.0F);
        if (actionElapsed == VILLAIN_RETURN_SNAKE_TICKS / 2) {
            play(ModSounds.QOH_VILLAIN_DEAD_2.get(), 1.7F, 1.0F);
        }
        if (actionTicks <= 0) {
            setSnakeForm(false);
            transition(State.VILLAIN_RETURN_HUMAN, VILLAIN_RETURN_HUMAN_TICKS, "human");
            play(ModSounds.QOH_VILLAIN_DEAD_BOOM.get(), 1.8F, 1.0F);
        }
    }

    private void tickVillainReturnHuman(ServerLevel level) {
        stopActionMovement();
        if (actionTicks <= 0) {
            play(ModSounds.QOH_VILLAIN_DEAD_END.get(), 1.7F, 1.0F);
            finishVillainReturn(level, returnWasKilled);
        }
    }

    private void lockBeam(ServerLevel level, LivingEntity target) {
        beamStart = beamOrigin();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 direction = targetCenter.subtract(beamStart);
        if (direction.lengthSqr() < 1.0E-7D) direction = getLookAngle();
        Vec3 desiredEnd = beamStart.add(direction.normalize().scale(LASER_RANGE));
        beamEnd = QueenOfHatredCombat.clipToBlocks(level, this, beamStart, desiredEnd);
    }

    private Vec3 beamOrigin() {
        return position().add(0.0D, getBbHeight() * (isSnakeForm() ? 0.72D : 0.68D), 0.0D);
    }

    private void captureContainmentOrigin() {
        if (containmentOrigin == null || isContainedState(state)) containmentOrigin = position();
    }

    private void finishHeroReturn(ServerLevel level, boolean suppressed) {
        clearVisuals();
        teleportToContainment(level);
        setSnakeForm(false);
        setEscape(false);
        setQliphothCounter(getMaxQliphothCounter());
        setHealth(getMaxHealth());
        heroStaffDeaths = 0;
        heroStartingStaff = 0;
        pendingStarTicks = 0;
        pendingStarStart = null;
        pendingStarEnd = null;
        transition(State.CONTAINED, 0, "idle_inside");
    }

    private void finishVillainReturn(ServerLevel level, boolean killed) {
        boolean escaped = hasEscape();
        clearVisuals();
        teleportToContainment(level);
        setSnakeForm(false);
        setEscape(false);
        setQliphothCounter(getMaxQliphothCounter());
        setHealth(getMaxHealth());
        villainTeleportStage = 0;
        returnWasKilled = false;
        transition(State.CONTAINED, 0, "idle_inside");
        if (escaped) {
            EscapeTracker.getInstance().onEscapeStop(this);
            MinecraftForge.EVENT_BUS.post(new AbnormalityEscapeStopEvent(this, killed));
        }
    }

    private void teleportToContainment(ServerLevel level) {
        Vec3 target = containmentOrigin;
        if (target == null && getHomeRoomMarker() != null) {
            target = Vec3.atBottomCenterOf(getHomeRoomMarker());
        }
        if (target == null) return;
        BlockPos safe = EntityUtil.findSafeEntityPositionInCompany(level, this,
                BlockPos.containing(target), 4, 3);
        Vec3 destination = safe == null ? target : Vec3.atBottomCenterOf(safe);
        moveTo(destination.x, destination.y, destination.z, getYRot(), getXRot());
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.0D, getZ(),
                24, 0.6D, 1.0D, 0.6D, 0.04D);
        play(ModSounds.QOH_TELEPORT.get(), 1.4F, 1.0F);
    }

    private void teleportNear(ServerLevel level, LivingEntity target, boolean damageArrival) {
        if (target == null) return;
        BlockPos safe = EntityUtil.findSafeEntityPositionInCompany(level, this,
                target.blockPosition(), 5, 3);
        if (safe == null) safe = findGroundTeleportFallback(level, target.blockPosition());
        if (safe == null) return;
        Vec3 destination = Vec3.atBottomCenterOf(safe);
        level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0D, getZ(),
                32, 0.7D, 1.0D, 0.7D, 0.2D);
        moveTo(destination.x, destination.y, destination.z, getYRot(), getXRot());
        setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0D, getZ(),
                32, 0.7D, 1.0D, 0.7D, 0.2D);
        play(ModSounds.QOH_TELEPORT.get(), 1.5F, 1.0F);
        if (!damageArrival) return;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(4.0D), entity -> QueenOfHatredCombat.isVillainTarget(entity, this))) {
            QueenOfHatredCombat.damageBlack(this, living, 20, 26);
            EntityUtil.clearHurtTime(living);
        }
    }

    /** Finds a company-local floor below a flying target when its current Y is unreachable. */
    private BlockPos findGroundTeleportFallback(ServerLevel level, BlockPos target) {
        int minY = level.getMinBuildHeight();
        int maxY = Math.min(target.getY() - 1, level.getMaxBuildHeight() - 2);
        for (int y = maxY; y >= minY; y--) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos candidate = new BlockPos(target.getX() + dx, y, target.getZ() + dz);
                    if (!level.getBlockState(candidate.below()).isFaceSturdy(
                            level, candidate.below(), net.minecraft.core.Direction.UP)) continue;
                    if (!level.getBlockState(candidate).isAir()) continue;
                    BlockPos safe = EntityUtil.findSafeEntityPositionInCompany(level, this, candidate, 0, 0);
                    if (safe != null) return safe;
                }
            }
        }
        return null;
    }

    private LivingEntity nearestTarget(ServerLevel level,
                                       java.util.function.Predicate<LivingEntity> predicate) {
        LivingEntity nearest = null;
        double nearestDistance = TARGET_SCAN_RANGE * TARGET_SCAN_RANGE;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !predicate.test(living)) continue;
            double distance = distanceToSqr(living);
            if (distance < nearestDistance) {
                nearest = living;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void moveToward(LivingEntity target, double speed, String movingAnimation,
                            String idleAnimation) {
        setTarget(target);
        if (distanceToSqr(target) > 4.0D) {
            getNavigation().moveTo(target, speed);
            setAnimation(movingAnimation);
        } else {
            stopActionMovement();
            setAnimation(idleAnimation);
        }
    }

    private void face(LivingEntity target) {
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        Vec3 difference = target.position().subtract(position());
        if (difference.horizontalDistanceSqr() > 1.0E-6D) {
            float yaw = (float) (Mth.atan2(difference.z, difference.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            setYHeadRot(yaw);
            yBodyRot = yaw;
        }
    }

    private void transition(State next, int ticks, String animation) {
        state = next;
        actionTicks = Math.max(0, ticks);
        actionElapsed = 0;
        setAnimation(animation);
    }

    private void stopActionMovement() {
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        setTarget(null);
    }

    private int randomBetween(int minimum, int maximum) {
        int lower = Math.min(minimum, maximum);
        int upper = Math.max(minimum, maximum);
        return lower + random.nextInt(upper - lower + 1);
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        if (!(level() instanceof ServerLevel level) || sound == null) return;
        level.playSound(null, blockPosition(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private void playLoopAtInterval(SoundEvent sound, int intervalTicks, float volume) {
        if (actionElapsed == 1 || actionElapsed % Math.max(1, intervalTicks) == 0) {
            play(sound, volume, 1.0F);
        }
    }

    private void sendVisual(ServerLevel level, IMessage message) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level) MessageLoader.getLoader().sendToPlayer(player, message);
        }
    }

    /** Replays the current laser state for a player who joined while it was active. */
    void syncVisualToPlayer(ServerPlayer player) {
        if (player == null || !(level() instanceof ServerLevel level) || player.level() != level) return;
        if (!isLaserState(state) || beamStart == null || beamEnd == null) return;
        LaserVisualState visual = currentLaserVisualState();
        if (visual == null) return;
        MessageLoader.getLoader().sendToPlayer(player, QueenLaserPrototypePacket.resume(
                beamStart, beamEnd, QueenLaserPrototypePacket.MODE_MAX,
                visual.castTicks(), visual.durationTicks(), visual.ageTicks()));
    }

    private LaserVisualState currentLaserVisualState() {
        int villainCastTicks = VILLAIN_CHARGE_1_TICKS
                + VILLAIN_CHARGE_2_TICKS + VILLAIN_CHARGE_3_TICKS;
        return switch (state) {
            case HERO_LASER_CAST -> new LaserVisualState(HERO_LASER_CAST_TICKS,
                    HERO_LASER_CAST_TICKS + HERO_LASER_FIRE_TICKS, actionElapsed);
            case HERO_LASER_FIRE -> new LaserVisualState(HERO_LASER_CAST_TICKS,
                    HERO_LASER_CAST_TICKS + HERO_LASER_FIRE_TICKS,
                    HERO_LASER_CAST_TICKS + actionElapsed);
            case VILLAIN_CHARGE_1 -> new LaserVisualState(villainCastTicks,
                    villainCastTicks + VILLAIN_LASER_TICKS, actionElapsed);
            case VILLAIN_CHARGE_2 -> new LaserVisualState(villainCastTicks,
                    villainCastTicks + VILLAIN_LASER_TICKS,
                    VILLAIN_CHARGE_1_TICKS + actionElapsed);
            case VILLAIN_CHARGE_3 -> new LaserVisualState(villainCastTicks,
                    villainCastTicks + VILLAIN_LASER_TICKS,
                    VILLAIN_CHARGE_1_TICKS + VILLAIN_CHARGE_2_TICKS + actionElapsed);
            case VILLAIN_LASER -> new LaserVisualState(villainCastTicks,
                    villainCastTicks + VILLAIN_LASER_TICKS,
                    villainCastTicks + actionElapsed);
            default -> null;
        };
    }

    private record LaserVisualState(int castTicks, int durationTicks, int ageTicks) {
    }

    private void syncVisualsToPlayers(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            syncVisualToPlayer(player);
        }
    }

    private void clearVisuals() {
        if (!(level() instanceof ServerLevel level)) return;
        sendVisual(level, QueenLaserPrototypePacket.clear());
        sendVisual(level, QueenStarPrototypePacket.clear());
    }

    private void clearPendingWork() {
        kissTarget = null;
        kissWork = null;
        completedWork.clear();
    }

    private static boolean isContainedState(State value) {
        return switch (value) {
            case CONTAINED, CONTAINED_GREETING, CONTAINED_KISS,
                    CONTAINED_HYSTERIA_START, CONTAINED_HYSTERIC, CONTAINED_RESTORE -> true;
            default -> false;
        };
    }

    private static boolean isHeroState(State value) {
        return switch (value) {
            case HERO_IDLE, HERO_ATTACK, HERO_LASER_CAST, HERO_LASER_FIRE, HERO_WEAK,
                    HERO_HYSTERIA_START, HERO_HYSTERIC, HERO_DYING -> true;
            default -> false;
        };
    }

    private static boolean isHeroCombatState(State value) {
        return switch (value) {
            case HERO_IDLE, HERO_ATTACK, HERO_LASER_CAST, HERO_LASER_FIRE, HERO_WEAK -> true;
            default -> false;
        };
    }

    private static boolean isVillainState(State value) {
        return switch (value) {
            case VILLAIN_IDLE, VILLAIN_CHARGE_1, VILLAIN_CHARGE_2,
                    VILLAIN_CHARGE_3, VILLAIN_LASER, VILLAIN_WEAK -> true;
            default -> false;
        };
    }

    private static boolean usesSnakeModel(State value) {
        return isVillainState(value) || value == State.VILLAIN_RETURN_SNAKE;
    }

    @Override
    public boolean isAbnormalityCombatTarget() {
        return hasEscape() || isHeroState(state) && state != State.HERO_DYING;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (state == State.HERO_DYING || state == State.VILLAIN_RETURN_SNAKE
                || state == State.VILLAIN_RETURN_HUMAN) return false;
        if (isHeroState(state)) {
            Entity attacker = source.getEntity();
            boolean hostileAttacker = attacker instanceof AbstractAbnormality abnormality && abnormality.hasEscape()
                    || FacilityCombatRules.isOrdealCombatant(attacker)
                    || (attacker instanceof LivingEntity living
                    && FacilityAbnormalityTargetPolicy.isCoreSuppressionCombatant(living));
            if (!hostileAttacker && !DamageHelper.isKill(source)) return false;
            return baseHurt(source, amount);
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && isVillainState(state)) {
            checkVillainTeleportThreshold();
        }
        return hurt;
    }

    private void checkVillainTeleportThreshold() {
        if (!(level() instanceof ServerLevel level) || !isVillainState(state) || getMaxHealth() <= 0.0F) return;
        float ratio = getHealth() / getMaxHealth();
        float threshold = 0.7F - villainTeleportStage * 0.3F;
        if (villainTeleportStage >= 3 || ratio >= threshold) return;
        List<LivingEntity> staff = QueenOfHatredStaff.liveStaff(level);
        if (!staff.isEmpty()) {
            teleportNear(level, staff.get(random.nextInt(staff.size())), true);
        }
        villainTeleportStage++;
    }

    @Override
    public void die(DamageSource source) {
        if (isHeroState(state) && state != State.HERO_DYING) {
            setHealth(1.0F);
            clearVisuals();
            transition(State.HERO_DYING, HERO_DYING_TICKS, "heal");
            return;
        }
        if (isVillainState(state)) {
            setHealth(1.0F);
            returnWasKilled = true;
            clearVisuals();
            transition(State.VILLAIN_RETURN_SNAKE, VILLAIN_RETURN_SNAKE_TICKS, "human");
            return;
        }
        super.die(source);
    }

    @Override
    public void stopEscape() {
        if (!(level() instanceof ServerLevel level) || !hasEscape()) return;
        finishVillainReturn(level, false);
    }

    @Override
    protected boolean usesContainmentReturnOnDeath() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new software.bernie.geckolib.core.animation.AnimationController<>(
                this, "queen_controller", 2, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityQueenOfHatred> event) {
        String animation = getAnimation();
        boolean snake = isSnakeForm();
        if (!animation.equals(lastClientAnimation) || snake != lastClientSnake) {
            event.getController().forceAnimationReset();
            lastClientAnimation = animation;
            lastClientSnake = snake;
        }
        String resourceAnimation = (snake
                ? "animation.the_queen_of_hatred_snake."
                : "animation.the_queen_of_hatred_human.") + animation;
        return switch (animation) {
            case "idle_inside", "idle_outside", "move", "hysteria2", "weak",
                    "idle", "charge4" ->
                    event.setAndContinue(RawAnimation.begin().thenLoop(resourceAnimation));
            case "magic_power1", "hysteria", "heal", "human", "restore",
                    "charge", "charge3", "charge5" ->
                    event.setAndContinue(RawAnimation.begin().thenPlayAndHold(resourceAnimation));
            default -> event.setAndContinue(RawAnimation.begin().thenPlay(resourceAnimation));
        };
    }

    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityQueenOfHatred((EntityType<? extends TamableAnimal>) getType(), serverLevel);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && !isRemoved()) clearVisuals();
        super.remove(reason);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, TARGET_SCAN_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.7D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.3D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.5D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("QueenState", state.name());
        tag.putInt("QueenActionTicks", actionTicks);
        tag.putInt("QueenActionElapsed", actionElapsed);
        tag.putInt("QueenHeroNormalCooldown", heroNormalCooldown);
        tag.putInt("QueenHeroLaserCooldown", heroLaserCooldown);
        tag.putInt("QueenHeroTeleportCooldown", heroTeleportCooldown);
        tag.putInt("QueenVillainCooldown", villainAttackCooldown);
        tag.putInt("QueenStageDeaths", stageStaffDeaths);
        tag.putInt("QueenHeroDeaths", heroStaffDeaths);
        tag.putInt("QueenHeroStartingStaff", heroStartingStaff);
        tag.putInt("QueenVillainTeleportStage", villainTeleportStage);
        tag.putInt("QueenLastFacilityDay", lastFacilityDay);
        tag.putBoolean("QueenHelpedThisStage", helpedThisStage);
        tag.putBoolean("QueenPlayerWasNearby", playerWasNearby);
        tag.putBoolean("QueenReturnWasKilled", returnWasKilled);
        if (containmentOrigin != null) {
            tag.putBoolean("QueenHasContainmentOrigin", true);
            tag.putDouble("QueenContainmentX", containmentOrigin.x);
            tag.putDouble("QueenContainmentY", containmentOrigin.y);
            tag.putDouble("QueenContainmentZ", containmentOrigin.z);
        }
        if (beamStart != null && beamEnd != null) {
            tag.putBoolean("QueenHasBeam", true);
            tag.putDouble("QueenBeamStartX", beamStart.x);
            tag.putDouble("QueenBeamStartY", beamStart.y);
            tag.putDouble("QueenBeamStartZ", beamStart.z);
            tag.putDouble("QueenBeamEndX", beamEnd.x);
            tag.putDouble("QueenBeamEndY", beamEnd.y);
            tag.putDouble("QueenBeamEndZ", beamEnd.z);
        }
        if (kissTarget != null) tag.putUUID("QueenKissTarget", kissTarget);
        if (kissWork != null) {
            tag.putString("QueenKissResult", kissWork.result().name());
            tag.putInt("QueenKissPe", kissWork.peOutput());
            tag.putBoolean("QueenKissForced", kissWork.forcedEnd());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            state = State.valueOf(tag.getString("QueenState"));
        } catch (IllegalArgumentException ignored) {
            state = hasEscape() ? State.VILLAIN_IDLE : State.CONTAINED;
        }
        actionTicks = Math.max(0, tag.getInt("QueenActionTicks"));
        actionElapsed = Math.max(0, tag.getInt("QueenActionElapsed"));
        heroNormalCooldown = Math.max(0, tag.getInt("QueenHeroNormalCooldown"));
        heroLaserCooldown = Math.max(0, tag.getInt("QueenHeroLaserCooldown"));
        heroTeleportCooldown = Math.max(0, tag.getInt("QueenHeroTeleportCooldown"));
        villainAttackCooldown = Math.max(0, tag.getInt("QueenVillainCooldown"));
        stageStaffDeaths = Math.max(0, tag.getInt("QueenStageDeaths"));
        heroStaffDeaths = Math.max(0, tag.getInt("QueenHeroDeaths"));
        heroStartingStaff = Math.max(0, tag.getInt("QueenHeroStartingStaff"));
        villainTeleportStage = Mth.clamp(tag.getInt("QueenVillainTeleportStage"), 0, 3);
        lastFacilityDay = tag.contains("QueenLastFacilityDay") ? tag.getInt("QueenLastFacilityDay") : -1;
        helpedThisStage = tag.getBoolean("QueenHelpedThisStage");
        playerWasNearby = tag.getBoolean("QueenPlayerWasNearby");
        returnWasKilled = tag.getBoolean("QueenReturnWasKilled");
        if (tag.getBoolean("QueenHasContainmentOrigin")) {
            containmentOrigin = new Vec3(tag.getDouble("QueenContainmentX"),
                    tag.getDouble("QueenContainmentY"), tag.getDouble("QueenContainmentZ"));
        }
        if (tag.getBoolean("QueenHasBeam")) {
            beamStart = new Vec3(tag.getDouble("QueenBeamStartX"), tag.getDouble("QueenBeamStartY"),
                    tag.getDouble("QueenBeamStartZ"));
            beamEnd = new Vec3(tag.getDouble("QueenBeamEndX"), tag.getDouble("QueenBeamEndY"),
                    tag.getDouble("QueenBeamEndZ"));
        } else {
            beamStart = null;
            beamEnd = null;
        }
        kissTarget = tag.hasUUID("QueenKissTarget") ? tag.getUUID("QueenKissTarget") : null;
        if (tag.contains("QueenKissResult")) {
            try {
                kissWork = new CompletedWork(WorkResult.valueOf(tag.getString("QueenKissResult")),
                        tag.getInt("QueenKissPe"), tag.getBoolean("QueenKissForced"));
            } catch (IllegalArgumentException ignored) {
                kissWork = null;
            }
        }

        visualResyncPending = isLaserState(state);
        if (hasEscape() && !isVillainState(state)
                && state != State.VILLAIN_RETURN_SNAKE && state != State.VILLAIN_RETURN_HUMAN) {
            state = State.VILLAIN_IDLE;
        }
        setSnakeForm(usesSnakeModel(state));
        setAnimation(animationForState(state));
    }

    private static String animationForState(State value) {
        return switch (value) {
            case CONTAINED -> "idle_inside";
            case CONTAINED_GREETING -> "idle_inside2";
            case CONTAINED_KISS -> "kiss";
            case CONTAINED_HYSTERIA_START, HERO_HYSTERIA_START -> "hysteria";
            case CONTAINED_HYSTERIC, HERO_HYSTERIC -> "hysteria2";
            case CONTAINED_RESTORE -> "restore";
            case HERO_IDLE -> "idle_outside";
            case HERO_ATTACK -> "attack";
            case HERO_LASER_CAST -> "attack2";
            case HERO_LASER_FIRE -> "magic_power1";
            case HERO_WEAK -> "weak";
            case HERO_DYING -> "heal";
            case VILLAIN_IDLE -> "idle";
            case VILLAIN_CHARGE_1 -> "charge";
            case VILLAIN_CHARGE_2 -> "charge2";
            case VILLAIN_CHARGE_3 -> "charge3";
            case VILLAIN_LASER -> "charge4";
            case VILLAIN_WEAK -> "charge5";
            case VILLAIN_RETURN_SNAKE, VILLAIN_RETURN_HUMAN -> "human";
        };
    }

    private static boolean isLaserState(State value) {
        return value == State.HERO_LASER_CAST || value == State.HERO_LASER_FIRE
                || value == State.VILLAIN_CHARGE_1 || value == State.VILLAIN_CHARGE_2
                || value == State.VILLAIN_CHARGE_3 || value == State.VILLAIN_LASER;
    }

    String stateNameForTest() {
        return state.name();
    }

    int laserVisualAgeTicksForTest() {
        LaserVisualState visual = currentLaserVisualState();
        return visual == null ? -1 : visual.ageTicks();
    }

    void setHeroStaffSnapshotForTest(int startingStaff, int deaths) {
        state = State.HERO_IDLE;
        heroStartingStaff = Math.max(0, startingStaff);
        heroStaffDeaths = Math.max(0, deaths);
        setAnimation("idle_outside");
    }

    void setHeroDamageStateForTest(boolean laserCasting) {
        setEscape(false);
        setHealth(getMaxHealth());
        setNoAi(false);
        transition(laserCasting ? State.HERO_LASER_CAST : State.HERO_IDLE,
                laserCasting ? HERO_LASER_CAST_TICKS : 0,
                laserCasting ? "attack2" : "idle_outside");
    }
}
