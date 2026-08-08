package com.wzz.lobotocraft.ego;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent description of an E.G.O item. Registry ids alone are not enough:
 * some E.G.O variants share an item id and are distinguished by initial NBT.
 */
public final class EgoItemSpec {
    public static final int MAX_ITEMS_PER_RESEARCH = 16;
    private static final String SOLEMN_LAMENT_CODE = "T-01-68";
    private static final String SOLEMN_LAMENT_ITEM = "lobotocraft:butterfly_funeral_weapon";
    private static final String SOLEMN_LAMENT_BLACK_TAG = "BFBlack";

    private final String itemId;
    private final CompoundTag initialTag;
    private final String variantId;

    public EgoItemSpec(String itemId, CompoundTag initialTag) {
        ResourceLocation parsed = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (parsed == null) throw new IllegalArgumentException("Invalid item id: " + itemId);
        this.itemId = parsed.toString();
        this.initialTag = sanitizeInitialTag(this.itemId, initialTag);
        this.variantId = this.itemId + "#" + canonical(this.initialTag);
    }

    public static EgoItemSpec fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : new EgoItemSpec(id.toString(), stack.getTag());
    }

    public static List<EgoItemSpec> fromStacks(Collection<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return List.of();
        List<EgoItemSpec> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            EgoItemSpec spec = fromStack(stack);
            if (spec != null) result.add(spec);
            if (result.size() >= MAX_ITEMS_PER_RESEARCH) break;
        }
        return List.copyOf(result);
    }

    public static List<EgoItemSpec> fromItemIds(Collection<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return List.of();
        List<EgoItemSpec> result = new ArrayList<>();
        for (String itemId : itemIds) {
            try {
                result.add(new EgoItemSpec(itemId, null));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed ids from edited/corrupt legacy saves.
            }
            if (result.size() >= MAX_ITEMS_PER_RESEARCH) break;
        }
        return List.copyOf(result);
    }

    public String itemId() {
        return itemId;
    }

    public CompoundTag initialTag() {
        return initialTag.copy();
    }

    public String variantId() {
        return variantId;
    }

    public Set<String> initialKeys() {
        return Set.copyOf(initialTag.getAllKeys());
    }

    public ItemStack createStack() {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        if (!initialTag.isEmpty()) stack.setTag(initialTag.copy());
        return stack;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Item", itemId);
        if (!initialTag.isEmpty()) tag.put("InitialTag", initialTag.copy());
        return tag;
    }

    public static EgoItemSpec load(CompoundTag tag) {
        if (tag == null) return null;
        try {
            return new EgoItemSpec(tag.getString("Item"),
                    tag.contains("InitialTag", Tag.TAG_COMPOUND)
                            ? tag.getCompound("InitialTag") : null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static List<EgoItemSpec> readList(ListTag tags) {
        List<EgoItemSpec> result = new ArrayList<>();
        for (int i = 0; i < tags.size() && result.size() < MAX_ITEMS_PER_RESEARCH; i++) {
            EgoItemSpec spec = load(tags.getCompound(i));
            if (spec != null) result.add(spec);
        }
        return List.copyOf(result);
    }

    public static List<EgoItemSpec> normalizeLegacyVariants(
            String abnormalityCode, String equipmentType, Collection<EgoItemSpec> source) {
        List<EgoItemSpec> specs = source == null
                ? List.of() : source.stream().limit(MAX_ITEMS_PER_RESEARCH).toList();
        if (!SOLEMN_LAMENT_CODE.equalsIgnoreCase(abnormalityCode)
                || !"weapon".equals(equipmentType)
                || specs.size() != 2
                || specs.stream().anyMatch(spec -> !SOLEMN_LAMENT_ITEM.equals(spec.itemId()))) {
            return List.copyOf(specs);
        }

        // Solemn Lament always consists of one white and one black gun. Repair both
        // legacy [same id, same id] records and manually corrupted duplicate variants.
        CompoundTag black = new CompoundTag();
        black.putBoolean(SOLEMN_LAMENT_BLACK_TAG, true);
        return List.of(new EgoItemSpec(SOLEMN_LAMENT_ITEM, null),
                new EgoItemSpec(SOLEMN_LAMENT_ITEM, black));
    }

    public boolean hasControlledState(ItemStack stack, Collection<String> controlledKeys) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation actual = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (actual == null || !itemId.equals(actual.toString())) return false;
        CompoundTag actualTag = stack.getTag();
        for (String key : controlledKeys) {
            Tag expected = initialTag.get(key);
            Tag found = actualTag == null ? null : actualTag.get(key);
            if (!Objects.equals(expected, found)) return false;
        }
        return true;
    }

    private static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            return compound.getAllKeys().stream().sorted(Comparator.naturalOrder())
                    .map(key -> key + "=" + canonical(compound.get(key)))
                    .reduce("{", (left, right) -> left + right + ";") + "}";
        }
        if (tag instanceof ListTag list) {
            StringBuilder result = new StringBuilder("[");
            for (Tag value : list) result.append(canonical(value)).append(';');
            return result.append(']').toString();
        }
        return tag == null ? "null" : tag.toString();
    }

    private static CompoundTag sanitizeInitialTag(String itemId, CompoundTag source) {
        CompoundTag safe = new CompoundTag();
        if (source == null) return safe;
        // Initial E.G.O state is deliberately allow-listed. Runtime cooldowns,
        // enchantments, attribute modifiers and arbitrary save-edited NBT must
        // never become part of a permanent projection template.
        if (SOLEMN_LAMENT_ITEM.equals(itemId)
                && source.contains(SOLEMN_LAMENT_BLACK_TAG, Tag.TAG_BYTE)) {
            safe.putBoolean(SOLEMN_LAMENT_BLACK_TAG, source.getBoolean(SOLEMN_LAMENT_BLACK_TAG));
        }
        return safe;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EgoItemSpec spec && variantId.equals(spec.variantId);
    }

    @Override
    public int hashCode() {
        return variantId.hashCode();
    }
}
