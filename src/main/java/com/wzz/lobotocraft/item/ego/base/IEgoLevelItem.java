package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface IEgoLevelItem {
    /**
     * 子类覆盖这个方法，返回每个属性要求的等级
     * key 可以是 "FortitudeLevel", "PrudenceLevel", "TemperanceLevel", "JusticeLevel", "EmployeeLevel"
     * value 是要求等级 (1-5)
     */
    default Map<String, Integer> getRequiredLevels() {
        return new HashMap<>();
    }

    /**
     * 英文属性名 → 中文映射
     */
     Map<String, String> ATTRIBUTE_NAMES = Map.of(
            "FortitudeLevel", "勇气等级",
            "PrudenceLevel", "谨慎等级",
            "TemperanceLevel", "自律等级",
            "JusticeLevel", "正义等级",
            "EmployeeLevel", "员工总等级"
    );

    /**
     * 检查玩家是否满足条件，如果不足则返回不足的属性列表
     */
    default List<String> getMissingLevels(Player player) {
        List<String> missing = new ArrayList<>();
        player.getCapability(EmployeeStatsProvider.EMPLOYEE_STATS).ifPresent(stats -> {
            getRequiredLevels().forEach((key, required) -> {
                int value = switch (key) {
                    case "FortitudeLevel" -> stats.getFortitudeLevel();
                    case "PrudenceLevel" -> stats.getPrudenceLevel();
                    case "TemperanceLevel" -> stats.getTemperanceLevel();
                    case "JusticeLevel" -> stats.getJusticeLevel();
                    case "EmployeeLevel" -> stats.getEmployeeLevel();
                    default -> 0;
                };
                if (value < required) {
                    String chineseName = ATTRIBUTE_NAMES.getOrDefault(key, key);
                    missing.add(chineseName + " " + value + "/" + required);
                }
            });
        });
        return missing;
    }

    /**
     * 检查玩家是否可以使用此物品，客户端提示不足的属性
     */
    default boolean canUseItem(Player player) {
        List<String> missing = getMissingLevels(player);
        if (!missing.isEmpty() && player.level.isClientSide) {
            String msg = "§c等级不足: " + String.join(", ", missing);
            player.sendSystemMessage(Component.literal(msg));
        }
        return missing.isEmpty();
    }
}
