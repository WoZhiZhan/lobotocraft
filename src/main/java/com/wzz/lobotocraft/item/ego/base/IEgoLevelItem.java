package com.wzz.lobotocraft.item.ego.base;

import com.wzz.lobotocraft.capability.EmployeeStatsProvider;
import com.wzz.lobotocraft.capability.EmployeeLevelCalculator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

    Map<String, String> LEGACY_ATTRIBUTE_NAMES = Map.of(
            "FortitudeLevel", "勇气等级",
            "PrudenceLevel", "谨慎等级",
            "TemperanceLevel", "自律等级",
            "JusticeLevel", "正义等级",
            "EmployeeLevel", "员工总等级"
    );

    Map<String, String> ATTRIBUTE_TRANSLATION_KEYS = Map.of(
            "FortitudeLevel", "stat.lobotocraft.fortitude",
            "PrudenceLevel", "stat.lobotocraft.prudence",
            "TemperanceLevel", "stat.lobotocraft.temperance",
            "JusticeLevel", "stat.lobotocraft.justice",
            "EmployeeLevel", "stat.lobotocraft.employee_level"
    );

    record MissingLevel(String attributeKey, int current, int required) {
        public Component description() {
            return Component.translatable(
                    "message.lobotocraft.ego.requirement_entry",
                    Component.translatable(ATTRIBUTE_TRANSLATION_KEYS.getOrDefault(
                            attributeKey, attributeKey)),
                    formatLevel(current), formatLevel(required));
        }

        public String legacyDescription() {
            return LEGACY_ATTRIBUTE_NAMES.getOrDefault(attributeKey, attributeKey)
                    + " " + formatLevel(current) + "/" + formatLevel(required);
        }
    }

    /**
     * 检查玩家是否满足条件，如果不足则返回不足的属性列表
     */
    default List<String> getMissingLevels(Player player) {
        return getMissingLevelEntries(player).stream()
                .map(MissingLevel::legacyDescription)
                .toList();
    }

    default List<MissingLevel> getMissingLevelEntries(Player player) {
        List<MissingLevel> missing = new ArrayList<>();
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
                int rawValue = switch (key) {
                    case "FortitudeLevel" -> stats.getFortitude();
                    case "PrudenceLevel" -> stats.getPrudence();
                    case "TemperanceLevel" -> stats.getTemperance();
                    case "JusticeLevel" -> stats.getJustice();
                    default -> 0;
                };
                if (rawValue > 0) {
                    value = EmployeeLevelCalculator.levelForEquipmentRequirement(
                            rawValue, value, required);
                }
                if (value < required) {
                    missing.add(new MissingLevel(key, value, required));
                }
            });
        });
        return missing;
    }

    default List<Component> getMissingLevelComponents(Player player) {
        return getMissingLevelEntries(player).stream()
                .map(MissingLevel::description)
                .toList();
    }

    static Component joinMissingLevels(List<Component> missing) {
        MutableComponent result = Component.empty();
        for (int index = 0; index < missing.size(); index++) {
            if (index > 0) {
                result.append(Component.translatable("text.lobotocraft.list_separator"));
            }
            result.append(missing.get(index));
        }
        return result;
    }

    private static String formatLevel(int level) {
        return EmployeeLevelCalculator.levelGrade(level);
    }

    /**
     * 检查玩家是否可以使用此物品，客户端提示不足的属性
     */
    default boolean canUseItem(Player player) {
        List<Component> missing = getMissingLevelComponents(player);
        if (!missing.isEmpty() && player.level.isClientSide) {
            player.sendSystemMessage(Component.translatable(
                    "message.lobotocraft.ego.requirements_missing",
                    joinMissingLevels(missing)));
        }
        return missing.isEmpty();
    }
}
