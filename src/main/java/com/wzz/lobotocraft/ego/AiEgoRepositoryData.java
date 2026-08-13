package com.wzz.lobotocraft.ego;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiEgoRepositoryData extends SavedData {
    private static final String ID = "lobotocraft_ai_ego_repository";

    private final Map<String, Stock> stocks = new HashMap<>();
    private final Map<UUID, Map<String, String>> assignments = new HashMap<>();

    public static AiEgoRepositoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                AiEgoRepositoryData::load, AiEgoRepositoryData::new, ID);
    }

    public boolean research(String abnormalityCode, String equipmentType,
                            List<ItemStack> rewards, int maximum) {
        List<EgoItemSpec> itemSpecs = EgoItemSpec.fromStacks(rewards);
        if (itemSpecs.isEmpty()) return false;
        String key = key(abnormalityCode, equipmentType);
        Stock current = stocks.get(key);
        int total = current == null ? 0 : current.total();
        if (total >= Math.max(1, maximum)) return false;
        stocks.put(key, new Stock(abnormalityCode, equipmentType, total + 1,
                Math.max(1, maximum), itemSpecs));
        setDirty();
        return true;
    }

    /** Grants the complete AI research authorization in one idempotent operation. */
    public int researchToMaximum(String abnormalityCode, String equipmentType,
                                 List<ItemStack> rewards, int maximum) {
        return researchSpecsToMaximum(abnormalityCode, equipmentType,
                EgoItemSpec.fromStacks(rewards), maximum);
    }

    /** Adds the one global AI copy awarded by a suppression-only E.G.O. */
    public boolean grantUniqueSuppressionReward(String abnormalityCode, String equipmentType,
                                                List<ItemStack> rewards) {
        return researchToMaximum(abnormalityCode, equipmentType, rewards, 1) > 0;
    }

    int researchSpecsToMaximum(String abnormalityCode, String equipmentType,
                               List<EgoItemSpec> itemSpecs, int maximum) {
        if (itemSpecs.isEmpty()) return 0;
        int target = Math.max(1, maximum);
        String key = key(abnormalityCode, equipmentType);
        Stock current = stocks.get(key);
        int previous = current == null ? 0 : current.total();
        Stock updated = new Stock(abnormalityCode, equipmentType, target, target, itemSpecs);
        if (!updated.equals(current)) {
            stocks.put(key, updated);
            setDirty();
        }
        return Math.max(0, target - previous);
    }

    public int total(String abnormalityCode, String equipmentType) {
        Stock stock = stocks.get(key(abnormalityCode, equipmentType));
        return stock == null ? 0 : stock.total();
    }

    public int assigned(String abnormalityCode, String equipmentType) {
        String wanted = key(abnormalityCode, equipmentType);
        int count = 0;
        for (Map<String, String> loadout : assignments.values()) {
            if (wanted.equals(loadout.get(equipmentType))) count++;
        }
        return count;
    }

    public int available(String abnormalityCode, String equipmentType) {
        return Math.max(0, total(abnormalityCode, equipmentType)
                - assigned(abnormalityCode, equipmentType));
    }

    public boolean assign(UUID employeeId, String abnormalityCode, String equipmentType) {
        String wanted = key(abnormalityCode, equipmentType);
        Map<String, String> current = assignments.computeIfAbsent(employeeId, ignored -> new HashMap<>());
        if (wanted.equals(current.get(equipmentType))) return true;
        if (available(abnormalityCode, equipmentType) <= 0) return false;
        current.put(equipmentType, wanted);
        setDirty();
        return true;
    }

    public void release(UUID employeeId, String equipmentType) {
        Map<String, String> current = assignments.get(employeeId);
        if (current == null) return;
        if (current.remove(equipmentType) != null) setDirty();
        if (current.isEmpty()) assignments.remove(employeeId);
    }

    public List<Stock> stocks() {
        return stocks.values().stream()
                .sorted(java.util.Comparator.comparing(Stock::abnormalityCode)
                        .thenComparing(Stock::equipmentType))
                .toList();
    }

    public Stock stock(String abnormalityCode, String equipmentType) {
        return stocks.get(key(abnormalityCode, equipmentType));
    }

    public String assignedCode(UUID employeeId, String equipmentType) {
        Map<String, String> loadout = assignments.get(employeeId);
        if (loadout == null) return "";
        String value = loadout.getOrDefault(equipmentType, "");
        int separator = value.lastIndexOf('|');
        return separator < 0 ? "" : value.substring(0, separator);
    }

    public static AiEgoRepositoryData load(CompoundTag tag) {
        AiEgoRepositoryData data = new AiEgoRepositoryData();
        ListTag stocksTag = tag.getList("Stocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < stocksTag.size(); i++) {
            CompoundTag entry = stocksTag.getCompound(i);
            String code = entry.getString("AbnormalityCode");
            String type = entry.getString("EquipmentType");
            List<EgoItemSpec> items;
            if (entry.contains("ItemSpecs", Tag.TAG_LIST)) {
                items = EgoItemSpec.readList(entry.getList("ItemSpecs", Tag.TAG_COMPOUND));
            } else {
                items = List.of();
            }
            if (items.isEmpty()) {
                items = EgoItemSpec.fromItemIds(readStrings(entry.getList("Items", Tag.TAG_STRING)));
            }
            items = EgoItemSpec.normalizeLegacyVariants(code, type, items);
            if (!code.isEmpty() && !type.isEmpty() && !items.isEmpty()) {
                data.stocks.put(key(code, type), new Stock(code, type,
                        entry.getInt("Total"), entry.getInt("Maximum"), items));
            }
        }
        ListTag assignmentTag = tag.getList("Assignments", Tag.TAG_COMPOUND);
        for (int i = 0; i < assignmentTag.size(); i++) {
            CompoundTag entry = assignmentTag.getCompound(i);
            if (!entry.hasUUID("Employee")) continue;
            Map<String, String> loadout = new HashMap<>();
            if (entry.contains("weapon")) loadout.put("weapon", entry.getString("weapon"));
            if (entry.contains("armor")) loadout.put("armor", entry.getString("armor"));
            if (!loadout.isEmpty()) data.assignments.put(entry.getUUID("Employee"), loadout);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag stocksTag = new ListTag();
        for (Stock stock : stocks.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("AbnormalityCode", stock.abnormalityCode());
            entry.putString("EquipmentType", stock.equipmentType());
            entry.putInt("Total", stock.total());
            entry.putInt("Maximum", stock.maximum());
            ListTag items = new ListTag();
            stock.itemIds().forEach(item -> items.add(StringTag.valueOf(item)));
            entry.put("Items", items);
            ListTag itemSpecs = new ListTag();
            stock.itemSpecs().forEach(item -> itemSpecs.add(item.save()));
            entry.put("ItemSpecs", itemSpecs);
            stocksTag.add(entry);
        }
        tag.put("Stocks", stocksTag);

        ListTag assignmentTag = new ListTag();
        assignments.forEach((employee, loadout) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Employee", employee);
            loadout.forEach(entry::putString);
            assignmentTag.add(entry);
        });
        tag.put("Assignments", assignmentTag);
        return tag;
    }

    private static List<String> readStrings(ListTag tag) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < tag.size(); i++) result.add(tag.getString(i));
        return List.copyOf(result);
    }

    private static String key(String abnormalityCode, String equipmentType) {
        return abnormalityCode + "|" + equipmentType;
    }

    public record Stock(String abnormalityCode, String equipmentType, int total,
                        int maximum, List<EgoItemSpec> itemSpecs) {
        public Stock {
            itemSpecs = EgoItemSpec.normalizeLegacyVariants(
                    abnormalityCode, equipmentType, itemSpecs);
        }

        public List<String> itemIds() {
            return itemSpecs.stream().map(EgoItemSpec::itemId).toList();
        }
    }
}
