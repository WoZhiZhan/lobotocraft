package com.wzz.lobotocraft.llm;

import com.wzz.lobotocraft.employee.EmployeeDepartment;
import com.wzz.lobotocraft.employee.DepartmentProgressData;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenDepartmentAccessPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LlmDepartmentAccessService {
    private LlmDepartmentAccessService() {
    }

    public static void open(ServerPlayer operator) {
        send(operator, true);
    }

    public static void refresh(ServerPlayer operator) {
        send(operator, false);
    }

    private static void send(ServerPlayer operator, boolean forceOpen) {
        if (!operator.hasPermissions(LlmConfigService.CONFIG_PERMISSION_LEVEL)) {
            operator.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.llm.department_access_denied"));
            return;
        }
        MessageLoader.getLoader().sendToPlayer(operator,
                new OpenDepartmentAccessPacket(buildSnapshot(operator), forceOpen));
    }

    public static boolean apply(ServerPlayer operator, List<Update> updates) {
        if (!operator.hasPermissions(LlmConfigService.CONFIG_PERMISSION_LEVEL)) return false;
        if (updates.size() > 128) return false;
        LlmDepartmentAccessData data = LlmDepartmentAccessData.get(operator.server);
        Map<UUID, ServerPlayer> online = new LinkedHashMap<>();
        operator.server.getPlayerList().getPlayers().forEach(player -> online.put(player.getUUID(), player));

        for (Update update : updates) {
            ServerPlayer target = online.get(update.playerId());
            LlmDepartmentAccessData.Assignment existing = data.assignment(update.playerId());
            if (target == null && existing == null) return false;
            String name = target != null ? target.getGameProfile().getName() : existing.playerName();
            EnumSet<EmployeeDepartment> openOnly = EnumSet.noneOf(EmployeeDepartment.class);
            for (EmployeeDepartment department : update.departments()) {
                if (DepartmentProgressData.get(operator.server).isOpen(department)) {
                    openOnly.add(department);
                }
            }
            data.setAssignment(update.playerId(), name, openOnly);
        }
        return true;
    }

    private static CompoundTag buildSnapshot(ServerPlayer operator) {
        LlmDepartmentAccessData data = LlmDepartmentAccessData.get(operator.server);
        Map<UUID, PlayerView> players = new LinkedHashMap<>();
        for (LlmDepartmentAccessData.Assignment assignment : data.assignments()) {
            players.put(assignment.playerId(), new PlayerView(assignment.playerId(),
                    assignment.playerName(), false, false, true,
                    openOnly(operator, assignment.departmentCopy())));
        }
        for (ServerPlayer player : operator.server.getPlayerList().getPlayers()) {
            LlmDepartmentAccessData.Assignment assignment = data.assignment(player.getUUID());
            boolean explicit = assignment != null;
            players.put(player.getUUID(), new PlayerView(player.getUUID(),
                    player.getGameProfile().getName(), true,
                    player.hasPermissions(LlmConfigService.CONFIG_PERMISSION_LEVEL), explicit,
                    explicit ? openOnly(operator, assignment.departmentCopy())
                            : data.effectiveDepartments(player)));
        }

        ArrayList<PlayerView> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.comparing(PlayerView::online).reversed()
                .thenComparing(PlayerView::name, String.CASE_INSENSITIVE_ORDER));
        CompoundTag root = new CompoundTag();
        ListTag rows = new ListTag();
        for (PlayerView player : sorted) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", player.id());
            row.putString("Name", player.name());
            row.putBoolean("Online", player.online());
            row.putBoolean("Operator", player.operator());
            row.putBoolean("Explicit", player.explicit());
            ListTag departments = new ListTag();
            player.departments().forEach(department ->
                    departments.add(StringTag.valueOf(department.id())));
            row.put("Departments", departments);
            rows.add(row);
        }
        root.put("Players", rows);
        ListTag openDepartments = new ListTag();
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department != EmployeeDepartment.UNASSIGNED
                    && DepartmentProgressData.get(operator.server).isOpen(department)) {
                openDepartments.add(StringTag.valueOf(department.id()));
            }
        }
        root.put("OpenDepartments", openDepartments);
        return root;
    }

    private static EnumSet<EmployeeDepartment> openOnly(
            ServerPlayer operator, Set<EmployeeDepartment> source) {
        EnumSet<EmployeeDepartment> result = EnumSet.noneOf(EmployeeDepartment.class);
        for (EmployeeDepartment department : source) {
            if (DepartmentProgressData.get(operator.server).isOpen(department)) result.add(department);
        }
        return result;
    }

    public record Update(UUID playerId, Set<EmployeeDepartment> departments) {
        public Update {
            departments = Set.copyOf(departments);
        }
    }

    private record PlayerView(UUID id, String name, boolean online, boolean operator,
                              boolean explicit, EnumSet<EmployeeDepartment> departments) {
    }
}
