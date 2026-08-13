package com.wzz.lobotocraft.employee;

import com.mojang.authlib.GameProfile;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.capability.EmployeeStats;
import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.core_suppression.CoreSuppressionManager;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PanicType;
import com.wzz.lobotocraft.compat.ysm.YsmEmployeeCompat;
import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggEye;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggHigh;
import com.wzz.lobotocraft.entity.abnormality.EntityEndBirdEggSmall;
import com.wzz.lobotocraft.entity.abnormality.EntityPunishingBird;
import com.wzz.lobotocraft.entity.abnormality.EntityRedShoes;
import com.wzz.lobotocraft.entity.abnormality.EntitySnowQueen;
import com.wzz.lobotocraft.entity.data.EgoWeaponDisplayCatalog;
import com.wzz.lobotocraft.entity.ordeal.OrdealEntity;
import com.wzz.lobotocraft.event.listener.EmployeeStatsApplier;
import com.wzz.lobotocraft.event.listener.EgoAttackOwnershipPolicy;
import com.wzz.lobotocraft.item.ego.base.AiCombatModeEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.AiRangedEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.AiSpecialEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaGroup;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.util.FacilityCombatRules;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
import com.wzz.lobotocraft.work.WorkType;
import com.wzz.lobotocraft.work.AbnormalityWorkRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class EmployeeRuntimeManager {
    public enum AppearanceChangeResult {
        APPLIED,
        BUSY,
        FAILED
    }

    private static final double MOVE_ARRIVAL_DISTANCE = 0.75D;
    private static final int IDLE_WANDER_ATTEMPTS = 32;
    private static final int IDLE_WANDER_MIN_DELAY = 100;
    private static final int IDLE_WANDER_DELAY_RANGE = 301;
    private static final int COMBAT_TARGET_SCAN_INTERVAL = 10;
    private static final int PANIC_UNREACHABLE_RETRY_TICKS = 100;
    private static final Map<UUID, RuntimeEmployee> ACTIVE = new HashMap<>();
    private static final Set<MinecraftServer> INITIALIZED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private EmployeeRuntimeManager() {
    }

    public static EmployeeRecord createAndSpawn(ServerPlayer owner, String name, EmployeeDepartment department) {
        return createAndSpawn(owner, name, department, 10, 10, 10, 10);
    }

    public static EmployeeRecord createAndSpawn(ServerPlayer owner, String name, EmployeeDepartment department,
                                                int fortitude, int prudence,
                                                int temperance, int justice) {
        return createAndSpawn(owner, name, department, fortitude, prudence, temperance, justice,
                EmployeeAppearance.DEFAULT);
    }

    public static EmployeeRecord createAndSpawn(ServerPlayer owner, String name, EmployeeDepartment department,
                                                int fortitude, int prudence,
                                                int temperance, int justice,
                                                EmployeeAppearance appearance) {
        FacilityPersonnelData data = FacilityPersonnelData.get(owner.server);
        EmployeeRecord record = data.create(name,
                DepartmentProgressService.isOpen(owner.server, department)
                        ? department : EmployeeDepartment.UNASSIGNED);
        if (record == null) return null;
        record.setStats(fortitude, prudence, temperance, justice);
        record.setAppearance(appearance);
        record.setPosition(owner.serverLevel().dimension().location(), owner.getX(), owner.getY(), owner.getZ());
        record.setDeployed(true);
        data.changed();
        return spawn(owner.server, record);
    }

    public static EmployeeRecord spawn(MinecraftServer server, EmployeeRecord record) {
        if (ACTIVE.containsKey(record.id())) return record;
        ResourceLocation dimensionId = ResourceLocation.tryParse(record.dimension());
        if (dimensionId == null) return null;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) return null;

        EmployeeServerPlayer employee = new EmployeeServerPlayer(level,
                new GameProfile(record.id(), profileName(record)), record);
        CompoundTag saved = record.playerData();
        boolean restored = !saved.isEmpty();
        if (!restored && YsmEmployeeCompat.isAvailable()) {
            employee.saveWithoutId(saved);
        }
        boolean ysmPrepared = YsmEmployeeCompat.preparePlayerData(record, saved);
        if (restored || ysmPrepared) employee.load(saved);
        employee.absMoveTo(record.x(), record.y(), record.z(), 0.0F, 0.0F);
        employee.setCustomName(departmentDisplayName(record));
        employee.setCustomNameVisible(true);
        employee.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            stats.setFortitude(record.fortitude());
            stats.setPrudence(record.prudence());
            stats.setTemperance(record.temperance());
            stats.setJustice(record.justice());
        });
        EmployeeStatsApplier.applyAllAttributes(employee);
        if (!restored) {
            employee.getCapability(com.wzz.lobotocraft.capability.MentalValueProvider.MENTAL_VALUE)
                    .ifPresent(mental -> mental.setMentalValue(mental.getEffectiveMaxMentalValue()));
        }
        AiEgoRepositoryData repository = AiEgoRepositoryData.get(server);
        EgoLoadoutService.validateAiAssignmentsOnSpawn(employee, repository);
        EgoLoadoutService.ensureAiLoadout(employee, repository);

        ClientboundPlayerInfoUpdatePacket info = createAiPlayerInfoPacket(List.of(employee));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) viewer.connection.send(info);
        level.addNewPlayer(employee);
        ACTIVE.put(record.id(), new RuntimeEmployee(employee, new EmployeeNavigator(employee)));
        CoreSuppressionManager.reconcileHodState(employee);
        record.setDeployed(true);
        // Orders are runtime-only. Never expose a stale WORK/MOVE status after a server restart.
        record.setTask(EmployeeTaskType.IDLE, "");
        FacilityPersonnelData.get(server).changed();
        return record;
    }

    public static boolean remove(MinecraftServer server, EmployeeRecord record) {
        RuntimeEmployee runtime = ACTIVE.remove(record.id());
        if (runtime != null) {
            runtime.player.serverLevel().removePlayerImmediately(runtime.player, Entity.RemovalReason.DISCARDED);
        }
        ClientboundPlayerInfoRemovePacket remove = new ClientboundPlayerInfoRemovePacket(List.of(record.id()));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) viewer.connection.send(remove);
        AiEgoRepositoryData repository = AiEgoRepositoryData.get(server);
        repository.release(record.id(), "weapon");
        repository.release(record.id(), "armor");
        com.lobotocraft.facility.damage.DamageHistory.clear(record.id());
        com.lobotocraft.facility.player.PlayerStatusEvents.forget(record.id());
        com.wzz.lobotocraft.llm.LlmHistoryData.get(server).removeEmployee(record.id());
        return FacilityPersonnelData.get(server).remove(record.id());
    }

    public static boolean assignMove(EmployeeRecord record, BlockPos target) {
        return assignMove(record, List.of(target), false);
    }

    public static boolean assignManualMove(EmployeeRecord record, BlockPos target) {
        return assignMove(record, List.of(target), true);
    }

    public static boolean assignManualMove(EmployeeRecord record, List<BlockPos> targets) {
        return assignMove(record, targets, true);
    }

    private static boolean assignMove(EmployeeRecord record, List<BlockPos> targets,
                                      boolean rememberStation) {
        RuntimeEmployee runtime = ACTIVE.get(record.id());
        if (!canAcceptNewOrder(runtime) || !DepartmentProgressService.isSectionOpen(
                runtime.player.server, record.department(), record.centralSection())
                || targets == null || targets.isEmpty()) return false;
        EmployeeOrder order = EmployeeOrder.move(targets);
        BlockPos target = order.targetPos;
        if (rememberStation) {
            record.setManualStation(runtime.player.serverLevel().dimension().location(), target);
        }
        runtime.idleMoving = false;
        runtime.order = order;
        runtime.navigator.moveTo(target, MOVE_ARRIVAL_DISTANCE);
        record.setTask(EmployeeTaskType.MOVE, "移动至 " + target.toShortString());
        FacilityPersonnelData.get(runtime.player.server).changed();
        return true;
    }

    public static boolean assignWork(EmployeeRecord record, AbstractAbnormality abnormality, WorkType workType) {
        return assignWork(record, abnormality, workType, false);
    }

    /** Tool use is a dedicated employee order and never enters WorkManager. */
    public static boolean assignToolUse(EmployeeRecord record, AbstractAbnormality abnormality) {
        RuntimeEmployee runtime = ACTIVE.get(record.id());
        if (!canAcceptNewOrder(runtime) || !abnormality.isToolType()
                || !DepartmentProgressService.isSectionOpen(runtime.player.server,
                record.department(), record.centralSection())
                || abnormality.level() != runtime.player.level()
                || isWorkTargetReserved(abnormality, record.id())) return false;
        runtime.idleMoving = false;
        runtime.order = EmployeeOrder.toolUse(abnormality.getUUID());
        runtime.navigator.moveNear(abnormality.position(), Math.sqrt(7.0D));
        record.setTask(EmployeeTaskType.TOOL_USE, "Use tool / " + abnormality.getAbnormalityName());
        FacilityPersonnelData.get(runtime.player.server).changed();
        return true;
    }

    public static boolean assignMonitorWork(EmployeeRecord record, AbstractAbnormality abnormality,
                                            WorkType workType) {
        return assignWork(record, abnormality, workType, true);
    }

    private static boolean assignWork(EmployeeRecord record, AbstractAbnormality abnormality,
                                      WorkType workType, boolean returnToManualStation) {
        RuntimeEmployee runtime = ACTIVE.get(record.id());
        if (!canAcceptNewOrder(runtime) || !AbnormalityWorkRules.isRegistered(abnormality)
                || !DepartmentProgressService.isSectionOpen(
                runtime.player.server, record.department(), record.centralSection())
                || abnormality.level() != runtime.player.level()
                || isWorkTargetReserved(abnormality, record.id())) return false;
        runtime.idleMoving = false;
        WorkType resolvedWorkType = CoreSuppressionManager.randomizeWorkType(
                runtime.player, workType);
        runtime.order = EmployeeOrder.work(
                abnormality.getUUID(), workType, resolvedWorkType, returnToManualStation);
        runtime.navigator.moveNear(abnormality.position(), Math.sqrt(7.0D));
        record.setTask(EmployeeTaskType.WORK,
                (CoreSuppressionManager.isMalkuthWorkScrambled(runtime.player.level())
                        ? "???" : workType.getDisplayName())
                        + "工作 / " + abnormality.getAbnormalityName());
        FacilityPersonnelData.get(runtime.player.server).changed();
        return true;
    }

    public static boolean isWorkTargetReserved(AbstractAbnormality abnormality) {
        return isWorkTargetReserved(abnormality, null);
    }

    /** Checks only AI reservations; a pending order from {@code requesterId} may be replaced. */
    public static boolean isWorkTargetReserved(AbstractAbnormality abnormality, UUID requesterId) {
        if (abnormality == null || WorkManager.isAiEmployeeWorking(abnormality)) return true;
        UUID targetId = abnormality.getUUID();
        for (RuntimeEmployee runtime : ACTIVE.values()) {
            EmployeeOrder order = runtime.order;
            if (order != null && (order.type == EmployeeTaskType.WORK || order.type == EmployeeTaskType.TOOL_USE)
                    && targetId.equals(order.targetEntity)
                    && !runtime.player.getUUID().equals(requesterId)
                    && runtime.player.isAlive() && !runtime.player.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    public static boolean assignSuppress(EmployeeRecord record, LivingEntity target) {
        RuntimeEmployee runtime = ACTIVE.get(record.id());
        if (!canAcceptNewOrder(runtime) || !DepartmentProgressService.isSectionOpen(
                runtime.player.server, record.department(), record.centralSection())
                || target.level() != runtime.player.level() || target instanceof Player) return false;
        if (!selectAssignedEgoWeapon(runtime.player)) return false;
        runtime.idleMoving = false;
        runtime.order = EmployeeOrder.suppress(target.getUUID());
        clearAutonomousCombat(runtime);
        runtime.navigator.moveTo(target.blockPosition());
        record.setTask(EmployeeTaskType.SEARCH_AND_SUPPRESS, "镇压 " + target.getName().getString());
        FacilityPersonnelData.get(runtime.player.server).changed();
        return true;
    }

    /** Assign a persistent follow order. The order remains active until replaced or the target disappears. */
    public static boolean assignFollow(EmployeeRecord record, Entity target) {
        RuntimeEmployee runtime = ACTIVE.get(record.id());
        if (!canAcceptNewOrder(runtime) || !DepartmentProgressService.isSectionOpen(
                runtime.player.server, record.department(), record.centralSection())
                || target == runtime.player || target.level() != runtime.player.level()
                || target.isRemoved()) return false;
        runtime.idleMoving = false;
        runtime.order = EmployeeOrder.follow(target.getUUID());
        runtime.navigator.moveTo(target.blockPosition());
        record.setTask(EmployeeTaskType.FOLLOW, "跟随 " + target.getName().getString());
        FacilityPersonnelData.get(runtime.player.server).changed();
        return true;
    }

    /** Cancel a pending AI order. A formal WorkManager session remains locked. */
    public static boolean cancelOrder(EmployeeRecord record, String reason) {
        if (record == null) return false;
        RuntimeEmployee runtime = ACTIVE.get(record.id());
        if (runtime == null || !runtime.player.isAlive() || runtime.player.isRemoved()
                || WorkManager.isPlayerWorking(runtime.player)) return false;
        fail(runtime, reason == null || reason.isBlank() ? "调试命令取消任务" : reason);
        return true;
    }

    /**
     * A pending route can be replaced by a new command. Once WorkManager has
     * created the real work session, the AI employee is locked until that
     * session ends (death/panic/cancellation included).
     */
    private static boolean canAcceptNewOrder(RuntimeEmployee runtime) {
        if (runtime == null || !runtime.player.isAlive() || runtime.player.isRemoved()) return false;
        if (EntitySnowQueen.isFrozen(runtime.player)
                || EntityRedShoes.isRedShoesControlled(runtime.player)) return false;
        if (runtime.order != null && runtime.order.type == EmployeeTaskType.WORK
                && CoreSuppressionManager.isMalkuthWorkCancellationLocked(runtime.player.level())) {
            return false;
        }
        return AbnormalityWorkRules.canReplacePendingAiOrder(
                WorkManager.isPlayerWorking(runtime.player));
    }

    public static EmployeeServerPlayer activePlayer(UUID employeeId) {
        RuntimeEmployee runtime = ACTIVE.get(employeeId);
        return runtime == null ? null : runtime.player;
    }

    public static void refreshDepartmentAppearance(EmployeeRecord record) {
        if (record == null) return;
        EmployeeServerPlayer employee = activePlayer(record.id());
        if (employee == null) return;
        employee.setCustomName(departmentDisplayName(record));
        employee.refreshDisplayName();
        employee.refreshTabListName();
    }

    /** Recreates an idle synthetic player so YSM publishes its new capability to all trackers. */
    public static AppearanceChangeResult changeAppearance(MinecraftServer server,
                                                          EmployeeRecord record,
                                                          EmployeeAppearance appearance) {
        if (server == null || record == null || record.retired()) {
            return AppearanceChangeResult.FAILED;
        }
        EmployeeAppearance clean = appearance == null
                ? EmployeeAppearance.DEFAULT : appearance.sanitized();
        if (record.appearance().equals(clean)) return AppearanceChangeResult.APPLIED;

        RuntimeEmployee runtime = ACTIVE.get(record.id());
        if (runtime == null) {
            record.setAppearance(clean);
            FacilityPersonnelData.get(server).changed();
            return AppearanceChangeResult.APPLIED;
        }
        EmployeeServerPlayer player = runtime.player;
        if (player.isRemoved() || !player.isAlive() || runtime.order != null
                || runtime.panicking || MentalValueUtil.isPanic(player)
                || WorkManager.isPlayerWorking(player)) {
            return AppearanceChangeResult.BUSY;
        }

        EmployeeAppearance previous = record.appearance();
        saveRuntime(runtime);
        if (!ACTIVE.remove(record.id(), runtime)) return AppearanceChangeResult.FAILED;

        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        ClientboundPlayerInfoRemovePacket remove = new ClientboundPlayerInfoRemovePacket(
                List.of(record.id()));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            viewer.connection.send(remove);
        }

        record.setAppearance(clean);
        FacilityPersonnelData data = FacilityPersonnelData.get(server);
        data.changed();
        if (spawn(server, record) != null) return AppearanceChangeResult.APPLIED;

        record.setAppearance(previous);
        if (spawn(server, record) == null) record.setDeployed(false);
        data.changed();
        return AppearanceChangeResult.FAILED;
    }

    static Component departmentDisplayName(EmployeeRecord record) {
        Component name = Component.literal(record.name());
        EmployeeDepartment department = record.department();
        if (department == null || department == EmployeeDepartment.UNASSIGNED) return name;
        return name.copy().withStyle(style -> style.withColor(
                TextColor.fromRgb(department.textColorRgb())));
    }

    public static Collection<EmployeeServerPlayer> activePlayers() {
        return ACTIVE.values().stream().map(runtime -> runtime.player).toList();
    }

    /** Clears stuck runtime state so every deployed AI employee can plan a fresh route. */
    public static int resetAllForExhibition(MinecraftServer server, String reason) {
        int reset = 0;
        for (RuntimeEmployee runtime : new ArrayList<>(ACTIVE.values())) {
            EmployeeServerPlayer player = runtime.player;
            if (player.server != server || player.isRemoved() || !player.isAlive()) continue;

            if (WorkManager.isPlayerWorking(player)) {
                WorkManager.cancelWork(player, reason);
            }
            if (runtime.order != null && runtime.order.type == EmployeeTaskType.TOOL_USE
                    && runtime.order.started) {
                Entity target = findEntity(player.server, runtime.order.targetEntity);
                if (target instanceof com.wzz.lobotocraft.entity.base.IAbnormality tool) {
                    tool.stopUsing(player);
                }
            }

            runtime.navigator.stop();
            runtime.order = null;
            runtime.idleMoving = false;
            runtime.panicking = false;
            clearPanicCombat(runtime);
            clearAutonomousCombat(runtime);
            player.setShiftKeyDown(false);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.employeeRecord().setTask(EmployeeTaskType.IDLE,
                    reason == null ? "" : reason);
            scheduleNextIdleMove(runtime, player.serverLevel().getGameTime());
            reset++;
        }
        if (reset > 0) FacilityPersonnelData.get(server).changed();
        return reset;
    }

    public static boolean hasLockedMalkuthWorkOrder(UUID employeeId) {
        RuntimeEmployee runtime = ACTIVE.get(employeeId);
        return runtime != null && runtime.order != null
                && runtime.order.type == EmployeeTaskType.WORK
                && CoreSuppressionManager.isMalkuthWorkCancellationLocked(runtime.player.level());
    }

    public static String navigationPurpose(UUID employeeId) {
        RuntimeEmployee runtime = ACTIVE.get(employeeId);
        if (runtime == null || runtime.order == null || runtime.navigator.target() == null
                || runtime.navigator.isComplete()) return "";
        String summary = visibleTaskSummary(runtime.player.employeeRecord());
        return summary == null ? "" : summary;
    }

    public static String visibleTaskSummary(EmployeeRecord record) {
        if (record == null) return "";
        RuntimeEmployee runtime = ACTIVE.get(record.id());
        if (runtime == null || runtime.order == null || runtime.order.type != EmployeeTaskType.WORK
                || !CoreSuppressionManager.isMalkuthWorkScrambled(runtime.player.level())) {
            return record.taskSummary();
        }
        Entity target = findEntity(runtime.player.server, runtime.order.targetEntity);
        return target instanceof AbstractAbnormality abnormality
                ? "???工作 / " + abnormality.getAbnormalityName()
                : "???工作";
    }

    public static EmployeeServerPlayer activePlayerByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (RuntimeEmployee runtime : ACTIVE.values()) {
            if (runtime.player.employeeRecord().name().equalsIgnoreCase(name)
                    || runtime.player.getGameProfile().getName().equalsIgnoreCase(name)) {
                return runtime.player;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (INITIALIZED.add(server)) {
            for (EmployeeRecord record : FacilityPersonnelData.get(server).allRecords()) {
                // 已持久化为死亡（退休墓碑）的记录：彻底清理，绝不重新 spawn。
                if (wasPersistedDead(record)) {
                    remove(server, record);
                    continue;
                }
                if (!record.deployed()) continue;
                spawn(server, record);
            }
        }
        for (RuntimeEmployee runtime : new ArrayList<>(ACTIVE.values())) tick(runtime);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEmployeeDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EmployeeServerPlayer employee)) return;
        RuntimeEmployee runtime = ACTIVE.get(employee.getUUID());
        // 死亡路径延迟清理伤害历史：LivingDeathEvent 在 die() 开头触发，
        // 此刻清空会导致死亡消息的悬浮伤害记录为空（改由 DamageHistory.decorateDeathMessage 清理）
        if (runtime != null) {
            retireDead(runtime, true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (RuntimeEmployee runtime : new ArrayList<>(ACTIVE.values())) {
            if (runtime.player.isRemoved() || !runtime.player.isAlive()) retireDead(runtime);
            else saveRuntime(runtime);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof EmployeeServerPlayer) return;
        for (RuntimeEmployee runtime : ACTIVE.values()) {
            if (runtime.player.server == event.getEntity().getServer()) {
                runtime.navigator.revalidateRoute();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        INITIALIZED.remove(event.getServer());
        EmployeeNavigator.clearPlanningBudget(event.getServer());
    }

    private static void tick(RuntimeEmployee runtime) {
        EmployeeServerPlayer player = runtime.player;
        EmployeeRecord record = player.employeeRecord();
        if (player.isRemoved()) {
            retireDead(runtime);
            return;
        }
        if (!player.isAlive()) {
            retireDead(runtime);
            return;
        }

        player.doTick();
        // doTick() 内部可能触发死亡（此时 onEmployeeDeath 已将其移出 ACTIVE 并安排移除），
        // 继续跑下面的订单/存档逻辑等于操作一个已死实体，故在此复查一次
        if (player.isRemoved() || !player.isAlive()) {
            retireDead(runtime);
            return;
        }
        if (EntitySnowQueen.isFrozen(player)) {
            if (runtime.order != null) {
                fail(runtime, "被冰雪女皇冰封，工作中断");
            } else {
                runtime.navigator.stop();
                runtime.idleMoving = false;
                clearAutonomousCombat(runtime);
            }
            if (WorkManager.isPlayerWorking(player)) {
                WorkManager.cancelWork(player, "被冰雪女皇冰封，工作中断");
            }
            return;
        }
        if (EntityRedShoes.isRedShoesControlled(player)) {
            if (runtime.order != null) {
                fail(runtime, "被红舞鞋控制，工作中断");
            } else {
                runtime.navigator.stop();
                runtime.idleMoving = false;
                clearAutonomousCombat(runtime);
            }
            if (WorkManager.isPlayerWorking(player)) {
                WorkManager.cancelWork(player, "被红舞鞋控制，工作中断");
            }
            return;
        }
        if (player.tickCount % 20 == 0) {
            EgoLoadoutService.ensureAiLoadout(player, AiEgoRepositoryData.get(player.server));
        }
        if (isPanicking(player)) {
            if (!runtime.panicking) {
                runtime.panicking = true;
                stopIdleMovement(runtime);
                runtime.navigator.stop();
            }
            if (isBerserkPanic(player)) tickBerserkCombat(runtime);
            else clearPanicCombat(runtime);
        } else {
            if (runtime.panicking) {
                clearPanicCombat(runtime);
                runtime.panicking = false;
                resumeOrderNavigation(runtime);
            }
            if (runtime.order == null) {
                tickAutonomousCombat(runtime);
                if (runtime.autonomousTarget == null) tickIdleMovement(runtime);
            }
            else processOrder(runtime);
        }
        if (player.tickCount % 100 == 0) saveRuntime(runtime);
    }

    private static boolean isPanicking(EmployeeServerPlayer player) {
        return MentalValueUtil.isPanic(player);
    }

    private static boolean isBerserkPanic(EmployeeServerPlayer player) {
        return player.getCapability(MentalValueProvider.MENTAL_VALUE)
                .map(mental -> MentalValueUtil.isPanic(mental)
                        && mental.getPanicType() == PanicType.BERSERK)
                .orElse(false);
    }

    private static void resumeOrderNavigation(RuntimeEmployee runtime) {
        if (runtime.order == null) return;
        if (runtime.order.type == EmployeeTaskType.MOVE && runtime.order.targetPos != null) {
            runtime.navigator.moveTo(runtime.order.targetPos, MOVE_ARRIVAL_DISTANCE);
            return;
        }
        Entity target = findEntity(runtime.player.server, runtime.order.targetEntity);
        if (target != null && target.level() == runtime.player.level()) {
            if (runtime.order.type == EmployeeTaskType.WORK || runtime.order.type == EmployeeTaskType.TOOL_USE) {
                runtime.navigator.moveNear(target.position(), Math.sqrt(7.0D));
            } else {
                runtime.navigator.moveTo(target.blockPosition());
            }
        }
    }

    private static void processOrder(RuntimeEmployee runtime) {
        EmployeeOrder order = runtime.order;
        if (order == null) return;
        switch (order.type) {
            case MOVE -> {
                runtime.navigator.tick();
                if (runtime.navigator.isComplete()) complete(runtime);
                else if (order.hasMoveTargetAlternative()
                        && runtime.navigator.hasConclusiveNoPath()) {
                    tryNextMoveTarget(runtime);
                }
                else if (runtime.navigator.isUnreachable() && !tryNextMoveTarget(runtime)) {
                    fail(runtime, "无法到达目标位置");
                }
            }
            case WORK -> tickWork(runtime, order);
            case TOOL_USE -> tickToolUse(runtime, order);
            case SEARCH_AND_SUPPRESS -> tickSuppress(runtime, order);
            case FOLLOW -> tickFollow(runtime, order);
            default -> complete(runtime);
        }
    }

    private static boolean tryNextMoveTarget(RuntimeEmployee runtime) {
        EmployeeOrder order = runtime.order;
        if (order == null || order.type != EmployeeTaskType.MOVE
                || !order.advanceMoveTarget()) return false;
        BlockPos target = order.targetPos;
        runtime.navigator.moveTo(target, MOVE_ARRIVAL_DISTANCE);
        EmployeeRecord record = runtime.player.employeeRecord();
        if (order.moveTargets.size() > 1) {
            record.setManualStation(runtime.player.serverLevel().dimension().location(), target);
        }
        record.setTask(EmployeeTaskType.MOVE, "移动至 " + target.toShortString());
        FacilityPersonnelData.get(runtime.player.server).changed();
        return true;
    }

    private static void tickWork(RuntimeEmployee runtime, EmployeeOrder order) {
        Entity entity = findEntity(runtime.player.server, order.targetEntity);
        if (!(entity instanceof AbstractAbnormality abnormality) || abnormality.isDeadOrDying()) {
            fail(runtime, "工作目标不可用");
            return;
        }
        // 员工只在设施内部活动：目标若不在同一维度，其坐标对本维度无意义
        //（会导致员工朝一个空地持续寻路、任务永不结束），直接判失败
        if (abnormality.level() != runtime.player.level()) {
            fail(runtime, "工作目标不在本设施");
            return;
        }
        if (order.started) {
            if (!WorkManager.isPlayerWorking(runtime.player)) finishMonitorWork(runtime, order);
            return;
        }
        if (runtime.player.distanceToSqr(abnormality) > 7.0D) {
            runtime.navigator.retargetNear(abnormality.position(), Math.sqrt(7.0D));
            runtime.navigator.tick();
            if (runtime.navigator.isUnreachable()) fail(runtime, "无法到达工作目标");
            return;
        }
        runtime.navigator.stop();
        if (WorkManager.startResolvedWork(
                runtime.player, abnormality, order.workType, order.resolvedWorkType)) {
            order.started = true;
        } else {
            fail(runtime, "无法开始工作");
        }
    }

    private static void tickToolUse(RuntimeEmployee runtime, EmployeeOrder order) {
        Entity entity = findEntity(runtime.player.server, order.targetEntity);
        if (!(entity instanceof AbstractAbnormality abnormality) || !abnormality.isAlive()
                || abnormality.isRemoved() || !abnormality.isToolType()
                || abnormality.level() != runtime.player.level()) {
            finishToolUse(runtime, order);
            return;
        }
        if (!order.started) {
            if (runtime.player.distanceToSqr(abnormality) > 7.0D) {
                runtime.navigator.retargetNear(abnormality.position(), Math.sqrt(7.0D));
                runtime.navigator.tick();
                if (runtime.navigator.isUnreachable()) fail(runtime, "Unable to reach tool");
                return;
            }
            runtime.navigator.stop();
            if (!abnormality.startUsing(runtime.player)) {
                fail(runtime, "Tool rejected employee");
                return;
            }
            order.started = true;
            if (!abnormality.isContinuousUseTool()) finishToolUse(runtime, order);
            return;
        }
        if (!abnormality.isContinuousUseTool()) finishToolUse(runtime, order);
    }

    private static void finishToolUse(RuntimeEmployee runtime, EmployeeOrder order) {
        Entity entity = findEntity(runtime.player.server, order.targetEntity);
        if (order.started && entity instanceof com.wzz.lobotocraft.entity.base.IAbnormality tool) {
            tool.stopUsing(runtime.player);
        }
        complete(runtime);
    }

    private static void finishMonitorWork(RuntimeEmployee runtime, EmployeeOrder completedOrder) {
        EmployeeRecord record = runtime.player.employeeRecord();
        ResourceLocation stationDimension = record.manualStationDimension();
        BlockPos station = record.manualStationPos();
        if (!completedOrder.returnToManualStation || stationDimension == null || station == null
                || !runtime.player.serverLevel().dimension().location().equals(stationDimension)) {
            complete(runtime);
            return;
        }
        runtime.order = EmployeeOrder.move(station);
        runtime.navigator.moveTo(station, MOVE_ARRIVAL_DISTANCE);
        record.setTask(EmployeeTaskType.MOVE, "返回驻留点 " + station.toShortString());
        FacilityPersonnelData.get(runtime.player.server).changed();
    }

    private static void tickSuppress(RuntimeEmployee runtime, EmployeeOrder order) {
        Entity entity = findEntity(runtime.player.server, order.targetEntity);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            complete(runtime);
            return;
        }
        // 同上：跨维度目标的坐标不可用于本维度寻路，视为镇压结束
        if (target.level() != runtime.player.level()) {
            complete(runtime);
            return;
        }
        if (!selectAssignedEgoWeapon(runtime.player)) {
            fail(runtime, "未分配可用且满足等级要求的 E.G.O 武器");
            return;
        }
        RangedProfile ranged = rangedProfile(runtime.player.getMainHandItem());
        prepareWeaponForCombat(runtime.player);
        if (trySpecialSuppress(runtime, target)) return;
        if (ranged != null) tickRangedSuppress(runtime, target, ranged);
        else tickMeleeSuppress(runtime, target);
    }

    private static void tickFollow(RuntimeEmployee runtime, EmployeeOrder order) {
        Entity target = findEntity(runtime.player.server, order.targetEntity);
        if (target == null || target.isRemoved()) {
            fail(runtime, "跟随目标不可用");
            return;
        }
        if (target.level() != runtime.player.level()) {
            fail(runtime, "跟随目标不在本设施");
            return;
        }

        double distance = runtime.player.distanceTo(target);
        if (distance <= 3.0D) {
            runtime.navigator.stop();
            runtime.player.faceTarget(target.getEyePosition());
            return;
        }
        runtime.navigator.retargetTo(target.blockPosition());
        runtime.navigator.tick();
        if (runtime.navigator.isUnreachable()) fail(runtime, "无法接近跟随目标");
    }

    private static void tickMeleeSuppress(RuntimeEmployee runtime, LivingEntity target) {
        double distance = runtime.player.distanceTo(target);
        double reach = Math.max(1.5D,
                runtime.player.getAttributeValue(ForgeMod.ENTITY_REACH.get()) - 0.35D);
        if (distance > reach || !runtime.player.hasLineOfSight(target)) {
            navigateToCombatRange(runtime, target, reach);
            return;
        }

        runtime.navigator.stop();
        runtime.player.faceTarget(target.getEyePosition());
        // BaseEgoWeapon requires a completely charged hit. Calling ServerPlayer.attack()
        // directly bypasses Forge's item left-click hook and turns E.G.O. hits into generic
        // physical/red damage, so reproduce that hook before falling back to vanilla melee.
        if (runtime.player.getAttackStrengthScale(0.0F) < 1.0F) return;
        ItemStack stack = runtime.player.getMainHandItem();
        boolean handledByItem = stack.getItem().onLeftClickEntity(stack, runtime.player, target);
        if (EgoAttackOwnershipPolicy.shouldUseVanillaFallback(
                handledByItem, stack.getItem() instanceof BaseEgoWeapon)) {
            runtime.player.attack(target);
        }
        runtime.player.swing(InteractionHand.MAIN_HAND);
        runtime.player.resetAttackStrengthTicker();
    }

    /**
     * Uses an AI-enabled right-click ability as soon as its item-owned cooldown is ready.
     * Returning false leaves the weapon on its normal melee/ranged combat path.
     */
    private static boolean trySpecialSuppress(RuntimeEmployee runtime, LivingEntity target) {
        EmployeeServerPlayer player = runtime.player;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AiSpecialEgoWeapon specialWeapon)
                || !specialWeapon.isAiSpecialAttackReady(player, stack)) {
            return false;
        }

        double attackRange = Math.max(1.0D, specialWeapon.aiSpecialAttackRange());
        boolean needsLineOfSight = specialWeapon.aiSpecialRequiresLineOfSight();
        if (player.distanceTo(target) > attackRange
                || needsLineOfSight && !player.hasLineOfSight(target)) {
            navigateToCombatRange(runtime, target, attackRange);
            return true;
        }

        runtime.navigator.stop();
        player.faceTarget(target.getEyePosition());
        useRangedHand(player, stack, InteractionHand.MAIN_HAND,
                specialWeapon.aiSpecialRequiresCrouch());
        return true;
    }

    private static void tickRangedSuppress(RuntimeEmployee runtime, LivingEntity target, RangedProfile profile) {
        EmployeeServerPlayer player = runtime.player;
        double distance = player.distanceTo(target);
        RangedCombatPolicy.State state = RangedCombatPolicy.state(
                distance, player.hasLineOfSight(target), profile.positioning);

        if (state == RangedCombatPolicy.State.HOLD) {
            runtime.navigator.stop();
        } else {
            runtime.navigator.retargetCombat(target,
                    profile.positioning.minimumSpacing(),
                    profile.positioning.preferredSpacing(),
                    profile.positioning.maximumSpacing());
            runtime.navigator.tick();
        }

        if (runtime.navigator.isUnreachable()) {
            failCombat(runtime, "无法进入武器射程");
            return;
        }
        if (state != RangedCombatPolicy.State.HOLD) return;

        player.faceTarget(target.getEyePosition());
        ItemStack stack = player.getMainHandItem();
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;
        InteractionResult result = useAiRangedHand(player, stack, InteractionHand.MAIN_HAND,
                profile.requiresCrouch, target, (AiRangedEgoWeapon) stack.getItem());
        ItemStack offhand = player.getOffhandItem();
        if (result == InteractionResult.PASS && !offhand.isEmpty()
                && offhand.getItem() == stack.getItem()
                && offhand.getItem() instanceof AiRangedEgoWeapon offhandWeapon) {
            useAiRangedHand(player, offhand, InteractionHand.OFF_HAND,
                    offhandWeapon.aiRequiresCrouch(), target, offhandWeapon);
        }
    }

    private static InteractionResult useAiRangedHand(EmployeeServerPlayer player, ItemStack stack,
                                                      InteractionHand hand, boolean crouching,
                                                      LivingEntity target,
                                                      AiRangedEgoWeapon weapon) {
        boolean previousCrouch = player.isShiftKeyDown();
        player.setShiftKeyDown(crouching);
        try {
            return weapon.useForAi(player, stack, hand, target);
        } finally {
            player.setShiftKeyDown(previousCrouch);
        }
    }

    private static InteractionResult useRangedHand(EmployeeServerPlayer player, ItemStack stack,
                                                    InteractionHand hand, boolean crouching) {
        boolean previousCrouch = player.isShiftKeyDown();
        player.setShiftKeyDown(crouching);
        try {
            return stack.getItem().use(player.level(), player, hand).getResult();
        } finally {
            player.setShiftKeyDown(previousCrouch);
        }
    }

    private static void prepareWeaponForCombat(EmployeeServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof AiCombatModeEgoWeapon combatMode) {
            combatMode.prepareAiCombat(player, stack);
        }
    }

    private static void navigateToMovingTarget(RuntimeEmployee runtime, Entity target) {
        runtime.navigator.retargetTo(target.blockPosition());
        runtime.navigator.tick();
        if (runtime.navigator.isUnreachable()) failCombat(runtime, "无法接近镇压目标");
    }

    private static void navigateToCombatRange(RuntimeEmployee runtime, LivingEntity target,
                                               double attackRange) {
        runtime.navigator.retargetCombat(target, attackRange);
        runtime.navigator.tick();
        if (runtime.navigator.isUnreachable()) failCombat(runtime, "无法接近镇压目标");
    }

    private static void tickBerserkCombat(RuntimeEmployee runtime) {
        EmployeeServerPlayer player = runtime.player;
        long now = player.serverLevel().getGameTime();
        UUID previousTarget = runtime.panicTarget;
        LivingEntity target = panicTarget(runtime);
        if (target == null) {
            runtime.panicTarget = null;
            if (previousTarget != null) {
                runtime.navigator.stop();
                runtime.nextPanicTargetScanAt = 0L;
            }
            if (now < runtime.nextPanicTargetScanAt) return;
            runtime.nextPanicTargetScanAt = now + COMBAT_TARGET_SCAN_INTERVAL;
            target = nearestPanicTarget(runtime, now);
            runtime.panicTarget = target == null ? null : target.getUUID();
        }
        if (target == null) {
            runtime.navigator.stop();
            return;
        }

        // A panicking employee still uses an assigned E.G.O when possible, but lack of
        // a valid assignment must not suppress berserk melee entirely.
        selectAssignedEgoWeapon(player);
        RangedProfile ranged = rangedProfile(player.getMainHandItem());
        prepareWeaponForCombat(player);
        if (trySpecialSuppress(runtime, target)) return;
        if (ranged != null) tickRangedSuppress(runtime, target, ranged);
        else tickMeleeSuppress(runtime, target);
    }

    private static LivingEntity panicTarget(RuntimeEmployee runtime) {
        if (runtime.panicTarget == null) return null;
        Entity entity = findEntity(runtime.player.server, runtime.panicTarget);
        return entity instanceof LivingEntity living && isBerserkTarget(runtime.player, living)
                ? living : null;
    }

    private static LivingEntity nearestPanicTarget(RuntimeEmployee runtime, long now) {
        runtime.panicRejectedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        EmployeeServerPlayer player = runtime.player;
        Map<UUID, LivingEntity> candidates = new HashMap<>();
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(64.0D),
                target -> isBerserkTarget(player, target))) {
            candidates.put(entity.getUUID(), entity);
        }
        for (FacilityAreaEntry area : currentFacilityAreas(player)) {
            AABB bounds = new AABB(area.minPos, area.maxPos.offset(1, 1, 1));
            for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(
                    LivingEntity.class, bounds, target -> isBerserkTarget(player, target))) {
                candidates.put(entity.getUUID(), entity);
            }
        }
        return candidates.values().stream()
                .filter(target -> !runtime.panicRejectedUntil.containsKey(target.getUUID()))
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private static boolean isBerserkTarget(EmployeeServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive() || target.isRemoved()
                || target.level() != player.level()) return false;
        if (target instanceof Player targetPlayer
                && (targetPlayer.isCreative() || targetPlayer.isSpectator())) return false;
        return !(target instanceof AbstractAbnormality abnormality) || abnormality.hasEscape();
    }

    private static void tickAutonomousCombat(RuntimeEmployee runtime) {
        EmployeeServerPlayer player = runtime.player;
        List<FacilityAreaEntry> facilityAreas = currentFacilityAreas(player);
        String groupId = FacilityAreaGroup.groupAt(facilityAreas, player.blockPosition());
        List<FacilityAreaEntry> groupAreas = FacilityAreaGroup.members(facilityAreas, groupId);
        if (groupAreas.isEmpty()) {
            clearAutonomousCombat(runtime);
            return;
        }
        // Prefer assigned E.G.O, but an unarmed employee still defends against hostile mobs.
        selectAssignedEgoWeapon(player);

        LivingEntity target = autonomousTarget(runtime, groupId, groupAreas);
        long now = player.serverLevel().getGameTime();
        if (target == null && now >= runtime.nextAutonomousTargetScanAt) {
            runtime.nextAutonomousTargetScanAt = now + COMBAT_TARGET_SCAN_INTERVAL;
            target = nearestThreat(player, groupId, groupAreas);
            runtime.autonomousTarget = target == null ? null : target.getUUID();
            runtime.autonomousArea = target == null ? "" : groupId;
            if (target != null) {
                stopIdleMovement(runtime);
                player.employeeRecord().setTask(EmployeeTaskType.SEARCH_AND_SUPPRESS,
                        "区域内镇压 " + target.getName().getString());
                FacilityPersonnelData.get(player.server).changed();
            }
        }
        if (target == null) {
            clearAutonomousCombat(runtime);
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        RangedProfile ranged = rangedProfile(player.getMainHandItem());
        prepareWeaponForCombat(player);
        if (trySpecialSuppress(runtime, target)) {
            // The special path owns movement and attack timing while it is ready.
        } else if (ranged != null) tickRangedSuppress(runtime, target, ranged);
        else tickMeleeSuppress(runtime, target);
        if (!FacilityAreaGroup.contains(groupAreas, groupId, player.blockPosition())) {
            runtime.navigator.stop();
            player.absMoveTo(x, y, z, yaw, pitch);
            clearAutonomousCombat(runtime);
        }
    }

    private static void tickIdleMovement(RuntimeEmployee runtime) {
        EmployeeServerPlayer player = runtime.player;
        long now = player.serverLevel().getGameTime();
        if (runtime.idleMoving) {
            runtime.navigator.tick();
            if (!runtime.navigator.isComplete() && !runtime.navigator.isUnreachable()) return;
            runtime.navigator.stop();
            runtime.idleMoving = false;
            scheduleNextIdleMove(runtime, now);
            return;
        }
        if (now < runtime.nextIdleMoveAt) return;

        List<FacilityAreaEntry> facilityAreas = currentFacilityAreas(player);
        String groupId = FacilityAreaGroup.groupAt(facilityAreas, player.blockPosition());
        if (groupId.isBlank()) {
            scheduleNextIdleMove(runtime, now);
            return;
        }
        List<FacilityWorldLayout.AreaDefinition> targetAreas =
                FacilityWorldLayout.targetAreas(player.serverLevel(), groupId);
        Optional<BlockPos> target = FacilityMoveTargetSampler.sample(targetAreas, player.getRandom(),
                IDLE_WANDER_ATTEMPTS, candidate -> candidate.distToCenterSqr(player.position()) >= 9.0D
                        && FacilityAreaMoveService.isNavigableTarget(
                        player.serverLevel(), player, candidate));
        if (target.isEmpty()) {
            scheduleNextIdleMove(runtime, now);
            return;
        }
        runtime.idleMoving = true;
        runtime.navigator.moveTo(target.get(), MOVE_ARRIVAL_DISTANCE);
    }

    private static void stopIdleMovement(RuntimeEmployee runtime) {
        if (!runtime.idleMoving) return;
        runtime.idleMoving = false;
        runtime.navigator.stop();
    }

    private static void scheduleNextIdleMove(RuntimeEmployee runtime, long now) {
        runtime.nextIdleMoveAt = now + idleWanderDelay(runtime.player.getRandom());
    }

    static int idleWanderDelay(net.minecraft.util.RandomSource random) {
        return IDLE_WANDER_MIN_DELAY + random.nextInt(IDLE_WANDER_DELAY_RANGE);
    }

    private static LivingEntity autonomousTarget(RuntimeEmployee runtime, String groupId,
                                                   List<FacilityAreaEntry> groupAreas) {
        if (runtime.autonomousTarget == null || !groupId.equals(runtime.autonomousArea)) return null;
        Entity entity = findEntity(runtime.player.server, runtime.autonomousTarget);
        if (!(entity instanceof LivingEntity living) || !isThreatFor(runtime.player, living)
                || living.level() != runtime.player.level()
                || !FacilityAreaGroup.contains(groupAreas, groupId, living.blockPosition())) return null;
        return living;
    }

    private static LivingEntity nearestThreat(EmployeeServerPlayer player, String groupId,
                                               List<FacilityAreaEntry> groupAreas) {
        Map<UUID, LivingEntity> threats = new HashMap<>();
        for (FacilityAreaEntry area : groupAreas) {
            AABB bounds = new AABB(area.minPos, area.maxPos.offset(1, 1, 1));
            for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(
                    LivingEntity.class, bounds, target -> isThreatFor(player, target))) {
                if (FacilityAreaGroup.contains(groupAreas, groupId, entity.blockPosition())) {
                    threats.put(entity.getUUID(), entity);
                }
            }
        }
        return threats.values().stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static boolean isThreatFor(EmployeeServerPlayer observer, LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved() || entity instanceof EntityPunishingBird) return false;
        if (entity instanceof EmployeeServerPlayer
                && EntityRedShoes.isRedShoesControlled(entity)) return true;
        if (entity instanceof EmployeeServerPlayer employee && MentalValueUtil.isPanic(employee)) {
            PanicType panicType = employee.getCapability(MentalValueProvider.MENTAL_VALUE)
                    .map(mental -> mental.getPanicType()).orElse(PanicType.NONE);
            return shouldCounterPanickingEmployee(panicType, assignedWeaponDamageType(observer));
        }
        if (isEndBirdEgg(entity)) return true;
        if (entity instanceof AbstractAbnormality abnormality) return abnormality.hasEscape();
        return FacilityCombatRules.isOrdealCombatant(entity)
                || entity instanceof Enemy
                || entity.getType().getCategory() == MobCategory.MONSTER;
    }

    private static boolean isEndBirdEgg(LivingEntity entity) {
        return entity instanceof EntityEndBirdEggSmall
                || entity instanceof EntityEndBirdEggHigh
                || entity instanceof EntityEndBirdEggEye;
    }

    static boolean shouldCounterPanickingEmployee(PanicType panicType, String weaponDamageType) {
        if (panicType == PanicType.BERSERK) return true;
        return panicType == PanicType.PRUDENCE && "WHITE".equalsIgnoreCase(weaponDamageType);
    }

    private static String assignedWeaponDamageType(EmployeeServerPlayer player) {
        String code = AiEgoRepositoryData.get(player.server).assignedCode(player.getUUID(), "weapon");
        EgoWeaponDisplayCatalog.WeaponSummary summary = EgoWeaponDisplayCatalog.find(code);
        return summary == null ? "" : summary.damageType();
    }

    private static List<FacilityAreaEntry> currentFacilityAreas(EmployeeServerPlayer player) {
        ResourceLocation dimension = player.serverLevel().dimension().location();
        return FacilityWorldLayout.mergeAreas(dimension,
                CompanyRoomData.get(player.serverLevel()).getAreas(dimension));
    }

    private static boolean selectAssignedEgoWeapon(EmployeeServerPlayer player) {
        return EgoLoadoutService.selectUsableAssignedAiWeapon(
                player, AiEgoRepositoryData.get(player.server));
    }

    private static void failCombat(RuntimeEmployee runtime, String reason) {
        if (runtime.panicking) {
            rejectPanicTarget(runtime);
            return;
        }
        if (runtime.order != null) fail(runtime, reason);
        else clearAutonomousCombat(runtime);
    }

    private static void rejectPanicTarget(RuntimeEmployee runtime) {
        if (runtime.panicTarget != null) {
            runtime.panicRejectedUntil.put(runtime.panicTarget,
                    runtime.player.serverLevel().getGameTime() + PANIC_UNREACHABLE_RETRY_TICKS);
        }
        runtime.panicTarget = null;
        runtime.nextPanicTargetScanAt = runtime.player.serverLevel().getGameTime() + 1L;
        runtime.navigator.stop();
    }

    private static void clearPanicCombat(RuntimeEmployee runtime) {
        if (runtime.panicTarget == null && runtime.panicRejectedUntil.isEmpty()) return;
        runtime.navigator.stop();
        runtime.panicTarget = null;
        runtime.panicRejectedUntil.clear();
        runtime.nextPanicTargetScanAt = 0L;
    }

    private static void clearAutonomousCombat(RuntimeEmployee runtime) {
        boolean hadCombatState = runtime.autonomousTarget != null
                || !runtime.autonomousArea.isBlank();
        if (hadCombatState) runtime.navigator.stop();
        runtime.autonomousTarget = null;
        runtime.autonomousArea = "";
        if (shouldResetAutonomousSuppressionTask(
                runtime.order != null, runtime.player.employeeRecord().taskType())) {
            runtime.player.employeeRecord().setTask(EmployeeTaskType.IDLE, "");
            FacilityPersonnelData.get(runtime.player.server).changed();
        }
    }

    static boolean shouldResetAutonomousSuppressionTask(boolean hasOrder,
                                                        EmployeeTaskType taskType) {
        return !hasOrder && taskType == EmployeeTaskType.SEARCH_AND_SUPPRESS;
    }

    /** Only weapons explicitly declaring an AI-safe normal ranged attack enter this path. */
    private static RangedProfile rangedProfile(ItemStack stack) {
        if (!(stack.getItem() instanceof AiRangedEgoWeapon weapon)) return null;
        return new RangedProfile(RangedCombatPolicy.normalize(
                weapon.aiAttackRange(), weapon.aiMinSpacing(),
                weapon.aiPreferredSpacing(), weapon.aiMaxSpacing()),
                weapon.aiRequiresCrouch());
    }

    private record RangedProfile(RangedCombatPolicy.Profile positioning,
                                 boolean requiresCrouch) {
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        if (uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static void complete(RuntimeEmployee runtime) {
        runtime.navigator.stop();
        runtime.idleMoving = false;
        runtime.order = null;
        clearAutonomousCombat(runtime);
        runtime.player.employeeRecord().setTask(EmployeeTaskType.IDLE, "");
        scheduleNextIdleMove(runtime, runtime.player.serverLevel().getGameTime());
        FacilityPersonnelData.get(runtime.player.server).changed();
    }

    private static void fail(RuntimeEmployee runtime, String reason) {
        runtime.navigator.stop();
        runtime.idleMoving = false;
        runtime.order = null;
        clearAutonomousCombat(runtime);
        runtime.player.employeeRecord().setTask(EmployeeTaskType.IDLE, reason);
        scheduleNextIdleMove(runtime, runtime.player.serverLevel().getGameTime());
        FacilityPersonnelData.get(runtime.player.server).changed();
    }

    private static void saveRuntime(RuntimeEmployee runtime) {
        EmployeeServerPlayer player = runtime.player;
        EmployeeRecord record = player.employeeRecord();
        record.setPosition(player.serverLevel().dimension().location(), player.getX(), player.getY(), player.getZ());
        CompoundTag playerData = new CompoundTag();
        player.saveWithoutId(playerData);
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            if (stats instanceof EmployeeStats values) {
                record.updateRuntimeData(playerData, values.getBaseFortitude(), values.getBasePrudence(),
                        values.getBaseTemperance(), values.getBaseJustice());
            } else {
                record.updateRuntimeData(playerData, stats.getFortitude(), stats.getPrudence(),
                        stats.getTemperance(), stats.getJustice());
            }
        });
        FacilityPersonnelData.get(player.server).changed();
    }

    public static boolean persistNow(UUID employeeId) {
        RuntimeEmployee runtime = ACTIVE.get(employeeId);
        if (runtime == null || runtime.player.isRemoved()) return false;
        saveRuntime(runtime);
        return true;
    }

    public static void sendPlayerInfo(ServerPlayer viewer) {
        if (ACTIVE.isEmpty() || viewer.isRemoved()) return;
        List<ServerPlayer> players = ACTIVE.values().stream()
                .map(runtime -> (ServerPlayer) runtime.player)
                .filter(player -> player.isAlive() && !player.isRemoved())
                .toList();
        if (!players.isEmpty()) viewer.connection.send(createAiPlayerInfoPacket(players));
    }

    /**
     * Synthetic employees never own a signed chat session. Including INITIALIZE_CHAT makes
     * reconnects decode an unnecessary public-key field and turns any stale profile state into
     * a fatal vanilla packet error before the player can enter the world.
     */
    private static ClientboundPlayerInfoUpdatePacket createAiPlayerInfoPacket(
            Collection<ServerPlayer> players) {
        return new ClientboundPlayerInfoUpdatePacket(aiPlayerInfoActions(), players);
    }

    static EnumSet<ClientboundPlayerInfoUpdatePacket.Action> aiPlayerInfoActions() {
        return EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
        );
    }

    private static void retireDead(RuntimeEmployee runtime) {
        retireDead(runtime, false);
    }

    private static void retireDead(RuntimeEmployee runtime, boolean deferDamageHistoryClear) {
        EmployeeServerPlayer player = runtime.player;
        EmployeeRecord record = player.employeeRecord();
        MinecraftServer server = player.server;
        if (runtime.order != null && runtime.order.type == EmployeeTaskType.TOOL_USE
                && runtime.order.started) {
            Entity target = findEntity(server, runtime.order.targetEntity);
            if (target instanceof com.wzz.lobotocraft.entity.base.IAbnormality tool) {
                tool.stopUsing(player);
            }
        }
        if (!ACTIVE.remove(record.id(), runtime)) return;

        // All terminal paths converge here. Removing ACTIVE first makes item recovery idempotent.
        // Recover ordinary items and gifts, then clear assigned projections before vanilla loot.
        EmployeeWarehouseService.recoverOnDeath(player);
        EgoLoadoutService.clearAiProjection(player, "weapon");
        EgoLoadoutService.clearAiProjection(player, "armor");

        AiEgoRepositoryData repository = AiEgoRepositoryData.get(server);
        repository.release(record.id(), "weapon");
        repository.release(record.id(), "armor");
        // 问题2：死亡不可逆，立即把该员工标记为退休墓碑并强制落盘。
        // 不真正删记录（保留墓碑，重启时靠 retired 标志识别为已死），
        // 强制写盘避免死亡后崩溃/强杀（未触发 ServerStopping）时磁盘上仍为 deployed=true → 复活。
        FacilityPersonnelData data = FacilityPersonnelData.get(server);
        data.retire(record.id());
        com.wzz.lobotocraft.llm.LlmHistoryData.get(server).removeEmployee(record.id());
        server.overworld().getDataStorage().save();
        if (!deferDamageHistoryClear) com.lobotocraft.facility.damage.DamageHistory.clear(record.id());
        com.lobotocraft.facility.player.PlayerStatusEvents.forget(record.id());

        // 问题1：LivingDeathEvent 在 LivingEntity.die() 前段触发，此刻同步移除实体会打断
        // die() 后续的死亡消息广播、dropAllDeathLoot、经验掉落等逻辑。改为延后到下一 tick，
        // 让 die() 先完整跑完再移除实体。
        server.execute(() -> {
            if (!player.isRemoved()) {
                player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.KILLED);
            }
            ClientboundPlayerInfoRemovePacket remove = new ClientboundPlayerInfoRemovePacket(List.of(record.id()));
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) viewer.connection.send(remove);
        });
    }

    private static boolean wasPersistedDead(EmployeeRecord record) {
        // 读显式的退休墓碑标志，而非从 Health/DeathTime 推断（推断在崩溃场景下不可靠）。
        return record.retired();
    }

    private static String profileName(EmployeeRecord record) {
        String clean = record.name().replaceAll("[^A-Za-z0-9_]", "_");
        if (clean.isBlank()) clean = "employee";
        clean = clean.substring(0, Math.min(8, clean.length()));
        String value = "AI_" + clean + "_" + record.id().toString().substring(0, 4);
        return value.substring(0, Math.min(16, value.length()));
    }

    private static final class RuntimeEmployee {
        private final EmployeeServerPlayer player;
        private final EmployeeNavigator navigator;
        private EmployeeOrder order;
        private UUID autonomousTarget;
        private String autonomousArea = "";
        private long nextAutonomousTargetScanAt;
        private UUID panicTarget;
        private final Map<UUID, Long> panicRejectedUntil = new HashMap<>();
        private long nextPanicTargetScanAt;
        /** 上一 tick 是否处于恐慌状态，用于暂停并恢复正常订单。 */
        private boolean panicking;
        private boolean idleMoving;
        private long nextIdleMoveAt;

        private RuntimeEmployee(EmployeeServerPlayer player, EmployeeNavigator navigator) {
            this.player = player;
            this.navigator = navigator;
            scheduleNextIdleMove(this, player.serverLevel().getGameTime());
        }
    }
}
