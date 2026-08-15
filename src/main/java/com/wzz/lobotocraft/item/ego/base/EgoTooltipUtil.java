package com.wzz.lobotocraft.item.ego.base;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

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

    public static void add(List<Component> tooltip, String key, Style style,
                           Object... arguments) {
        tooltip.add(Component.translatable(key, arguments).withStyle(style));
    }

    public static void blank(List<Component> tooltip) {
        tooltip.add(Component.empty());
    }

    /**
     * Appends the common weapon/armor description layout without touching item behavior.
     *
     * <p>Summary, story and normal metrics are always shown. Detailed mechanics are shown
     * when Shift is held; otherwise the shared Shift hint is shown. Empty keys are ignored,
     * so equipment without one of the sections does not receive a placeholder line.</p>
     */
    public static void appendStandardDescription(List<Component> tooltip,
                                                 boolean showDetails,
                                                 String summaryKey,
                                                 String[] storyKeys,
                                                 String[] normalKeys,
                                                 String[] detailKeys) {
        appendStandardDescription(tooltip, showDetails, summaryKey, storyKeys,
                normalKeys, detailKeys, null);
    }

    /** Common layout variant with per-line colors for normal metrics. */
    public static void appendStandardDescription(List<Component> tooltip,
                                                 boolean showDetails,
                                                 String summaryKey,
                                                 String[] storyKeys,
                                                 String[] normalKeys,
                                                 String[] detailKeys,
                                                 ChatFormatting[] normalColors) {
        boolean hasSummary = summaryKey != null && !summaryKey.isBlank();
        addKeyIfPresent(tooltip, summaryKey, ChatFormatting.GOLD);
        if (hasSummary && (hasKeys(storyKeys) || hasKeys(normalKeys))) blank(tooltip);
        addKeys(tooltip, storyKeys, ChatFormatting.GRAY);
        addKeys(tooltip, normalKeys, normalColors);

        if (detailKeys != null && detailKeys.length > 0) {
            blank(tooltip);
            if (showDetails) {
                addKeys(tooltip, detailKeys, ChatFormatting.GOLD);
            } else {
                add(tooltip, "tooltip.lobotocraft.common.shift_details");
            }
        }
    }

    /** Common effects/set-effects layout for E.G.O accessories. */
    public static void appendGiftDescription(List<Component> tooltip,
                                             String[] effectKeys,
                                             String[] setEffectKeys) {
        appendGiftDescription(tooltip, effectKeys, null, setEffectKeys, null);
    }

    /** Common accessory layout with explicit section colors for existing tooltip styles. */
    public static void appendGiftDescription(List<Component> tooltip,
                                             String[] effectKeys,
                                             ChatFormatting effectColor,
                                             String[] setEffectKeys,
                                             ChatFormatting setEffectColor) {
        if (effectKeys != null && effectKeys.length > 0) {
            add(tooltip, "tooltip.lobotocraft.common.effects", ChatFormatting.YELLOW);
            addKeys(tooltip, effectKeys, effectColor);
        }
        if (setEffectKeys != null && setEffectKeys.length > 0) {
            blank(tooltip);
            add(tooltip, "tooltip.lobotocraft.common.full_set_effect", ChatFormatting.AQUA);
            addKeys(tooltip, setEffectKeys, setEffectColor);
        }
    }

    private static void addKeys(List<Component> tooltip, String[] keys, ChatFormatting color) {
        if (keys == null) return;
        for (String key : keys) addKeyIfPresent(tooltip, key, color);
    }

    private static void addKeys(List<Component> tooltip, String[] keys, ChatFormatting[] colors) {
        if (keys == null) return;
        for (int i = 0; i < keys.length; i++) {
            ChatFormatting color = colors != null && i < colors.length ? colors[i] : null;
            addKeyIfPresent(tooltip, keys[i], color);
        }
    }

    private static boolean hasKeys(String[] keys) {
        if (keys == null) return false;
        for (String key : keys) {
            if (key != null && !key.isBlank()) return true;
        }
        return false;
    }

    private static void addKeyIfPresent(List<Component> tooltip, String key, ChatFormatting color) {
        if (key == null || key.isBlank()) return;
        if (color == null) add(tooltip, key);
        else add(tooltip, key, color);
    }
}
