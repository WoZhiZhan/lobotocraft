package com.wzz.lobotocraft.item.ego.base;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Optional combat stance that an AI employee may activate after acquiring a target. */
public interface AiCombatModeEgoWeapon {
    void prepareAiCombat(Player player, ItemStack stack);
}
