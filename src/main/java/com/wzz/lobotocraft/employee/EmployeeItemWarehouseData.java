package com.wzz.lobotocraft.employee;

import com.wzz.lobotocraft.ego.EgoLoadoutService;
import com.wzz.lobotocraft.item.ego.base.BaseEgoArmor;
import com.wzz.lobotocraft.item.ego.base.BaseEgoWeapon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** Facility-wide storage for ordinary items recovered from AI employees. */
public final class EmployeeItemWarehouseData extends SavedData {
    public static final int PAGE_SIZE = 54;
    public static final int MAX_PAGES = 4096;
    private static final int MAX_SLOTS = PAGE_SIZE * MAX_PAGES;
    private static final String ID = "lobotocraft_employee_item_warehouse";

    private final List<ItemStack> items = new ArrayList<>();

    public static EmployeeItemWarehouseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                EmployeeItemWarehouseData::load, EmployeeItemWarehouseData::new, ID);
    }

    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0 || slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        ItemStack stored = items.get(slot);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = stored.split(amount);
        if (stored.isEmpty()) items.set(slot, ItemStack.EMPTY);
        compactTail();
        setDirty();
        return removed;
    }

    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        ItemStack removed = items.set(slot, ItemStack.EMPTY);
        compactTail();
        setDirty();
        return removed;
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= MAX_SLOTS) return;
        if (!stack.isEmpty() && !isTransferable(stack)) return;
        ensureSize(slot + 1);
        ItemStack stored = stack.copy();
        stored.setCount(Math.min(stored.getCount(), stored.getMaxStackSize()));
        items.set(slot, stored);
        compactTail();
        setDirty();
    }

    /** Inserts as much as possible and returns the uninserted remainder. */
    public ItemStack insert(ItemStack offered) {
        if (!isTransferable(offered)) return offered.copy();
        ItemStack remaining = offered.copy();
        for (ItemStack stored : items) {
            if (remaining.isEmpty()) break;
            if (stored.isEmpty() || !ItemStack.isSameItemSameTags(stored, remaining)) continue;
            int moved = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
            if (moved > 0) {
                stored.grow(moved);
                remaining.shrink(moved);
            }
        }
        for (int slot = 0; slot < items.size() && !remaining.isEmpty(); slot++) {
            if (!items.get(slot).isEmpty()) continue;
            items.set(slot, splitStack(remaining));
        }
        while (!remaining.isEmpty() && items.size() < MAX_SLOTS) {
            items.add(splitStack(remaining));
        }
        if (remaining.getCount() != offered.getCount()) setDirty();
        return remaining;
    }

    public int occupiedSlots() {
        int occupied = 0;
        for (ItemStack stack : items) if (!stack.isEmpty()) occupied++;
        return occupied;
    }

    public int pageCount() {
        return pageCountForSlotCount(items.size());
    }

    public static int pageCountForSlotCount(int slots) {
        long safeSlots = Math.min(MAX_SLOTS, Math.max(0L, slots));
        return Math.max(1, Math.min(MAX_PAGES, (int) (safeSlots / PAGE_SIZE) + 1));
    }

    public static boolean isTransferable(ItemStack stack) {
        if (stack == null || stack.isEmpty() || EgoLoadoutService.isProjection(stack)) return false;
        return isWarehouseEligibleItem(stack.getItem());
    }

    static boolean isWarehouseEligibleItem(Item item) {
        return item != null && isWarehouseEligibleItemType(item.getClass());
    }

    static boolean isWarehouseEligibleItemType(Class<? extends Item> itemType) {
        return itemType != null
                && !BaseEgoWeapon.class.isAssignableFrom(itemType)
                && !BaseEgoArmor.class.isAssignableFrom(itemType);
    }

    public static EmployeeItemWarehouseData load(CompoundTag tag) {
        EmployeeItemWarehouseData data = new EmployeeItemWarehouseData();
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag itemTag = list.getCompound(index);
            int slot = itemTag.getInt("Slot");
            if (slot < 0 || slot >= MAX_SLOTS) continue;
            ItemStack stack = ItemStack.of(itemTag);
            if (stack.isEmpty() || !isTransferable(stack)) continue;
            stack.setCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
            data.ensureSize(slot + 1);
            data.items.set(slot, stack);
        }
        data.compactTail();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!isTransferable(stack)) continue;
            CompoundTag itemTag = stack.save(new CompoundTag());
            itemTag.putInt("Slot", slot);
            list.add(itemTag);
        }
        tag.put("Items", list);
        return tag;
    }

    private static ItemStack splitStack(ItemStack source) {
        int amount = Math.min(source.getCount(), source.getMaxStackSize());
        ItemStack result = source.copy();
        result.setCount(amount);
        source.shrink(amount);
        return result;
    }

    private void ensureSize(int size) {
        while (items.size() < size && items.size() < MAX_SLOTS) items.add(ItemStack.EMPTY);
    }

    private void compactTail() {
        while (!items.isEmpty() && items.get(items.size() - 1).isEmpty()) {
            items.remove(items.size() - 1);
        }
    }
}
