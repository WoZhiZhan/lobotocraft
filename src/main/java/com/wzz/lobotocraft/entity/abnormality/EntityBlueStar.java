package com.wzz.lobotocraft.entity.abnormality;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.block.entity.RegenerationReactorBlockEntity;
import com.wzz.lobotocraft.employee.FacilityAccessService;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.init.ModAttributes;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.init.ModSounds;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.ElevatorTeleportPacket;
import com.wzz.lobotocraft.network.packet.ScreenDistortionEffectPacket;
import com.wzz.lobotocraft.network.packet.ShockwaveEffectPacket;
import com.wzz.lobotocraft.util.*;
import com.wzz.lobotocraft.world.data.FacilityAreaType;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkResult;
import com.wzz.lobotocraft.work.WorkType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public class EntityBlueStar extends AbstractAbnormality {
    private final Random random = new Random();
    private boolean escapeDestinationPending;
    /** Timers started by this escape instance; they must not outlive containment. */
    private final Set<EscapeTimerHandle> activeEscapeTimers = ConcurrentHashMap.newKeySet();

    private record EscapeTimerHandle(UUID targetId, TimerEntry<?> timer) {
    }

    public EntityBlueStar(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected AbstractAbnormality createNewInstance(ServerLevel serverLevel) {
        return new EntityBlueStar((EntityType<? extends TamableAnimal>) this.getType(), serverLevel);
    }

    @Override
    protected void initializeAbnormality() {
        this.abnormalityCode = "O-03-93";
        this.abnormalityName = "碧蓝新星";
        this.riskLevel = RiskLevel.ALEPH;
        this.damageType = "WHITE";
        this.maxPEOutput = 33;

        float[] basePreferences = {0.3f, 0.5f, 0.0f, 0.4f};
        initializeWorkPreferences(basePreferences);
        initializeQliphothCounter(2);
    }

    @Override
    protected float[] getWorkPreferencesModifier() {
        return new float[] {0.0f, 0.0f, 0.0f, 0.0f};
    }

    @Override
    protected float[][] getWorkPreferencesLevelModifiers() {
        float[][] levelModifiers = new float[4][5];
        levelModifiers[0] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[1] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[2] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        levelModifiers[3] = new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        return levelModifiers;
    }

    @Override
    public @NotNull SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                 MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                                 @Nullable CompoundTag tag) {
        setAnimation("animation.blue_star.release");
        return super.finalizeSpawn(level, difficulty, spawnType, spawnData, tag);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    public void triggerEscape() {
        if (hasEscape() || this.level().isClientSide || !canEscape()) return;
        ServerLevel serverLevel = (ServerLevel) this.level();
        if (!serverLevel.dimension().equals(ModDimensions.LOBOTO_KEY)) {
            beginEscape(null);
            return;
        }
        escapeDestinationPending = !beginEscapeAtSafePosition(findHomeReactorLanding(serverLevel));
    }

    /** MC adaptation: the containment department's main room, indoors near its reactor. */
    private Vec3 findHomeReactorLanding(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        BlockPos home = dimension.equals(getHomeRoomDimension()) && getHomeRoomMarker() != null
                ? getHomeRoomMarker() : blockPosition();
        var binding = FacilityWorldLayout.containmentAt(dimension, home);
        var homeArea = FacilityWorldLayout.facilityAreaAt(level, home);
        String group = binding.map(FacilityWorldLayout.ContainmentBinding::groupId)
                .orElseGet(() -> homeArea.map(area -> area.groupId).orElse(""));
        var section = FacilityAccessService.resolveSection(dimension, group);
        if (section.isEmpty()) return null;
        var rooms = FacilityWorldLayout.fixedAreas(dimension).stream()
                .filter(area -> area.type() == FacilityAreaType.REST_ROOM
                        && (area.id().endsWith("/main") || area.id().endsWith("-main")
                        || area.id().endsWith("/main-reactor") || area.id().endsWith("-main-reactor")))
                .filter(area -> FacilityAccessService.resolveSection(dimension, area.groupId())
                        .filter(section.get()::equals).isPresent())
                .filter(area -> FacilityAccessService.isGroupOpen(
                        level.getServer(), dimension, area.groupId()))
                .sorted(Comparator.comparingDouble(area -> area.minPos().distSqr(home)))
                .toList();
        for (var room : rooms) {
            List<BlockPos> reactors = new ArrayList<>();
            for (int cx = room.minPos().getX() >> 4; cx <= room.maxPos().getX() >> 4; cx++) {
                for (int cz = room.minPos().getZ() >> 4; cz <= room.maxPos().getZ() >> 4; cz++) {
                    for (var blockEntity : level.getChunk(cx, cz).getBlockEntities().values()) {
                        if (blockEntity instanceof RegenerationReactorBlockEntity
                                && room.contains(blockEntity.getBlockPos())) {
                            reactors.add(blockEntity.getBlockPos().immutable());
                        }
                    }
                }
            }
            reactors.sort(Comparator.comparingDouble(pos -> pos.distSqr(home)));
            for (BlockPos reactor : reactors) {
                for (int radius = 0; radius <= 6; radius++) {
                    for (int y = room.minPos().getY(); y <= room.maxPos().getY(); y++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                                BlockPos candidate = new BlockPos(
                                        reactor.getX() + dx, y, reactor.getZ() + dz);
                                if (candidate.distSqr(reactor) > 100) continue;
                                // Carpets and low trapdoors require fractional feet heights.
                                var surface = level.getBlockState(candidate).getCollisionShape(level, candidate);
                                double offset = surface.isEmpty() ? 0
                                        : surface.max(net.minecraft.core.Direction.Axis.Y);
                                if (offset >= 1) continue;
                                Vec3 landing = new Vec3(candidate.getX() + 0.5D,
                                        y + offset, candidate.getZ() + 0.5D);
                                AABB box = getDimensions(getPose()).makeBoundingBox(
                                        landing.x, landing.y, landing.z);
                                if (box.minX < room.minPos().getX() || box.maxX > room.maxPos().getX() + 1
                                        || box.minY < room.minPos().getY() || box.maxY > room.maxPos().getY() + 1
                                        || box.minZ < room.minPos().getZ() || box.maxZ > room.maxPos().getZ() + 1) continue;
                                if (!level.getBlockState(candidate.below()).isFaceSturdy(
                                        level, candidate.below(), net.minecraft.core.Direction.UP)) continue;
                                // Measured open room geometry is authoritative, not the legacy
                                // generation radius (which excludes parts of the actual facility).
                                if (!FacilityAccessService.isPositionOpen(
                                                level.getServer(), dimension, candidate)
                                        || !level.noCollision(this, box)
                                        || level.noCollision(this, box.move(0, -0.05D, 0))
                                        || !level.getEntities(this, box,
                                                other -> other.isAlive() && !other.isRemoved()).isEmpty()) continue;
                                return landing;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean hasEscapeAmbientSound() { return true; }

    @Override
    public SoundEvent getEscapeAmbientSound() { return ModSounds.BLUE_STAR_ESCAPE.get(); }

    @Override
    public int getEscapeAmbientSoundInterval() { return 560; }

    @Override
    public void stopAmbientSoundForAllPlayers() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        List<ServerPlayer> players = serverLevel.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            com.wzz.lobotocraft.network.MessageLoader.getLoader().sendToPlayer(player,
                    new com.wzz.lobotocraft.network.packet.StopAllSoundPacket()
            );
        }
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result) {
        if (getQliphothCounter() > 0 && player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS)
                .map(stats -> stats.getPrudenceLevel() <= 4).orElse(false)) {
            decreaseQliphothCounter(1);
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.blue_star.careless_counter"), false);
        }
        if (MentalValueUtil.isPanic(player)) {
            decreaseQliphothCounter(1);
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.blue_star.panic_counter"));
        }
    }

    @Override
    public void onWorkComplete(ServerPlayer player, WorkType workType, WorkResult result,
                               WorkManager.WorkSession session) {
        // Original OnWorkClosed: timeout first, then prudence only while a counter remains.
        if (session.workDuration >= 60 * 20) {
            decreaseQliphothCounter(1);
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.blue_star.work_devoured"), false);
            player.displayClientMessage(Component.translatable(
                    "message.lobotocraft.blue_star.counter_minus_one"), false);
            killMob(player, false);
        }
        onWorkComplete(player, workType, result);
    }

    @Override
    public ObservationLevelBonus[] getObservationBonuses() {
        return new ObservationLevelBonus[] {
                new ObservationLevelBonus(0.04f, 0),
                new ObservationLevelBonus(0.0f, 2),
                new ObservationLevelBonus(0.04f, 0),
                new ObservationLevelBonus(0.0f, 2, true, true, true)
        };
    }

    @Override
    public void onQliphothMeltdown() {
        super.onQliphothMeltdown();
        triggerEscape();
    }

    @Override
    public void onBadWork(ServerPlayer player) {
        decreaseQliphothCounter(1);
    }

    @Override
    public void die(DamageSource damageSource) {
        setAnimation("animation.blue_star.dead");
        playSound(ModSounds.BLUE_STAR_DIE.get());
        super.die(damageSource);
    }

    @Override
    public void stopEscape() {
        escapeDestinationPending = false;
        boolean wasEscaped = hasEscape();
        if (wasEscaped) {
            for (EscapeTimerHandle handle : activeEscapeTimers) {
                handle.timer().removeTimer(handle.targetId());
            }
            activeEscapeTimers.clear();
        }
        super.stopEscape();
        if (!wasEscaped || hasEscape()) return;
        setAnimation("animation.blue_star.idle");
        setTarget(null);
    }

    @Override
    protected void resetForNewDay() {
        super.resetForNewDay();
        escapeDestinationPending = false;
    }

    private void trackEscapeTimer(TimerEntry<?> timer, LivingEntity target) {
        if (timer != null && target != null) {
            activeEscapeTimers.add(new EscapeTimerHandle(target.getUUID(), timer));
        }
    }

    private void untrackEscapeTimer(LivingEntity target, TimerEntry<?> timer) {
        if (target != null && timer != null) {
            activeEscapeTimers.remove(new EscapeTimerHandle(target.getUUID(), timer));
        }
    }

    private boolean isEscapeCombatActive() {
        return hasEscape() && !isContainmentReturnPending();
    }

    private void killMob(LivingEntity living, boolean remove) {
        if (isContainmentReturnPending()
                || living == null || !living.isAlive() || living.isRemoved()) return;
        TimerEntry<LivingEntity> timerEntry = new TimerEntry<>() {
            @Override
            public void onStart(@NotNull LivingEntity living) {
                if (!living.isAlive() || living.isRemoved()) return;
                EntityUtil.teleportToFront(living, EntityBlueStar.this, 0);
            }

            @Override
            public void onRunning(@NotNull LivingEntity living) {
                if (!living.isAlive() || living.isRemoved()) return;
                EntityUtil.teleportToFront(living, EntityBlueStar.this, 0);
            }

            @Override
            public void onEnd(@NotNull LivingEntity living) {
                untrackEscapeTimer(living, this);
                if (!living.isAlive() || living.isRemoved()) return;
                if (living instanceof EntityClerk clerk) {
                    EntityClerk.markNoTombstone(clerk);
                }
                if (!remove)
                    living.kill();
                else living.discard();
                if (living instanceof Player player) {
                    player.displayClientMessage(Component.translatable(
                            "message.lobotocraft.blue_star.killed"), false);
                    boolean hasPlayer = false;
                    for (Player p : EntityUtil.findPlayersAround(player, 10, 32)) {
                        if (p != getTarget()) {
                            hasPlayer = true;
                            break;
                        }
                    }
                    if (!hasPlayer) {
                        stopEscape();
                    }
                }
                setAnimation("animation.blue_star.idle");
            }

            @Override
            public void onCancelled(@NotNull LivingEntity living, @NotNull CancellationReason reason) {
                untrackEscapeTimer(living, this);
                if (reason == CancellationReason.LOGOUT && living instanceof ServerPlayer player) {
                    PendingPlayerExecution.mark(player);
                }
                setAnimation("animation.blue_star.idle");
            }
        };
        if (timerEntry.addSkillTimer(living, 0, 1800, 1)) {
            trackEscapeTimer(timerEntry, living);
            setAnimation("animation.blue_star.attack");
        }
    }

    @Override
    public void tick() {
        super.tick();
        setNoAi(!isEscapeCombatActive());
        if (level().isClientSide) {
            return;
        }
        if (!hasEscape() && !isContainmentReturnPending() && this.tickCount % 20 == 0
                && (escapeDestinationPending || getQliphothCounter() == 0)) {
            triggerEscape();
        }
        if (this.tickCount % 20 == 0) {
            for (Player player : EntityUtil.findPlayersAround(this, 4, 4)) {
                AtomicBoolean shouldDead = new AtomicBoolean(false);
                player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(data -> {
                    if (data.getTemperanceLevel() < 4) {
                        shouldDead.set(true);
                    }
                });
                if (shouldDead.get()) {
                   killMob(player, false);
                }
            }
            if (isEscapeCombatActive()) {
                for (Player player : EntityUtil.findPlayersAround(this, 4, 8)) {
                    AtomicBoolean shouldDead = new AtomicBoolean(false);
                    player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(data -> {
                        if (data.getTemperanceLevel() < 3 || data.getPrudenceLevel() < 4) {
                            shouldDead.set(true);
                        }
                    });
                    if (shouldDead.get()) {
                        killMob(player, false);
                    }
                }
                for (Entity e : EntityUtil.findAllEntities(this, 400)) {
                    if (e instanceof ServerPlayer player && MentalValueUtil.isPanic(player)
                            && !player.getPersistentData().getBoolean("isStartElevatorAnim")) {
                        player.getPersistentData().putBoolean("isStartElevatorAnim", true);
                        int animDuration = 40;
                        // 坐电梯同款过渡动画:先发相机过渡包,再把玩家瞬间传送到碧蓝新星处
                        net.minecraft.world.phys.Vec3 startPos = player.position().add(0, 1, 0);
                        net.minecraft.world.phys.Vec3 endPos = this.position().add(0, 1, 0);
                        MessageLoader.getLoader().sendToPlayer(
                                player,
                                new ElevatorTeleportPacket(startPos, endPos, animDuration)
                        );
                        player.teleportTo(this.getX(), this.getY(), this.getZ());
                        // 过渡动画结束后再击杀(40 tick ≈ 2000 毫秒)
                        TimerEntry<ServerPlayer> killTimer = getPlayerTimerEntry();
                        if (killTimer.addSkillTimer(player, 0, animDuration * 50, 1)) {
                            trackEscapeTimer(killTimer, player);
                        }
                    }
                }
            }
        }
        if (this.tickCount % 80 == 0 && isEscapeCombatActive()) {
            setAnimation("animation.blue_star.attack");
            for (Entity entity : EntityUtil.findAllEntities(this, 400)) {
                if (entity instanceof LivingEntity living && !(living instanceof EntityBlueStar)) {
                    TimerEntry<LivingEntity> timerEntry = getEntityTimerEntry();
                    if (timerEntry.addSkillTimer(living, 0, 1800, 1)) {
                        trackEscapeTimer(timerEntry, living);
                    }
                }
            }
        }
    }

    private @NotNull TimerEntry<ServerPlayer> getPlayerTimerEntry() {
        TimerEntry<ServerPlayer> killTimer = new TimerEntry<>() {
            @Override
            public void onEnd(@NotNull ServerPlayer living) {
                untrackEscapeTimer(living, this);
                if (living.isAlive() && !living.isRemoved()) {
                    living.kill();
                }
            }

            @Override
            public void onCancelled(@NotNull ServerPlayer living, @NotNull CancellationReason reason) {
                untrackEscapeTimer(living, this);
                if (reason == CancellationReason.LOGOUT) PendingPlayerExecution.mark(living);
            }
        };
        return killTimer;
    }

    private @NotNull TimerEntry<LivingEntity> getEntityTimerEntry() {
        TimerEntry<LivingEntity> timerEntry = new TimerEntry<>() {
            @Override
            public void onRunning(@NotNull LivingEntity living) {
                if (!living.isAlive() || living.isRemoved()) return;
                float damage = 15 + random.nextInt(6);
                if (this.getExecutions() == 2) {
                    playAttackSound();
                    if (!(living instanceof ServerPlayer player)) {
                        if (living instanceof Villager villager && random.nextInt(101) <= 20) {
                            killMob(villager, true);
                        } else {
                        if (living instanceof EntityClerk clerk && damage >= clerk.getHealth()) {
                                EntityClerk.markNoTombstone(clerk);
                            }
                            DamageSource source = QueenOfHatredCombat.isQueenOfHatredCombatTarget(living)
                                    ? DamageHelper.getDamage(EntityBlueStar.this, "white")
                                    : living.damageSources().fellOutOfWorld();
                            living.hurt(source, damage);
                        }
                    } else {
                        MessageLoader.getLoader().sendToPlayer(player, new ScreenDistortionEffectPacket(0.3f, 10));
                        player.playSound(SoundEvents.PLAYER_HURT);
                        player.hurt(DamageHelper.getDamage(EntityBlueStar.this, "white"), damage);
                        MessageLoader.getLoader().sendToPlayer(player, new ShockwaveEffectPacket(
                                getX(), getY(), getZ(), 60f,
                                0x88CCFF
                        ));
                    }
                }
            }

            @Override
            public void onEnd(@NotNull LivingEntity living) {
                untrackEscapeTimer(living, this);
                setAnimation("animation.blue_star.idle");
            }

            @Override
            public void onCancelled(@NotNull LivingEntity living, @NotNull CancellationReason reason) {
                untrackEscapeTimer(living, this);
                setAnimation("animation.blue_star.idle");
            }
        };
        return timerEntry;
    }

    @Override
    public List<String> getWorkLogs() {
        List<String> logs = new ArrayList<>();
        logs.add("通常情况下，员工们会渴望把自己献给“碧蓝新星”。");
        logs.add("我们的“碧蓝新星”诞生自绝望的深渊，亦将飘向一个全新的开始。");
        logs.add("我们生来即是罪人，而碧蓝新星是唯一能够接纳我们这种罪人的地方，我们将在那里净化我们的一切。");
        logs.add("我们终有一天会回到那个地方，这是我们与生具来的本能。");
        logs.add("你也许会认为，那个把自己献给碧蓝新星的艾米丽已经死了？大错特错！她回到了真正的归属，她是一位殉教者，她化成了一颗永远的明星！");
        logs.add("一颗冉冉升起的新星意味着全新的开始！你听不到那永恒的号角吗！？");
        logs.add("靠近那颗星，你能听到他们正欢呼着，歌唱着迎接我们的到来！");
        return logs;
    }

    @Override
    public int getBasicInfoCost() {
        return 30;
    }

    @Override
    public int getWorkPreferencesCost() {
        return 10;
    }

    @Override
    public int getSensitiveInfoCost() {
        return 30;
    }

    @Override
    public int getManualCost(int manualIndex) {
        return 7;
    }

    @Override
    public String name() {
        return "blue_star";
    }

    @Override
    public Float modifyWorkSuccessRate(ServerPlayer player, WorkType workType, float baseRate) {
        if (workType == WorkType.INSIGHT) {
            return Math.min(0.95f, baseRate + 0.20f);
        }
        return null;
    }

    @Override
    public void attackPlayerOnFailure(Player player, WorkType workType) {
        float damage = 6.0f + random.nextInt(2) + 3;
        player.hurt(DamageHelper.getDamage(this, "lobotocraft:white"), damage);
    }

    @Override
    public SoundEvent getAttackSound() {
        return ModSounds.BLUE_STAR_ATTACK.get();
    }

    @Override
    public void playAttackSound() {
        playGlobalSound(getAttackSound());
    }

    @Override
    public EGOEquipmentData.GiftData getEGOGiftData() {
        return new EGOEquipmentData.GiftData(
                ResourceUtil.createInstance("textures/item/blue_star_curio.png"),
                "新星之声",
                "眼部",
                "blue_star_curio",
                "精神值+15",
                "移动速度+10"
        );
    }

    @Override
    public int getWeaponDevelopmentCost() {
        return 222;
    }

    @Override
    public int getWeaponDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public int getArmorDevelopmentCost() {
        return 150;
    }

    @Override
    public int getArmorDevelopmentMaxCount() {
        return 1;
    }

    @Override
    public EGOEquipmentData.WeaponData getEGOWeaponData() {
        return new EGOEquipmentData.WeaponData(
                ResourceUtil.createInstance("textures/item/blue_star_weapon.png"),
                "新星之声",
                getRiskLevel(),
                "WHITE",
                "8-12 / 15-18",
                "1.5s",
                "25格",
                getWeaponDevelopmentMaxCount(),
                "blue_star_weapon"
        );
    }

    @Override
    public EGOEquipmentData.ArmorData getEGOArmorData() {
        return new EGOEquipmentData.ArmorData(
                ResourceUtil.createInstance("textures/gui/ego/blue_star_armor.png"),
                "新星之声",
                getRiskLevel(),
                0.4f,
                0.4f,
                0.4f,
                1.0f,
                getArmorDevelopmentMaxCount(),
                "blue_star"
        );
    }

    @Override
    public float[] getGiftRenderOffset() {
        return new float[] {1f, 0.0f, 0.0f};
    }

    @Override
    public float[] getWeaponRenderScale() {
        return new float[] {0.8f, 0.8f, 0.8f};
    }

    @Override
    public float[] getWeaponRenderOffset() {
        return new float[] {15f, 0.0f, 0.0f};
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<EntityBlueStar> event) {
        String action = getAnimation();
        return switch (action) {
            case "animation.blue_star.release" ->
                    event.setAndContinue(RawAnimation.begin()
                            .thenPlay("animation.blue_star.release"));
            case "animation.blue_star.attack" ->
                    event.setAndContinue(RawAnimation.begin()
                            .thenPlay("animation.blue_star.attack"));
            case "animation.blue_star.dead" ->
                    event.setAndContinue(RawAnimation.begin()
                            .thenPlay("animation.blue_star.dead"));
            case "animation.blue_star.absorb" ->
                    event.setAndContinue(RawAnimation.begin()
                            .thenPlay("animation.blue_star.absorb"));
            default ->
                    event.setAndContinue(RawAnimation.begin().thenLoop("animation.blue_star.idle"));
        };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2200.0D)
                .add(Attributes.FLYING_SPEED, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 3.1D)
                .add(ModAttributes.RED_DAMAGE_RESISTANCE.get(), 0.4D)
                .add(ModAttributes.WHITE_DAMAGE_RESISTANCE.get(), 0.2D)
                .add(ModAttributes.BLACK_DAMAGE_RESISTANCE.get(), 0.8D)
                .add(ModAttributes.BLUE_DAMAGE_RESISTANCE.get(), 1.2D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1024D);
    }
}
