package com.wzz.lobotocraft.entity.base;

import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.api.NameProvider;
import com.wzz.lobotocraft.block.EscapeBlock;
import com.wzz.lobotocraft.block.entity.EscapeBlockEntity;
import com.wzz.lobotocraft.block.entity.RoomMarkerBlockEntity;
import com.wzz.lobotocraft.capability.CompanyDailyDataProvider;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.entity.EntityRedShoesClerk;
import com.wzz.lobotocraft.entity.ai.goal.MoveToBlackForestDoorGoal;
import com.wzz.lobotocraft.entity.abnormality.EntityIsharmla;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.effect.KissEffect;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeEvent;
import com.wzz.lobotocraft.event.definition.abnormality.AbnormalityEscapeStopEvent;
import com.wzz.lobotocraft.event.definition.abnormality.QliphothCounterEvent;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.CompanyDailySyncPacket;
import com.wzz.lobotocraft.init.ModMobEffects;
import com.wzz.lobotocraft.util.DamageHelper;
import com.wzz.lobotocraft.util.EntityUtil;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import com.wzz.lobotocraft.util.MethodHandleUtils;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.data.FloorEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ITeleporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 异想体抽象基类
 */
public abstract class AbstractAbnormality extends BaseGeoEntity implements IAbnormality, NameProvider {
    private enum ContainmentReturnResult {
        COMPLETED,
        TRANSFERRED,
        DEFERRED
    }

    private record EscapeDestination(BlockPos marker, BlockPos position) {
    }

    private static final int[][] ESCAPE_BLOCK_SEARCH_RANGES = {
            {12, 8},
            {24, 12},
            {48, 20},
            {96, 32},
            {192, 64},
            {Integer.MAX_VALUE, Integer.MAX_VALUE}
    };
    private static final int CONTAINMENT_RETURN_RETRY_INTERVAL = 20;

    private static EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME;
    public static final EntityDataAccessor<Boolean> DATA_ESCAPED =
            SynchedEntityData.defineId(AbstractAbnormality.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<String> DATA_JADE_NAME =
            SynchedEntityData.defineId(AbstractAbnormality.class, EntityDataSerializers.STRING);

    // 基础数据
    public String abnormalityCode;    // 编号
    public String abnormalityName;    // 名称
    public RiskLevel riskLevel;       // 风险等级
    public String damageType;         // 伤害类型
    public int maxPEOutput;           // 最大PE产量
    public BlockPos escapePosition = null;
    private ResourceLocation escapeDimension;
    private ResourceLocation homeRoomDimension;
    private BlockPos homeRoomMarker;
    private boolean pendingContainmentReturn;
    private boolean pendingStopEscapeEvent;
    private boolean containmentReturnWasSuppression;
    private int lastFacilityDaySeen = -1;
    private int lastHurtTimestamp = -10000; // 记录上次受伤的时刻（tick）

    public float[] workPreferences = new float[4];      // 简化偏好
    public float[][] fullWorkPreferences = new float[4][5]; // 完整偏好矩阵

    // 逆卡巴拉计数器
    public int qliphothCounter;
    public int maxQliphothCounter;

    // 常态音效计时器
    private int ambientSoundTimer = 0;
    private int escapeAmbientSoundTimer = 0;

    public AbstractAbnormality(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            lastFacilityDaySeen = FacilityDayData.get(serverLevel).getDay();
        }
        initializeAbnormality();
        this.setPersistenceRequired();
        this.setCustomNameVisible(false);
        this.setCustomName(Component.literal(this.abnormalityCode));
    }

    protected void broadcastMessage(String message) {
        EntityUtil.broadcastMessage(level(), message);
    }

    protected void broadcastMessage(Component message) {
        EntityUtil.broadcastMessage(level(), message);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public @NotNull SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                 MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                                 @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
        if (hasAbnormalityAmbientSound()) {
            int interval = getAbnormalityAmbientSoundInterval();
            int maxFirstDelay = Math.min(interval, getFirstAmbientSoundMaxDelay());
            ambientSoundTimer = interval - level.getRandom().nextInt(maxFirstDelay);
        }
        return data;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        //this.goalSelector.addGoal(0, new LookAtTargetGoal(this, 16, false, target -> this.hasEscape() && target != null));
        if (canEscape() && !isContinuousUseTool() && !isToolType()) {
            this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        }
    }

    public String getAbnormalityJadeName() {
        return this.entityData.get(DATA_JADE_NAME);
    }

    public Component getLocalizedAbnormalityName() {
        return Component.translatable(this.getType().getDescriptionId());
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target instanceof EntityIsharmla
                || (this instanceof EntityIsharmla && target instanceof AbstractAbnormality)) {
            super.setTarget(null);
            return;
        }
        super.setTarget(target);
    }

    /**
     * 初始化异想体数据 - 子类必须实现
     */
    protected abstract void initializeAbnormality();

    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return null;
    }

    /** Special transition entities can opt out of the normal containment-bound suppression return. */
    protected boolean usesContainmentReturnOnDeath() {
        return true;
    }

    /** Lets special forms shrink and clear transient combat state before placement is evaluated. */
    protected void prepareForContainmentReturn() {
    }

    protected int getFirstAmbientSoundMaxDelay() {
        return 60;
    }

    protected void respawnAtEscapePosition(ServerLevel serverLevel) {
        if (escapePosition == null) {
            return;
        }
        if (returnSameEntityToContainment(serverLevel) == ContainmentReturnResult.DEFERRED) {
            pendingContainmentReturn = true;
            pendingStopEscapeEvent = true;
            setHealth(ContainmentReturnPolicy.healthAfterDeferredSuppression(getMaxHealth()));
            setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Records the authoritative containment-room marker for suppression and day-change recovery. */
    public void bindHomeRoom(ResourceLocation dimension, BlockPos markerPos) {
        if (dimension == null || markerPos == null) return;
        homeRoomDimension = dimension;
        homeRoomMarker = markerPos.immutable();
    }

    public ResourceLocation getHomeRoomDimension() {
        return homeRoomDimension;
    }

    public BlockPos getHomeRoomMarker() {
        return homeRoomMarker;
    }

    protected final boolean isContainmentReturnPending() {
        return pendingContainmentReturn;
    }

    /**
     * 判断当前异想体是否为黑森林三鸟
     */
    public boolean isBirdOfBlackForest() {
        String code = getAbnormalityCode();
        return "O-02-40".equals(code)   // 大鸟
                || "O-02-62".equals(code)   // 审判鸟
                || "O-02-56".equals(code);  // 惩戒鸟
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.isPassenger()) {
            this.stopRiding();
        }
        if (!this.level().isClientSide) {
            if (this.level() instanceof ServerLevel serverLevel) {
                int facilityDay = FacilityDayData.get(serverLevel).getDay();
                if (lastFacilityDaySeen != facilityDay) {
                    lastFacilityDaySeen = facilityDay;
                    // FacilityManager performs the first day-change recovery
                    // from a stable entity snapshot. If that return had to be
                    // deferred, wait for the normal retry loop instead of
                    // invoking stopEscape a second time in the same transition.
                    if (hasEscape() && !pendingContainmentReturn) stopEscape();
                }
            }
            if (ContainmentReturnPolicy.shouldRetry(pendingContainmentReturn, this.tickCount,
                    CONTAINMENT_RETURN_RETRY_INTERVAL)) {
                // Dynamic dispatch is required so subtype cleanup runs after a deferred return succeeds.
                stopEscape();
            }
            if (this.tickCount == 1 || this.tickCount % 100 == 0) {
                syncNearbyPlayers();
            }
            if (hasAbnormalityAmbientSound()) {
                ambientSoundTimer++;
                if (ambientSoundTimer >= getAbnormalityAmbientSoundInterval()) {
                    playAbnormalityAmbientSound();
                    ambientSoundTimer = 0;
                }
            }
            if (hasEscape() && hasEscapeAmbientSound()) {
                escapeAmbientSoundTimer++;
                if (escapeAmbientSoundTimer >= getEscapeAmbientSoundInterval()) {
                    playEscapeAmbientSound();
                    escapeAmbientSoundTimer = 0;
                }
            } else {
                escapeAmbientSoundTimer = 0; // 未出逃时重置，下次出逃从头开始
            }
            if (this.tickCount % 20 == 0) {
                checkPlayerDistanceAndStopSound();
            }
        }
    }

    @Override
    public void discard() {
        if (!this.level.isClientSide) {
            this.checkPlayerDistanceAndStopSound();
        }
        super.discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && level() instanceof ServerLevel serverLevel) {
            // Chunk unloads and dimension transfers are non-destructive removal reasons and must
            // keep the alert. Actual death/discard unregisters only this exact entity UUID.
            EscapeTracker.getInstance().onEscapeStop(serverLevel.getServer(), getUUID());
        }
        super.remove(reason);
    }

    /**
     * 检查附近玩家距离，超出范围则通知客户端停止音效
     */
    private void checkPlayerDistanceAndStopSound() {
        if (getAbnormalityAmbientSound() == null || getAbnormalityAmbientSoundSource() == null) return;
        double soundRange = getAbnormalityAmbientSoundRange();
        java.util.List<net.minecraft.server.level.ServerPlayer> players =
                ((net.minecraft.server.level.ServerLevel)this.level())
                        .getServer().getPlayerList().getPlayers();

        for (net.minecraft.server.level.ServerPlayer player : players) {
            double distance = player.distanceTo(this);
            // 如果玩家超出音效范围，发送停止音效的包
            if (distance > soundRange) {
                com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(
                        player,
                        new com.wzz.lobotocraft.network.packet.StopAmbientSoundPacket(
                                getAbnormalityAmbientSound().getLocation(),
                                getAbnormalityAmbientSoundSource()
                        )
                );
            }
        }
    }

    public void startMovingToDoor() {
        this.setTarget(null);
        this.getNavigation().stop();

        this.goalSelector.getRunningGoals()
                .filter(goal -> goal.getGoal() instanceof MoveToBlackForestDoorGoal)
                .forEach(WrappedGoal::stop);

        // 添加新的向门移动目标，优先级最高
        this.goalSelector.addGoal(0, new MoveToBlackForestDoorGoal(this, 1.2));

        // 如果当前是静止状态，强制设置移动
        this.setNoAi(false);
    }

    private int targetReevaluationCooldown = 0;

    /**
     * AI 步骤中，在其他异想体的定制逻辑之前调用
     * 确保自定义的 targets 先寻找异想体目标
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.tickCount - lastHurtTimestamp < 100 && getTarget() != null && getTarget().isAlive()) {
            return;
        }
        if (!hasEscape() || level().isClientSide) return;

        // 三鸟不参与异想体间战斗
        if (isBirdOfBlackForest()) return;

        // 每秒重新评估目标
        if (targetReevaluationCooldown > 0) {
            targetReevaluationCooldown--;
            return;
        }
        targetReevaluationCooldown = 20;

        // 如果已经有目标且目标是异想体，继续保持
        if (getTarget() instanceof AbstractAbnormality
                && getTarget().isAlive()) {
            return;
        }

        // 查找最近的出逃异想体
        AbstractAbnormality nearestAbnormality = this.level().getEntitiesOfClass(
                        AbstractAbnormality.class,
                        this.getBoundingBox().inflate(32.0),
                        ab -> ab != this
                                && ab.hasEscape()
                                && !ab.isBirdOfBlackForest()  // 不攻击三鸟
                                && ab.isAlive()
                ).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);

        // 优先攻击异想体
        if (nearestAbnormality != null) {
            setTarget(nearestAbnormality);
            return;
        }

        EntityRedShoesClerk nearestRedShoesClerk = this.level().getEntitiesOfClass(
                        EntityRedShoesClerk.class,
                        this.getBoundingBox().inflate(32.0),
                        EntityRedShoesClerk::isAlive
                ).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);

        if (nearestRedShoesClerk != null) {
            setTarget(nearestRedShoesClerk);
            return;
        }

        // 如果没有异想体目标，寻找玩家
        Player nearestPlayer = this.level().getNearestPlayer(this, 32.0);
        if (nearestPlayer != null
                && !nearestPlayer.isCreative()
                && !nearestPlayer.isSpectator()) {
            setTarget(nearestPlayer);
        }
    }

    /**
     * 播放出逃环境音效（3D位置音效）
     */
    protected void playEscapeAmbientSound() {
        SoundEvent sound = getEscapeAmbientSound();
        if (sound == null) return;

        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                sound,
                getEscapeAmbientSoundSource(),
                getEscapeAmbientSoundVolume(),
                getEscapeAmbientSoundPitch()
        );
    }

    protected void playEscapeSound() {
        if (!(this.level() instanceof ServerLevel) || getEscapeSound() == null) return;
        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                getEscapeSound(),
                SoundSource.HOSTILE,
                1.0f,
                1.0f
        );
    }

    protected void playEscapeWarningSound() {
        if (!(this.level() instanceof ServerLevel serverLevel) || getEscapeWarningSound() == null) return;
        List<ServerPlayer> players = serverLevel.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            player.playNotifySound(
                    getEscapeWarningSound(),
                    SoundSource.MASTER,
                    1.0f,
                    1.0f
            );
        }
    }

    protected void playGlobalSound(SoundEvent soundEvent) {
        if (!(this.level() instanceof ServerLevel serverLevel) || soundEvent == null) return;
        serverLevel.players().forEach(player ->
                player.playNotifySound(soundEvent, SoundSource.MASTER, 1.0f, 1.0f)
        );
    }

    protected void playAttackSound() {
        if (!(this.level() instanceof ServerLevel) || getAttackSound() == null) return;
        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                getAttackSound(),
                SoundSource.HOSTILE,
                1.0f,
                1.0f
        );
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        // 玩家开始追踪时立即同步
        syncNameToPlayer(player);
    }

    private void syncNearbyPlayers() {
        if (this.level().isClientSide) return;

        AABB searchBox = this.getBoundingBox().inflate(64.0D);
        List<ServerPlayer> nearbyPlayers = this.level().getEntitiesOfClass(
                ServerPlayer.class,
                searchBox
        );

        for (ServerPlayer player : nearbyPlayers) {
            syncNameToPlayer(player);
        }
    }

    public void triggerEscape() {
        beginEscape(null);
    }

    /** Starts the common escape lifecycle, preferring a subtype-specific spawn anchor when supplied. */
    protected final boolean beginEscape(@Nullable BlockPos preferredDestination) {
        if (hasEscape() || this.level().isClientSide || !canEscape()) return false;
        BlockPos teleportTarget = preferredDestination == null
                ? null : findSafeEscapePosition(preferredDestination);
        if (teleportTarget == null) teleportTarget = findAvailableEscapePosition();
        escapeDimension = this.level().dimension().location();
        escapePosition = this.blockPosition();
        setEscape(true);
        if (teleportTarget != null) {
            teleportTo(teleportTarget.getX() + 0.5, teleportTarget.getY(),
                    teleportTarget.getZ() + 0.5);
        }
        stopAmbientSoundForAllPlayers();
        playEscapeWarningSound();
        playEscapeSound();
        broadcastEscapeMessage();
        decreaseAllPlayersTodayWorkCount();
        MinecraftForge.EVENT_BUS.post(new AbnormalityEscapeEvent(this));
        EscapeTracker.getInstance().onEscapeStart(this);
        return true;
    }

    protected void decreaseAllPlayersTodayWorkCount() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            player.getCapability(CompanyDailyDataProvider.COMPANY_DAILY_DATA).ifPresent(data -> {
                int oldCount = data.getTodayWorkCount();
                if (oldCount <= 0) return;
                data.setTodayWorkCount(oldCount - 1);
                MessageLoader.getLoader().sendToPlayer(player,
                        new CompanyDailySyncPacket(
                                data.getCurrentDay(),
                                data.getTodayWorkCount(),
                                data.isArmorLocked(),
                                data.isHasSleep()
                        ));
            });
        }
    }

    protected void broadcastEscapeMessage() {
        EntityUtil.broadcastMessage(level(), Component.translatable(
                "message.lobotocraft.abnormality.escaped", getLocalizedAbnormalityName()));
    }

    private BlockPos findAvailableEscapePosition() {
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        ResourceLocation currentDimension = serverLevel.dimension().location();
        BlockPos center = homeRoomMarker != null && currentDimension.equals(homeRoomDimension)
                ? homeRoomMarker : getOnPos();
        List<EscapeDestination> roomExits = findHomeRoomEscapeDestinations(serverLevel, center);
        if (!roomExits.isEmpty()) return roomExits.get(0).position();
        List<FloorEntry> floors = CompanyRoomData.get(serverLevel)
                .getFloors(currentDimension);
        int homeFloor = CompanyRoomData.resolveFloorIndex(floors, center);
        BlockPos nearestCrossFloorFallback = null;
        for (int[] range : ESCAPE_BLOCK_SEARCH_RANGES) {
            List<EscapeDestination> available = new ArrayList<>();
            for (EscapeBlockEntity escapeBlock : EntityUtil.findEscapeBlocks(
                    level(), center, range[0], range[1])) {
                BlockPos marker = escapeBlock.getBlockPos();
                if (!FacilityAccessService.isPositionOpen(
                        serverLevel.getServer(), currentDimension, marker)) continue;
                BlockPos safe = findSafeEscapePosition(marker);
                if (safe != null) available.add(new EscapeDestination(marker, safe));
            }
            if (available.isEmpty()) continue;
            if (homeFloor < 0) {
                return available.get(0).position();
            }
            List<EscapeDestination> sameFloor = available.stream()
                    .filter(destination -> CompanyRoomData.resolveFloorIndex(
                            floors, destination.marker()) == homeFloor)
                    .toList();
            if (!sameFloor.isEmpty()) {
                return sameFloor.get(0).position();
            }
            if (nearestCrossFloorFallback == null) {
                nearestCrossFloorFallback = available.get(0).position();
            }
        }
        return nearestCrossFloorFallback;
    }

    /** Prefer the nearest usable exit to the authoritative containment-room marker. */
    private List<EscapeDestination> findHomeRoomEscapeDestinations(ServerLevel serverLevel,
                                                                     BlockPos center) {
        if (homeRoomMarker == null || !serverLevel.dimension().location().equals(homeRoomDimension)) {
            return List.of();
        }
        if (!serverLevel.hasChunkAt(homeRoomMarker)) serverLevel.getChunkAt(homeRoomMarker);
        if (!(serverLevel.getBlockEntity(homeRoomMarker) instanceof RoomMarkerBlockEntity)) {
            return List.of();
        }

        // The generated facility has a stable marker -> doorway mapping. Resolve that
        // coordinate directly so a doorway in another chunk cannot disappear merely
        // because its block entity has not ticked yet.
        List<EscapeDestination> fixed = new ArrayList<>();
        FacilityWorldLayout.containmentAt(serverLevel.dimension().location(), homeRoomMarker)
                .map(FacilityWorldLayout.ContainmentBinding::escapePos)
                .map(position -> resolveEscapeDestination(serverLevel, position))
                .ifPresent(destination -> fixed.add(destination));
        if (!fixed.isEmpty()) return fixed;

        // If the fixed doorway is blocked or was edited, use the remaining fixed
        // doorways in geometric order before falling back to dynamic registrations.
        List<BlockPos> fixedAlternatives = FacilityWorldLayout.containmentBindings(
                        serverLevel.dimension().location()).stream()
                .map(FacilityWorldLayout.ContainmentBinding::escapePos)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparingDouble(position -> position.distSqr(center)))
                .toList();
        for (BlockPos position : fixedAlternatives) {
            EscapeDestination destination = resolveEscapeDestination(serverLevel, position);
            if (destination != null) return List.of(destination);
        }

        List<EscapeDestination> result = new ArrayList<>();
        for (EscapeBlockEntity escapeBlock : EntityUtil.findEscapeBlocks(
                serverLevel, homeRoomMarker, 32, 12)) {
            BlockPos marker = escapeBlock.getBlockPos();
            // The official facility places containment exits 21-23 blocks from
            // room markers. RoomMarkerBlockEntity#getScanBox only describes the
            // interior scan volume and must not be used as a doorway boundary.
            if (!FacilityAccessService.isPositionOpen(serverLevel.getServer(),
                    serverLevel.dimension().location(), marker)) continue;
            BlockPos safe = findSafeEscapePosition(marker);
            if (safe != null) result.add(new EscapeDestination(marker, safe));
        }
        result.sort(Comparator.comparingDouble(destination ->
                destination.marker().distSqr(center)));
        return result;
    }

    @Nullable
    private EscapeDestination resolveEscapeDestination(ServerLevel serverLevel, BlockPos marker) {
        if (marker == null || !isValidContainmentAnchor(serverLevel, marker)) return null;
        if (!FacilityAccessService.isPositionOpen(serverLevel.getServer(),
                serverLevel.dimension().location(), marker)) return null;
        if (!serverLevel.hasChunkAt(marker)) serverLevel.getChunkAt(marker);
        if (!(serverLevel.getBlockEntity(marker) instanceof EscapeBlockEntity)
                || !(serverLevel.getBlockState(marker).getBlock() instanceof EscapeBlock)) return null;
        BlockPos safe = findSafeEscapePosition(marker);
        return safe == null ? null : new EscapeDestination(marker, safe);
    }

    private BlockPos findSafeEscapePosition(BlockPos spawnAnchor) {
        if (!(level() instanceof ServerLevel serverLevel) || spawnAnchor == null) return null;
        BlockPos safe = EntityUtil.findSafeEntityPositionInCompany(
                serverLevel, this, spawnAnchor.above(), 4, 2);
        if (safe == null || !FacilityAccessService.isPositionOpen(serverLevel.getServer(),
                serverLevel.dimension().location(), safe)) return null;
        return safe;
    }

    public BlockPos getEscapePosition() {
        return escapePosition;
    }

    /** 设置计数器 */
    public void setQliphothCounter(int value) {
        this.qliphothCounter = value;
    }

    public void stopEscape() {
        if (!hasEscape()) return;
        ContainmentReturnResult result = level() instanceof ServerLevel serverLevel
                ? returnSameEntityToContainment(serverLevel)
                : ContainmentReturnResult.DEFERRED;
        if (result == ContainmentReturnResult.DEFERRED) {
            pendingContainmentReturn = true;
            pendingStopEscapeEvent = true;
            setTarget(null);
            getNavigation().stop();
            setDeltaMovement(0.0D, 0.0D, 0.0D);
            return;
        }
        if (result == ContainmentReturnResult.TRANSFERRED) return;
        pendingStopEscapeEvent = false;
        MinecraftForge.EVENT_BUS.post(new AbnormalityEscapeStopEvent(
                this, containmentReturnWasSuppression));
        containmentReturnWasSuppression = false;
    }

    /**
     * 停止所有玩家的环境音效
     */
    public void stopAmbientSoundForAllPlayers() {
        if (!(this.level() instanceof ServerLevel serverLevel) || getAbnormalityAmbientSound() == null) return;
        List<ServerPlayer> players = serverLevel.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(
                    player,
                    new com.wzz.lobotocraft.network.packet.StopAmbientSoundPacket(
                            getAbnormalityAmbientSound().getLocation(),
                            SoundSource.HOSTILE
                    )
            );
        }
    }

    /**
     * 同步名称给指定玩家（根据解锁状态）
     */
    private void syncNameToPlayer(ServerPlayer player) {
        player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA)
                .ifPresent(data -> {
                    int observationLevel = data.getObservationLevel(this.abnormalityCode);
                    Component displayName = observationLevel > 0
                            ? getLocalizedAbnormalityName()
                            : Component.literal(this.abnormalityCode);
                    sendPerPlayerName(player, displayName);
                });
    }

    @SuppressWarnings("unchecked")
    private static void ensureAccessors() {
        if (DATA_CUSTOM_NAME != null) return;
        try {
            Field f1 = MethodHandleUtils.getField(Entity.class, "DATA_CUSTOM_NAME", "f_19833_");
            f1.setAccessible(true);
            DATA_CUSTOM_NAME = (EntityDataAccessor<Optional<Component>>) f1.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access Entity name data accessors", e);
        }
    }

    public void sendPerPlayerName(ServerPlayer player, Component name) {
        ensureAccessors();

        int idName = DATA_CUSTOM_NAME.getId();
        EntityDataSerializer<Optional<Component>> serName = DATA_CUSTOM_NAME.getSerializer();
        List<SynchedEntityData.DataValue<?>> values = List.of(
                new SynchedEntityData.DataValue<>(idName, serName, Optional.of(name))
        );
        this.entityData.set(DATA_JADE_NAME, name.getString());
        player.connection.send(new ClientboundSetEntityDataPacket(this.getId(), values));
    }

    /**
     * 播放常态音效（3D位置音效）
     */
    protected void playAbnormalityAmbientSound() {
        SoundEvent sound = getAbnormalityAmbientSound();
        if (sound == null) return;

        float volume = getAbnormalityAmbientSoundVolume();
        float pitch = getAbnormalityAmbientSoundPitch();
        SoundSource soundSource = getAbnormalityAmbientSoundSource();

        // 使用位置音效，根据玩家距离自动调整音量
        this.level().playSound(
                null,  // null表示播放给所有玩家
                this.getX(),
                this.getY(),
                this.getZ(),
                sound,
                soundSource,  // 使用配置的音效类型（默认RECORDS）
                volume,
                pitch
        );
    }

    @Override
    public void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ESCAPED, false);
        this.entityData.define(DATA_JADE_NAME, "???");
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return this.hasEscape() || super.isCurrentlyGlowing();
    }

    @Override
    public int getTeamColor() {
        return this.hasEscape() ? 0xFFFF00 : super.getTeamColor();
    }

    @Override
    public int getEntityId() {
        return this.getId();
    }

    @Override
    public String getAbnormalityCode() {
        return abnormalityCode;
    }

    @Override
    public String getAbnormalityName() {
        return abnormalityName;
    }

    @Override
    public boolean hasEscape() {
        return this.entityData.get(DATA_ESCAPED);
    }

    public void setEscape(boolean value) {
        this.entityData.set(DATA_ESCAPED, value);
    }

    @Override
    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    @Override
    public String getDamageType() {
        return damageType;
    }

    @Override
    public int getMaxPEOutput() {
        return maxPEOutput;
    }

    @Override
    public float[] getWorkPreferences() {
        return workPreferences;
    }

    @Override
    public float[][] getFullWorkPreferences() {
        return fullWorkPreferences;
    }

    @Override
    public boolean hurt(DamageSource damageSource, float f) {
        if (!FacilityCombatRules.canDamageProtectedCombatant(damageSource)) {
            if (FacilityCombatRules.isImmuneEnvironment(damageSource)) clearFire();
            return false;
        }
        if (!hasEscape() && !DamageHelper.isKill(damageSource))
            return false;
        if (!this.canEscape()) {
            if (!DamageHelper.isKill(damageSource))
                return false;
        }
        boolean hurt = super.hurt(damageSource, f);
        if (hurt) {
            if (FacilityCombatRules.ignoresHurtCooldown(damageSource)) EntityUtil.clearHurtTime(this);
            if (damageSource.getEntity() instanceof LivingEntity attacker && attacker != this) {
                lastHurtTimestamp = this.tickCount;
            }
        }
        return hurt;
    }

    protected boolean baseHurt(DamageSource damageSource, float f) {
        return super.hurt(damageSource, f);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (level instanceof ServerLevel && canEscape() && usesContainmentReturnOnDeath() && hasEscape()) {
            containmentReturnWasSuppression = true;
            // Use dynamic dispatch so each abnormality clears its own temporary escape state.
            stopEscape();
            if (!isRemoved() && pendingContainmentReturn) {
                setHealth(ContainmentReturnPolicy.healthAfterDeferredSuppression(getMaxHealth()));
                setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            return;
        }
        super.die(damageSource);
    }

    private ContainmentReturnResult returnSameEntityToContainment(ServerLevel currentLevel) {
        prepareForContainmentReturn();
        clearContainmentCombatState();
        ResourceLocation targetDimension = ContainmentReturnPolicy.selectTargetDimension(
                homeRoomDimension, escapeDimension, currentLevel.dimension().location());
        ServerLevel targetLevel = currentLevel.getServer().getLevel(ResourceKey.create(
                Registries.DIMENSION, targetDimension));
        if (targetLevel == null) return ContainmentReturnResult.DEFERRED;

        if (homeRoomMarker != null && isValidContainmentAnchor(targetLevel, homeRoomMarker)
                && !targetLevel.hasChunkAt(homeRoomMarker)) {
            // The bound marker is authoritative; load its chunk for this bounded return transaction.
            targetLevel.getChunkAt(homeRoomMarker);
        }
        RoomMarkerBlockEntity marker = homeRoomMarker != null
                && isValidContainmentAnchor(targetLevel, homeRoomMarker)
                && targetLevel.getBlockEntity(homeRoomMarker) instanceof RoomMarkerBlockEntity room ? room : null;
        BlockPos primary = escapePosition != null
                && isValidContainmentAnchor(targetLevel, escapePosition) ? escapePosition : homeRoomMarker;
        if (primary == null) return ContainmentReturnResult.DEFERRED;
        if (!isValidContainmentAnchor(targetLevel, primary)) return ContainmentReturnResult.DEFERRED;
        if (!targetLevel.hasChunkAt(primary)) targetLevel.getChunkAt(primary);
        BlockPos safe = findSafeContainmentPosition(targetLevel, marker, primary);
        if (safe == null && homeRoomMarker != null && !homeRoomMarker.equals(primary)) {
            safe = findSafeContainmentPosition(targetLevel, marker, homeRoomMarker);
        }
        if (safe == null) return ContainmentReturnResult.DEFERRED;

        if (targetLevel != currentLevel) {
            BlockPos destination = safe.immutable();
            boolean suppression = containmentReturnWasSuppression;
            Entity moved = changeDimension(targetLevel, new ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel source, ServerLevel destinationLevel,
                                          float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                    Entity replacement = repositionEntity.apply(false);
                    if (replacement != null) {
                        replacement.moveTo(destination.getX() + 0.5D, destination.getY(),
                                destination.getZ() + 0.5D, entity.getYRot(), entity.getXRot());
                    }
                    return replacement;
                }
            });
            if (!(moved instanceof AbstractAbnormality returned)) {
                return ContainmentReturnResult.DEFERRED;
            }
            if (returned.level() != targetLevel) return ContainmentReturnResult.DEFERRED;
            returned.containmentReturnWasSuppression = suppression;
            returned.pendingContainmentReturn = false;
            returned.pendingStopEscapeEvent = false;
            returned.stopEscape();
            if (!returned.hasEscape() && !returned.pendingContainmentReturn) {
                restoreContainmentBinding(targetLevel, marker, returned);
            }
            // Forge normally preserves UUID across dimensions. If another teleporter replaces it,
            // the known source UUID is still the only additional identity that belongs here.
            EscapeTracker.getInstance().onEscapeStop(currentLevel.getServer(), getUUID());
            // Keep the removed source instance escaped so subclass cleanup guards skip it.
            // The migrated replacement performs cleanup and emits the stop event exactly once.
            return ContainmentReturnResult.TRANSFERRED;
        }

        moveTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, getYRot(), getXRot());
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        setTarget(null);
        getNavigation().stop();
        hurtMarked = true;
        pendingContainmentReturn = false;
        escapePosition = null;
        escapeDimension = null;
        setEscape(false);
        qliphothCounter = maxQliphothCounter;
        setTarget(null);
        setHealth(getMaxHealth());
        restoreContainmentBinding(targetLevel, marker, this);
        EscapeTracker.getInstance().onEscapeStop(this);
        return ContainmentReturnResult.COMPLETED;
    }

    private void clearContainmentCombatState() {
        removeEffect(ModMobEffects.KISS.get());
        KissEffect.clearFreezeState(this);
        getPersistentData().remove("SnowQueenWeaponIce");
    }

    private static void restoreContainmentBinding(ServerLevel targetLevel,
                                                  @Nullable RoomMarkerBlockEntity marker,
                                                  AbstractAbnormality returned) {
        if (marker == null || returned == null || returned.isRemoved()
                || returned.level() != targetLevel) return;
        UUID returnedId = returned.getUUID();
        String boundCode = marker.getBoundCode();
        if (returnedId.equals(marker.getBoundAbnormality())
                && (boundCode == null || boundCode.isBlank()
                || boundCode.equals(returned.getAbnormalityCode()))) {
            return;
        }
        marker.bindByUuid(targetLevel, returnedId);
    }

    private static boolean isValidContainmentAnchor(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight()
                && pos.getY() < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(pos);
    }

    private BlockPos findSafeContainmentPosition(ServerLevel level, @Nullable RoomMarkerBlockEntity marker, BlockPos center) {
        AABB roomBounds = marker == null ? null : marker.getScanBox();
        int maxRadius = marker == null ? 4 : Math.min(32, Math.max(0, marker.getRadiusXZ()));
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (!level.hasChunkAt(pos)) continue;
                        if (roomBounds != null && !roomBounds.contains(
                                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)) continue;
                        AABB destination = getBoundingBox().move(
                                pos.getX() + 0.5D - getX(), pos.getY() - getY(), pos.getZ() + 0.5D - getZ());
                        if (!level.noCollision(this, destination)) continue;
                        if (!level.getEntities(this, destination,
                                entity -> entity.isAlive() && !entity.isRemoved() && entity.isPickable()).isEmpty()) {
                            continue;
                        }
                        if (!isNoGravity() && !level.getBlockState(pos.below()).isFaceSturdy(
                                level, pos.below(), net.minecraft.core.Direction.UP)) continue;
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public int getQliphothCounter() {
        return qliphothCounter;
    }

    @Override
    public int getMaxQliphothCounter() {
        return maxQliphothCounter;
    }

    @Override
    public void decreaseQliphothCounter(int amount) {
        int old = qliphothCounter;
        QliphothCounterPolicy.Result result = QliphothCounterPolicy.decrease(old, amount);
        qliphothCounter = result.value();
        if (result.firstMeltdown()) {
            onQliphothMeltdown();
            MinecraftForge.EVENT_BUS.post(new QliphothCounterEvent(this, old, 0, true));
        } else if (old != qliphothCounter) {
            MinecraftForge.EVENT_BUS.post(new QliphothCounterEvent(this, old, qliphothCounter, false));
        }
    }

    @Override
    public void increaseQliphothCounter(int amount) {
        if (qliphothCounter >= maxQliphothCounter) {
            qliphothCounter = maxQliphothCounter;
            return;
        }
        int old = qliphothCounter;
        qliphothCounter += amount;
        MinecraftForge.EVENT_BUS.post(new QliphothCounterEvent(this, old, qliphothCounter, false));
    }

    protected void initializeQliphothCounter(int amount) {
        this.qliphothCounter = amount;
        this.maxQliphothCounter = amount;
    }

    protected void initializeQliphothCounter(int max, int now) {
        this.qliphothCounter = now;
        this.maxQliphothCounter = max;
    }

    protected void initializeQliphothCounter() {
        this.qliphothCounter = 114514;
        this.maxQliphothCounter = 114514;
    }

    @Override
    public void onQliphothMeltdown() {
        // 默认实现：子类可以重写
    }

    @Override
    public boolean onWorkStart(ServerPlayer player, WorkType workType) {
        if (this.canEscape() && this.hasEscape()) {
            return false;
        }
        return IAbnormality.super.onWorkStart(player, workType);
    }

    @Override
    public boolean onOpenManualScreen(ServerPlayer player) {
        if (this.canEscape() && this.hasEscape()) {
            return false;
        }
        return IAbnormality.super.onOpenManualScreen(player);
    }

    @Override
    public boolean onOpenWorkScreen(ServerPlayer player) {
        if (this.canEscape() && this.hasEscape()) {
            return false;
        }
        return IAbnormality.super.onOpenWorkScreen(player);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double p_20286_, double p_20287_, double p_20288_) {
    }

    @Override
    public void pushEntities() {
    }

    @Override
    public void doPush(Entity p_20971_) {
    }

    @Override
    public void push(Entity p_21294_) {
    }

    @Override
    public void knockback(double p_147241_, double p_147242_, double p_147243_) {
    }

    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.1f, -0.05f, 0.2f, -0.1f};  // 子类可以重写返回加成值
    }

    // 默认每级加成值（默认所有级别是1.0，没有变化）
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                levelModifiers[i][j] = -114514.0f;  // 默认没有加成
            }
        }
        return levelModifiers;
    }

    /**
     * 初始化工作偏好矩阵
     * @param base 基础偏好 [本能, 洞察, 沟通, 压迫]
     */
    protected void initializeWorkPreferences(float[] base) {
        this.workPreferences = base;
        float[] modifiers = getWorkPreferencesModifier();
        float[][] levelModifiers = getWorkPreferencesLevelModifiers();

        for (int work = 0; work < 4; work++) {
            float baseRate = base[work] + modifiers[work];

            for (int level = 0; level < 5; level++) {
                float levelModifier = levelModifiers[work][level];
                float finalRate;

                // 如果levelModifier是-114514.0，说明使用默认值，回退到原算法
                if (levelModifier == -114514.0f) {
                    float oldStyleModifier = 1.0f + (level - 2) * 0.05f;
                    finalRate = baseRate * oldStyleModifier;
                } else {
                    finalRate = baseRate + levelModifier;
                }

                this.fullWorkPreferences[work][level] = Math.max(-1.0f,
                        Math.min(1.0f, finalRate));
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        qliphothCounter = tag.getInt("QliphothCounter");
        ambientSoundTimer = tag.getInt("AmbientSoundTimer");
        this.entityData.set(DATA_ESCAPED, tag.getBoolean("HasEscaped"));
        this.entityData.set(DATA_JADE_NAME, tag.getString("AbnormalityJadeName"));
        if (tag.contains("EscapePosX")) {
            escapePosition = new BlockPos(
                    tag.getInt("EscapePosX"),
                    tag.getInt("EscapePosY"),
                    tag.getInt("EscapePosZ")
            );
        }
        escapeDimension = ResourceLocation.tryParse(tag.getString("EscapeDimension"));
        if (escapeDimension == null && escapePosition != null) {
            escapeDimension = level().dimension().location();
        }
        homeRoomDimension = ResourceLocation.tryParse(tag.getString("HomeRoomDimension"));
        if (tag.contains("HomeRoomMarker")) homeRoomMarker = BlockPos.of(tag.getLong("HomeRoomMarker"));
        pendingContainmentReturn = tag.getBoolean("PendingContainmentReturn");
        ContainmentReturnPolicy.LoadedState loadedReturn = ContainmentReturnPolicy.normalizeLoadedState(
                this.entityData.get(DATA_ESCAPED), pendingContainmentReturn,
                tag.getBoolean("ContainmentReturnWasSuppression"), getHealth(), getMaxHealth());
        pendingContainmentReturn = loadedReturn.pending();
        pendingStopEscapeEvent = loadedReturn.stopEventPending();
        containmentReturnWasSuppression = loadedReturn.suppression();
        if (loadedReturn.escaped() != this.entityData.get(DATA_ESCAPED)) {
            this.entityData.set(DATA_ESCAPED, loadedReturn.escaped());
        }
        if (Float.compare(loadedReturn.health(), getHealth()) != 0) {
            setHealth(loadedReturn.health());
        }
        if (tag.contains("LastFacilityDaySeen")) lastFacilityDaySeen = tag.getInt("LastFacilityDaySeen");

    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("QliphothCounter", qliphothCounter);
        tag.putInt("AmbientSoundTimer", ambientSoundTimer);
        tag.putBoolean("HasEscaped", this.entityData.get(DATA_ESCAPED));
        tag.putString("AbnormalityJadeName", this.entityData.get(DATA_JADE_NAME));
        if (escapePosition != null) {
            tag.putInt("EscapePosX", escapePosition.getX());
            tag.putInt("EscapePosY", escapePosition.getY());
            tag.putInt("EscapePosZ", escapePosition.getZ());
        }
        if (escapeDimension != null) tag.putString("EscapeDimension", escapeDimension.toString());
        if (homeRoomDimension != null) tag.putString("HomeRoomDimension", homeRoomDimension.toString());
        if (homeRoomMarker != null) tag.putLong("HomeRoomMarker", homeRoomMarker.asLong());
        tag.putBoolean("PendingContainmentReturn", pendingContainmentReturn);
        tag.putBoolean("PendingStopEscapeEvent", pendingStopEscapeEvent);
        tag.putBoolean("ContainmentReturnWasSuppression", containmentReturnWasSuppression);
        tag.putInt("LastFacilityDaySeen", lastFacilityDaySeen);
    }
}
