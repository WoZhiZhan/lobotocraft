package com.wzz.lobotocraft.employee;

import com.lobotocraft.facility.data.FacilityDayData;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenDepartmentExpansionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative lifecycle and synchronization for department expansion. */
public final class DepartmentProgressService {
    private DepartmentProgressService() {
    }

    public static void onDayStarted(MinecraftServer server, int day) {
        DepartmentProgressData progress = DepartmentProgressData.get(server);
        progress.prepareForDay(day);
        if (progress.pendingDay() == 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            open(player, true);
        }
    }

    public static void open(ServerPlayer viewer, boolean forceOpen) {
        MessageLoader.getLoader().sendToPlayer(viewer,
                new OpenDepartmentExpansionPacket(snapshot(viewer), forceOpen));
    }

    public static void confirm(ServerPlayer sender, int pendingDay,
                               EmployeeDepartment department) {
        if (!sender.hasPermissions(2)) {
            sender.sendSystemMessage(Component.translatable(
                            "message.lobotocraft.department_expansion.permission")
                    .withStyle(ChatFormatting.RED));
            open(sender, false);
            return;
        }

        MinecraftServer server = sender.server;
        int currentDay = FacilityDayData.get(server.overworld()).getDay();
        DepartmentProgressData progress = DepartmentProgressData.get(server);
        DepartmentProgressData.ExpandResult result = pendingDay > 0 && pendingDay <= currentDay
                ? progress.tryExpand(pendingDay, department)
                : DepartmentProgressData.ExpandResult.PENDING_DAY_MISMATCH;
        if (result == DepartmentProgressData.ExpandResult.SUCCESS) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.lobotocraft.department_expansion.success",
                                    sender.getGameProfile().getName(),
                                    Component.translatable("department.lobotocraft." + department.id()),
                                    progress.level(department))
                            .withStyle(ChatFormatting.GOLD), false);
            progress.prepareForDay(pendingDay < currentDay ? pendingDay + 1 : currentDay);
            syncOpenScreens(server);
            return;
        }

        sender.sendSystemMessage(resultMessage(result).copy().withStyle(ChatFormatting.RED));
        open(sender, false);
    }

    public static CompoundTag snapshot(ServerPlayer viewer) {
        DepartmentProgressData progress = DepartmentProgressData.get(viewer.server);
        CompoundTag snapshot = progress.writeSnapshot(new CompoundTag());
        snapshot.putInt("CurrentDay", FacilityDayData.get(viewer.server.overworld()).getDay());
        snapshot.putBoolean("CanConfirm", viewer.hasPermissions(2));
        snapshot.putInt("LastExpandedDay", progress.lastExpandedDay());
        snapshot.putString("LastExpandedDepartment", progress.lastExpandedDepartment().id());
        snapshot.putBoolean("CentralLowerOpen", progress.isCentralLowerOpen());
        return snapshot;
    }

    public static boolean isOpen(MinecraftServer server, EmployeeDepartment department) {
        return department == EmployeeDepartment.UNASSIGNED
                || DepartmentProgressData.get(server).isOpen(department);
    }

    public static boolean isSectionOpen(MinecraftServer server, EmployeeDepartment department,
                                        int centralSection) {
        if (!isOpen(server, department)) return false;
        return department != EmployeeDepartment.CENTRAL || centralSection <= 0
                || DepartmentProgressData.get(server).isCentralLowerOpen();
    }

    public static int employeeCapacity(MinecraftServer server, EmployeeDepartment department,
                                       int centralSection) {
        return DepartmentProgressData.get(server).employeeCapacity(department, centralSection);
    }

    public static int containmentSlotCapacity(MinecraftServer server, EmployeeDepartment department,
                                              int centralSection) {
        return DepartmentProgressData.get(server).containmentSlotCapacity(department, centralSection);
    }

    private static void syncOpenScreens(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            open(player, false);
        }
    }

    private static Component resultMessage(DepartmentProgressData.ExpandResult result) {
        String key = switch (result) {
            case ALREADY_EXPANDED, NO_PENDING_EXPANSION ->
                    "message.lobotocraft.department_expansion.already_confirmed";
            case LOCKED -> "message.lobotocraft.department_expansion.locked";
            case MAX_LEVEL -> "message.lobotocraft.department_expansion.max_level";
            case PENDING_DAY_MISMATCH, INVALID_DAY ->
                    "message.lobotocraft.department_expansion.expired";
            default -> "message.lobotocraft.department_expansion.invalid";
        };
        return Component.translatable(key);
    }
}
