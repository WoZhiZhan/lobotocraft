package com.lobotocraft.facility.player;

import com.lobotocraft.facility.network.FacilityNetwork;
import com.lobotocraft.facility.network.PlayerStatusPacket;
import com.wzz.lobotocraft.employee.EmployeeRuntimeManager;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.employee.EmployeeTaskType;
import com.wzz.lobotocraft.entity.abnormality.EntityRedShoes;
import com.wzz.lobotocraft.entity.abnormality.EntitySnowQueen;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.work.WorkManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class PlayerStatusEvents {
    public static final int IDLE = 0;
    public static final int COMBAT = 1;
    public static final int WORKING = 2;
    public static final int NAVIGATING = 3;
    /** 精神崩溃（恐慌）—— 优先级高于工作/寻路/战斗。 */
    public static final int PANIC = 4;
    /** 异想体控制状态优先于恐慌、工作、寻路和战斗。 */
    public static final int CONTROLLED = 5;
    private static final int COMBAT_WINDOW_TICKS = 100;
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final Map<UUID, Integer> LAST_DEALT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_RECEIVED = new HashMap<>();
    private static final Map<UUID, Integer> COMBAT_UNTIL = new HashMap<>();

    private PlayerStatusEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) return;
        ServerPlayer victim = event.getEntity() instanceof ServerPlayer player ? player : null;
        Entity sourceEntity = event.getSource().getEntity();
        ServerPlayer attacker = sourceEntity instanceof ServerPlayer player ? player : null;
        if (victim == null && attacker == null) return;

        MinecraftServer server = victim != null ? victim.server : attacker.server;
        int now = server.getTickCount();
        if (attacker != null && attacker != victim) {
            LAST_DEALT.put(attacker.getUUID(), now);
            refreshCombatIfQualified(attacker.getUUID(), now);
        }
        if (victim != null && attacker != victim) {
            LAST_RECEIVED.put(victim.getUUID(), now);
            refreshCombatIfQualified(victim.getUUID(), now);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
            || event.getServer().getTickCount() % SYNC_INTERVAL_TICKS != 0) return;
        sync(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_DEALT.remove(id);
        LAST_RECEIVED.remove(id);
        COMBAT_UNTIL.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_DEALT.clear();
        LAST_RECEIVED.clear();
        COMBAT_UNTIL.clear();
    }

    public static void forget(UUID playerId) {
        LAST_DEALT.remove(playerId);
        LAST_RECEIVED.remove(playerId);
        COMBAT_UNTIL.remove(playerId);
    }

    private static void refreshCombatIfQualified(UUID playerId, int now) {
        int dealt = LAST_DEALT.getOrDefault(playerId, Integer.MIN_VALUE / 2);
        int received = LAST_RECEIVED.getOrDefault(playerId, Integer.MIN_VALUE / 2);
        if (now - dealt <= COMBAT_WINDOW_TICKS && now - received <= COMBAT_WINDOW_TICKS) {
            COMBAT_UNTIL.put(playerId, now + COMBAT_WINDOW_TICKS);
        }
    }

    private static void sync(MinecraftServer server) {
        int now = server.getTickCount();
        Map<UUID, ServerPlayer> visiblePlayers = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            visiblePlayers.put(player.getUUID(), player);
        }
        for (ServerPlayer employee : EmployeeRuntimeManager.activePlayers()) {
            visiblePlayers.put(employee.getUUID(), employee);
        }

        List<PlayerStatusPacket.Entry> entries = new ArrayList<>();
        for (ServerPlayer player : visiblePlayers.values()) {
            int state = IDLE;
            Component target = Component.empty();
            if (isControlled(player)) {
                state = CONTROLLED;
            } else if (MentalValueUtil.getMentalValue(player) <= 0.0F
                    && MentalValueUtil.getEffectiveMaxMentalValue(player) > 0.0F) {
                state = PANIC;
            } else if (WorkManager.isPlayerWorking(player)
                    || player instanceof EmployeeServerPlayer employee
                    && employee.employeeRecord().taskType() == EmployeeTaskType.WORK
                    && EmployeeRuntimeManager.navigationPurpose(player.getUUID()).isEmpty()) {
                state = WORKING;
                IAbnormality abnormality = WorkManager.getWorkingAbnormality(player);
                if (abnormality instanceof Entity entity) {
                    target = Component.translatable(entity.getType().getDescriptionId());
                }
            } else if (player instanceof EmployeeServerPlayer) {
                String purpose = EmployeeRuntimeManager.navigationPurpose(player.getUUID());
                if (!purpose.isEmpty()) {
                    state = NAVIGATING;
                    target = EmployeeRuntimeManager.navigationPurposeComponent(player.getUUID());
                } else if (COMBAT_UNTIL.getOrDefault(player.getUUID(), 0) >= now) {
                    state = COMBAT;
                }
            } else if (COMBAT_UNTIL.getOrDefault(player.getUUID(), 0) >= now) {
                state = COMBAT;
            }
            entries.add(new PlayerStatusPacket.Entry(player.getUUID(), state, target,
                player.getHealth(), player.getMaxHealth(), MentalValueUtil.getMentalValue(player),
                MentalValueUtil.getEffectiveMaxMentalValue(player)));
        }
        PlayerStatusPacket packet = new PlayerStatusPacket(List.copyOf(entries));
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (recipient.isRemoved() || recipient.isChangingDimension()) continue;
            FacilityNetwork.send(recipient, packet);
        }
    }

    private static boolean isControlled(ServerPlayer player) {
        return EntitySnowQueen.isFrozen(player) || EntityRedShoes.isRedShoesControlled(player);
    }
}
