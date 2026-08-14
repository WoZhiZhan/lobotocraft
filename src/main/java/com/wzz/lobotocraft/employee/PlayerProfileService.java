package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.ego.SpecialEgoCatalog;
import com.wzz.lobotocraft.ego.SpecialEgoRegistryData;
import com.wzz.lobotocraft.ego.SpecialEgoService;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenPlayerProfilePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.List;

/** Builds compact, server-authoritative snapshots for player-facing facility screens. */
public final class PlayerProfileService {
    private PlayerProfileService() {
    }

    public static void open(ServerPlayer player, View view) {
        if (player == null || view == null) return;
        if (!com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(player.level())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "请回到公司后再使用玩家档案。"));
            return;
        }
        CompoundTag snapshot = view == View.DEPARTMENT
                ? departmentSnapshot(player) : specialEgoSnapshot(player);
        MessageLoader.getLoader().sendToPlayer(player,
                new OpenPlayerProfilePacket(view, snapshot));
    }

    private static CompoundTag departmentSnapshot(ServerPlayer player) {
        CompoundTag snapshot = baseSnapshot(player, View.DEPARTMENT);
        PlayerFacilityProfileData profiles = PlayerFacilityProfileData.get(player.server);
        EmployeeDepartment current = profiles.department(player.getUUID());
        snapshot.putString("CurrentDepartment", current.id());

        DepartmentProgressData progress = DepartmentProgressData.get(player.server);
        ListTag departments = new ListTag();
        for (EmployeeDepartment department : EmployeeDepartment.values()) {
            if (department == EmployeeDepartment.UNASSIGNED) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", department.id());
            entry.putString("Name", department.displayName());
            entry.putInt("Color", 0xFF000000 | department.textColorRgb());
            entry.putInt("Level", progress.level(department));
            entry.putBoolean("Open", progress.isOpen(department));
            entry.putBoolean("Selected", department == current);
            departments.add(entry);
        }
        snapshot.put("Departments", departments);
        return snapshot;
    }

    private static CompoundTag specialEgoSnapshot(ServerPlayer player) {
        CompoundTag snapshot = baseSnapshot(player, View.SPECIAL_EGO);
        SpecialEgoRegistryData registry = SpecialEgoRegistryData.get(player.server);
        ListTag entries = new ListTag();
        for (SpecialEgoCatalog.Definition definition : SpecialEgoCatalog.definitions()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", definition.id());
            entry.putString("NameKey", definition.displayNameKey());
            entry.putString("SubtitleKey", definition.subtitleKey());
            entry.putString("Risk", definition.riskLevel());
            entry.putInt("AccentColor", 0xFF000000 | definition.accentRgb());
            entry.putBoolean("Unlocked", registry.isUnlocked(definition.id()));
            putSpecialTypeSnapshot(entry, player, registry, definition, "weapon");
            putSpecialTypeSnapshot(entry, player, registry, definition, "armor");

            ListTag lore = new ListTag();
            definition.loreKeys().forEach(value -> lore.add(StringTag.valueOf(value)));
            entry.put("LoreKeys", lore);
            entry.putString("Damage", "ALL 18");
            entry.putString("AttackSpeed", "0.6");
            entry.putString("AttackRange", "4");
            entry.putFloat("RedResistance", 0.3F);
            entry.putFloat("WhiteResistance", 0.3F);
            entry.putFloat("BlackResistance", 0.3F);
            entry.putFloat("PaleResistance", 0.5F);
            entries.add(entry);
        }
        snapshot.put("SpecialEgos", entries);
        return snapshot;
    }

    private static void putSpecialTypeSnapshot(CompoundTag entry, ServerPlayer player,
                                               SpecialEgoRegistryData registry,
                                               SpecialEgoCatalog.Definition definition,
                                               String equipmentType) {
        String prefix = "weapon".equals(equipmentType) ? "Weapon" : "Armor";
        entry.putBoolean(prefix + "InUse", registry.isUsed(definition.id(), equipmentType));
        entry.putBoolean(prefix + "UsedByViewer", registry.isUsedBy(
                definition.id(), equipmentType, player.getUUID()));
        entry.putString(prefix + "User", registry.userName(definition.id(), equipmentType));

        List<String> missing = SpecialEgoService.missingRequirements(
                player, definition, equipmentType);
        entry.putBoolean(prefix + "RequirementsMet", missing.isEmpty());
        ListTag missingTag = new ListTag();
        missing.forEach(value -> missingTag.add(StringTag.valueOf(value)));
        entry.put(prefix + "Missing", missingTag);

        ListTag items = new ListTag();
        definition.itemIds(equipmentType).forEach(value -> items.add(StringTag.valueOf(value)));
        entry.put(prefix + "Items", items);
        Map<String, Integer> requirements = EgoLoadoutService.requiredLevels(
                definition.itemIds(equipmentType));
        requirements.forEach((key, value) -> entry.putInt(prefix + "Required" + key, value));
    }

    private static CompoundTag baseSnapshot(ServerPlayer player, View view) {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("View", view.id());
        snapshot.putString("Viewer", player.getGameProfile().getName());
        return snapshot;
    }

    public enum View {
        DEPARTMENT("department"),
        SPECIAL_EGO("special_ego");

        private final String id;

        View(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static View byId(String id) {
            for (View view : values()) if (view.id.equals(id)) return view;
            return null;
        }
    }
}
