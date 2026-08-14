package com.wzz.lobotocraft.item.ego.base;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class EgoTooltipUtil {
    private EgoTooltipUtil() {
    }

    public static void add(List<Component> tooltip, String key, ChatFormatting color,
                           Object... arguments) {
        tooltip.add(Component.translatable(key, arguments).withStyle(color));
    }

    public static void add(List<Component> tooltip, String key, Object... arguments) {
        tooltip.add(Component.translatable(key, arguments));
    }

    public static void blank(List<Component> tooltip) {
        tooltip.add(Component.empty());
    }
}
