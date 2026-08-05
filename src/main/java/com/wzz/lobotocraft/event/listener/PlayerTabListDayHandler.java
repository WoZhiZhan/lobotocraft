package com.wzz.lobotocraft.event.listener;

import com.wzz.lobotocraft.ModMain;
import com.wzz.lobotocraft.client.PlayerDepartmentClientState;
import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.EmployeeServerPlayer;
import com.wzz.lobotocraft.employee.PlayerFacilityProfileData;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.PlayerDepartmentSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModMain.MODID)
public final class PlayerTabListDayHandler {
    private PlayerTabListDayHandler() {
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        EmployeeDepartment department = department(event.getEntity());
        if (department == EmployeeDepartment.UNASSIGNED) return;
        event.setDisplayname(event.getUsername().copy().withStyle(style ->
                style.withColor(TextColor.fromRgb(department.textColorRgb()))));
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EmployeeDepartment department = PlayerFacilityProfileData.get(player.server)
                .department(player.getUUID());
        Component name = Component.literal(player.getGameProfile().getName());
        if (department != EmployeeDepartment.UNASSIGNED) {
            name = name.copy().withStyle(style ->
                    style.withColor(TextColor.fromRgb(department.textColorRgb())));
        }
        event.setDisplayName(name);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.refreshDisplayName();
        syncAllDepartmentsTo(player);
        syncDepartmentToAll(player);
        refreshAllTabListNames(player.server);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.refreshDisplayName();
            refreshPlayerTabListName(player);
            syncDepartmentToAll(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ServerPlayer recipient : player.server.getPlayerList().getPlayers()) {
            if (!recipient.getUUID().equals(player.getUUID())) {
                MessageLoader.getLoader().sendToPlayer(recipient,
                        new PlayerDepartmentSyncPacket(
                                player.getUUID(), EmployeeDepartment.UNASSIGNED.id()));
            }
        }
    }

    public static void refreshPlayerTabListName(ServerPlayer player) {
        player.refreshTabListName();
    }

    public static void syncDepartmentToAll(ServerPlayer player) {
        EmployeeDepartment department = PlayerFacilityProfileData.get(player.server)
                .department(player.getUUID());
        for (ServerPlayer recipient : player.server.getPlayerList().getPlayers()) {
            MessageLoader.getLoader().sendToPlayer(recipient,
                    new PlayerDepartmentSyncPacket(player.getUUID(), department.id()));
        }
        player.refreshTabListName();
    }

    private static void syncAllDepartmentsTo(ServerPlayer recipient) {
        PlayerFacilityProfileData profiles = PlayerFacilityProfileData.get(recipient.server);
        for (ServerPlayer player : recipient.server.getPlayerList().getPlayers()) {
            MessageLoader.getLoader().sendToPlayer(recipient,
                    new PlayerDepartmentSyncPacket(player.getUUID(),
                            profiles.department(player.getUUID()).id()));
        }
    }

    private static void refreshAllTabListNames(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(
                PlayerTabListDayHandler::refreshPlayerTabListName);
    }

    private static EmployeeDepartment department(Player player) {
        if (player instanceof EmployeeServerPlayer employee) {
            return employee.employeeRecord().department();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return PlayerFacilityProfileData.get(serverPlayer.server)
                    .department(serverPlayer.getUUID());
        }
        return PlayerDepartmentClientState.department(player.getUUID());
    }
}
