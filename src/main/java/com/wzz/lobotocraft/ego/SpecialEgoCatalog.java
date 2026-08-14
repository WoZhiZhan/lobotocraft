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
                    "special_ego.lobotocraft.thin_dusk.name",
                    "special_ego.lobotocraft.thin_dusk.subtitle",
                    "ALEPH",
                    0xB58BE2,
                    List.of("lobotocraft:end_bird_weapon"),
                    List.of(
                            "lobotocraft:end_bird_chestplate",
                            "lobotocraft:end_bird_leggings",
                            "lobotocraft:end_bird_boots"),
                    List.of(
                            "special_ego.lobotocraft.thin_dusk.lore.1",
                            "special_ego.lobotocraft.thin_dusk.lore.2")));

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

    public record Definition(String id, String displayNameKey, String subtitleKey,
                             String riskLevel, int accentRgb,
                             List<String> weaponItemIds, List<String> armorItemIds,
                             List<String> loreKeys) {
        public Definition {
            weaponItemIds = List.copyOf(weaponItemIds);
            armorItemIds = List.copyOf(armorItemIds);
            loreKeys = List.copyOf(loreKeys);
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
