package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.EmployeeLevelCalculator;
import com.wzz.lobotocraft.capability.MentalValueProvider;
import com.wzz.lobotocraft.capability.PlayerAbnormalityDataProvider;
import com.wzz.lobotocraft.compat.ysm.YsmEmployeeCompat;
import com.wzz.lobotocraft.ego.AiEgoRepositoryData;
import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.entity.base.IAbnormality;
import com.wzz.lobotocraft.entity.data.EGOEquipmentData;
import com.wzz.lobotocraft.entity.data.EgoWeaponDisplayCatalog;
import com.wzz.lobotocraft.entity.data.RiskLevel;
import com.wzz.lobotocraft.item.PEBoxItem;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import com.wzz.lobotocraft.item.ego.base.IEgoArmor;
import com.wzz.lobotocraft.item.ego.base.IWorkBonusItem;
import com.wzz.lobotocraft.init.ModDimensions;
import com.wzz.lobotocraft.network.MessageLoader;
import com.wzz.lobotocraft.network.packet.OpenFacilityManagementPacket;
import com.wzz.lobotocraft.llm.LlmDispatchService;
import com.wzz.lobotocraft.util.CuriosUtil;
import com.wzz.lobotocraft.util.MentalValueUtil;
import com.wzz.lobotocraft.world.data.CompanyRoomData;
import com.wzz.lobotocraft.world.data.FacilityAreaEntry;
import com.wzz.lobotocraft.world.data.FacilityAreaSelection;
import com.wzz.lobotocraft.world.data.FacilityWorldLayout;
import com.wzz.lobotocraft.world.data.RoomEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public final class FacilityManagementService {
    private FacilityManagementService() {
    }

    public static void open(ServerPlayer viewer) {
        open(viewer, false, false, null);
    }

    public static void openEgoAssignment(ServerPlayer viewer) {
        open(viewer, true, false, null);
    }

    public static void openDepartmentAssignment(ServerPlayer viewer, java.util.UUID selectedEmployee) {
        open(viewer, false, true, selectedEmployee);
    }

    private static void open(ServerPlayer viewer, boolean openEgoAssignment,
                             boolean openDepartmentAssignment, java.util.UUID selectedEmployee) {
        if (!com.wzz.lobotocraft.util.AbnormalitySpawnHelper.isCompany(viewer.level())) {
            viewer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "请回到公司后再使用设施管理。"));
            return;
        }
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("Viewer", viewer.getGameProfile().getName());
        snapshot.putBoolean("OpenEgoAssignment", openEgoAssignment);
        snapshot.putBoolean("OpenDepartmentAssignment", openDepartmentAssignment);
        if (selectedEmployee != null) snapshot.putUUID("SelectedEmployee", selectedEmployee);
        snapshot.putBoolean("LlmEnabled", LlmDispatchService.enabled());
        snapshot.putBoolean("CanManageAi", viewer.hasPermissions(2));
        snapshot.putBoolean("CanRecruit", viewer.hasPermissions(2));
        String ysmProvider = YsmEmployeeCompat.activeProvider();
        snapshot.putBoolean("YsmAvailable", !ysmProvider.isEmpty());
        snapshot.putString("YsmProvider", ysmProvider);
        YsmEmployeeCompat.serverCatalog().writeTo(snapshot);
        snapshot.putBoolean("CanConfigureLlm", viewer.hasPermissions(
                com.wzz.lobotocraft.llm.LlmConfigService.CONFIG_PERMISSION_LEVEL));
        snapshot.put("DepartmentProgress", DepartmentProgressService.snapshot(viewer));

        AiEgoRepositoryData repository = AiEgoRepositoryData.get(viewer.server);
        Map<String, IAbnormality> abnormalities = loadedAbnormalities(viewer);
        PlayerFacilityProfileData playerProfiles = PlayerFacilityProfileData.get(viewer.server);

        ListTag employees = new ListTag();
        for (ServerPlayer player : viewer.server.getPlayerList().getPlayers()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", player.getUUID());
            entry.putString("Name", player.getGameProfile().getName());
            entry.putString("ActorType", "HUMAN_PLAYER");
            entry.putBoolean("Commandable", false);
            EmployeeDepartment playerDepartment = playerProfiles.department(player.getUUID());
            entry.putString("Department", playerDepartment.displayName());
            entry.putString("DepartmentId", playerDepartment.id());
            entry.putFloat("Health", player.getHealth());
            entry.putFloat("MaxHealth", player.getMaxHealth());
            entry.putBoolean("Panicking", MentalValueUtil.isPanic(player));
            player.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                entry.putFloat("Mental", mental.getMentalValue());
                entry.putFloat("BaseMaxMental", mental.getMaxMentalValue());
                entry.putFloat("MaxMental", mental.getEffectiveMaxMentalValue());
                entry.putFloat("MentalBonus",
                        mental.getEffectiveMaxMentalValue() - mental.getMaxMentalValue());
            });
            player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
                entry.putInt("Fortitude", stats.getFortitude());
                entry.putInt("Prudence", stats.getPrudence());
                entry.putInt("Temperance", stats.getTemperance());
                entry.putInt("Justice", stats.getJustice());
                entry.putFloat("BaseMaxHealth", stats.getFortitude());
                entry.putFloat("HealthBonus", player.getMaxHealth() - stats.getFortitude());
                putStatLevels(entry, stats.getFortitude(), stats.getPrudence(),
                        stats.getTemperance(), stats.getJustice());
            });
            player.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data ->
                    putPersonalEgoNames(entry, data, abnormalities));
            entry.putString("Status", "在线");
            employees.add(entry);
        }
        for (EmployeeRecord record : FacilityPersonnelData.get(viewer.server).employees()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", record.id());
            entry.putString("Name", record.name());
            entry.putString("ActorType", "AI_EMPLOYEE");
            entry.putBoolean("Commandable", true);
            entry.putString("Department", record.department().displayName());
            entry.putString("DepartmentId", record.department().id());
            entry.putInt("CentralSection", record.centralSection());
            entry.putInt("Fortitude", record.fortitude());
            entry.putInt("Prudence", record.prudence());
            entry.putInt("Temperance", record.temperance());
            entry.putInt("Justice", record.justice());
            if (!record.appearance().isDefault()) {
                entry.put("Appearance", record.appearance().save());
            }
            float baseMaxHealth = Math.max(20.0F, record.fortitude());
            float baseMaxMental = Math.max(20.0F, record.prudence());
            entry.putFloat("BaseMaxHealth", baseMaxHealth);
            entry.putFloat("MaxHealth", baseMaxHealth);
            entry.putFloat("HealthBonus", 0.0F);
            entry.putFloat("BaseMaxMental", baseMaxMental);
            entry.putFloat("MaxMental", baseMaxMental);
            entry.putFloat("MentalBonus", 0.0F);
            putStatLevels(entry, record.fortitude(), record.prudence(),
                    record.temperance(), record.justice());
            putDerivedStats(entry, null, record.temperance(), record.justice());
            var runtime = EmployeeRuntimeManager.activePlayer(record.id());
            entry.putBoolean("Deployed", runtime != null);
            if (runtime != null) {
                entry.putString("ProfileName", runtime.getGameProfile().getName());
                entry.putFloat("Health", runtime.getHealth());
                entry.putFloat("MaxHealth", runtime.getMaxHealth());
                entry.putBoolean("Panicking", MentalValueUtil.isPanic(runtime));
                entry.putFloat("HealthBonus", runtime.getMaxHealth() - baseMaxHealth);
                runtime.getCapability(MentalValueProvider.MENTAL_VALUE).ifPresent(mental -> {
                    entry.putFloat("Mental", mental.getMentalValue());
                    entry.putFloat("BaseMaxMental", mental.getMaxMentalValue());
                    entry.putFloat("MaxMental", mental.getEffectiveMaxMentalValue());
                    entry.putFloat("MentalBonus",
                            mental.getEffectiveMaxMentalValue() - mental.getMaxMentalValue());
                });
                putDerivedStats(entry, runtime, record.temperance(), record.justice());
                putPreviewEquipment(entry, runtime);
            }
            entry.putString("Status", record.taskType().name());
            entry.putString("Task", EmployeeRuntimeManager.visibleTaskSummary(record));
            entry.putString("NavigationPurpose", EmployeeRuntimeManager.navigationPurpose(record.id()));
            String weaponCode = repository.assignedCode(record.id(), "weapon");
            String armorCode = repository.assignedCode(record.id(), "armor");
            entry.putString("WeaponEgo", weaponCode);
            entry.putString("ArmorEgo", armorCode);
            entry.putString("WeaponEgoName", assignedEgoName(
                    repository, abnormalities, weaponCode, "weapon"));
            entry.putString("ArmorEgoName", assignedEgoName(
                    repository, abnormalities, armorCode, "armor"));
            employees.add(entry);
        }
        snapshot.put("Employees", employees);
        putDepartmentAbnormalities(viewer, snapshot);

        viewer.getCapability(PlayerAbnormalityDataProvider.PLAYER_ABNORMALITY_DATA).ifPresent(data -> {
            ListTag personal = new ListTag();
            for (Map.Entry<String, Map<String, java.util.List<String>>> codeEntry
                    : data.getPersonalEgoResearch().entrySet()) {
                for (Map.Entry<String, java.util.List<String>> typeEntry : codeEntry.getValue().entrySet()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putString("Code", codeEntry.getKey());
                    entry.putString("Type", typeEntry.getKey());
                    entry.putString("DisplayName", egoDisplayName(codeEntry.getKey(),
                            typeEntry.getKey(), typeEntry.getValue(), abnormalities));
                    entry.putBoolean("Equipped", codeEntry.getKey().equals(
                            data.getEquippedPersonalEgo(typeEntry.getKey())));
                    ListTag items = new ListTag();
                    typeEntry.getValue().forEach(item -> items.add(StringTag.valueOf(item)));
                    entry.put("Items", items);
                    personal.add(entry);
                }
            }
            snapshot.put("PersonalEgo", personal);
        });

        ListTag aiStocks = new ListTag();
        for (AiEgoRepositoryData.Stock stock : repository.stocks()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Code", stock.abnormalityCode());
            entry.putString("Type", stock.equipmentType());
            entry.putInt("Total", stock.total());
            entry.putInt("Maximum", stock.maximum());
            entry.putInt("Assigned", repository.assigned(stock.abnormalityCode(), stock.equipmentType()));
            entry.putInt("Available", repository.available(stock.abnormalityCode(), stock.equipmentType()));
            ListTag items = new ListTag();
            stock.itemIds().forEach(item -> items.add(StringTag.valueOf(item)));
            entry.put("Items", items);
            EgoLoadoutService.requiredLevels(stock.itemIds()).forEach((key, value) ->
                    entry.putInt("Required" + key, value));
            putEgoDisplayData(entry, stock, abnormalities.get(stock.abnormalityCode()));
            aiStocks.add(entry);
        }
        snapshot.put("AiEgo", aiStocks);
        snapshot.putInt("RecruitPeBoxes", countPeBoxes(viewer));
        snapshot.putInt("RecruitEmeralds", viewer.getInventory().countItem(Items.EMERALD));
        snapshot.putInt("RecruitIron", viewer.getInventory().countItem(Items.IRON_INGOT));
        snapshot.putInt("RecruitGold", viewer.getInventory().countItem(Items.GOLD_INGOT));
        snapshot.putInt("RecruitDiamonds", viewer.getInventory().countItem(Items.DIAMOND));
        MessageLoader.getLoader().sendToPlayer(viewer, new OpenFacilityManagementPacket(snapshot));
    }

    private static void putDepartmentAbnormalities(ServerPlayer viewer, CompoundTag snapshot) {
        ResourceLocation companyDimension = ModDimensions.LOBOTO_KEY.location();
        CompanyRoomData roomData = CompanyRoomData.get(viewer.serverLevel());
        java.util.List<FacilityAreaEntry> areas = roomData.getAreas(companyDimension);
        ListTag entries = new ListTag();
        for (RoomEntry room : roomData.getRooms(companyDimension)) {
            // The monitor applies the fixed schematic binding before rendering. Keep
            // this snapshot consistent so rooms created before the binding still
            // resolve to their department in the deployment screen.
            FacilityWorldLayout.applyRoomBinding(room);
            if (room.abnormalityCode == null || room.abnormalityCode.isBlank()
                    || room.departmentId == null || room.departmentId.isBlank()
                    || room.status == RoomEntry.STATUS_UNEXPANDED) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("Code", room.abnormalityCode);
            entry.putString("Name", room.abnormalityName == null ? "" : room.abnormalityName);
            entry.putString("Risk", room.riskName == null ? "" : room.riskName);
            entry.putInt("RiskColor", room.riskColor);
            entry.putString("DepartmentId", room.departmentId);
            entry.putInt("CentralSection", centralSection(room, areas, companyDimension));
            entry.putString("Dimension", room.dimension.toString());
            entry.putLong("Pos", room.pos.asLong());
            entries.add(entry);
        }
        snapshot.put("Abnormalities", entries);
    }

    private static int centralSection(RoomEntry room, java.util.List<FacilityAreaEntry> areas,
                                      ResourceLocation dimension) {
        if (!EmployeeDepartment.CENTRAL.id().equals(room.departmentId)) return 0;
        int boundSection = FacilityWorldLayout.containmentAt(dimension, room.pos)
                .map(binding -> binding.groupId().startsWith("dept/central/lower/") ? 1 : 0)
                .orElse(-1);
        if (boundSection >= 0) return boundSection;
        return FacilityAreaSelection.containing(areas, dimension, room.pos)
                .map(area -> area.groupId.startsWith("dept/central/lower/") ? 1 : 0)
                .orElse(0);
    }

    private static Map<String, IAbnormality> loadedAbnormalities(ServerPlayer viewer) {
        Map<String, IAbnormality> result = new HashMap<>();
        for (var level : viewer.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof IAbnormality abnormality) {
                    result.putIfAbsent(abnormality.getAbnormalityCode(), abnormality);
                }
            }
        }
        return result;
    }

    private static void putEgoDisplayData(CompoundTag entry, AiEgoRepositoryData.Stock stock,
                                          IAbnormality abnormality) {
        entry.putString("DisplayName", egoDisplayName(stock.abnormalityCode(),
                stock.equipmentType(), stock.itemIds(), abnormality));
        ItemStack icon = firstStack(stock.itemIds());
        if (!icon.isEmpty()) {
            ResourceLocation iconId = ForgeRegistries.ITEMS.getKey(icon.getItem());
            if (iconId != null) entry.putString("IconItem", iconId.toString());
        }

        RiskLevel fallbackRisk = riskFromItems(stock.itemIds());
        if (fallbackRisk != null) entry.putString("RiskLevel", fallbackRisk.name());
        if ("weapon".equals(stock.equipmentType())) {
            putWeaponSummary(entry, EgoWeaponDisplayCatalog.find(stock.abnormalityCode()));
        }
        if ("armor".equals(stock.equipmentType())) {
            for (String itemId : stock.itemIds()) {
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                if (item instanceof IEgoArmor armor) {
                    entry.putFloat("RedResistance", armor.getRedResistance());
                    entry.putFloat("WhiteResistance", armor.getWhiteResistance());
                    entry.putFloat("BlackResistance", armor.getBlackResistance());
                    entry.putFloat("PaleResistance", armor.getPaleResistance());
                    break;
                }
            }
        }
        if (abnormality == null) return;

        if ("weapon".equals(stock.equipmentType())) {
            EGOEquipmentData.WeaponData data = abnormality.getEGOWeaponData();
            if (data == null) return;
            if (data.name() != null && !data.name().isBlank()) {
                entry.putString("DisplayName", data.name());
            }
            entry.putString("RiskLevel", data.riskLevel().name());
            entry.putString("DamageType", data.damageType());
            entry.putString("AttackPower", data.attackPower());
            entry.putString("AttackSpeed", data.attackSpeed());
            entry.putString("AttackRange", data.attackRange());
        } else if ("armor".equals(stock.equipmentType())) {
            EGOEquipmentData.ArmorData data = abnormality.getEGOArmorData();
            if (data == null) return;
            if (data.name() != null && !data.name().isBlank()) {
                entry.putString("DisplayName", data.name());
            }
            entry.putString("RiskLevel", data.riskLevel().name());
            entry.putFloat("RedResistance", data.redResistance());
            entry.putFloat("WhiteResistance", data.whiteResistance());
            entry.putFloat("BlackResistance", data.blackResistance());
            entry.putFloat("PaleResistance", data.paleResistance());
        }
    }

    private static void putWeaponSummary(CompoundTag entry,
                                         EgoWeaponDisplayCatalog.WeaponSummary summary) {
        if (summary == null) return;
        entry.putString("DamageType", summary.damageType());
        entry.putString("AttackPower", summary.attackPower());
        entry.putString("AttackSpeed", summary.attackSpeed());
        entry.putString("AttackRange", summary.attackRange());
    }

    private static void putPersonalEgoNames(CompoundTag entry,
                                            com.wzz.lobotocraft.capability.PlayerAbnormalityData data,
                                            Map<String, IAbnormality> abnormalities) {
        String weaponCode = data.getEquippedPersonalEgo("weapon");
        String armorCode = data.getEquippedPersonalEgo("armor");
        entry.putString("WeaponEgo", weaponCode);
        entry.putString("ArmorEgo", armorCode);
        entry.putString("WeaponEgoName", egoDisplayName(weaponCode, "weapon",
                data.getPersonalEgoItems(weaponCode, "weapon"), abnormalities));
        entry.putString("ArmorEgoName", egoDisplayName(armorCode, "armor",
                data.getPersonalEgoItems(armorCode, "armor"), abnormalities));
    }

    private static String assignedEgoName(AiEgoRepositoryData repository,
                                          Map<String, IAbnormality> abnormalities,
                                          String code, String type) {
        if (code == null || code.isBlank()) return "无";
        AiEgoRepositoryData.Stock stock = repository.stock(code, type);
        return egoDisplayName(code, type,
                stock == null ? java.util.List.of() : stock.itemIds(), abnormalities);
    }

    private static String egoDisplayName(String code, String type,
                                         java.util.List<String> itemIds,
                                         Map<String, IAbnormality> abnormalities) {
        return egoDisplayName(code, type, itemIds, abnormalities.get(code));
    }

    private static String egoDisplayName(String code, String type,
                                         java.util.List<String> itemIds,
                                         IAbnormality abnormality) {
        if (code == null || code.isBlank()) return "无";
        if (abnormality != null) {
            if ("weapon".equals(type)) {
                EGOEquipmentData.WeaponData data = abnormality.getEGOWeaponData();
                if (data != null && data.name() != null && !data.name().isBlank()) return data.name();
            } else if ("armor".equals(type)) {
                EGOEquipmentData.ArmorData data = abnormality.getEGOArmorData();
                if (data != null && data.name() != null && !data.name().isBlank()) return data.name();
            }
        }
        ItemStack stack = firstStack(itemIds);
        return stack.isEmpty() ? "无" : stack.getHoverName().getString();
    }

    private static ItemStack firstStack(java.util.List<String> itemIds) {
        for (String itemId : itemIds) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item != null) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    private static RiskLevel riskFromItems(java.util.List<String> itemIds) {
        BaseEgoWeapon weapon = null;
        for (String itemId : itemIds) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item instanceof IEgoArmor armor) return armor.riskLevel();
            if (item instanceof BaseEgoWeapon egoWeapon) weapon = egoWeapon;
        }
        if (weapon == null) return null;
        String setId = weapon.weaponName();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof IEgoArmor armor && setId.equals(armor.getSetId())) {
                return armor.riskLevel();
            }
        }
        return null;
    }

    private static void putStatLevels(CompoundTag entry, int fortitude, int prudence,
                                      int temperance, int justice) {
        int fortitudeLevel = statLevel(fortitude);
        int prudenceLevel = statLevel(prudence);
        int temperanceLevel = statLevel(temperance);
        int justiceLevel = statLevel(justice);
        entry.putInt("FortitudeLevel", fortitudeLevel);
        entry.putInt("PrudenceLevel", prudenceLevel);
        entry.putInt("TemperanceLevel", temperanceLevel);
        entry.putInt("JusticeLevel", justiceLevel);
        entry.putInt("EmployeeLevel", EmployeeLevelCalculator.fromStatLevels(
                fortitudeLevel, prudenceLevel, temperanceLevel, justiceLevel));
    }

    private static int statLevel(int value) {
        return EmployeeLevelCalculator.statLevel(value);
    }

    private static void putDerivedStats(CompoundTag entry, ServerPlayer player,
                                        int temperance, int justice) {
        float temperancePercent = Math.max(0.0F, (temperance - 20) / 10.0F);
        entry.putFloat("WorkSuccessBase", temperancePercent);
        entry.putFloat("WorkSpeedBase", temperancePercent);
        entry.putFloat("WorkSuccessBonus", 0.0F);
        entry.putFloat("WorkSpeedBonus", 0.0F);

        if (player == null) {
            entry.putFloat("AttackSpeedBase", 4.0F * (1.0F
                    + Math.max(0, justice - 20) * 0.0025F));
            entry.putFloat("AttackSpeedBonus", 0.0F);
            entry.putFloat("MovementSpeedBase", 10.0F * (1.0F
                    + Math.max(0, justice - 20) * 0.005F));
            entry.putFloat("MovementSpeedBonus", 0.0F);
            return;
        }

        float workSuccessBonus = 0.0F;
        int workSpeedBonus = 0;
        java.util.List<ItemStack> bonusItems = new java.util.ArrayList<>(CuriosUtil.getCuriosItems(player));
        player.getArmorSlots().forEach(bonusItems::add);
        for (ItemStack stack : bonusItems) {
            if (stack.getItem() instanceof IWorkBonusItem bonusItem) {
                workSuccessBonus += bonusItem.getWorkSuccessBonus(player, null) * 100.0F;
                workSpeedBonus += bonusItem.getWorkSpeedBonus(player, null);
            }
        }
        entry.putFloat("WorkSuccessBonus", workSuccessBonus);
        entry.putFloat("WorkSpeedBonus", workSpeedBonus * 2.5F);

        putSpeed(entry, player.getAttribute(Attributes.ATTACK_SPEED),
                "AttackSpeed", Math.max(0, justice - 20) * 0.0025F, 1.0F);
        putSpeed(entry, player.getAttribute(Attributes.MOVEMENT_SPEED),
                "MovementSpeed", Math.max(0, justice - 20) * 0.005F, 100.0F);
    }

    private static void putSpeed(CompoundTag entry, AttributeInstance attribute,
                                 String key, float justiceMultiplier, float displayScale) {
        if (attribute == null) return;
        float baseline = (float) (attribute.getBaseValue() * (1.0F + justiceMultiplier)
                * displayScale);
        float actual = (float) attribute.getValue() * displayScale;
        entry.putFloat(key + "Base", baseline);
        entry.putFloat(key + "Bonus", actual - baseline);
    }

    private static void putPreviewEquipment(CompoundTag entry, ServerPlayer player) {
        CompoundTag equipment = new CompoundTag();
        putPreviewSlot(equipment, "MainHand", player.getItemBySlot(EquipmentSlot.MAINHAND));
        putPreviewSlot(equipment, "OffHand", player.getItemBySlot(EquipmentSlot.OFFHAND));
        putPreviewSlot(equipment, "Head", player.getItemBySlot(EquipmentSlot.HEAD));
        putPreviewSlot(equipment, "Chest", player.getItemBySlot(EquipmentSlot.CHEST));
        putPreviewSlot(equipment, "Legs", player.getItemBySlot(EquipmentSlot.LEGS));
        putPreviewSlot(equipment, "Feet", player.getItemBySlot(EquipmentSlot.FEET));
        entry.put("PreviewEquipment", equipment);
    }

    private static void putPreviewSlot(CompoundTag equipment, String key, ItemStack stack) {
        if (!stack.isEmpty()) equipment.put(key, stack.save(new CompoundTag()));
    }

    private static int countPeBoxes(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof PEBoxItem) count += stack.getCount();
        }
        return count;
    }
}
