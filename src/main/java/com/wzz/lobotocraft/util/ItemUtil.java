package com.wzz.lobotocraft.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

public class ItemUtil {
    
    /**
     * 检查玩家是否有指定物品
     */
    public static boolean hasItem(Player player, Item item) {
        if (player == null || item == null) return false;
        
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack getItem(Player player, Item item) {
        if (player == null || item == null) return ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * 检查玩家是否有指定物品堆栈
     */
    public static boolean hasItemStack(Player player, ItemStack targetStack) {
        if (player == null || targetStack == null || targetStack.isEmpty()) {
            return false;
        }
        
        for (ItemStack stack : player.getInventory().items) {
            if (ItemStack.isSameItemSameTags(stack, targetStack)) {
                return true;
            }
        }
        return false;
    }

    public static void removeAllItem(Player player, Item item) {
        if (player == null || item == null) return;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                stack.setCount(0);
            }
        }
    }
    
    /**
     * 计算玩家拥有的指定物品数量
     */
    public static int countItem(Player player, Item item) {
        if (player == null || item == null) return 0;
        
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
    
    /**
     * 丢弃物品
     */
    public static void dropItem(LivingEntity entity, ItemStack itemStack) {
        if (entity == null || itemStack == null || itemStack.isEmpty() || entity.level.isClientSide) {
            return;
        }
        
        ItemEntity itemEntity = new ItemEntity(
                entity.level,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                itemStack.copy()  // 使用副本，避免修改原堆栈
        );
        
        itemEntity.setPickUpDelay(30);
        
        // 随机方向抛出
        double xSpeed = (entity.getRandom().nextDouble() - 0.5) * 0.1;
        double ySpeed = entity.getRandom().nextDouble() * 0.2 + 0.1;
        double zSpeed = (entity.getRandom().nextDouble() - 0.5) * 0.1;
        itemEntity.setDeltaMovement(xSpeed, ySpeed, zSpeed);
        
        entity.level.addFreshEntity(itemEntity);
    }

    public static void removeEnchant(ItemStack itemStack, Enchantment enchantment) {
        if (!itemStack.isEmpty()) {
            int curseLevel = itemStack.getEnchantmentLevel(enchantment);
            if (curseLevel > 0) {
                CompoundTag tag = itemStack.getOrCreateTag();
                if (tag.contains("Enchantments", 9)) {
                    ListTag enchantList = tag.getList("Enchantments", 10);
                    ListTag newList = new ListTag();
                    ResourceLocation curseId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
                    for (int i = 0; i < enchantList.size(); i++) {
                        CompoundTag enchantTag = enchantList.getCompound(i);
                        String id = enchantTag.getString("id");
                        if (curseId != null && !id.equals(curseId.toString())) {
                            newList.add(enchantTag);
                        }
                    }
                    if (newList.isEmpty()) {
                        tag.remove("Enchantments");
                    } else {
                        tag.put("Enchantments", newList);
                    }
                }
            }
        }
    }

    public static void addItem(Player player, ItemStack stack) {
        if (player == null || stack.isEmpty() || player.level.isClientSide) return;
        if (player.inventory.add(stack)) {
            dropItem(player, stack);
        }
    }
}