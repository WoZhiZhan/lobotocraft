package com.wzz.lobotocraft.capability;

import com.wzz.lobotocraft.entity.data.AbnormalityEncyclopediaData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 玩家异想体数据（扩展版）
 * 观察等级 = 解锁的管理须知数量
 */
public class PlayerAbnormalityData {

    // 已解锁的管理须知 Map<异想体编号, Set<条目索引>>
    private Map<String, Set<Integer>> unlockedManuals = new HashMap<>();
    
    // 装备研发进度 Map<异想体编号, Map<装备类型, 已研发数量>>
    // 装备类型: "weapon" 或 "armor"
    private Map<String, Map<String, Integer>> equipmentDevelopment = new HashMap<>();

    /**
     * 获取异想体的观察等级
     * 新规则：
     * - 基础信息(entryIndex=0): +1
     * - 工作偏好(entryIndex=100-103): 全部解锁后 +1
     * - 敏感信息(entryIndex=2): +1
     * - 管理须知(entryIndex>=3): 全部解锁后 +1
     */
    public int getObservationLevel(String abnormalityCode) {
        Set<Integer> unlocked = unlockedManuals.get(abnormalityCode);
        if (unlocked == null || unlocked.isEmpty()) {
            return 0;
        }

        int level = 0;

        // 基础信息
        if (unlocked.contains(0)) level++;

        // 工作偏好（检查4个工作类型是否全部解锁）
        // 100=本能, 101=洞察, 102=依恋, 103=压迫
        boolean allWorkPrefsUnlocked = true;
        for (int i = 100; i <= 103; i++) {
            if (!unlocked.contains(i)) {
                allWorkPrefsUnlocked = false;
                break;
            }
        }
        if (allWorkPrefsUnlocked) {
            level++;
        }

        // 敏感信息
        if (unlocked.contains(2)) level++;

        // 管理须知（检查是否全部解锁）
        // 需要从AbnormalityEncyclopediaData获取管理须知总数
        AbnormalityEncyclopediaData.EntryData data =
                AbnormalityEncyclopediaData.getData(abnormalityCode);
        int totalManuals = data.getManualCount();

        if (totalManuals > 0) {
            boolean allManualsUnlocked = true;
            for (int i = 0; i < totalManuals; i++) {
                if (!unlocked.contains(i + 3)) {
                    allManualsUnlocked = false;
                    break;
                }
            }
            if (allManualsUnlocked) {
                level++;
            }
        }

        return level;
    }

    // ==================== 管理须知相关 ====================

    /**
     * 检查管理须知是否已解锁
     */
    public boolean isManualUnlocked(String abnormalityCode, int entryIndex) {
        Set<Integer> unlocked = unlockedManuals.get(abnormalityCode);
        return unlocked != null && unlocked.contains(entryIndex);
    }

    /**
     * 解锁管理须知
     */
    public void unlockManual(String abnormalityCode, int entryIndex) {
        unlockedManuals.computeIfAbsent(abnormalityCode, k -> new HashSet<>()).add(entryIndex);
    }

    /**
     * 获取已解锁的管理须知数量
     */
    public int getUnlockedManualCount(String abnormalityCode) {
        Set<Integer> unlocked = unlockedManuals.get(abnormalityCode);
        return unlocked != null ? unlocked.size() : 0;
    }

    public int countFullyObservedAbnormalities() {
        int count = 0;
        for (String abnormalityCode : unlockedManuals.keySet()) {
            if (getObservationLevel(abnormalityCode) >= 4) {
                count++;
            }
        }
        return count;
    }

    /**
     * 检查是否所有管理须知都已解锁
     */
    public boolean isAllManualsUnlocked(String abnormalityCode, int totalManuals) {
        return getUnlockedManualCount(abnormalityCode) >= totalManuals;
    }

    // ==================== 装备研发相关 ====================

    /**
     * 获取装备研发数量
     * @param abnormalityCode 异想体编号
     * @param equipmentType "weapon" 或 "armor"
     * @return 当前研发数量
     */
    public int getEquipmentDevelopmentCount(String abnormalityCode, String equipmentType) {
        Map<String, Integer> count = equipmentDevelopment.get(abnormalityCode);
        if (count == null) return 0;
        return count.getOrDefault(equipmentType, 0);
    }

    /**
     * 增加装备研发数量
     * @param abnormalityCode 异想体编号
     * @param equipmentType "weapon" 或 "armor"
     * @param amount 增加的数量
     */
    public void addEquipmentDevelopmentCount(String abnormalityCode, String equipmentType, int amount) {
        equipmentDevelopment
            .computeIfAbsent(abnormalityCode, k -> new HashMap<>())
            .merge(equipmentType, amount, Integer::sum);
    }

    /**
     * 从另一个实例复制数据
     */
    public void copyFrom(PlayerAbnormalityData source) {
        this.unlockedManuals = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : source.unlockedManuals.entrySet()) {
            this.unlockedManuals.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        
        this.equipmentDevelopment = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : source.equipmentDevelopment.entrySet()) {
            this.equipmentDevelopment.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
    }

    /**
     * 保存到NBT
     */
    public void saveNBTData(CompoundTag nbt) {
        // 保存已解锁的管理须知
        ListTag manualsList = new ListTag();
        for (Map.Entry<String, Set<Integer>> entry : unlockedManuals.entrySet()) {
            CompoundTag manualTag = new CompoundTag();
            manualTag.putString("AbnormalityCode", entry.getKey());

            int[] indices = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            manualTag.putIntArray("Unlocked", indices);

            manualsList.add(manualTag);
        }
        nbt.put("UnlockedManuals", manualsList);

        // 保存装备研发数量
        ListTag listTag = new ListTag();
        for (Map.Entry<String, Map<String, Integer>> entry : equipmentDevelopment.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("AbnormalityCode", entry.getKey());
            
            CompoundTag equipmentTag = new CompoundTag();
            for (Map.Entry<String, Integer> equipEntry : entry.getValue().entrySet()) {
                equipmentTag.putInt(equipEntry.getKey(), equipEntry.getValue());
            }
            tag.put("Equipment", equipmentTag);
            
            listTag.add(tag);
        }
        nbt.put("EquipmentCount", listTag);
    }

    /**
     * 序列化到NBT（返回新的CompoundTag）
     */
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        saveNBTData(nbt);
        return nbt;
    }

    /**
     * 从NBT加载
     */
    public void loadNBTData(CompoundTag nbt) {
        // 加载已解锁的管理须知
        unlockedManuals.clear();
        ListTag manualsList = nbt.getList("UnlockedManuals", Tag.TAG_COMPOUND);
        for (int i = 0; i < manualsList.size(); i++) {
            CompoundTag manualTag = manualsList.getCompound(i);
            String abnormalityCode = manualTag.getString("AbnormalityCode");
            int[] indices = manualTag.getIntArray("Unlocked");

            Set<Integer> unlockedSet = new HashSet<>();
            for (int index : indices) {
                unlockedSet.add(index);
            }

            unlockedManuals.put(abnormalityCode, unlockedSet);
        }

        // 加载装备研发数量
        equipmentDevelopment.clear();
        ListTag equipmentCount = nbt.getList("EquipmentCount", Tag.TAG_COMPOUND);
        for (int i = 0; i < equipmentCount.size(); i++) {
            CompoundTag tag = equipmentCount.getCompound(i);
            String abnormalityCode = tag.getString("AbnormalityCode");
            CompoundTag equipmentTag = tag.getCompound("Equipment");
            Map<String, Integer> equipmentMap = new HashMap<>();
            for (String key : equipmentTag.getAllKeys()) {
                equipmentMap.put(key, equipmentTag.getInt(key));
            }
            equipmentDevelopment.put(abnormalityCode, equipmentMap);
        }
    }

    /**
     * 反序列化NBT
     */
    public void deserializeNBT(CompoundTag nbt) {
        loadNBTData(nbt);
    }
}
