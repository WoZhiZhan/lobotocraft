package com.wzz.lobotocraft.ego;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Extensible catalog for unlocked, player-only E.G.O with type-exclusive active slots. */
public final class SpecialEgoCatalog {
    public static final String THIN_DUSK_ID = "thin_dusk";

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(
                    THIN_DUSK_ID,
                    "薄暝",
                    "黑森林的终末裁决",
                    "ALEPH",
                    0xB58BE2,
                    List.of("lobotocraft:end_bird_weapon"),
                    List.of(
                            "lobotocraft:end_bird_chestplate",
                            "lobotocraft:end_bird_leggings",
                            "lobotocraft:end_bird_boots"),
                    List.of(
                            "大鸟的眼、高鸟的秤与小鸟的喙在此合而为一。",
                            "武器与护甲分别只允许一名管理者同时使用。")));

    private SpecialEgoCatalog() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Definition byId(String id) {
        if (id == null) return null;
        for (Definition definition : DEFINITIONS) {
            if (definition.id().equals(id)) return definition;
        }
        return null;
    }

    public static boolean isSpecialItem(ItemStack stack) {
        return !specialIdFor(stack).isBlank();
    }

    public static String specialIdFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return "";
        String itemId = key.toString();
        return DEFINITIONS.stream()
                .filter(definition -> definition.itemIds().contains(itemId))
                .map(Definition::id)
                .findFirst().orElse("");
    }

    public static String equipmentTypeFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return "";
        String itemId = key.toString();
        for (Definition definition : DEFINITIONS) {
            if (definition.weaponItemIds().contains(itemId)) return "weapon";
            if (definition.armorItemIds().contains(itemId)) return "armor";
        }
        return "";
    }

    public record Definition(String id, String displayName, String subtitle,
                             String riskLevel, int accentRgb,
                             List<String> weaponItemIds, List<String> armorItemIds,
                             List<String> lore) {
        public Definition {
            weaponItemIds = List.copyOf(weaponItemIds);
            armorItemIds = List.copyOf(armorItemIds);
            lore = List.copyOf(lore);
        }

        public List<String> itemIds() {
            List<String> result = new ArrayList<>(weaponItemIds.size() + armorItemIds.size());
            result.addAll(weaponItemIds);
            result.addAll(armorItemIds);
            return List.copyOf(result);
        }

        public List<String> itemIds(String equipmentType) {
            return "weapon".equals(equipmentType) ? weaponItemIds
                    : "armor".equals(equipmentType) ? armorItemIds : List.of();
        }

        public String projectionCode() {
            return "special:" + id;
        }
    }
}
