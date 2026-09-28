package com.wzz.lobotocraft.work;

import com.lobotocraft.facility.FacilityPauseState;
import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.employee.EmployeeNavigator;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.entity.EntityClerk;
import com.wzz.lobotocraft.entity.abnormality.EntityCensored;
import com.wzz.lobotocraft.entity.abnormality.EntityArmyInBlack;
import com.wzz.lobotocraft.entity.abnormality.EntityRedHoodMercenary;
import com.wzz.lobotocraft.entity.abnormality.EntitySnowQueen;
import com.wzz.lobotocraft.entity.base.AbstractAbnormality;
import com.wzz.lobotocraft.entity.control.HostileControlState;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.UniqueWorkStatePacket;
import com.wzz.lobotocraft.util.MentalValueUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Owns allocation, movement, settlement and cleanup for a unique command.
 * Neither ordinary work sessions nor client-selected employees own this lifecycle.
 */
@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class UniqueWorkRequest {
    public enum Stage { DRAWING, WALKING, SACRIFICING, SUCCEEDED, FAILED }
    private static final String CONTROL_TAG = "lobotocraft_unique_sacrifice";
    private static final Map<UUID, UniqueWorkRequest> ACTIVE = new HashMap<>();
    private static final int DRAW_TICKS = 60;
    private static final int WALK_TIMEOUT = 2400;
    private static final int SACRIFICE_TICKS = 91;
    private final AbstractAbnormality source;
    private final AbnormalityCommandPolicy.Action action;
    private final LivingEntity victim;
    private final UUID operator;
    private final int day;
    private final List<String> drawNames;
    private Stage stage;
    private int ticks;
    private EmployeeNavigator employeeNavigator;

    private UniqueWorkRequest(ServerPlayer operator, AbstractAbnormality source,
                              LivingEntity victim, List<String> drawNames,
                              AbnormalityCommandPolicy.Action action) {
        this.source = source;
        this.victim = victim;
        this.operator = operator.getUUID();
        this.day = FacilityDayData.get(operator.server.overworld()).getDay();
        this.drawNames = drawNames;
        this.action = action;
        this.stage = action == AbnormalityCommandPolicy.Action.SACRIFICE ? Stage.DRAWING : Stage.WALKING;
    }

    public static boolean isControlled(LivingEntity entity) {
        return entity != null && ACTIVE.values().stream().anyMatch(request -> request.victim == entity);
    }

    public static boolean isBusy(AbstractAbnormality source) {
        return source != null && ACTIVE.containsKey(source.getUUID());
    }

    /** Hard type boundary: even ordinary/fake ServerPlayers are not lottery candidates. */
    public static boolean isCandidate(LivingEntity entity) {
        if (!(entity instanceof EmployeeServerPlayer) && !(entity instanceof EntityClerk)) return false;
        if (!entity.isAlive() || entity.isRemoved() || entity.isInvulnerable()
                || isControlled(entity) || HostileControlState.isActive(entity)
                || com.wzz.lobotocraft.util.MovementLockUtil.isLocked(entity)) return false;
        if (entity instanceof EmployeeServerPlayer employee) {
            return !employee.isSpectator() && !employee.isCreative()
                    && !com.wzz.lobotocraft.event.listener.PlayerControlLock.isLocked(employee)
                    && !EntitySnowQueen.isFrozen(employee)
                    && !MentalValueUtil.isPanic(employee)
                    && !WorkManager.isPlayerWorking(employee)
                    && EmployeeRuntimeManager.canAllocateUniqueWork(employee);
        }
        return true;
    }

    /** Returns a translation key on rejection, null after one authoritative draw. */
    public static String start(ServerPlayer operator, EntityCensored source) {
        if (operator == null || source == null || operator.level() != source.level()
                || operator.level().dimension() != ModDimensions.LOBOTO_KEY
                || !source.isAlive() || source.hasEscape() || !AbnormalityWorkRules.isRegistered(source)) {
            return "message.lobotocraft.sacrifice.unavailable";
        }
        if (FacilityPauseState.isPaused(operator.server) || isBusy(source)
                || WorkManager.isAbnormalityBeingWorked(source)
                || EmployeeRuntimeManager.isWorkTargetReserved(source, null)) {
            return "message.lobotocraft.sacrifice.busy";
        }
        Map<UUID, LivingEntity> pool = new HashMap<>();
        for (var entity : operator.serverLevel().getAllEntities()) {
            if (entity instanceof LivingEntity living && isCandidate(living)) {
                pool.put(living.getUUID(), living);
            }
        }
        for (var employee : EmployeeRuntimeManager.activePlayers()) {
            if (employee.level() == source.level() && isCandidate(employee)) pool.put(employee.getUUID(), employee);
        }
        if (pool.isEmpty()) return "message.lobotocraft.sacrifice.empty";
        List<LivingEntity> candidates = new ArrayList<>(pool.values());
        candidates.sort(java.util.Comparator.comparing(LivingEntity::getUUID));
        LivingEntity victim = candidates.get(source.getRandom().nextInt(candidates.size()));
        if (victim instanceof EmployeeServerPlayer employee
                && !EmployeeRuntimeManager.cancelOrder(employee.employeeRecord(), "CENSORED 献祭")) {
            return "message.lobotocraft.sacrifice.busy";
        }
        List<String> names = new ArrayList<>(candidates.stream().limit(63)
                .map(entity -> entity.getName().getString()).toList());
        if (!names.contains(victim.getName().getString())) names.add(victim.getName().getString());
        UniqueWorkRequest request = new UniqueWorkRequest(operator, source, victim, List.copyOf(names),
                AbnormalityCommandPolicy.Action.SACRIFICE);
        ACTIVE.put(source.getUUID(), request);
        // All admitted candidates were non-invulnerable. Persist the ownership tag
        // so a server restart can restore that state before admitting new commands.
        victim.getPersistentData().putBoolean(CONTROL_TAG, true);
        victim.setInvulnerable(true);
        source.setUniqueWorkBusy(true);
        request.stopVictim();
        request.publish();
        return null;
    }

    private static String commandDenial(ServerPlayer operator, AbstractAbnormality source) {
        if (operator == null || source == null || operator.level() != source.level()
                || operator.level().dimension() != ModDimensions.LOBOTO_KEY
                || !source.isAlive() || source.isRemoved() || source.hasEscape()
                || !AbnormalityWorkRules.isRegistered(source)) return "message.lobotocraft.unique_work.unavailable";
        if (FacilityPauseState.isPaused(operator.server) || isBusy(source)
                || WorkManager.isAbnormalityBeingWorked(source)
                || EmployeeRuntimeManager.isWorkTargetReserved(source, null)) {
            return "message.lobotocraft.sacrifice.busy";
        }
        return null;
    }

    public static String startProtection(ServerPlayer operator, EntityArmyInBlack army, ServerPlayer worker) {
        String denial = commandDenial(operator, army);
        if (denial != null) return denial;
        if (!army.canAcceptProtection() || worker == null || worker.level() != army.level()
                || !worker.isAlive() || worker.isRemoved() || WorkManager.isPlayerWorking(worker)
                || com.wzz.lobotocraft.util.MovementLockUtil.isLocked(worker)
                || com.wzz.lobotocraft.event.listener.PlayerControlLock.isLocked(worker)
                || AbnormalityWorkRules.controlStateDenial(worker) != null || MentalValueUtil.isPanic(worker)) {
            return "message.lobotocraft.unique_work.unavailable";
        }
        if (!(worker instanceof EmployeeServerPlayer employee)) {
            if (worker != operator) return "message.lobotocraft.unique_work.unavailable";
            return army.beginProtectionCommand(worker) ? null : "message.lobotocraft.unique_work.unavailable";
        }
        if (!isCandidate(employee)
                || !EmployeeRuntimeManager.cancelOrder(employee.employeeRecord(), "深黯保护")) {
            return "message.lobotocraft.sacrifice.busy";
        }
        UniqueWorkRequest request = new UniqueWorkRequest(operator, army, worker, List.of(),
                AbnormalityCommandPolicy.Action.PROTECTION);
        ACTIVE.put(army.getUUID(), request);
        worker.getPersistentData().putBoolean(CONTROL_TAG, true);
        worker.setInvulnerable(true);
        request.stopVictim();
        request.publish();
        return null;
    }

    public static String openCommission(ServerPlayer operator, EntityRedHoodMercenary hood) {
        String denial = commandDenial(operator, hood);
        if (denial != null) return denial;
        if (!hood.canAcceptCommission()) return "message.lobotocraft.unique_work.unavailable";
        var targets = new ArrayList<com.wzz.lobotocraft.network.packet.UniqueCommandTargetsPacket.Target>();
        for (var entity : operator.serverLevel().getAllEntities()) {
            if (entity instanceof LivingEntity target && isCommissionTarget(hood, target)) {
                String name = target instanceof AbstractAbnormality abnormality
                        ? abnormality.getAbnormalityCode() : target.getName().getString();
                targets.add(new com.wzz.lobotocraft.network.packet.UniqueCommandTargetsPacket.Target(
                        target.getUUID(), name));
                if (targets.size() >= 128) break;
            }
        }
        if (targets.isEmpty()) return "message.lobotocraft.commission.empty";
        MessageLoader.getLoader().sendToPlayer(operator,
                new com.wzz.lobotocraft.network.packet.UniqueCommandTargetsPacket(hood.getUUID(), targets));
        return null;
    }

    public static String commitCommission(ServerPlayer operator, EntityRedHoodMercenary hood, LivingEntity target) {
        String denial = commandDenial(operator, hood);
        if (denial != null) return denial;
        if (!hood.canAcceptCommission() || !isCommissionTarget(hood, target)) {
            return "message.lobotocraft.unique_work.unavailable";
        }
        net.minecraft.world.item.ItemStack marker = net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < operator.getInventory().getContainerSize(); i++) {
            var stack = operator.getInventory().getItem(i);
            if (stack.getItem() instanceof com.wzz.lobotocraft.item.TargetMarkerItem) {
                marker = stack;
                break;
            }
        }
        if (!operator.isCreative() && marker.isEmpty()) return "message.lobotocraft.commission.requires_marker";
        // The native commission owns pursuit, combat, failure and return-to-containment.
        // This command commits exactly once and does not create an ordinary work session.
        hood.startCommission(target);
        if (!hood.isCommissionedAgainst(target.getUUID())) return "message.lobotocraft.unique_work.unavailable";
        com.wzz.lobotocraft.item.TargetMarkerItem.markTarget(target);
        if (!operator.isCreative()) marker.shrink(1);
        return null;
    }

    private static boolean isCommissionTarget(EntityRedHoodMercenary source, LivingEntity target) {
        return target != null && target != source && target.level() == source.level()
                && target.isAlive() && !target.isRemoved()
                && target instanceof AbstractAbnormality abnormality && abnormality.hasEscape()
                && !(target instanceof EntityRedHoodMercenary)
                && !(target instanceof com.wzz.lobotocraft.entity.abnormality.EntityIsharmla);
    }

    /** Called instead of ordinary employee orders, using their existing A* navigator. */
    public static boolean tickEmployee(EmployeeServerPlayer employee, EmployeeNavigator navigator) {
        UniqueWorkRequest request = ACTIVE.values().stream()
                .filter(active -> active.victim == employee).findFirst().orElse(null);
        if (request == null) {
            recoverTransientState(employee);
            return false;
        }
        request.employeeNavigator = navigator;
        if (request.stage == Stage.WALKING) {
            navigator.moveNear(request.source.position(), 2.6D);
            navigator.tick();
            if (navigator.isUnreachable()) request.finish(false);
        } else {
            navigator.stop();
            employee.clearNavigationInput();
        }
        return true;
    }

    /** The clerk's highest-priority movement goal calls this, suppressing flee/wander goals. */
    public static void tickClerk(EntityClerk clerk) {
        UniqueWorkRequest request = ACTIVE.values().stream()
                .filter(active -> active.victim == clerk).findFirst().orElse(null);
        if (request == null) return;
        clerk.setTarget(null);
        if (request.stage == Stage.WALKING && !FacilityPauseState.isPaused(clerk.getServer())) {
            clerk.getLookControl().setLookAt(request.source);
            if (clerk.tickCount % 10 == 0 || clerk.getNavigation().isDone()) {
                clerk.getNavigation().moveTo(request.source, 1.1D);
            }
        } else {
            clerk.getNavigation().stop();
            clerk.setDeltaMovement(Vec3.ZERO);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var request : List.copyOf(ACTIVE.values())) {
            if (request.source.getServer() == event.getServer()) request.tick();
        }
    }

    private void tick() {
        MinecraftServer server = source.getServer();
        if (server == null || source.isRemoved() || !source.isAlive() || source.hasEscape()
                || victim.isRemoved() || !victim.isAlive() || victim.level() != source.level()
                || !AbnormalityWorkRules.isRegistered(source)) {
            finish(false);
            return;
        }
        if (FacilityDayData.get(server.overworld()).getDay() != day) {
            finish(stage == Stage.SACRIFICING);
            return;
        }
        if (FacilityPauseState.isPaused(server)) {
            stopVictim();
            return;
        }
        ticks++;
        if (stage == Stage.DRAWING && ticks >= DRAW_TICKS) changeStage(Stage.WALKING);
        else if (stage == Stage.WALKING) {
            if (victim.distanceToSqr(source) <= 2.6D * 2.6D
                    && Math.abs(victim.getY() - source.getY()) <= 1.0D
                    && victim.hasLineOfSight(source)) {
                stopVictim();
                if (action == AbnormalityCommandPolicy.Action.SACRIFICE) {
                    ((EntityCensored) source).beginUniqueSacrificeAnimation();
                    changeStage(Stage.SACRIFICING);
                } else {
                    finish(true);
                }
            } else if (ticks >= WALK_TIMEOUT) finish(false);
        } else if (stage == Stage.SACRIFICING && ticks >= SACRIFICE_TICKS) finish(true);
    }

    public static void endDay(MinecraftServer server) {
        for (var request : List.copyOf(ACTIVE.values())) {
            if (request.source.getServer() == server) request.finish(request.stage == Stage.SACRIFICING);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onDayCommitted(com.lobotocraft.facility.day.FacilityDayCommittedEvent event) {
        endDay(event.getServer());
    }

    private void changeStage(Stage next) {
        stage = next;
        ticks = 0;
        publish();
    }

    private void finish(boolean sacrifice) {
        if (!ACTIVE.remove(source.getUUID(), this)) return;
        stopVictim();
        victim.getPersistentData().remove(CONTROL_TAG);
        victim.setInvulnerable(false);
        if (source instanceof EntityCensored censored) {
            censored.setUniqueWorkBusy(false);
            if (!source.hasEscape()) source.setAnimation("idle");
        }
        boolean success = false;
        if (sacrifice && victim.isAlive() && source.isAlive() && !source.hasEscape()) {
            if (action == AbnormalityCommandPolicy.Action.PROTECTION
                    && source instanceof EntityArmyInBlack army && victim instanceof ServerPlayer player) {
                success = army.beginProtectionCommand(player);
            } else if (action == AbnormalityCommandPolicy.Action.SACRIFICE
                    && (victim instanceof EmployeeServerPlayer || victim instanceof EntityClerk)) {
                if (victim instanceof EntityClerk clerk) EntityClerk.markNoTombstone(clerk);
                victim.kill();
                success = !victim.isAlive();
                if (success) {
                    source.setQliphothCounter(Math.min(source.getMaxQliphothCounter(),
                            source.getQliphothCounter() + 1));
                    victim.setInvisible(true);
                }
            }
        }
        stage = success ? Stage.SUCCEEDED : Stage.FAILED;
        publish();
    }

    private void stopVictim() {
        if (employeeNavigator != null) employeeNavigator.stop();
        if (victim instanceof EmployeeServerPlayer employee) employee.clearNavigationInput();
        if (victim instanceof EntityClerk clerk) {
            clerk.setTarget(null);
            clerk.getNavigation().stop();
        }
        victim.setDeltaMovement(Vec3.ZERO);
    }

    private void publish() {
        MinecraftServer server = source.getServer();
        if (server == null) return;
        ServerPlayer viewer = server.getPlayerList().getPlayer(operator);
        if (action == AbnormalityCommandPolicy.Action.PROTECTION) {
            if (viewer != null) viewer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    stage == Stage.FAILED ? "message.lobotocraft.protection.failed"
                            : stage == Stage.SUCCEEDED ? "message.lobotocraft.abnormality.army_in_black.protection_started"
                            : "message.lobotocraft.protection.allocated"));
            return;
        }
        if (viewer != null) MessageLoader.getLoader().sendToPlayer(viewer,
                new UniqueWorkStatePacket(source.getUUID(), victim.getName().getString(),
                        stage, stage == Stage.DRAWING ? drawNames : List.of()));
    }

    /** Restores a saved victim when the runtime-only request did not survive a restart. */
    public static void recoverTransientState(LivingEntity entity) {
        if (entity.getPersistentData().getBoolean(CONTROL_TAG) && !isControlled(entity)) {
            entity.getPersistentData().remove(CONTROL_TAG);
            entity.setInvulnerable(false);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide
                && (entity instanceof EmployeeServerPlayer || entity instanceof EntityClerk)) {
            recoverTransientState(entity);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (var request : List.copyOf(ACTIVE.values())) {
            if (request.source.getServer() == event.getServer()) request.finish(false);
        }
    }
}
